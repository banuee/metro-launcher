package dev.metro.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.settingsStore by preferencesDataStore(name = "metro_settings")

/**
 * Пользовательские настройки стиля Metro: акценты, прозрачности, блюр.
 */
data class MetroSettings(
    val accentColor: Int? = null, // ARGB Int, null = авто из обоев
    val autoAccent: Boolean = true,
    val generatedPalette: List<Int> = emptyList(),
    val blurEnabled: Boolean = true,
    val blurRadius: Int = 14,
    val glassColor: Int = 0xFFFFFFFF.toInt(), // Цвет подложки плиток
    val glassAlpha: Float = 0.07f,            // Прозрачность подложки (0..1.0)
    val glassDeepAlpha: Float = 0.90f,
    val strokeAlpha: Float = 0.08f,
    val autoUpdateIntervalMinutes: Int = 0, // 0 = никогда, 10, 30, 60, 180, 360, 720, 1440
    val lastNotifiedVersion: String = "",
)

class MetroSettingsRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private val KEY_ACCENT_COLOR = intPreferencesKey("accent_color")
        private val KEY_AUTO_ACCENT = booleanPreferencesKey("auto_accent")
        private val KEY_GENERATED_PALETTE = stringPreferencesKey("generated_palette")
        private val KEY_BLUR_ENABLED = booleanPreferencesKey("blur_enabled")
        private val KEY_BLUR_RADIUS = intPreferencesKey("blur_radius")
        private val KEY_GLASS_COLOR = intPreferencesKey("glass_color")
        private val KEY_GLASS_ALPHA = floatPreferencesKey("glass_alpha")
        private val KEY_GLASS_DEEP_ALPHA = floatPreferencesKey("glass_deep_alpha")
        private val KEY_STROKE_ALPHA = floatPreferencesKey("stroke_alpha")
        private val KEY_AUTO_UPDATE_INTERVAL = intPreferencesKey("auto_update_interval")
        private val KEY_LAST_NOTIFIED_VERSION = stringPreferencesKey("last_notified_version")

        val DEFAULT = MetroSettings()
    }

    val settingsFlow: Flow<MetroSettings> = context.settingsStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { prefs ->
        val paletteStr = prefs[KEY_GENERATED_PALETTE] ?: ""
        val palette = if (paletteStr.isBlank()) {
            emptyList()
        } else {
            paletteStr.split(",").mapNotNull {
                try { it.trim().toLong(16).toInt() } catch (_: Exception) { null }
            }
        }

        MetroSettings(
            accentColor = if (prefs.contains(KEY_ACCENT_COLOR)) prefs[KEY_ACCENT_COLOR] else null,
            autoAccent = prefs[KEY_AUTO_ACCENT] ?: true,
            generatedPalette = palette,
            blurEnabled = prefs[KEY_BLUR_ENABLED] ?: true,
            blurRadius = prefs[KEY_BLUR_RADIUS] ?: 14,
            glassColor = prefs[KEY_GLASS_COLOR] ?: 0xFFFFFFFF.toInt(),
            glassAlpha = prefs[KEY_GLASS_ALPHA] ?: 0.07f,
            glassDeepAlpha = prefs[KEY_GLASS_DEEP_ALPHA] ?: 0.90f,
            strokeAlpha = prefs[KEY_STROKE_ALPHA] ?: 0.08f,
            autoUpdateIntervalMinutes = prefs[KEY_AUTO_UPDATE_INTERVAL] ?: 0,
            lastNotifiedVersion = prefs[KEY_LAST_NOTIFIED_VERSION] ?: "",
        )
    }

    val settings: StateFlow<MetroSettings> = settingsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = DEFAULT,
    )

    fun setAccentColor(color: Int?, auto: Boolean = false) {
        scope.launch {
            context.settingsStore.edit { prefs ->
                if (color != null) {
                    prefs[KEY_ACCENT_COLOR] = color
                } else {
                    prefs.remove(KEY_ACCENT_COLOR)
                }
                prefs[KEY_AUTO_ACCENT] = auto
            }
        }
    }

    fun setAutoAccent(enabled: Boolean) {
        scope.launch {
            context.settingsStore.edit { prefs ->
                prefs[KEY_AUTO_ACCENT] = enabled
            }
        }
    }

    fun setGeneratedPalette(palette: List<Int>) {
        scope.launch {
            context.settingsStore.edit { prefs ->
                val str = palette.joinToString(",") { "%08X".format(it) }
                prefs[KEY_GENERATED_PALETTE] = str
            }
        }
    }

    fun setBlurEnabled(enabled: Boolean) {
        scope.launch {
            context.settingsStore.edit { prefs ->
                prefs[KEY_BLUR_ENABLED] = enabled
            }
        }
    }

    fun setBlurRadius(radius: Int) {
        val clamped = radius.coerceIn(4, 32)
        scope.launch {
            context.settingsStore.edit { prefs ->
                prefs[KEY_BLUR_RADIUS] = clamped
            }
        }
    }

    fun setGlassColor(color: Int) {
        scope.launch {
            context.settingsStore.edit { prefs ->
                prefs[KEY_GLASS_COLOR] = color
            }
        }
    }

    fun setGlassAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.00f, 1.00f)
        scope.launch {
            context.settingsStore.edit { prefs ->
                prefs[KEY_GLASS_ALPHA] = clamped
            }
        }
    }

    fun setGlassDeepAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.10f, 0.95f)
        scope.launch {
            context.settingsStore.edit { prefs ->
                prefs[KEY_GLASS_DEEP_ALPHA] = clamped
            }
        }
    }

    fun setStrokeAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.02f, 0.35f)
        scope.launch {
            context.settingsStore.edit { prefs ->
                prefs[KEY_STROKE_ALPHA] = clamped
            }
        }
    }

    fun resetGlassDefaults() {
        scope.launch {
            context.settingsStore.edit { prefs ->
                prefs[KEY_BLUR_ENABLED] = DEFAULT.blurEnabled
                prefs[KEY_BLUR_RADIUS] = DEFAULT.blurRadius
                prefs[KEY_GLASS_COLOR] = DEFAULT.glassColor
                prefs[KEY_GLASS_ALPHA] = DEFAULT.glassAlpha
                prefs[KEY_GLASS_DEEP_ALPHA] = DEFAULT.glassDeepAlpha
                prefs[KEY_STROKE_ALPHA] = DEFAULT.strokeAlpha
            }
        }
    }

    fun setAutoUpdateInterval(minutes: Int) {
        val valid = minutes.coerceAtLeast(0)
        scope.launch {
            context.settingsStore.edit { prefs ->
                prefs[KEY_AUTO_UPDATE_INTERVAL] = valid
            }
        }
    }

    fun setLastNotifiedVersion(version: String) {
        scope.launch {
            context.settingsStore.edit { prefs ->
                prefs[KEY_LAST_NOTIFIED_VERSION] = version
            }
        }
    }

    fun resetAllDefaults() {
        scope.launch {
            context.settingsStore.edit { prefs ->
                prefs.clear()
            }
        }
    }
}
