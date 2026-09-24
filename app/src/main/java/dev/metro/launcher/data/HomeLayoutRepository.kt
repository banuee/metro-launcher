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
        GridPacker.applyDecision(list, decision)
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
