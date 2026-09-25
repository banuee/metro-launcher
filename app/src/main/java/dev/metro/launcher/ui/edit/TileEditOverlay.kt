package dev.metro.launcher.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import dev.metro.launcher.ui.theme.FrostedGlassBox
import dev.metro.launcher.ui.theme.MetroAnimations
import dev.metro.launcher.ui.theme.MetroIcon
import dev.metro.launcher.ui.theme.MetroIcons
import dev.metro.launcher.ui.theme.metroBlurEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
    val scheme = LocalMetroScheme.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val stepX = with(density) { 45.dp.toPx() }
    val stepY = with(density) { 45.dp.toPx() }

    val handlesAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        handlesAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 200, easing = MetroAnimations.OpenEasing),
        )
    }

    // Актуальные колбэки и спаны без перезапуска детекторов жестов на каждой
    // рекомпозиции: иначе первый же ресайз пересоздаёт pill под пальцем и
    // обрывает жест — за одно тягание получался бы только один шаг.
    val latestStartDrag by rememberUpdatedState(onStartDrag)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestEndDrag by rememberUpdatedState(onEndDrag)
    val latestResize by rememberUpdatedState(onResize)
    val latestSpans by rememberUpdatedState(colSpan to rowSpan)

    // Поглощение нажатий и распознавание свободного 2D перетаскивания по телу плитки.
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

    // Внешняя окантовка выделения в акцентном цвете Metro с анимацией появления
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = handlesAnim.value
                scaleX = 0.88f + 0.12f * p
                scaleY = 0.88f + 0.12f * p
                alpha = p.coerceIn(0f, 1f)
            }
            .padding(1.dp)
            .border(
                width = 1.5.dp,
                color = scheme.accent.copy(alpha = 0.85f),
                shape = RoundedCornerShape(MetroDimens.radius + 1.dp),
            ),
    )

    // Верхняя пилюля: видимая часть 44x7, хитбокс 64x28 для пальца.
    var topDragY by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-4).dp)
            .size(width = 64.dp, height = 28.dp)
            .testTag(RESIZE_TOP_TAG)
            .graphicsLayer {
                val p = handlesAnim.value
                scaleX = 0.80f + 0.20f * p
                scaleY = 0.80f + 0.20f * p
                alpha = p.coerceIn(0f, 1f)
            }
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
                .size(width = 44.dp, height = 7.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFFE8E8E8).copy(alpha = 0.92f))
                .border(1.dp, scheme.accent.copy(alpha = 0.70f), RoundedCornerShape(percent = 50)),
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
            .graphicsLayer {
                val p = handlesAnim.value
                scaleX = 0.80f + 0.20f * p
                scaleY = 0.80f + 0.20f * p
                alpha = p.coerceIn(0f, 1f)
            }
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
                .size(width = 44.dp, height = 7.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFFE8E8E8).copy(alpha = 0.92f))
                .border(1.dp, scheme.accent.copy(alpha = 0.70f), RoundedCornerShape(percent = 50)),
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
            .graphicsLayer {
                val p = handlesAnim.value
                scaleX = 0.80f + 0.20f * p
                scaleY = 0.80f + 0.20f * p
                alpha = p.coerceIn(0f, 1f)
            }
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
                .size(width = 7.dp, height = 44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFFE8E8E8).copy(alpha = 0.92f))
                .border(1.dp, scheme.accent.copy(alpha = 0.70f), RoundedCornerShape(percent = 50)),
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
            .graphicsLayer {
                val p = handlesAnim.value
                scaleX = 0.80f + 0.20f * p
                scaleY = 0.80f + 0.20f * p
                alpha = p.coerceIn(0f, 1f)
            }
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
                .size(width = 7.dp, height = 44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFFE8E8E8).copy(alpha = 0.92f))
                .border(1.dp, scheme.accent.copy(alpha = 0.70f), RoundedCornerShape(percent = 50)),
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
    val density = LocalDensity.current

    // Snappy Hyprland popin 70% animation
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = MetroAnimations.OpenEasing),
        )
    }

    val slidePx = with(density) { 8.dp.toPx() }

    FrostedGlassBox(
        modifier = modifier
            .testTag(TILE_MENU_TAG)
            .graphicsLayer {
                val p = anim.value
                scaleX = 0.70f + 0.30f * p
                scaleY = 0.70f + 0.30f * p
                alpha = p.coerceIn(0f, 1f)
                translationY = (1f - p) * slidePx
            }
            .widthIn(min = 240.dp, max = 300.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(MetroDimens.panelRadius)),
        shape = MetroDimens.panelRadius,
        tint = scheme.glassDeep,
        borderColor = scheme.strokeStrong,
        borderWidth = 1.dp,
    ) {
        // Тонкий акцентный блик сверху как в quickshell metro-shot
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.5f)
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            scheme.accent.copy(alpha = 0.85f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Заголовок виджета / приложения
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(scheme.accent),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tileTitle,
                    color = scheme.text,
                    fontSize = 13.sp,
                    fontFamily = MetroFonts.headline,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(color = scheme.stroke, thickness = 1.dp)

            // 1. Открыть приложение
            if (onOpenApp != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MetroDimens.radiusSmall))
                        .background(scheme.glass)
                        .border(1.dp, scheme.stroke, RoundedCornerShape(MetroDimens.radiusSmall))
                        .metroClickable(targetScale = 0.96f, onClick = onOpenApp)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MetroIcon(
                            icon = MetroIcons.ExternalLink,
                            color = scheme.accent,
                            fontSize = 15.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Открыть приложение",
                        color = scheme.text,
                        fontSize = 14.sp,
                        fontFamily = MetroFonts.text,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 2. Подсказка про свободное перемещение и ресайз за края
            Text(
                text = "Тяните плитку, чтобы переместить. Тяните за края, чтобы изменить размер.",
                color = scheme.textDim,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontFamily = MetroFonts.text,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )

            Spacer(Modifier.height(6.dp))

            HorizontalDivider(color = scheme.stroke, thickness = 1.dp)

            Spacer(Modifier.height(8.dp))

            // 3. Удалить виджет / значок (аутентичный Metro WP Red)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MetroDimens.radiusSmall))
                    .background(scheme.red.copy(alpha = 0.12f))
                    .border(1.dp, scheme.red.copy(alpha = 0.35f), RoundedCornerShape(MetroDimens.radiusSmall))
                    .metroClickable(targetScale = 0.96f, onClick = onDelete)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(scheme.red.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center,
                ) {
                    MetroIcon(
                        icon = MetroIcons.Trash,
                        color = scheme.red,
                        fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isAppPin) "Удалить значок" else "Удалить виджет",
                    color = scheme.red,
                    fontSize = 14.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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

