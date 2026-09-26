package dev.metro.launcher.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BackupResult(
    val success: Boolean,
    val message: String? = null,
)

/**
 * Репозиторий резервного копирования и восстановления состояния лаунчера:
 * - Все настройки стиля, прозрачности, акцентов, таймеров и анимаций
 * - Обои (base64 JPEG)
 * - Сетка и размеры плиток
 * - Кастомизация приложений (скрытые приложения, кастомные заголовки, иконки base64)
 * - Заметки
 */
class BackupRepository(private val context: Context) {

    suspend fun exportBackup(
        uri: Uri,
        settingsRepo: MetroSettingsRepository,
        layoutRepo: HomeLayoutRepository,
        notesRepo: NotesRepository,
        wallpaperRepo: WallpaperRepository,
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            root.put("version", 1)
            root.put("timestamp", System.currentTimeMillis())
            root.put("app", "dev.metro.launcher")

            // 1. Settings
            val s = settingsRepo.settingsFlow.first()
            val settingsObj = JSONObject().apply {
                if (s.accentColor != null) put("accentColor", s.accentColor)
                put("autoAccent", s.autoAccent)
                val paletteArr = JSONArray()
                s.generatedPalette.forEach { paletteArr.put(it) }
                put("generatedPalette", paletteArr)
                put("blurEnabled", s.blurEnabled)
                put("blurRadius", s.blurRadius)
                put("glassColor", s.glassColor)
                put("glassAlpha", s.glassAlpha.toDouble())
                put("glassDeepAlpha", s.glassDeepAlpha.toDouble())
                put("strokeAlpha", s.strokeAlpha.toDouble())
                put("autoUpdateIntervalMinutes", s.autoUpdateIntervalMinutes)
                put("lastNotifiedVersion", s.lastNotifiedVersion)
                put("unlockAnimationEnabled", s.unlockAnimationEnabled)
            }
            root.put("settings", settingsObj)

            // 2. Wallpaper
            val wpFile = File(context.filesDir, "launcher_wallpaper.jpg")
            if (wpFile.exists()) {
                val bytes = wpFile.readBytes()
                root.put("wallpaperBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            } else {
                root.put("wallpaperBase64", JSONObject.NULL)
            }

            // 3. Layout (Tiles)
            val layoutJson = layoutRepo.getLayoutJson()
            root.put("layoutJson", layoutJson)

            // 4. Customizations
            val customRepo = AppCustomizationRepository.getInstance(context)
            val customObj = JSONObject().apply {
                val labels = customRepo.getAllCustomLabels()
                val labelsObj = JSONObject()
                labels.forEach { (k, v) -> labelsObj.put(k, v) }
                put("labels", labelsObj)

                val hidden = customRepo.hiddenPackages.first()
                val hiddenArr = JSONArray()
                hidden.forEach { hiddenArr.put(it) }
                put("hidden", hiddenArr)

                val icons = customRepo.getAllCustomIconsBase64()
                val iconsObj = JSONObject()
                icons.forEach { (k, v) -> iconsObj.put(k, v) }
                put("iconsBase64", iconsObj)
            }
            root.put("customizations", customObj)

            // 5. Notes
            val notesJson = notesRepo.getNotesJson()
            root.put("notesJson", notesJson)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(root.toString(2))
                }
            } ?: return@withContext BackupResult(false, "Не удалось открыть файл для записи")

            BackupResult(true, "Резервная копия успешно создана")
        } catch (e: Exception) {
            BackupResult(false, e.localizedMessage ?: e.message)
        }
    }

    suspend fun importBackup(
        uri: Uri,
        settingsRepo: MetroSettingsRepository,
        layoutRepo: HomeLayoutRepository,
        notesRepo: NotesRepository,
        wallpaperRepo: WallpaperRepository,
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { inp ->
                inp.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } ?: return@withContext BackupResult(false, "Не удалось прочитать файл")

            val root = JSONObject(content)

            // 1. Settings
            if (root.has("settings")) {
                val sObj = root.getJSONObject("settings")
                val palList = mutableListOf<Int>()
                if (sObj.has("generatedPalette")) {
                    val pArr = sObj.getJSONArray("generatedPalette")
                    for (i in 0 until pArr.length()) {
                        palList.add(pArr.getInt(i))
                    }
                }
                val newSettings = MetroSettings(
                    accentColor = if (sObj.has("accentColor") && !sObj.isNull("accentColor")) sObj.getInt("accentColor") else null,
                    autoAccent = sObj.optBoolean("autoAccent", true),
                    generatedPalette = palList,
                    blurEnabled = sObj.optBoolean("blurEnabled", true),
                    blurRadius = sObj.optInt("blurRadius", 14),
                    glassColor = sObj.optInt("glassColor", 0xFFFFFFFF.toInt()),
                    glassAlpha = sObj.optDouble("glassAlpha", 0.07).toFloat(),
                    glassDeepAlpha = sObj.optDouble("glassDeepAlpha", 0.90).toFloat(),
                    strokeAlpha = sObj.optDouble("strokeAlpha", 0.08).toFloat(),
                    autoUpdateIntervalMinutes = sObj.optInt("autoUpdateIntervalMinutes", 0),
                    lastNotifiedVersion = sObj.optString("lastNotifiedVersion", ""),
                    unlockAnimationEnabled = sObj.optBoolean("unlockAnimationEnabled", true),
                )
                settingsRepo.restoreSettings(newSettings)
            }

            // 2. Wallpaper
            val wpFile = File(context.filesDir, "launcher_wallpaper.jpg")
            if (root.has("wallpaperBase64") && !root.isNull("wallpaperBase64")) {
                val b64 = root.getString("wallpaperBase64")
                if (b64.isNotBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    wpFile.writeBytes(bytes)
                } else {
                    if (wpFile.exists()) wpFile.delete()
                }
            } else {
                if (wpFile.exists()) wpFile.delete()
            }
            wallpaperRepo.reload()

            // 3. Layout
            if (root.has("layoutJson") && !root.isNull("layoutJson")) {
                val layoutJson = root.getString("layoutJson")
                layoutRepo.restoreLayoutJson(layoutJson)
            }

            // 4. Customizations
            if (root.has("customizations")) {
                val cObj = root.getJSONObject("customizations")
                val hiddenSet = mutableSetOf<String>()
                if (cObj.has("hidden")) {
                    val hArr = cObj.getJSONArray("hidden")
                    for (i in 0 until hArr.length()) {
                        hiddenSet.add(hArr.getString(i))
                    }
                }
                val labelsJson = if (cObj.has("labels")) cObj.getJSONObject("labels").toString() else null
                val iconsMap = mutableMapOf<String, String>()
                if (cObj.has("iconsBase64")) {
                    val iObj = cObj.getJSONObject("iconsBase64")
                    val keys = iObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        iconsMap[key] = iObj.getString(key)
                    }
                }
                val customRepo = AppCustomizationRepository.getInstance(context)
                customRepo.restoreCustomizations(hiddenSet, labelsJson, iconsMap)
            }

            // 5. Notes
            if (root.has("notesJson") && !root.isNull("notesJson")) {
                val notesJson = root.getString("notesJson")
                notesRepo.restoreNotesJson(notesJson)
            }

            BackupResult(true, "Состояние успешно восстановлено")
        } catch (e: Exception) {
            BackupResult(false, e.localizedMessage ?: e.message)
        }
    }
}
