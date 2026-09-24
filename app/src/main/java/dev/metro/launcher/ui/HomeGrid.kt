package dev.metro.launcher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import dev.metro.launcher.data.AppIconLoader
import dev.metro.launcher.data.AppInfo
import dev.metro.launcher.data.GridPacker
import dev.metro.launcher.data.HomeTileItem
import dev.metro.launcher.data.InternalWidgetType
import dev.metro.launcher.data.NotesRepository
import dev.metro.launcher.data.PlayerRepository
import dev.metro.launcher.data.WeatherRepository
import dev.metro.launcher.ui.edit.SmartLauncherHandles
import dev.metro.launcher.ui.edit.SmartLauncherPopupPositionProvider
import dev.metro.launcher.ui.edit.TileContextMenu
import dev.metro.launcher.ui.edit.detectTilePressDrag
import dev.metro.launcher.ui.edit.tileTag
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.tiles.AndroidWidgetTile
import dev.metro.launcher.ui.tiles.AppTile
import dev.metro.launcher.ui.tiles.ClockTile
import dev.metro.launcher.ui.tiles.NotesTile
import dev.metro.launcher.ui.tiles.PlayerTile
import dev.metro.launcher.ui.tiles.WeatherExpandedPanel
import dev.metro.launcher.ui.tiles.WeatherTile
import dev.metro.launcher.ui.tiles.rememberWeatherUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Тег пустого футера сетки (зона для дропа мимо плиток + тесты). */
const val EMPTY_SPACE_TAG = "empty_space_footer"

/**
 * Главный экран Metro: истинная 2D-сетка из 4 колонок (архитектура QuickShell Metro).
 * Поддерживает:
 * - Свободное 2D-перетаскивание (drag & drop) плиток в любое место сетки:
 *   в пустые места, в дыры между плитками разной высоты, со свопом плиток одинакового размера
 *   или автопоиском nearestFree при конфликте разных размеров.
 * - Долгий клик по ЛЮБОМУ пустому месту или ячейке сетки: диалог добавления («Добавить виджет» / «Добавить значок»)
 *   с установкой плитки прямо в выбранную ячейку.
 * - Автоскролл сетки при перетаскивании к верхнему/нижнему краю.
 * - Рукоятки Smart Launcher для изменения размера только тяганием за края и контекстное меню.
 * - Плавная анимация сдвига и раскрытия панели погоды без дыр.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeGrid(
    tiles: List<HomeTileItem>,
    apps: List<AppInfo>,
    notesRepo: NotesRepository,
    weatherRepo: WeatherRepository,
    playerRepo: PlayerRepository,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onAppClick: (AppInfo) -> Unit,
    onOpenDrawer: () -> Unit,
    onClockClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onUpdateTileSpan: (id: String, colSpan: Int, rowSpan: Int) -> Unit,
    onMoveTile: (id: String, toIndex: Int) -> Unit = { _, _ -> },
    onDeleteTile: (id: String) -> Unit,
    onEmptyLongClick: () -> Unit,
    onDropDecision: (GridPacker.DropDecision) -> Unit = { },
    onEmptyCellLongClick: (col: Int, row: Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val weatherUi = rememberWeatherUi(weatherRepo)
    val latestTiles by rememberUpdatedState(tiles)
    var weatherExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var selectedTileId by remember { mutableStateOf<String?>(null) }
    var draggingTileId by remember { mutableStateOf<String?>(null) }

    var dragFingerRoot by remember { mutableStateOf(Offset.Zero) }
    var dragGrabDelta by remember { mutableStateOf(Offset.Zero) }
    var gridRectRoot by remember { mutableStateOf<Rect?>(null) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    val padding = 16.dp
    val gap = MetroDimens.gap
    val paddingPx = with(density) { padding.toPx() }
    val gapPx = with(density) { gap.toPx() }

    val weatherTile = tiles.find { it is HomeTileItem.InternalWidget && it.type == InternalWidgetType.WEATHER }
    val weatherRow = weatherTile?.row ?: 0
    val weatherRowSpan = weatherTile?.rowSpan ?: 2
    val weatherSplitRow = weatherRow + weatherRowSpan
    var weatherPanelHeightPx by remember { mutableStateOf(with(density) { 540.dp.toPx() }) }
    val weatherPanelHeightDp = with(density) { weatherPanelHeightPx.toDp() }
    val weatherPanelOffset = if (weatherExpanded && weatherTile != null && weatherPanelHeightPx > 0f) weatherPanelHeightDp + gap else 0.dp

    var previewLayout by remember { mutableStateOf<List<HomeTileItem>?>(null) }
    var lastPreviewTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragInitialPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Сброс выделения по кнопке "Назад"
    BackHandler(enabled = selectedTileId != null || draggingTileId != null) {
        autoScrollJob?.cancel()
        autoScrollJob = null
        previewLayout = null
        lastPreviewTarget = null
        dragInitialPosition = null
        selectedTileId = null
        draggingTileId = null
        dragFingerRoot = Offset.Zero
        dragGrabDelta = Offset.Zero
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                gridRectRoot = coords.boundsInRoot()
            }
            .pointerInput(selectedTileId, draggingTileId) {
                if (selectedTileId != null || draggingTileId != null) return@pointerInput
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (acc < -120f) onOpenDrawer()
                        acc = 0f
                    },
                ) { _, dragAmount -> acc += dragAmount }
            },
    ) {
        val contentWidth = (maxWidth - padding * 2).coerceAtLeast(0.dp)
        val colWidth = ((contentWidth - gap * 3) / 4).coerceAtLeast(0.dp)
        val rowHeight = colWidth
        val colWidthPx = with(density) { colWidth.toPx() }
        val rowHeightPx = with(density) { rowHeight.toPx() }

        fun tileSlotRect(id: String): Rect? {
            val grid = gridRectRoot ?: return null
            val tile = latestTiles.find { it.id == id } ?: return null
            val c = tile.col ?: 0
            val r = tile.row ?: 0
            val cs = tile.colSpan.coerceIn(1, 4)
            val rs = tile.rowSpan.coerceAtLeast(1)
            val isBelow = weatherExpanded && weatherTile != null && r >= weatherSplitRow
            val extraY = if (isBelow) with(density) { weatherPanelOffset.toPx() } else 0f

            val x = grid.left + paddingPx + c * (colWidthPx + gapPx)
            val y = grid.top + paddingPx + r * (rowHeightPx + gapPx) + extraY - scrollState.value
            val w = colWidthPx * cs + gapPx * (cs - 1)
            val h = rowHeightPx * rs + gapPx * (rs - 1)
            return Rect(Offset(x, y), Size(w, h))
        }

        fun startTileDrag(tile: HomeTileItem, touchOffset: Offset) {
            if (weatherExpanded) weatherExpanded = false
            val slot = tileSlotRect(tile.id)
            val finger = (slot?.topLeft ?: Offset.Zero) + touchOffset
            dragGrabDelta = (slot?.center ?: finger) - finger
            dragFingerRoot = finger
            dragInitialPosition = Pair(tile.col ?: 0, tile.row ?: 0)
            previewLayout = null
            lastPreviewTarget = null
            selectedTileId = tile.id
            draggingTileId = tile.id
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        fun onTileDrag(dragAmount: Offset) {
            val droppedId = draggingTileId ?: return
            dragFingerRoot += dragAmount
            val grid = gridRectRoot ?: return
            val currentCenter = dragFingerRoot + dragGrabDelta

            val edgePx = 160f
            val scrollDy = when {
                currentCenter.y < grid.top + edgePx -> -28f
                currentCenter.y > grid.bottom - edgePx -> 28f
                else -> 0f
            }
            if (scrollDy != 0f && autoScrollJob?.isActive != true) {
                autoScrollJob = scope.launch { scrollState.scrollBy(scrollDy) }
            }

            val current = latestTiles
            val draggedTile = current.find { it.id == droppedId }
            if (draggedTile != null) {
                val w = draggedTile.colSpan.coerceIn(1, GridPacker.COLS)
                val h = draggedTile.rowSpan.coerceAtLeast(1)
                val tileWPx = colWidthPx * w + gapPx * (w - 1)
                val tileHPx = rowHeightPx * h + gapPx * (h - 1)

                val dropTopLeftX = currentCenter.x - tileWPx / 2f
                val dropTopLeftY = currentCenter.y - tileHPx / 2f

                val contentX = dropTopLeftX - grid.left
                val contentY = dropTopLeftY - grid.top + scrollState.value

                val extraY = if (weatherExpanded && weatherTile != null) with(density) { weatherPanelOffset.toPx() } else 0f
                val weatherSplitY = paddingPx + (rowHeightPx + gapPx) * weatherSplitRow
                val adjustedContentY = if (weatherExpanded && weatherTile != null && contentY > weatherSplitY) {
                    if (contentY < weatherSplitY + extraY) weatherSplitY else contentY - extraY
                } else {
                    contentY
                }

                val targetCol = Math.round((contentX - paddingPx) / (colWidthPx + gapPx)).coerceIn(0, GridPacker.COLS - w)
                val targetRow = Math.round((adjustedContentY - paddingPx) / (rowHeightPx + gapPx)).coerceAtLeast(0)

                val target = Pair(targetCol, targetRow)
                if (target != lastPreviewTarget) {
                    lastPreviewTarget = target
                    val initPos = dragInitialPosition
                    if (initPos != null && targetCol == initPos.first && targetRow == initPos.second) {
                        previewLayout = null
                    } else {
                        var toIdx = 0
                        for (other in current) {
                            if (other.id == droppedId) continue
                            val slot = tileSlotRect(other.id) ?: continue
                            val cx = slot.center.x
                            val cy = slot.center.y
                            val halfH = slot.height / 2f
                            val before = cy + halfH <= currentCenter.y ||
                                (kotlin.math.abs(cy - currentCenter.y) < halfH && cx < currentCenter.x)
                            if (before) toIdx++
                        }
                        previewLayout = GridPacker.previewDrop(current, droppedId, targetCol, targetRow, toIdx)
                    }
                }
            }
        }

        fun endTileDrag() {
            autoScrollJob?.cancel()
            autoScrollJob = null
            previewLayout = null
            lastPreviewTarget = null
            val initPos = dragInitialPosition
            dragInitialPosition = null
            val droppedId = draggingTileId
            draggingTileId = null
            if (droppedId != null) {
                val current = latestTiles
                val draggedTile = current.find { it.id == droppedId }
                val grid = gridRectRoot
                if (draggedTile != null && grid != null) {
                    val dropCenter = dragFingerRoot + dragGrabDelta
                    val w = draggedTile.colSpan.coerceIn(1, GridPacker.COLS)
                    val h = draggedTile.rowSpan.coerceAtLeast(1)
                    val tileWPx = colWidthPx * w + gapPx * (w - 1)
                    val tileHPx = rowHeightPx * h + gapPx * (h - 1)

                    val dropTopLeftX = dropCenter.x - tileWPx / 2f
                    val dropTopLeftY = dropCenter.y - tileHPx / 2f

                    val contentX = dropTopLeftX - grid.left
                    val contentY = dropTopLeftY - grid.top + scrollState.value

                    val contentCenterX = dropCenter.x - grid.left
                    val contentCenterY = dropCenter.y - grid.top + scrollState.value

                    val targetCol = Math.round((contentX - paddingPx) / (colWidthPx + gapPx))
                    val extraY = if (weatherExpanded && weatherTile != null) with(density) { weatherPanelOffset.toPx() } else 0f
                    val weatherSplitY = paddingPx + (rowHeightPx + gapPx) * weatherSplitRow
                    val adjustedContentY = if (weatherExpanded && weatherTile != null && contentY > weatherSplitY) {
                        if (contentY < weatherSplitY + extraY) weatherSplitY else contentY - extraY
                    } else {
                        contentY
                    }
                    val targetRow = Math.round((adjustedContentY - paddingPx) / (rowHeightPx + gapPx))

                    val clampedCol = targetCol.coerceIn(0, GridPacker.COLS - w)
                    val clampedRow = targetRow.coerceAtLeast(0)

                    // Считаем reading-order индекс дропа относительно слотов остальных плиток
                    var toIndex = 0
                    for (other in current) {
                        if (other.id == droppedId) continue
                        val slot = tileSlotRect(other.id) ?: continue
                        val cx = slot.center.x
                        val cy = slot.center.y
                        val halfH = slot.height / 2f
                        val before = cy + halfH <= dropCenter.y ||
                            (kotlin.math.abs(cy - dropCenter.y) < halfH && cx < dropCenter.x)
                        if (before) toIndex++
                    }

                    val footerRow = (current.maxOfOrNull { (it.row ?: 0) + it.rowSpan } ?: 0)
                    val footerContentY = paddingPx + (rowHeightPx + gapPx) * footerRow + extraY
                    val footerScreenY = grid.top + footerContentY - scrollState.value

                    val isFooterDrop = targetRow >= footerRow ||
                        contentCenterY >= footerContentY - 20f ||
                        dropCenter.y >= footerScreenY - 20f

                    val fromIndex = current.indexOfFirst { it.id == droppedId }
                    val finalRow = if (isFooterDrop) maxOf(footerRow, clampedRow) else clampedRow
                    val endIdx = current.size - 1
                    val finalToIndex = if (isFooterDrop) {
                        endIdx
                    } else if (fromIndex != -1 && toIndex == fromIndex && clampedRow > (draggedTile.row ?: 0)) {
                        (fromIndex + 1).coerceAtMost(endIdx)
                    } else {
                        toIndex
                    }

                    val decision = GridPacker.evalDrop(current, droppedId, clampedCol, finalRow, finalToIndex)
                    if (decision !is GridPacker.DropDecision.None) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDropDecision(decision)
                    } else {
                        val own = tileSlotRect(droppedId)
                        if (own != null) {
                            val m = 8f
                            val outside = dropCenter.x < own.left - m || dropCenter.x > own.right + m ||
                                dropCenter.y < own.top - m || dropCenter.y > own.bottom + m
                            if (outside) {
                                val dx = dropCenter.x - own.center.x
                                val dy = dropCenter.y - own.center.y
                                val adjTo = if (kotlin.math.abs(dy) >= kotlin.math.abs(dx)) {
                                    if (dy > 0) fromIndex + 1 else fromIndex - 1
                                } else {
                                    if (dx > 0) fromIndex + 1 else fromIndex - 1
                                }
                                val clampedTo = adjTo.coerceIn(0, current.size - 1)
                                if (fromIndex != -1 && clampedTo != fromIndex) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onMoveTile(droppedId, clampedTo)
                                }
                            }
                        }
                    }
                }
            }
            dragFingerRoot = Offset.Zero
            dragGrabDelta = Offset.Zero
        }

        val displayTiles = previewLayout ?: latestTiles
        val footerRow = (latestTiles.maxOfOrNull { (it.row ?: 0) + it.rowSpan } ?: 0)
        val totalGridHeight = padding * 2 + (rowHeight + gap) * footerRow + (if (weatherExpanded) weatherPanelOffset else 0.dp) + 240.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = selectedTileId == null && draggingTileId == null),
        ) {
            // Фон сетки: тап снимает выделение, долгий тап по пустой ячейке открывает меню добавления в эту ячейку
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalGridHeight)
                    .pointerInput(selectedTileId, latestTiles) {
                        detectTapGestures(
                            onTap = { selectedTileId = null },
                            onLongPress = { touchOffset ->
                                if (selectedTileId == null) {
                                    val touchX = touchOffset.x
                                    val touchY = touchOffset.y
                                    val extraY = if (weatherExpanded && weatherTile != null) with(density) { weatherPanelOffset.toPx() } else 0f
                                    val weatherSplitY = paddingPx + (rowHeightPx + gapPx) * weatherSplitRow
                                    if (weatherExpanded && weatherTile != null && touchY >= weatherSplitY && touchY < weatherSplitY + extraY) {
                                        return@detectTapGestures
                                    }
                                    val adjustedTouchY = if (weatherExpanded && weatherTile != null && touchY >= weatherSplitY + extraY) {
                                        touchY - extraY
                                    } else {
                                        touchY
                                    }
                                    val col = ((touchX - paddingPx) / (colWidthPx + gapPx)).toInt().coerceIn(0, 3)
                                    val row = ((adjustedTouchY - paddingPx) / (rowHeightPx + gapPx)).toInt().coerceAtLeast(0)
                                    val hits = GridPacker.rectHits(latestTiles, col, row, 1, 1)
                                    if (hits == null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onEmptyCellLongClick(col, row)
                                        onEmptyLongClick()
                                    }
                                } else {
                                    selectedTileId = null
                                }
                            },
                        )
                    },
            ) {
                // Drop target footprint preview indicator
                val previewTarget = lastPreviewTarget
                val draggedItem = if (draggingTileId != null) latestTiles.find { it.id == draggingTileId } else null
                if (previewTarget != null && draggedItem != null && previewLayout != null) {
                    val pCol = previewTarget.first
                    val pRow = previewTarget.second
                    val pColSpan = draggedItem.colSpan.coerceIn(1, 4)
                    val pRowSpan = draggedItem.rowSpan.coerceIn(1, 6)
                    val pIsBelowWeather = weatherExpanded && weatherTile != null && pRow >= weatherSplitRow
                    val pRowOffsetDp = if (pIsBelowWeather) weatherPanelOffset else 0.dp

                    val pXDp = padding + (colWidth + gap) * pCol
                    val pYDp = padding + (rowHeight + gap) * pRow + pRowOffsetDp
                    val pWidthDp = colWidth * pColSpan + gap * (pColSpan - 1)
                    val pHeightDp = rowHeight * pRowSpan + gap * (pRowSpan - 1)

                    Box(
                        modifier = Modifier
                            .offset(x = pXDp, y = pYDp)
                            .size(width = pWidthDp, height = pHeightDp)
                            .zIndex(0.5f)
                            .border(
                                width = 1.5.dp,
                                color = Color.White.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(MetroDimens.radius),
                            )
                            .background(
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(MetroDimens.radius),
                            ),
                    )
                }

                // Отрисовка плиток
                displayTiles.forEach { tile ->
                    val isSelected = selectedTileId == tile.id
                    val isDragging = draggingTileId == tile.id

                    val origPos = if (isDragging) dragInitialPosition else null
                    val col = origPos?.first ?: (tile.col ?: 0)
                    val row = origPos?.second ?: (tile.row ?: 0)
                    val colSpan = tile.colSpan.coerceIn(1, 4)
                    val rowSpan = tile.rowSpan.coerceIn(1, 6)
                    val isBelowWeather = weatherExpanded && weatherTile != null && row >= weatherSplitRow
                    val rowOffsetDp = if (isBelowWeather) weatherPanelOffset else 0.dp

                    val targetXDp = padding + (colWidth + gap) * col
                    val targetYDp = padding + (rowHeight + gap) * row + rowOffsetDp
                    val targetWidthDp = colWidth * colSpan + gap * (colSpan - 1)
                    val targetHeightDp = rowHeight * rowSpan + gap * (rowSpan - 1)

                    val animatedX by animateDpAsState(
                        targetValue = targetXDp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "x_${tile.id}",
                    )
                    val animatedY by animateDpAsState(
                        targetValue = targetYDp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "y_${tile.id}",
                    )
                    val animatedWidth by animateDpAsState(
                        targetValue = targetWidthDp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "w_${tile.id}",
                    )
                    val animatedHeight by animateDpAsState(
                        targetValue = targetHeightDp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "h_${tile.id}",
                    )

                    val dragTranslation = if (isDragging) {
                        val slot = tileSlotRect(tile.id)
                        if (slot != null) {
                            (dragFingerRoot + dragGrabDelta) - slot.center
                        } else Offset.Zero
                    } else Offset.Zero

                    Box(
                        modifier = Modifier
                            .offset(x = animatedX, y = animatedY)
                            .size(width = animatedWidth, height = animatedHeight)
                            .testTag(tileTag(tile.id))
                            .zIndex(if (isDragging) 100f else if (isSelected) 50f else 1f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationX = dragTranslation.x
                                    translationY = dragTranslation.y
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                    shadowElevation = 24f
                                }
                            }
                            .pointerInput(tile.id) {
                                detectTilePressDrag(
                                    onGrab = { offset -> startTileDrag(tile, offset) },
                                    onDrag = { dragAmount -> onTileDrag(dragAmount) },
                                    onRelease = { endTileDrag() },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (tile) {
                            is HomeTileItem.InternalWidget -> {
                                when (tile.type) {
                                    InternalWidgetType.CLOCK -> {
                                        ClockTile(
                                            onClickClock = onClockClick,
                                            onClickCalendar = onCalendarClick,
                                            height = animatedHeight,
                                            onLongPress = { selectedTileId = tile.id },
                                        )
                                    }
                                    InternalWidgetType.WEATHER -> {
                                        WeatherTile(
                                            ui = weatherUi,
                                            onClick = { weatherExpanded = !weatherExpanded },
                                            height = animatedHeight,
                                            onLongPress = { selectedTileId = tile.id },
                                        )
                                    }
                                    InternalWidgetType.NOTES -> {
                                        NotesTile(
                                            repo = notesRepo,
                                            height = animatedHeight,
                                            onLongPress = { selectedTileId = tile.id },
                                        )
                                    }
                                    InternalWidgetType.PLAYER -> {
                                        PlayerTile(
                                            repo = playerRepo,
                                            height = animatedHeight,
                                            onLongPress = { selectedTileId = tile.id },
                                        )
                                    }
                                }
                            }
                            is HomeTileItem.AppPin -> {
                                val initialApp = remember(tile.packageName, apps) {
                                    apps.find { it.packageName == tile.packageName } ?: AppInfo(
                                        label = tile.packageName.substringAfterLast('.'),
                                        packageName = tile.packageName,
                                        icon = context.packageManager.defaultActivityIcon,
                                    )
                                }
                                val app by produceState(
                                    initialValue = initialApp,
                                    tile.packageName,
                                    apps,
                                ) {
                                    val listedApp = apps.find { it.packageName == tile.packageName }
                                    value = listedApp
                                        ?: AppIconLoader.loadAppInfo(context, tile.packageName)
                                        ?: initialApp
                                }
                                AppTile(
                                    app = app,
                                    onClick = { onAppClick(app) },
                                    height = animatedHeight,
                                    colSpan = colSpan,
                                    rowSpan = rowSpan,
                                    onLongPress = { selectedTileId = tile.id },
                                )
                            }
                            is HomeTileItem.AndroidWidget -> {
                                AndroidWidgetTile(
                                    appWidgetHost = appWidgetHost,
                                    appWidgetManager = appWidgetManager,
                                    item = tile,
                                    onLongPress = { selectedTileId = tile.id },
                                )
                            }
                        }

                        // Рукоятки Smart Launcher при выделении
                        if (isSelected) {
                            SmartLauncherHandles(
                                colSpan = colSpan,
                                rowSpan = rowSpan,
                                onResize = { newC, newR ->
                                    onUpdateTileSpan(tile.id, newC, newR)
                                },
                                onStartDrag = { offset ->
                                    startTileDrag(tile, offset)
                                },
                                onDrag = { dragAmount ->
                                    onTileDrag(dragAmount)
                                },
                                onEndDrag = {
                                    endTileDrag()
                                },
                            )
                        }

                        // Всплывающее меню
                        if (isSelected && draggingTileId == null) {
                            Popup(
                                popupPositionProvider = remember { SmartLauncherPopupPositionProvider() },
                                onDismissRequest = { selectedTileId = null },
                                properties = PopupProperties(
                                    focusable = false,
                                    dismissOnClickOutside = false,
                                ),
                            ) {
                                val title = when (tile) {
                                    is HomeTileItem.AppPin -> {
                                        apps.find { it.packageName == tile.packageName }?.label ?: tile.packageName
                                    }
                                    is HomeTileItem.InternalWidget -> when (tile.type) {
                                        InternalWidgetType.CLOCK -> "Часы и календарь"
                                        InternalWidgetType.WEATHER -> "Погода"
                                        InternalWidgetType.NOTES -> "Заметки"
                                        InternalWidgetType.PLAYER -> "Плеер"
                                    }
                                    is HomeTileItem.AndroidWidget -> {
                                        val info = try {
                                            appWidgetManager.getAppWidgetInfo(tile.appWidgetId)
                                        } catch (_: Exception) {
                                            null
                                        }
                                        info?.loadLabel(context.packageManager) ?: tile.packageName
                                    }
                                }

                                val openAppAction: (() -> Unit)? = when (tile) {
                                    is HomeTileItem.AppPin -> {
                                        val app = apps.find { it.packageName == tile.packageName }
                                        if (app != null) {
                                            {
                                                selectedTileId = null
                                                onAppClick(app)
                                            }
                                        } else null
                                    }
                                    is HomeTileItem.InternalWidget -> when (tile.type) {
                                        InternalWidgetType.CLOCK -> {
                                            {
                                                selectedTileId = null
                                                onClockClick()
                                            }
                                        }
                                        InternalWidgetType.WEATHER -> {
                                            {
                                                selectedTileId = null
                                                weatherExpanded = !weatherExpanded
                                            }
                                        }
                                        InternalWidgetType.NOTES -> null
                                        InternalWidgetType.PLAYER -> null
                                    }
                                    is HomeTileItem.AndroidWidget -> {
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage(tile.packageName)
                                        if (launchIntent != null) {
                                            {
                                                selectedTileId = null
                                                try {
                                                    context.startActivity(launchIntent)
                                                } catch (_: Exception) {}
                                            }
                                        } else null
                                    }
                                }

                                TileContextMenu(
                                    tileTitle = title,
                                    isAppPin = tile is HomeTileItem.AppPin,
                                    onOpenApp = openAppAction,
                                    onDelete = {
                                        onDeleteTile(tile.id)
                                        selectedTileId = null
                                    },
                                )
                            }
                        }
                    }
                }

                // Панель погоды раскрывается строго под плиткой погоды
                if (weatherExpanded && weatherTile != null) {
                    val panelTopY = padding + (rowHeight + gap) * weatherSplitRow
                    Box(
                        modifier = Modifier
                            .offset(x = padding, y = panelTopY)
                            .width(contentWidth)
                            .zIndex(10f)
                            .onSizeChanged { size ->
                                if (size.height > 0) {
                                    weatherPanelHeightPx = size.height.toFloat()
                                }
                            }
                            .animateContentSize(),
                    ) {
                        WeatherExpandedPanel(repo = weatherRepo, ui = weatherUi)
                    }
                }

                // Пустой футер сетки для удобного тапа и дропа в конец
                Box(
                    modifier = Modifier
                        .offset(
                            x = padding,
                            y = padding + (rowHeight + gap) * footerRow + (if (weatherExpanded) weatherPanelOffset else 0.dp),
                        )
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag(EMPTY_SPACE_TAG)
                        .combinedClickable(
                            onClick = { selectedTileId = null },
                            onLongClick = {
                                if (selectedTileId == null) {
                                    onEmptyCellLongClick(0, footerRow)
                                    onEmptyLongClick()
                                } else {
                                    selectedTileId = null
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Spacer(Modifier.fillMaxSize())
                }
            }
        }
    }
}
