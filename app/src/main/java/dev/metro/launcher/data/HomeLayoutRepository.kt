package dev.metro.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException

private val Context.layoutStore by preferencesDataStore(name = "metro_layout")

/**
 * Репозиторий сетки главного экрана: сохранение списка плиток, их порядков, типов и размеров (colSpan/rowSpan).
 */
class HomeLayoutRepository(private val context: Context) {
    private val key = stringPreferencesKey("tiles_layout_json")

    val tiles: Flow<List<HomeTileItem>> = context.layoutStore.data.catch { e ->
        // Битый/нечитаемый файл — отдаём пустые prefs, чтобы map корректно
        // откатился на дефолт; остальные ошибки не маскируем.
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { prefs ->
        val list = parse(prefs[key]) ?: defaultTiles()
        GridPacker.packGrid(list)
    }

    suspend fun addTile(tile: HomeTileItem, targetCol: Int? = null, targetRow: Int? = null) = update { list ->
        val w = tile.colSpan.coerceIn(1, GridPacker.COLS)
        val h = tile.rowSpan.coerceAtLeast(1)
        val candidate = if (targetCol != null && targetRow != null) {
            val gcx = targetCol.coerceIn(0, GridPacker.COLS - w)
            val gry = targetRow.coerceAtLeast(0)
            if (GridPacker.rectHits(list, gcx, gry, w, h) == null) {
                tile.copyWithPosition(gcx, gry)
            } else {
                val spot = GridPacker.nearestFree(list, w, h, gcx, gry)
                if (spot != null) tile.copyWithPosition(spot.first, spot.second) else tile
            }
        } else if (tile.col != null && tile.row != null && GridPacker.rectHits(list, tile.col!!, tile.row!!, w, h) == null) {
            tile
        } else {
            val spot = GridPacker.nearestFree(list, w, h, 0, 0)
            if (spot != null) tile.copyWithPosition(spot.first, spot.second) else tile
        }
        GridPacker.packGrid(list + candidate)
    }

    suspend fun removeTile(id: String) = update { list ->
        list.filter { it.id != id }
    }

    suspend fun updateTileSpan(id: String, colSpan: Int, rowSpan: Int) = update { list ->
        val item = list.find { it.id == id } ?: return@update list
        val c = colSpan.coerceIn(1, GridPacker.COLS)
        val r = rowSpan.coerceIn(1, 6)
        val curCol = item.col ?: 0
        val curRow = item.row ?: 0
        val maxCol = (GridPacker.COLS - c).coerceAtLeast(0)
        val adjCol = curCol.coerceAtMost(maxCol)

        val hits = GridPacker.rectHits(list, adjCol, curRow, c, r, skipId = id) != null
        val (finalCol, finalRow) = if (hits) {
            GridPacker.nearestFree(list, c, r, adjCol, curRow, skipId = id) ?: Pair(adjCol, curRow)
        } else {
            Pair(adjCol, curRow)
        }

        val updated = list.map {
            if (it.id == id) {
                it.copyWithSpans(c, r).copyWithPosition(finalCol, finalRow)
            } else {
                it
            }
        }
        GridPacker.packGrid(updated)
    }

    suspend fun applyDropDecision(decision: GridPacker.DropDecision) = update { list ->
        when (decision) {
            is GridPacker.DropDecision.Move -> {
                val target = list.find { it.id == decision.id } ?: return@update list
                val fromIndex = list.indexOfFirst { it.id == decision.id }
                val w = target.colSpan.coerceIn(1, GridPacker.COLS)
                val h = target.rowSpan.coerceAtLeast(1)
                val c = decision.col.coerceIn(0, GridPacker.COLS - w)
                val r = decision.row.coerceAtLeast(0)

                val colliding = list.filter { other ->
                    if (other.id == decision.id) return@filter false
                    val oc = other.col ?: return@filter false
                    val or = other.row ?: return@filter false
                    val ow = other.colSpan.coerceIn(1, GridPacker.COLS)
                    val oh = other.rowSpan.coerceAtLeast(1)
                    c < oc + ow && c + w > oc && r < or + oh && r + h > or
                }

                val mutable = list.toMutableList()
                val item = mutable.removeAt(fromIndex)
                val insertIndex = decision.toIndex?.coerceIn(0, mutable.size) ?: fromIndex.coerceIn(0, mutable.size)
                mutable.add(insertIndex, item.copyWithPosition(c, r))

                if (colliding.isEmpty()) {
                    GridPacker.packGrid(mutable)
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
                            val ow = other.colSpan.coerceIn(1, GridPacker.COLS)
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
                        val iw = reloc.colSpan.coerceIn(1, GridPacker.COLS)
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
                                    if (cx < 0 || cy < 0 || cx + iw > GridPacker.COLS || cy + ih > GridPacker.MAX_ROWS) continue
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
                    GridPacker.packGrid(updated)
                }
            }
            is GridPacker.DropDecision.Swap -> {
                val a = list.find { it.id == decision.idA } ?: return@update list
                val b = list.find { it.id == decision.idB } ?: return@update list
                val aCol = a.col ?: 0
                val aRow = a.row ?: 0
                val bCol = b.col ?: 0
                val bRow = b.row ?: 0
                val indexA = list.indexOfFirst { it.id == decision.idA }
                val indexB = list.indexOfFirst { it.id == decision.idB }
                val mutable = list.toMutableList()
                mutable[indexA] = b.copyWithPosition(aCol, aRow)
                mutable[indexB] = a.copyWithPosition(bCol, bRow)
                GridPacker.packGrid(mutable)
            }
            GridPacker.DropDecision.None -> list
        }
    }

    suspend fun moveTileToPosition(id: String, col: Int, row: Int) =
        applyDropDecision(GridPacker.DropDecision.Move(id, col, row))

    suspend fun swapTiles(idA: String, idB: String) =
        applyDropDecision(GridPacker.DropDecision.Swap(idA, idB))

    suspend fun moveTileTo(id: String, toIndex: Int) = update { list ->
        val fromIndex = list.indexOfFirst { it.id == id }
        if (fromIndex == -1) return@update list
        val mutable = list.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex.coerceIn(0, mutable.size), item)
        val reset = mutable.map { it.copyWithPosition(null, null) }
        GridPacker.packGrid(reset)
    }

    suspend fun resetToDefault() {
        context.layoutStore.edit { prefs ->
            prefs[key] = serialize(defaultTiles())
        }
    }

    private suspend fun update(fn: (List<HomeTileItem>) -> List<HomeTileItem>) {
        context.layoutStore.edit { prefs ->
            val cur = parse(prefs[key]) ?: defaultTiles()
            prefs[key] = serialize(fn(cur))
        }
    }

    private fun parse(json: String?): List<HomeTileItem>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            if (arr.length() == 0) return emptyList()
            val result = mutableListOf<HomeTileItem>()
            for (i in 0 until arr.length()) {
                val item = HomeTileItem.fromJson(arr.getJSONObject(i)) ?: return null
                result.add(item)
            }
            GridPacker.packGrid(result)
        } catch (_: Exception) {
            null
        }
    }

    private fun serialize(items: List<HomeTileItem>): String {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    companion object {
        fun defaultTiles(): List<HomeTileItem> = listOf(
            HomeTileItem.InternalWidget(
                id = "widget_clock",
                type = InternalWidgetType.CLOCK,
                colSpan = 2,
                rowSpan = 2,
                col = 0,
                row = 0,
            ),
            HomeTileItem.InternalWidget(
                id = "widget_weather",
                type = InternalWidgetType.WEATHER,
                colSpan = 2,
                rowSpan = 2,
                col = 2,
                row = 0,
            ),
            HomeTileItem.InternalWidget(
                id = "widget_notes",
                type = InternalWidgetType.NOTES,
                colSpan = 2,
                rowSpan = 2,
                col = 0,
                row = 2,
            ),
            HomeTileItem.InternalWidget(
                id = "widget_player",
                type = InternalWidgetType.PLAYER,
                colSpan = 2,
                rowSpan = 2,
                col = 2,
                row = 2,
            ),
            HomeTileItem.AppPin(id = "pin_phone", packageName = "com.google.android.dialer", colSpan = 1, rowSpan = 1, col = 0, row = 4),
            HomeTileItem.AppPin(id = "pin_messages", packageName = "com.google.android.apps.messaging", colSpan = 1, rowSpan = 1, col = 1, row = 4),
            HomeTileItem.AppPin(id = "pin_contacts", packageName = "com.google.android.contacts", colSpan = 1, rowSpan = 1, col = 2, row = 4),
            HomeTileItem.AppPin(id = "pin_chrome", packageName = "com.android.chrome", colSpan = 1, rowSpan = 1, col = 3, row = 4),
            HomeTileItem.AppPin(id = "pin_camera", packageName = "com.android.camera2", colSpan = 1, rowSpan = 1, col = 0, row = 5),
            HomeTileItem.AppPin(id = "pin_photos", packageName = "com.google.android.apps.photos", colSpan = 1, rowSpan = 1, col = 1, row = 5),
            HomeTileItem.AppPin(id = "pin_gm", packageName = "com.google.android.gm", colSpan = 1, rowSpan = 1, col = 2, row = 5),
            HomeTileItem.AppPin(id = "pin_settings", packageName = "com.android.settings", colSpan = 1, rowSpan = 1, col = 3, row = 5),
            HomeTileItem.AppPin(id = "pin_calendar", packageName = "com.google.android.calendar", colSpan = 1, rowSpan = 1, col = 0, row = 6),
            HomeTileItem.AppPin(id = "pin_deskclock", packageName = "com.google.android.deskclock", colSpan = 1, rowSpan = 1, col = 1, row = 6),
            HomeTileItem.AppPin(id = "pin_files", packageName = "com.google.android.documentsui", colSpan = 1, rowSpan = 1, col = 2, row = 6),
        )
    }
}
