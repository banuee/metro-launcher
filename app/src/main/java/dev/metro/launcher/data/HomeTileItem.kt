package dev.metro.launcher.data

import org.json.JSONObject

enum class InternalWidgetType {
    CLOCK,
    WEATHER,
    NOTES,
    PLAYER,
}

sealed class HomeTileItem {
    abstract val id: String
    abstract val colSpan: Int
    abstract val rowSpan: Int
    abstract val col: Int?
    abstract val row: Int?

    data class AppPin(
        override val id: String,
        val packageName: String,
        override val colSpan: Int = 1,
        override val rowSpan: Int = 1,
        override val col: Int? = null,
        override val row: Int? = null,
    ) : HomeTileItem()

    data class InternalWidget(
        override val id: String,
        val type: InternalWidgetType,
        override val colSpan: Int = 2,
        override val rowSpan: Int = 2,
        override val col: Int? = null,
        override val row: Int? = null,
    ) : HomeTileItem()

    data class AndroidWidget(
        override val id: String,
        val appWidgetId: Int,
        val packageName: String,
        val className: String,
        override val colSpan: Int = 2,
        override val rowSpan: Int = 2,
        override val col: Int? = null,
        override val row: Int? = null,
    ) : HomeTileItem()

    fun copyWithSpans(newColSpan: Int, newRowSpan: Int): HomeTileItem {
        val c = newColSpan.coerceIn(1, 4)
        val r = newRowSpan.coerceIn(1, 6)
        return when (this) {
            is AppPin -> copy(colSpan = c, rowSpan = r)
            is InternalWidget -> copy(colSpan = c, rowSpan = r)
            is AndroidWidget -> copy(colSpan = c, rowSpan = r)
        }
    }

    fun copyWithPosition(newCol: Int?, newRow: Int?): HomeTileItem {
        return when (this) {
            is AppPin -> copy(col = newCol, row = newRow)
            is InternalWidget -> copy(col = newCol, row = newRow)
            is AndroidWidget -> copy(col = newCol, row = newRow)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("colSpan", colSpan)
        put("rowSpan", rowSpan)
        col?.let { put("col", it) }
        row?.let { put("row", it) }
        when (this@HomeTileItem) {
            is AppPin -> {
                put("kind", "app")
                put("packageName", packageName)
            }
            is InternalWidget -> {
                put("kind", "internal")
                put("type", type.name)
            }
            is AndroidWidget -> {
                put("kind", "android_widget")
                put("appWidgetId", appWidgetId)
                put("packageName", packageName)
                put("className", className)
            }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): HomeTileItem? {
            return try {
                val id = json.getString("id")
                val colSpan = json.optInt("colSpan", 1).coerceIn(1, 4)
                val rowSpan = json.optInt("rowSpan", 1).coerceIn(1, 6)
                val col = when {
                    json.has("col") -> json.getInt("col")
                    json.has("x") -> json.getInt("x")
                    else -> null
                }
                val row = when {
                    json.has("row") -> json.getInt("row")
                    json.has("y") -> json.getInt("y")
                    else -> null
                }
                when (json.optString("kind")) {
                    "app" -> AppPin(
                        id = id,
                        packageName = json.getString("packageName"),
                        colSpan = colSpan,
                        rowSpan = rowSpan,
                        col = col,
                        row = row,
                    )
                    "internal" -> {
                        val typeStr = json.getString("type")
                        val type = InternalWidgetType.valueOf(typeStr)
                        InternalWidget(
                            id = id,
                            type = type,
                            colSpan = colSpan,
                            rowSpan = rowSpan,
                            col = col,
                            row = row,
                        )
                    }
                    "android_widget" -> AndroidWidget(
                        id = id,
                        appWidgetId = json.getInt("appWidgetId"),
                        packageName = json.getString("packageName"),
                        className = json.getString("className"),
                        colSpan = colSpan,
                        rowSpan = rowSpan,
                        col = col,
                        row = row,
                    )
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
