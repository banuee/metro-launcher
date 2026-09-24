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

    /**
     * Applies a [DropDecision] to [list] and returns the rearranged layout.
     * Pure in-memory transformation: non-colliding tiles preserve coordinates,
     * collided tiles are relocated via nearest-free search, same-size items swap.
     */
    fun applyDecision(list: List<HomeTileItem>, decision: DropDecision): List<HomeTileItem> {
        return when (decision) {
            is DropDecision.Move -> {
                val target = list.find { it.id == decision.id } ?: return list
                val fromIndex = list.indexOfFirst { it.id == decision.id }
                val w = target.colSpan.coerceIn(1, COLS)
                val h = target.rowSpan.coerceAtLeast(1)
                val c = decision.col.coerceIn(0, COLS - w)
                val r = decision.row.coerceAtLeast(0)

                val colliding = list.filter { other ->
                    if (other.id == decision.id) return@filter false
                    val oc = other.col ?: return@filter false
                    val or = other.row ?: return@filter false
                    val ow = other.colSpan.coerceIn(1, COLS)
                    val oh = other.rowSpan.coerceAtLeast(1)
                    c < oc + ow && c + w > oc && r < or + oh && r + h > or
                }

                val mutable = list.toMutableList()
                val item = mutable.removeAt(fromIndex)
                val insertIndex = decision.toIndex?.coerceIn(0, mutable.size) ?: fromIndex.coerceIn(0, mutable.size)
                mutable.add(insertIndex, item.copyWithPosition(c, r))

                if (colliding.isEmpty()) {
                    packGrid(mutable)
                } else {
                    val occupied = mutableSetOf<Pair<Int, Int>>()
                    for (yy in r until r + h) {
                        for (xx in c until c + w) {
                            occupied.add(Pair(xx, yy))
                        }
                    }
                    val placedMap = mutableMapOf<String, Pair<Int, Int>>()
                    placedMap[decision.id] = Pair(c, r)

                    val toRelocate = colliding.toMutableList()
                    for (other in mutable) {
                        if (other.id == decision.id || colliding.any { it.id == other.id }) continue
                        val oc = other.col
                        val or = other.row
                        if (oc != null && or != null) {
                            val ow = other.colSpan.coerceIn(1, COLS)
                            val oh = other.rowSpan.coerceAtLeast(1)
                            var fits = true
                            for (yy in or until or + oh) {
                                for (xx in oc until oc + ow) {
                                    if (occupied.contains(Pair(xx, yy))) {
                                        fits = false
                                        break
                                    }
                                }
                                if (!fits) break
                            }
                            if (fits) {
                                for (yy in or until or + oh) {
                                    for (xx in oc until oc + ow) {
                                        occupied.add(Pair(xx, yy))
                                    }
                                }
                                placedMap[other.id] = Pair(oc, or)
                            } else {
                                toRelocate.add(other)
                            }
                        } else {
                            toRelocate.add(other)
                        }
                    }

                    for (reloc in toRelocate) {
                        val iw = reloc.colSpan.coerceIn(1, COLS)
                        val ih = reloc.rowSpan.coerceAtLeast(1)
                        val startC = reloc.col ?: c
                        val startR = reloc.row ?: r
                        var placed = false
                        for (ring in 0 until 60) {
                            for (dy in -ring..ring) {
                                val span = ring - kotlin.math.abs(dy)
                                for (s in -span..span) {
                                    val cx = startC + s
                                    val cy = startR + dy
                                    if (cx < 0 || cy < 0 || cx + iw > COLS || cy + ih > MAX_ROWS) continue
                                    var free = true
                                    for (yy in cy until cy + ih) {
                                        for (xx in cx until cx + iw) {
                                            if (occupied.contains(Pair(xx, yy))) {
                                                free = false
                                                break
                                            }
                                        }
                                        if (!free) break
                                    }
                                    if (free) {
                                        for (yy in cy until cy + ih) {
                                            for (xx in cx until cx + iw) {
                                                occupied.add(Pair(xx, yy))
                                            }
                                        }
                                        placedMap[reloc.id] = Pair(cx, cy)
                                        placed = true
                                        break
                                    }
                                }
                                if (placed) break
                            }
                            if (placed) break
                        }
                    }

                    val updated = mutable.map { itm ->
                        val pos = placedMap[itm.id]
                        if (pos != null) itm.copyWithPosition(pos.first, pos.second) else itm
                    }
                    packGrid(updated)
                }
            }
            is DropDecision.Swap -> {
                val a = list.find { it.id == decision.idA } ?: return list
                val b = list.find { it.id == decision.idB } ?: return list
                val aCol = a.col ?: 0
                val aRow = a.row ?: 0
                val bCol = b.col ?: 0
                val bRow = b.row ?: 0
                val indexA = list.indexOfFirst { it.id == decision.idA }
                val indexB = list.indexOfFirst { it.id == decision.idB }
                val mutable = list.toMutableList()
                mutable[indexA] = b.copyWithPosition(aCol, aRow)
                mutable[indexB] = a.copyWithPosition(bCol, bRow)
                packGrid(mutable)
            }
            DropDecision.None -> list
        }
    }

    /**
     * Calculates what the layout will look like if [draggedId] is dropped at (targetCol, targetRow).
     * If the drop would be a no-op (same position) or None, returns [tiles] unchanged.
     */
    fun previewDrop(
        tiles: List<HomeTileItem>,
        draggedId: String,
        targetCol: Int,
        targetRow: Int,
        toIndex: Int? = null,
    ): List<HomeTileItem> {
        val decision = evalDrop(tiles, draggedId, targetCol, targetRow, toIndex)
        if (decision is DropDecision.None) return tiles
        val applied = applyDecision(tiles, decision)
        val appliedMap = applied.associateBy { it.id }
        return tiles.map { original -> appliedMap[original.id] ?: original }
    }
}

