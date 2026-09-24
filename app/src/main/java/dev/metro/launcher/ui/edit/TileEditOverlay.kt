package dev.metro.launcher.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.metro.launcher.data.HomeTileItem
import dev.metro.launcher.data.InternalWidgetType
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.metroClickable

/** Границы спанов — единые с HomeTileItem.copyWithSpans: 1..4 по ширине, 1..6 по высоте. */
private const val MIN_SPAN = 1
private const val MAX_COL_SPAN = 4
private const val MAX_ROW_SPAN = 6

/** Тест-теги для UI-тестов перемещения/ресайза. */
const val RESIZE_TOP_TAG = "resize_handle_top"
const val RESIZE_BOTTOM_TAG = "resize_handle_bottom"
const val RESIZE_LEFT_TAG = "resize_handle_left"
const val RESIZE_RIGHT_TAG = "resize_handle_right"
const val TILE_MENU_TAG = "tile_context_menu"

/** Тег плитки по id: tileTag("pin_phone") -> "tile_pin_phone". */
fun tileTag(id: String) = "tile_$id"

/**
 * Рамка выделения плитки и 4 белые рукоятки (пилюли) по краям в точности как в Smart Launcher:
 * - Сверху: горизонтальная пилюля (drag вверх/вниз для изменения высоты)
 * - Снизу: горизонтальная пилюля (drag вниз/вверх для изменения высоты)
 * - Слева: вертикальная пилюля (drag влево/вправо для изменения ширины)
 * - Справа: вертикальная пилюля (drag вправо/влево для изменения ширины)
 * - Тело плитки: свободное 2D-перетаскивание в любое место сетки (drop = reorder).
 * Ресайз — только тяганием за края, без пресетов и без тапов-циклов.
 */
@Composable
fun BoxScope.SmartLauncherHandles(
    colSpan: Int,
    rowSpan: Int,
    onResize: (newColSpan: Int, newRowSpan: Int) -> Unit,
    onStartDrag: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onEndDrag: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val stepX = with(density) { 45.dp.toPx() }
    val stepY = with(density) { 45.dp.toPx() }

    // Актуальные колбэки и спаны без перезапуска детекторов жестов на каждой
    // рекомпозиции: иначе первый же ресайз пересоздаёт pill под пальцем и
    // обрывает жест — за одно тягание получался бы только один шаг.
    val latestStartDrag by rememberUpdatedState(onStartDrag)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestEndDrag by rememberUpdatedState(onEndDrag)
    val latestResize by rememberUpdatedState(onResize)
    val latestSpans by rememberUpdatedState(colSpan to rowSpan)

    // Поглощение нажатий и распознавание свободного 2D перетаскивания по телу плитки.
    // Press гасим сразу на down (а не на слопе): иначе вертикальный жест
    // перехватывает nested-scroll сетки раньше слопа детектора — в режиме
    // редактирования плитку вообще нельзя было тянуть строго вверх/вниз,
    // сетка просто скроллилась. Гашение down также отменяет tap детей под
    // оверлеем (тап по выбранной плитке больше не открывает приложение)
    // и разоружает скролл для жестов с пилюль: они down не гасят, оверлей
    // под ними — да. Старт drag'а по-прежнему на слопе, чтобы тап не дёргал
    // меню и scale.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val slop = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    }
                    if (slop != null) {
                        latestStartDrag(down.position)
                        drag(slop.id) { change ->
                            latestDrag(change.position - change.previousPosition)
                            change.consume()
                        }
                    }
                    latestEndDrag()
                }
            },
    )

    // Внешняя окантовка выделения
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(1.dp)
            .border(
                width = 1.5.dp,
                color = Color.White.copy(alpha = 0.85f),
                shape = RoundedCornerShape(MetroDimens.radius + 1.dp),
            ),
    )

    // Верхняя пилюля: видимая часть 44x8, хитбокс 64x28 для пальца.
    var topDragY by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-4).dp)
            .size(width = 64.dp, height = 28.dp)
            .testTag(RESIZE_TOP_TAG)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { topDragY = 0f },
                    onDragCancel = { topDragY = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    val (curC, curR) = latestSpans
                    topDragY += dragAmount
                    if (topDragY < -stepY && curR < MAX_ROW_SPAN) {
                        topDragY += stepY
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestResize(curC, curR + 1)
                    } else if (topDragY > stepY && curR > MIN_SPAN) {
                        topDragY -= stepY
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestResize(curC, curR - 1)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White),
        )
    }

    // Нижняя пилюля
    var bottomDragY by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset(y = 4.dp)
            .size(width = 64.dp, height = 28.dp)
            .testTag(RESIZE_BOTTOM_TAG)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { bottomDragY = 0f },
                    onDragCancel = { bottomDragY = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    val (curC, curR) = latestSpans
                    bottomDragY += dragAmount
                    if (bottomDragY > stepY && curR < MAX_ROW_SPAN) {
                        bottomDragY -= stepY
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestResize(curC, curR + 1)
                    } else if (bottomDragY < -stepY && curR > MIN_SPAN) {
                        bottomDragY += stepY
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestResize(curC, curR - 1)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White),
        )
    }

    // Левая пилюля
    var leftDragX by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .offset(x = (-4).dp)
            .size(width = 28.dp, height = 64.dp)
            .testTag(RESIZE_LEFT_TAG)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { leftDragX = 0f },
                    onDragCancel = { leftDragX = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    val (curC, curR) = latestSpans
                    leftDragX += dragAmount
                    if (leftDragX < -stepX && curC < MAX_COL_SPAN) {
                        leftDragX += stepX
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestResize(curC + 1, curR)
                    } else if (leftDragX > stepX && curC > MIN_SPAN) {
                        leftDragX -= stepX
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestResize(curC - 1, curR)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 8.dp, height = 44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White),
        )
    }

    // Правая пилюля
    var rightDragX by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .offset(x = 4.dp)
            .size(width = 28.dp, height = 64.dp)
            .testTag(RESIZE_RIGHT_TAG)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { rightDragX = 0f },
                    onDragCancel = { rightDragX = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    val (curC, curR) = latestSpans
                    rightDragX += dragAmount
                    if (rightDragX > stepX && curC < MAX_COL_SPAN) {
                        rightDragX -= stepX
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestResize(curC + 1, curR)
                    } else if (rightDragX < -stepX && curC > MIN_SPAN) {
                        rightDragX += stepX
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestResize(curC - 1, curR)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 8.dp, height = 44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White),
        )
    }
}

/**
 * Контекстное всплывающее меню выбранного виджета:
 * - Заголовок (название виджета / приложения)
 * - Кнопка «Открыть приложение» (если применимо)
 * - Подсказка: перемещение — тяганием плитки, размер — тяганием за белые края
 * - Кнопка «Удалить виджет» / «Удалить значок» (красный цвет)
 *
 * Перемещение кнопками «Выше»/«Ниже» и пресеты размеров удалены:
 * плитка свободно перетаскивается в любое место сетки, а размер
 * меняется только тяганием за краевые рукоятки.
 */
@Composable
fun TileContextMenu(
    tileTitle: String,
    isAppPin: Boolean,
    onOpenApp: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = LocalMetroScheme.current

    Column(
        modifier = modifier
            .testTag(TILE_MENU_TAG)
            .widthIn(min = 230.dp, max = 290.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E).copy(alpha = 0.96f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        // Заголовок
        Text(
            text = tileTitle,
            color = scheme.textDim,
            fontSize = 13.sp,
            fontFamily = MetroFonts.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

        // 1. Открыть приложение
        if (onOpenApp != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .metroClickable(onClick = onOpenApp)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = scheme.text,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Открыть приложение",
                    color = scheme.text,
                    fontSize = 14.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // 2. Подсказка про свободное перемещение и ресайз за края
        Text(
            text = "Тяните плитку, чтобы переместить. Тяните за белые края, чтобы изменить размер.",
            color = scheme.textDim,
            fontSize = 12.sp,
            fontFamily = MetroFonts.text,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
        )

        Spacer(Modifier.height(4.dp))

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

        Spacer(Modifier.height(6.dp))

        // 3. Удалить виджет / значок
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .metroClickable(onClick = onDelete)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.RemoveCircleOutline,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (isAppPin) "Удалить значок" else "Удалить виджет",
                color = Color(0xFFFF5252),
                fontSize = 14.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Умное позиционирование меню: под виджетом, или над ним, если снизу мало места.
 */
class SmartLauncherPopupPositionProvider : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset {
        val margin = 16
        val spaceBelow = windowSize.height - anchorBounds.bottom
        val spaceAbove = anchorBounds.top
        val fitsBelow = spaceBelow >= popupContentSize.height + margin * 2
        val fitsAbove = spaceAbove >= popupContentSize.height + margin * 2
        val x = (anchorBounds.left + anchorBounds.right - popupContentSize.width) / 2
        val maxXWithoutMargin = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val minX = margin.coerceAtMost(maxXWithoutMargin)
        val maxX = (maxXWithoutMargin - margin).coerceAtLeast(minX)
        val clampedX = x.coerceIn(minX, maxX)

        val preferredY = when {
            fitsBelow -> anchorBounds.bottom + margin
            fitsAbove -> anchorBounds.top - popupContentSize.height - margin
            spaceBelow >= spaceAbove -> anchorBounds.bottom + margin
            else -> anchorBounds.top - popupContentSize.height - margin
        }
        val maxYWithoutMargin = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val minY = margin.coerceAtMost(maxYWithoutMargin)
        val maxY = (maxYWithoutMargin - margin).coerceAtLeast(minY)
        val clampedY = preferredY.coerceIn(minY, maxY)
        return androidx.compose.ui.unit.IntOffset(clampedX, clampedY)
    }
}

