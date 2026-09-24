package dev.metro.launcher.data

import kotlin.math.abs

/**
 * 2D Grid Packer and Collision Engine for Metro Launcher.
 * Implements 2D tile placement, boundary checks, collision detection (rectHits),
 * nearest free spot search (nearestFree), and drop evaluation (evalDrop),
 * mirroring the QuickShell Metro shell architecture.
 */
object GridPacker {
    const val COLS = 4
    const val MAX_ROWS = 200

    /**
     * Checks if a rectangle at (col, row) with size (colSpan, rowSpan) intersects
     * with any placed tile in [tiles].
     * Ignores tile with id == [skipId] (e.g. tile currently being dragged).
     * Returns the first colliding tile or null if spot is clear.
     */
    fun rectHits(
        tiles: List<HomeTileItem>,
        col: Int,
        row: Int,
        colSpan: Int,
        rowSpan: Int,
        skipId: String? = null,
    ): HomeTileItem? {
        val w = colSpan.coerceIn(1, COLS)
        val h = rowSpan.coerceAtLeast(1)
        for (item in tiles) {
            if (item.id == skipId) continue
            val itemCol = item.col ?: continue
            val itemRow = item.row ?: continue
            val itemW = item.colSpan.coerceIn(1, COLS)
            val itemH = item.rowSpan.coerceAtLeast(1)

            // Bounding box overlap test
            if (col < itemCol + itemW && col + w > itemCol &&
                row < itemRow + itemH && row + h > itemRow
            ) {
                return item
            }
        }
        return null
    }

    /**
     * Searches in expanding rings around (gcx, gry) for the closest free spot
     * that can fit a tile of size (colSpan, rowSpan) without collisions.
     */
    fun nearestFree(
        tiles: List<HomeTileItem>,
        colSpan: Int,
        rowSpan: Int,
        gcx: Int,
        gry: Int,
        skipId: String? = null,
        maxRows: Int = MAX_ROWS,
    ): Pair<Int, Int>? {
        val w = colSpan.coerceIn(1, COLS)
        val h = rowSpan.coerceAtLeast(1)
        for (r in 0 until 60) {
            for (dy in -r..r) {
                val span = r - abs(dy)
                for (s in -span..span) {
                    val cx = gcx + s
                    val cy = gry + dy
                    if (cx < 0 || cy < 0 || cx + w > COLS || cy + h > maxRows) {
                        continue
                    }
                    if (rectHits(tiles, cx, cy, w, h, skipId) == null) {
                        return Pair(cx, cy)
                    }
                }
            }
        }
        return null
    }

    /**
     * Packs all tiles into non-overlapping grid positions:
     * 1. Tiles with existing valid (col, row) keep their positions if not colliding.
     * 2. Any tiles with missing or colliding (col, row) are packed via first-fit.
     * Results are sorted in standard reading order (row * COLS + col).
     */
    fun packGrid(
        tiles: List<HomeTileItem>,
        maxRows: Int = MAX_ROWS,
    ): List<HomeTileItem> {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        val placedMap = mutableMapOf<String, Pair<Int, Int>>()
        val pending = mutableListOf<HomeTileItem>()

        // 1. Preserve valid explicit positions
        for (item in tiles) {
            val col = item.col
            val row = item.row
            val w = item.colSpan.coerceIn(1, COLS)
            val h = item.rowSpan.coerceAtLeast(1)

            var fits = false
            if (col != null && row != null && col >= 0 && row >= 0 && col + w <= COLS && row + h <= maxRows) {
                var collision = false
                for (cy in row until row + h) {
                    for (cx in col until col + w) {
                        if (occupied.contains(Pair(cx, cy))) {
                            collision = true
                            break
                        }
                    }
                    if (collision) break
                }
                if (!collision) {
                    fits = true
                    for (cy in row until row + h) {
                        for (cx in col until col + w) {
                            occupied.add(Pair(cx, cy))
                        }
                    }
                    placedMap[item.id] = Pair(col, row)
                }
            }
            if (!fits) {
                pending.add(item)
            }
        }

        // 2. First-fit for pending tiles
        for (item in pending) {
            val w = item.colSpan.coerceIn(1, COLS)
            val h = item.rowSpan.coerceAtLeast(1)
            var placed = false
            for (cy in 0..maxRows - h) {
                for (cx in 0..COLS - w) {
                    var collision = false
                    for (yy in cy until cy + h) {
                        for (xx in cx until cx + w) {
                            if (occupied.contains(Pair(xx, yy))) {
                                collision = true
                                break
                            }
                        }
                        if (collision) break
                    }
                    if (!collision) {
                        for (yy in cy until cy + h) {
                            for (xx in cx until cx + w) {
                                occupied.add(Pair(xx, yy))
                            }
                        }
                        placedMap[item.id] = Pair(cx, cy)
                        placed = true
                        break
                    }
                }
                if (placed) break
            }
        }

        return tiles.map { item ->
            val pos = placedMap[item.id]
            if (pos != null) item.copyWithPosition(pos.first, pos.second) else item
        }
    }

    sealed class DropDecision {
        data class Move(val id: String, val col: Int, val row: Int, val toIndex: Int? = null) : DropDecision()
        data class Swap(val idA: String, val idB: String) : DropDecision()
        object None : DropDecision()
    }

    /**
     * Evaluates what should happen when tile [draggedId] is dropped at grid cell (targetCol, targetRow).
     * - If cell is empty and fits without collision: Move tile directly to (targetCol, targetRow).
     * - If cell hits a single tile of identical dimensions: Swap positions with it.
     * - If cell hits tile(s) of different dimensions: Move tile to (targetCol, targetRow),
     *   allowing the layout repository to place it and relocate displaced tiles cleanly.
     */
    fun evalDrop(
        tiles: List<HomeTileItem>,
        draggedId: String,
        targetCol: Int,
        targetRow: Int,
        toIndex: Int? = null,
    ): DropDecision {
        val cur = tiles.find { it.id == draggedId } ?: return DropDecision.None
        val w = cur.colSpan.coerceIn(1, COLS)
        val h = cur.rowSpan.coerceAtLeast(1)
        val gcx = targetCol.coerceIn(0, COLS - w)
        val gry = targetRow.coerceAtLeast(0)

        // Dropping on the exact same location is a no-op
        if (cur.col == gcx && cur.row == gry) {
            return DropDecision.None
        }

        val hit = rectHits(tiles, gcx, gry, w, h, skipId = draggedId)
        if (hit == null) {
            return DropDecision.Move(draggedId, gcx, gry, toIndex)
        }

        if (hit.colSpan == w && hit.rowSpan == h) {
            return DropDecision.Swap(draggedId, hit.id)
        }

        return DropDecision.Move(draggedId, gcx, gry, toIndex)
    }
}
