package dev.metro.launcher.data

import android.graphics.drawable.Drawable

/**
 * Одно запускаемое приложение (MAIN/LAUNCHER).
 * Иконка хранится как Drawable — в битмап конвертируется лениво через [IconCache].
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val iconResId: Int = 0,
)
