package dev.metro.launcher.data

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LoadedWidgetApp(
    val packageName: String,
    val appLabel: String,
    val appInfo: AppInfo?,
    val widgets: List<AppWidgetProviderInfo>,
    val widgetLabels: Map<String, String>,
)

object AppIconLoader {
    private const val APP_ICON_SIZE = 144
    private const val WIDGET_PREVIEW_SIZE = 140
    private const val APP_CACHE_SIZE = 256
    private const val WIDGET_CACHE_SIZE = 128

    private val lock = Any()
    private val appCache = LruCache<String, ImageBitmap>(APP_CACHE_SIZE)
    private val widgetCache = LruCache<String, ImageBitmap>(WIDGET_CACHE_SIZE)
    private val keyMutexes = mutableMapOf<String, Mutex>()
    private val keyVersions = mutableMapOf<String, Long>()
    private val appSources = mutableMapOf<String, WeakReference<Drawable>>()
    private val widgetSources = mutableMapOf<String, WeakReference<AppWidgetProviderInfo>>()
    private var cacheEpoch = 0L
    private var defaultAppIconBitmap: ImageBitmap? = null
    private var appContext: Context? = null

    private val _revisions = MutableStateFlow(0L)
    val revisions: StateFlow<Long> = _revisions.asStateFlow()

    private fun getCustomIcon(context: Context, packageName: String): ImageBitmap? {
        val file = File(context.filesDir, "custom_icons/$packageName.png")
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun primeAppIcons(context: Context, apps: List<AppInfo>) = withContext(Dispatchers.IO) {
        appContext = context.applicationContext
        val defaultIcon = context.applicationContext.packageManager.defaultActivityIcon
        val needsDefaultBitmap = synchronized(lock) { defaultAppIconBitmap == null }
        if (needsDefaultBitmap) {
            val defaultBitmap = try {
                defaultIcon.toImageBitmap(APP_ICON_SIZE)
            } catch (_: Exception) {
                null
            }
            if (defaultBitmap != null) {
                synchronized(lock) {
                    if (defaultAppIconBitmap == null) defaultAppIconBitmap = defaultBitmap
                }
            }
        }
        apps.forEach { app -> primeAppIcon(app, defaultIcon) }
    }

    fun cachedAppIcon(packageName: String, app: AppInfo? = null): ImageBitmap? {
        val key = appKey(packageName)
        synchronized(lock) {
            val cached = appCache.get(key)
            if (cached != null) return cached
        }
        val customBmp = appContext?.let { getCustomIcon(it, packageName) }
        if (customBmp != null) {
            synchronized(lock) {
                appCache.put(key, customBmp)
            }
            return customBmp
        }
        val icon = app?.icon
        if (icon != null) {
            val bmp = runCatching { icon.toImageBitmap(APP_ICON_SIZE) }.getOrNull()
            if (bmp != null) {
                synchronized(lock) {
                    appCache.put(key, bmp)
                }
                return bmp
            }
        }
        return synchronized(lock) { defaultAppIconBitmap }
    }

    suspend fun loadAppIcon(
        context: Context,
        packageName: String,
        fallbackApp: AppInfo? = null,
    ): ImageBitmap? {
        val loaded = loadApp(
            context = context,
            packageName = packageName,
            fallbackApp = fallbackApp,
            useCache = true,
        )
        return loaded?.icon ?: cachedAppIcon(packageName, fallbackApp)
    }

    suspend fun loadAppInfo(context: Context, packageName: String): AppInfo? = loadApp(
        context = context,
        packageName = packageName,
        fallbackApp = null,
        useCache = false,
    )?.appInfo

    suspend fun loadWidgetApps(
        context: Context,
        apps: List<AppInfo>,
    ): List<LoadedWidgetApp> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val widgetManager = AppWidgetManager.getInstance(appContext)
        val providers = try {
            widgetManager.installedProviders ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        providers.groupBy { it.provider.packageName }.map { (packageName, widgets) ->
            val widgetLabels = buildMap {
                widgets.forEach { widgetInfo ->
                    val label = try {
                        widgetInfo.loadLabel(pm)
                    } catch (_: Exception) {
                        null
                    }
                    put(widgetId(widgetInfo), label ?: packageName)
                }
            }
            val app = apps.find { it.packageName == packageName }
            val appLabel = app?.label ?: try {
                val applicationInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(applicationInfo).toString()
            } catch (_: Exception) {
                packageName
            }
            LoadedWidgetApp(
                packageName = packageName,
                appLabel = appLabel,
                appInfo = app,
                widgets = widgets,
                widgetLabels = widgetLabels,
            )
        }.sortedBy { it.appLabel.lowercase(Locale.getDefault()) }
    }

    fun widgetId(widgetInfo: AppWidgetProviderInfo): String =
        widgetInfo.provider.flattenToString()

    fun cachedWidgetPreview(widgetInfo: AppWidgetProviderInfo): ImageBitmap? {
        val key = prepareWidgetSource(widgetInfo)
        return synchronized(lock) { widgetCache.get(key) }
    }

    suspend fun loadWidgetPreview(
        context: Context,
        widgetInfo: AppWidgetProviderInfo,
    ): ImageBitmap? {
        val key = prepareWidgetSource(widgetInfo)
        return withContext(Dispatchers.IO) {
            val keyMutex = mutexFor(key)
            keyMutex.withLock {
                synchronized(lock) { widgetCache.get(key) }?.let { return@withLock it }
                val version = synchronized(lock) { versionTokenLocked(key) }
                val icon = try {
                    widgetInfo.loadPreviewImage(context.applicationContext, 0)
                        ?: widgetInfo.loadIcon(context.applicationContext, 0)
                } catch (_: Exception) {
                    null
                } ?: return@withLock null
                val bitmap = try {
                    icon.toImageBitmap(WIDGET_PREVIEW_SIZE)
                } catch (_: Exception) {
                    null
                } ?: return@withLock null
                val isDefault = icon == context.packageManager.defaultActivityIcon
                val isCurrent = synchronized(lock) {
                    if (version == versionTokenLocked(key)) {
                        if (!isDefault) widgetCache.put(key, bitmap)
                        true
                    } else {
                        false
                    }
                }
                if (isCurrent) bitmap else null
            }
        }
    }

    fun evict(packageName: String) {
        synchronized(lock) {
            appCache.remove(appKey(packageName))
            appSources.remove(appKey(packageName))
            keyVersions[appKey(packageName)] = versionLocked(appKey(packageName)) + 1
            val widgetKeys = (widgetCache.snapshot().keys + widgetSources.keys)
                .filter { it.startsWith(widgetKeyPrefix(packageName)) }
                .toSet()
            widgetKeys.forEach { key ->
                widgetCache.remove(key)
                widgetSources.remove(key)
                keyVersions[key] = versionLocked(key) + 1
            }
        }
        _revisions.update { it + 1 }
    }

    fun evictAll() {
        synchronized(lock) {
            cacheEpoch++
            appCache.evictAll()
            widgetCache.evictAll()
            appSources.clear()
            widgetSources.clear()
            defaultAppIconBitmap = null
        }
        _revisions.update { it + 1 }
    }

    private suspend fun primeAppIcon(app: AppInfo, defaultIcon: Drawable) {
        val key = prepareAppSource(appKey(app.packageName), app.icon)
        val keyMutex = mutexFor(key)
        keyMutex.withLock {
            synchronized(lock) { appCache.get(key) }?.let { return }
            val version = synchronized(lock) { versionTokenLocked(key) }
            val customIcon = appContext?.let { getCustomIcon(it, app.packageName) }
            if (customIcon != null) {
                synchronized(lock) {
                    if (version == versionTokenLocked(key)) {
                        appCache.put(key, customIcon)
                    }
                }
                return
            }
            if (app.icon != defaultIcon) {
                val bitmap = try {
                    app.icon.toImageBitmap(APP_ICON_SIZE)
                } catch (_: Exception) {
                    null
                }
                if (bitmap != null) {
                    synchronized(lock) {
                        if (version == versionTokenLocked(key)) {
                            appCache.put(key, bitmap)
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadApp(
        context: Context,
        packageName: String,
        fallbackApp: AppInfo?,
        useCache: Boolean,
    ): LoadedApp? {
        val key = prepareAppSource(appKey(packageName), fallbackApp?.icon)
        val keyMutex = mutexFor(key)
        return keyMutex.withLock {
            if (useCache) {
                synchronized(lock) { appCache.get(key) }?.let { cached ->
                    return@withLock LoadedApp(
                        appInfo = fallbackApp,
                        icon = cached,
                        isDefaultIcon = false,
                        cacheable = true,
                    )
                }
            }
            val version = synchronized(lock) { versionTokenLocked(key) }
            val loaded = withContext(Dispatchers.IO) {
                val appContext = context.applicationContext
                val customBmp = getCustomIcon(appContext, packageName)
                if (customBmp != null) {
                    val customLabel = AppCustomizationRepository.getInstance(appContext).getCustomLabel(packageName)
                    val appInfo = fallbackApp?.copy(label = customLabel ?: fallbackApp.label)
                        ?: AppInfo(
                            label = customLabel ?: packageName,
                            packageName = packageName,
                            icon = appContext.packageManager.defaultActivityIcon,
                        )
                    return@withContext LoadedApp(
                        appInfo = appInfo,
                        icon = customBmp,
                        isDefaultIcon = false,
                        cacheable = true,
                    )
                }
                val pm = appContext.packageManager
                val applicationInfo = try {
                    pm.getApplicationInfo(packageName, 0)
                } catch (_: Exception) {
                    null
                } ?: return@withContext fallbackApp?.let { fallback ->
                    val fallbackIcon = fallback.icon
                    val fallbackBitmap = try {
                        fallbackIcon.toImageBitmap(APP_ICON_SIZE)
                    } catch (_: Exception) {
                        null
                    } ?: return@withContext null
                    LoadedApp(
                        appInfo = fallback,
                        icon = fallbackBitmap,
                        isDefaultIcon = fallbackIcon == pm.defaultActivityIcon,
                        cacheable = false,
                    )
                }

                val icon = try {
                    pm.getApplicationIcon(applicationInfo)
                } catch (_: Exception) {
                    fallbackApp?.icon ?: pm.defaultActivityIcon
                }
                val customLabel = AppCustomizationRepository.getInstance(appContext).getCustomLabel(packageName)
                val label = customLabel ?: try {
                    pm.getApplicationLabel(applicationInfo).toString()
                } catch (_: Exception) {
                    fallbackApp?.label ?: packageName
                }
                val iconBitmap = try {
                    icon.toImageBitmap(APP_ICON_SIZE)
                } catch (_: Exception) {
                    null
                } ?: return@withContext null
                val appInfo = AppInfo(
                    label = label,
                    packageName = packageName,
                    icon = icon,
                )
                LoadedApp(
                    appInfo = appInfo,
                    icon = iconBitmap,
                    isDefaultIcon = icon == pm.defaultActivityIcon,
                    cacheable = true,
                )
            } ?: return@withLock null

            val canCache = synchronized(lock) {
                if (version == versionTokenLocked(key)) {
                    if (loaded.cacheable && !loaded.isDefaultIcon) {
                        appCache.put(key, loaded.icon)
                        val loadedIcon = loaded.appInfo?.icon
                        if (fallbackApp == null && loadedIcon != null) {
                            appSources[key] = WeakReference(loadedIcon)
                        }
                    }
                    true
                } else {
                    false
                }
            }
            if (canCache) loaded else null
        }
    }

    private fun prepareAppSource(key: String, source: Drawable?): String {
        if (source == null) return key
        synchronized(lock) {
            if (appSources[key]?.get() !== source) {
                appCache.remove(key)
                keyVersions[key] = versionLocked(key) + 1
                appSources[key] = WeakReference(source)
            }
        }
        return key
    }

    private fun prepareWidgetSource(widgetInfo: AppWidgetProviderInfo): String {
        val key = widgetPreviewKey(widgetInfo)
        synchronized(lock) {
            if (widgetSources[key]?.get() !== widgetInfo) {
                widgetCache.remove(key)
                keyVersions[key] = versionLocked(key) + 1
                widgetSources[key] = WeakReference(widgetInfo)
            }
        }
        return key
    }

    private fun widgetPreviewKey(widgetInfo: AppWidgetProviderInfo): String =
        "${widgetKeyPrefix(widgetInfo.provider.packageName)}${widgetId(widgetInfo)}:" +
            System.identityHashCode(widgetInfo)

    private fun appKey(packageName: String) = "app:$packageName"

    private fun widgetKeyPrefix(packageName: String) = "widget:$packageName:"

    private fun Drawable.toImageBitmap(size: Int): ImageBitmap = toBitmap(
        width = size,
        height = size,
        config = Bitmap.Config.ARGB_8888,
    ).asImageBitmap()

    private fun mutexFor(key: String): Mutex = synchronized(lock) {
        keyMutexes.getOrPut(key) { Mutex() }
    }

    private fun versionTokenLocked(key: String): Pair<Long, Long> =
        cacheEpoch to (keyVersions[key] ?: 0L)

    private fun versionLocked(key: String): Long = keyVersions[key] ?: 0L
}

private data class LoadedApp(
    val appInfo: AppInfo?,
    val icon: ImageBitmap,
    val isDefaultIcon: Boolean,
    val cacheable: Boolean,
)
