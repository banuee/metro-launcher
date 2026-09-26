package dev.metro.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private val Context.customizationStore by preferencesDataStore(name = "metro_app_customizations")

/**
 * Репозиторий кастомизации приложений: пользовательские названия,
 * скрытые приложения и собственные иконки.
 */
class AppCustomizationRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val iconsDir = File(context.filesDir, "custom_icons").apply { mkdirs() }

    companion object {
        private val KEY_CUSTOM_LABELS = stringPreferencesKey("custom_labels_json")
        private val KEY_HIDDEN_PACKAGES = stringSetPreferencesKey("hidden_packages")

        @Volatile
        private var instance: AppCustomizationRepository? = null

        fun getInstance(context: Context): AppCustomizationRepository {
            return instance ?: synchronized(this) {
                instance ?: AppCustomizationRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    val hiddenPackages: StateFlow<Set<String>> = context.customizationStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[KEY_HIDDEN_PACKAGES] ?: emptySet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val customLabels: StateFlow<Map<String, String>> = context.customizationStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> parseLabels(prefs[KEY_CUSTOM_LABELS]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private fun parseLabels(jsonStr: String?): Map<String, String> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                map[key] = json.getString(key)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun getAllCustomLabels(): Map<String, String> = withContext(Dispatchers.IO) {
        val prefs = context.customizationStore.data.catch { emit(emptyPreferences()) }.first()
        parseLabels(prefs[KEY_CUSTOM_LABELS])
    }

    fun getCustomLabel(packageName: String): String? {
        return customLabels.value[packageName]
    }

    suspend fun setCustomLabel(packageName: String, label: String?) = withContext(Dispatchers.IO) {
        context.customizationStore.edit { prefs ->
            val current = parseLabels(prefs[KEY_CUSTOM_LABELS]).toMutableMap()
            if (label.isNullOrBlank()) {
                current.remove(packageName)
            } else {
                current[packageName] = label.trim()
            }
            if (current.isEmpty()) {
                prefs.remove(KEY_CUSTOM_LABELS)
            } else {
                val json = JSONObject()
                current.forEach { (k, v) -> json.put(k, v) }
                prefs[KEY_CUSTOM_LABELS] = json.toString()
            }
        }
    }

    fun isHidden(packageName: String): Boolean {
        return hiddenPackages.value.contains(packageName)
    }

    suspend fun setHidden(packageName: String, hidden: Boolean) = withContext(Dispatchers.IO) {
        context.customizationStore.edit { prefs ->
            val current = (prefs[KEY_HIDDEN_PACKAGES] ?: emptySet()).toMutableSet()
            if (hidden) {
                current.add(packageName)
            } else {
                current.remove(packageName)
            }
            prefs[KEY_HIDDEN_PACKAGES] = current
        }
    }

    fun hasCustomIcon(packageName: String): Boolean {
        return File(iconsDir, "$packageName.png").exists()
    }

    fun getCustomIconBitmap(packageName: String): Bitmap? {
        val file = File(iconsDir, "$packageName.png")
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    suspend fun saveCustomIcon(packageName: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val file = File(iconsDir, "$packageName.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        AppIconLoader.evict(packageName)
    }

    suspend fun clearCustomIcon(packageName: String) = withContext(Dispatchers.IO) {
        val file = File(iconsDir, "$packageName.png")
        if (file.exists()) {
            file.delete()
        }
        AppIconLoader.evict(packageName)
    }
}
