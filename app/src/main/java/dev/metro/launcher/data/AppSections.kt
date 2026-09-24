package dev.metro.launcher.data

import java.util.Locale

/** Элемент WP-списка: заголовок-буква или строка приложения. */
sealed interface DrawerItem {
    data class Header(val letter: String) : DrawerItem
    data class Row(val app: AppInfo) : DrawerItem
}

/** Первая буква для секции: латиница/кириллица как есть, остальное (цифры, символы) → "#". */
fun sectionLetter(label: String): String {
    // titlecase(locale), а не uppercaseChar(): в турецкой локали 'i' → 'İ',
    // из-за чего приложения лили бы в отдельную секцию.
    val first = label.trim().firstOrNull()
        ?.titlecase(Locale.getDefault())
        ?.firstOrNull() ?: return "#"
    return if (first in 'A'..'Z' || first in 'А'..'Я' || first == 'Ё') first.toString() else "#"
}

/** Плоский список с заголовками + индекс позиции каждого заголовка для скролла. */
data class SectionedApps(
    val items: List<DrawerItem>,
    /** буква -> позиция Header в items */
    val headerIndex: Map<String, Int>,
)

fun sectionApps(apps: List<AppInfo>): SectionedApps {
    val items = mutableListOf<DrawerItem>()
    val headerIndex = mutableMapOf<String, Int>()
    var last = ""
    for (app in apps) {
        val letter = sectionLetter(app.label)
        if (letter != last) {
            last = letter
            headerIndex[letter] = items.size
            items.add(DrawerItem.Header(letter))
        }
        items.add(DrawerItem.Row(app))
    }
    return SectionedApps(items, headerIndex)
}

/** Алфавит Jump Grid: всегда полный (лат + Ё + кир + #), без вкладок. */
object JumpAlphabets {
    val all: List<String> =
        ('A'..'Z').map { it.toString() } +
            listOf("Ё") + ('А'..'Я').map { it.toString() } + listOf("#")
}
