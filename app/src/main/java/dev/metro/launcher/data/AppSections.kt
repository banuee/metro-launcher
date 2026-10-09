package dev.metro.launcher.data

import java.util.Locale

/** Элемент WP-списка: заголовок-буква или строка приложения. */
sealed interface DrawerItem {
    data class Header(val letter: String) : DrawerItem
    data class Row(val app: AppInfo) : DrawerItem
}

/** Сопоставление первой русской буквы с английской для режима "Только английский алфавит". */
fun cyrillicToLatinFirstLetter(ch: Char): Char? {
    val upper = ch.uppercaseChar()
    return when (upper) {
        'А' -> 'A'
        'Б' -> 'B'
        'В' -> 'V'
        'Г' -> 'G'
        'Д' -> 'D'
        'Е', 'Ё' -> 'E'
        'Ж' -> 'Z'
        'З' -> 'Z'
        'И' -> 'I'
        'Й' -> 'Y'
        'К' -> 'K'
        'Л' -> 'L'
        'М' -> 'M'
        'Н' -> 'N'
        'О' -> 'O'
        'П' -> 'P'
        'Р' -> 'R'
        'С' -> 'S'
        'Т' -> 'T'
        'У' -> 'U'
        'Ф' -> 'F'
        'Х' -> 'H'
        'Ц' -> 'C'
        'Ч' -> 'C'
        'Ш', 'Щ' -> 'S'
        'Ъ' -> 'E'
        'Ы' -> 'Y'
        'Ь' -> null
        'Э' -> 'E'
        'Ю' -> 'U'
        'Я' -> 'Y'
        else -> null
    }
}

/** Транслитерация для упорядочивания внутри секции. */
fun transliterateForSort(text: String): String {
    val sb = StringBuilder()
    for (ch in text) {
        val lower = ch.lowercaseChar()
        val mapped = when (lower) {
            'а' -> "a"
            'б' -> "b"
            'в' -> "v"
            'г' -> "g"
            'д' -> "d"
            'е', 'ё' -> "e"
            'ж' -> "zh"
            'з' -> "z"
            'и' -> "i"
            'й' -> "y"
            'к' -> "k"
            'л' -> "l"
            'м' -> "m"
            'н' -> "n"
            'о' -> "o"
            'п' -> "p"
            'р' -> "r"
            'с' -> "s"
            'т' -> "t"
            'у' -> "u"
            'ф' -> "f"
            'х' -> "h"
            'ц' -> "c"
            'ч' -> "ch"
            'ш', 'щ' -> "sh"
            'ъ', 'ь' -> ""
            'ы' -> "y"
            'э' -> "e"
            'ю' -> "yu"
            'я' -> "ya"
            else -> lower.toString()
        }
        sb.append(mapped)
    }
    return sb.toString()
}

/** Первая буква для секции. */
fun sectionLetter(label: String, englishOnly: Boolean = false): String {
    val first = label.trim().firstOrNull() ?: return "#"
    val upper = first.titlecase(Locale.getDefault()).firstOrNull() ?: first.uppercaseChar()

    if (englishOnly) {
        if (upper in 'A'..'Z') return upper.toString()
        val mapped = cyrillicToLatinFirstLetter(upper)
        if (mapped != null && mapped in 'A'..'Z') return mapped.toString()
        return "#"
    }

    return if (upper in 'A'..'Z' || upper in 'А'..'Я' || upper == 'Ё') upper.toString() else "#"
}

/** Сортировка списка приложений с учетом режима английского алфавита. */
fun sortApps(apps: List<AppInfo>, englishOnly: Boolean = false): List<AppInfo> {
    return if (!englishOnly) {
        apps.sortedBy { it.label.lowercase(Locale.getDefault()) }
    } else {
        apps.sortedWith(
            compareBy<AppInfo> { app ->
                val letter = sectionLetter(app.label, englishOnly = true)
                if (letter == "#") "ZZZ" else letter
            }.thenBy { app ->
                transliterateForSort(app.label)
            }
        )
    }
}

/** Плоский список с заголовками + индекс позиции каждого заголовка для скролла. */
data class SectionedApps(
    val items: List<DrawerItem>,
    /** буква -> позиция Header в items */
    val headerIndex: Map<String, Int>,
)

fun sectionApps(apps: List<AppInfo>, englishOnly: Boolean = false): SectionedApps {
    val items = mutableListOf<DrawerItem>()
    val headerIndex = mutableMapOf<String, Int>()
    var last = ""
    for (app in apps) {
        val letter = sectionLetter(app.label, englishOnly)
        if (letter != last) {
            last = letter
            headerIndex[letter] = items.size
            items.add(DrawerItem.Header(letter))
        }
        items.add(DrawerItem.Row(app))
    }
    return SectionedApps(items, headerIndex)
}

/** Алфавит Jump Grid. */
object JumpAlphabets {
    val all: List<String> =
        ('A'..'Z').map { it.toString() } +
            listOf("Ё") + ('А'..'Я').map { it.toString() } + listOf("#")

    val englishOnly: List<String> =
        ('A'..'Z').map { it.toString() } + listOf("#")
}
