package dev.metro.launcher.ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Аутентичные глифы Nerd Font (Noto Sans Nerd Font), используемые в Quickshell Metro.
 */
object MetroIcons {
    const val Search = "\uF002"
    const val Close = "\uF00D"
    const val Check = "\uF00C"
    const val ChevronRight = "\uF054"
    const val ChevronLeft = "\uF053"
    const val ChevronDown = "\uF078"
    const val ChevronUp = "\uF077"
    const val ExternalLink = "\uF08E"
    const val Trash = "\uF1F8"
    const val Widgets = "\uF009"
    const val Apps = "\uF00A"
    const val Settings = "\uF013"
    const val Plus = "\uF067"
    const val Music = "\uF001"
    const val Prev = "\uF048"
    const val Play = "\uF04B"
    const val Pause = "\uF04C"
    const val Next = "\uF051"
    const val Edit = "\uF044"
    const val Pin = "\uF08D"
    const val Eye = "\uF06E"
    const val EyeSlash = "\uF070"
    const val Image = "\uF03E"
    const val Reset = "\uF021"
}

/**
 * Единый компонент иконки на базе шрифта Nerd Font вместо разнородных Material Icons.
 */
@Composable
fun MetroIcon(
    icon: String,
    modifier: Modifier = Modifier,
    color: Color = LocalMetroScheme.current.text,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = icon,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontFamily = MetroFonts.icon,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
    )
}
