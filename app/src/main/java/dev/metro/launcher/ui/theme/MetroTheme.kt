package dev.metro.launcher.ui.theme

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Токены портированы из metro/Theme.qml (ветка Metro Live, material не трогаем).
 * unit/gap задают сетку: плитка 1x1 = 84dp, зазор 8dp, 4 колонки = 360dp < 392dp (Poco).
 */
object MetroDimens {
    val unit: Dp = 84.dp
    val gap: Dp = 8.dp
    val radius: Dp = 10.dp
    val radiusSmall: Dp = 8.dp
    val radiusLarge: Dp = 14.dp
    val panelRadius: Dp = 16.dp
    val iconBox: Dp = 46.dp

    /** Ширина плитки на [cols] колонки: cols * unit + (cols-1) * gap */
    fun tileW(cols: Int): Dp = unit * cols + gap * (cols - 1)

    /** Высота плитки на [rows] строк: rows * unit + (rows-1) * gap */
    fun tileH(rows: Int): Dp = unit * rows + gap * (rows - 1)
}

/** Монохромное стекло + акцент (НЕ радужные тайлы). */
data class MetroScheme(
    val accent: Color,
    val glass: Color = Color.White.copy(alpha = 0.07f),
    val glassHover: Color = Color.White.copy(alpha = 0.12f),
    val glassDeep: Color = Color(0xFF101010).copy(alpha = 0.90f),
    val stroke: Color = Color.White.copy(alpha = 0.08f),
    val strokeStrong: Color = Color.White.copy(alpha = 0.15f),
    val text: Color = Color(0xFFF7F7F7),
    val textDim: Color = Color.White.copy(alpha = 0.60f),
    val red: Color = Color(0xFFE51400),
    val blurEnabled: Boolean = true,
    val blurRadius: Int = 14,
)

val LocalMetroScheme: ProvidableCompositionLocal<MetroScheme> =
    compositionLocalOf { MetroScheme(accent = MetroDefaults.accentFallback) }

object MetroDefaults {
    /** Дефолт как в quickshell: teal #00aba9, пока не пришел цвет обоев. */
    val accentFallback = Color(0xFF00ABA9)
}

/**
 * Живой акцент из обоев — аналог metro-colors/matugen на десктопе.
 * На API < 27 WallpaperColors нет — остаемся на фолбэке.
 */
@Composable
fun rememberWallpaperAccent(): MutableState<Color> {
    val context = LocalContext.current
    val accent = remember { mutableStateOf(MetroDefaults.accentFallback) }

    DisposableEffect(context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            onDispose { }
        } else {
            val wm = context.getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
            fun pull() {
                val primary = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor
                if (primary != null) accent.value = Color(primary.toArgb())
            }
            pull()
            val listener = WallpaperManager.OnColorsChangedListener { _, _ -> pull() }
            wm.addOnColorsChangedListener(
                listener,
                android.os.Handler(android.os.Looper.getMainLooper()),
            )
            onDispose { wm.removeOnColorsChangedListener(listener) }
        }
    }
    return accent
}

@Composable
fun MetroTheme(
    settingsRepo: dev.metro.launcher.data.MetroSettingsRepository? = null,
    content: @Composable () -> Unit,
) {
    val wpAccent = rememberWallpaperAccent()
    val settingsState = settingsRepo?.settings?.collectAsState()
    val settings = settingsState?.value ?: dev.metro.launcher.data.MetroSettingsRepository.DEFAULT

    val effectiveAccent = remember(settings.accentColor, settings.autoAccent, wpAccent.value) {
        if (!settings.autoAccent && settings.accentColor != null) {
            Color(settings.accentColor)
        } else if (settings.accentColor != null) {
            Color(settings.accentColor)
        } else {
            wpAccent.value
        }
    }

    val baseGlassColor = remember(settings.glassColor) {
        Color(settings.glassColor)
    }

    val scheme = MetroScheme(
        accent = effectiveAccent,
        glass = baseGlassColor.copy(alpha = settings.glassAlpha),
        glassHover = baseGlassColor.copy(
            alpha = if (settings.glassAlpha <= 0.01f) 0.08f
                    else (settings.glassAlpha * 1.5f).coerceAtMost(1.0f)
        ),
        glassDeep = Color(0xFF101010).copy(alpha = settings.glassDeepAlpha),
        stroke = Color.White.copy(alpha = settings.strokeAlpha),
        strokeStrong = Color.White.copy(alpha = (settings.strokeAlpha * 1.8f).coerceAtMost(0.50f)),
        blurEnabled = settings.blurEnabled,
        blurRadius = settings.blurRadius,
    )
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMetroScheme provides scheme,
    ) {
        // Segoe UI глобально: все M3-тексты (диалоги, поля, кнопки) — нашим шрифтом.
        androidx.compose.material3.MaterialTheme(
            colorScheme = androidx.compose.material3.darkColorScheme(
                primary = scheme.accent,
                onPrimary = scheme.text,
                secondary = scheme.accent,
                surface = Color(0xFF161616),
                onSurface = scheme.text,
                surfaceVariant = Color(0xFF1E1E1E),
                onSurfaceVariant = scheme.textDim,
                background = Color.Black,
                onBackground = scheme.text,
            ),
            typography = metroTypography(),
            content = content,
        )
    }
}

/** Вся M3-типографика на Segoe UI Variable (text — как fontFamily в шелле). */
@Composable
private fun metroTypography(): androidx.compose.material3.Typography {
    val f = MetroFonts.text
    val d = androidx.compose.material3.Typography()
    return androidx.compose.material3.Typography(
        displayLarge = d.displayLarge.copy(fontFamily = f),
        displayMedium = d.displayMedium.copy(fontFamily = f),
        displaySmall = d.displaySmall.copy(fontFamily = f),
        headlineLarge = d.headlineLarge.copy(fontFamily = f),
        headlineMedium = d.headlineMedium.copy(fontFamily = f),
        headlineSmall = d.headlineSmall.copy(fontFamily = f),
        titleLarge = d.titleLarge.copy(fontFamily = f),
        titleMedium = d.titleMedium.copy(fontFamily = f),
        titleSmall = d.titleSmall.copy(fontFamily = f),
        bodyLarge = d.bodyLarge.copy(fontFamily = f),
        bodyMedium = d.bodyMedium.copy(fontFamily = f),
        bodySmall = d.bodySmall.copy(fontFamily = f),
        labelLarge = d.labelLarge.copy(fontFamily = f),
        labelMedium = d.labelMedium.copy(fontFamily = f),
        labelSmall = d.labelSmall.copy(fontFamily = f),
    )
}
