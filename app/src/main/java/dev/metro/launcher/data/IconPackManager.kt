package dev.metro.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

data class IconPackInfo(
    val packageName: String,
    val name: String,
    val isIconPackStudio: Boolean = false,
)

object IconPackManager {
    private const val APP_ICON_SIZE = 144

    private val THEME_INTENTS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.fede.launcher.THEME_ICONPACK",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
    )

    private val lock = Any()
    private var activePackPackage: String = ""
    private var activePackResources: Resources? = null
    // Component (pkg/cls) or package name -> drawable resource name
    private var activeComponentMap: Map<String, String>? = null

    /**
     * Поиск всех установленных пакетов иконок (включая темы Icon Pack Studio).
     */
    suspend fun getInstalledIconPacks(context: Context): List<IconPackInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packs = mutableMapOf<String, IconPackInfo>()

        for (action in THEME_INTENTS) {
            val intent = Intent(action)
            val list = try {
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            } catch (_: Exception) {
                emptyList()
            }
            for (resolve in list) {
                val pkg = resolve.activityInfo?.packageName ?: continue
                if (pkg == context.packageName) continue
                val label = try {
                    resolve.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkg
                }
                val isIps = pkg.startsWith("ginlemon.flowerfree") ||
                    pkg.contains("iconpackstudio", ignoreCase = true)
                packs.putIfAbsent(pkg, IconPackInfo(pkg, label, isIps))
            }
        }

        // Проверим также все установленные пакеты от Icon Pack Studio (ginlemon.flowerfree.*)
        try {
            val installedApps = pm.getInstalledApplications(0)
            for (app in installedApps) {
                if (app.packageName.startsWith("ginlemon.flowerfree") && !packs.containsKey(app.packageName)) {
                    val label = try {
                        pm.getApplicationLabel(app).toString()
                    } catch (_: Exception) {
                        app.packageName
                    }
                    packs[app.packageName] = IconPackInfo(app.packageName, label, isIconPackStudio = true)
                }
            }
        } catch (_: Exception) {}

        packs.values.sortedWith(
            compareByDescending<IconPackInfo> { it.isIconPackStudio }
                .thenBy { it.name.lowercase() }
        )
    }

    /**
     * Проверка, установлено ли само приложение Icon Pack Studio.
     */
    fun isIconPackStudioInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("ginlemon.iconpackstudio", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Загружает и парсит appfilter.xml выбранного пакета иконок.
     */
    suspend fun loadIconPack(context: Context, packageName: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (activePackPackage == packageName && activeComponentMap != null) {
                return@withContext
            }
            if (packageName.isBlank()) {
                activePackPackage = ""
                activePackResources = null
                activeComponentMap = null
                return@withContext
            }
        }

        val pm = context.packageManager
        val iconPackResources = try {
            pm.getResourcesForApplication(packageName)
        } catch (_: Exception) {
            null
        }

        if (iconPackResources == null) {
            synchronized(lock) {
                activePackPackage = ""
                activePackResources = null
                activeComponentMap = null
            }
            return@withContext
        }

        val map = parseAppFilter(context, packageName, iconPackResources)
        synchronized(lock) {
            activePackPackage = packageName
            activePackResources = iconPackResources
            activeComponentMap = map
        }
    }

    fun getActivePackPackage(): String {
        return synchronized(lock) { activePackPackage }
    }

    /**
     * Загружает значок приложения из активного пакета иконок.
     */
    fun loadIcon(context: Context, targetPackage: String): ImageBitmap? {
        val (res, map, packPkg) = synchronized(lock) {
            Triple(activePackResources, activeComponentMap, activePackPackage)
        }
        if (res == null || map == null || packPkg.isBlank()) return null

        val pm = context.packageManager
        val launchIntent = try { pm.getLaunchIntentForPackage(targetPackage) } catch (_: Exception) { null }
        val componentName = launchIntent?.component
        val drawableName = if (componentName != null) {
            val fullKey = "${componentName.packageName}/${componentName.className}"
            map[fullKey] ?: map[targetPackage]
        } else {
            map[targetPackage]
        } ?: return null

        val resId = try {
            res.getIdentifier(drawableName, "drawable", packPkg)
        } catch (_: Exception) {
            0
        }
        if (resId == 0) return null

        val drawable = try {
            res.getDrawable(resId, null)
        } catch (_: Exception) {
            null
        } ?: return null

        return runCatching { drawable.toImageBitmap(APP_ICON_SIZE) }.getOrNull()
    }

    private fun Drawable.toImageBitmap(size: Int): ImageBitmap = toBitmap(
        width = size,
        height = size,
        config = Bitmap.Config.ARGB_8888,
    ).asImageBitmap()

    private fun parseAppFilter(
        context: Context,
        packageName: String,
        resources: Resources,
    ): Map<String, String> {
        val map = HashMap<String, String>(1024)

        // 1. Попробуем открыть assets/appfilter.xml
        var inputStream: InputStream? = null
        try {
            val iconPackContext = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
            inputStream = iconPackContext.assets.open("appfilter.xml")
        } catch (_: Exception) {}

        if (inputStream != null) {
            try {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(inputStream, "UTF-8")
                parseXml(parser, map)
            } catch (_: Exception) {
            } finally {
                try { inputStream.close() } catch (_: Exception) {}
            }
            if (map.isNotEmpty()) return map
        }

        // 2. Попробуем res/xml/appfilter.xml
        try {
            val resId = resources.getIdentifier("appfilter", "xml", packageName)
            if (resId != 0) {
                val xmlParser = resources.getXml(resId)
                parseXml(xmlParser, map)
                xmlParser.close()
            }
        } catch (_: Exception) {}

        if (map.isNotEmpty()) return map

        // 3. Попробуем res/raw/appfilter.xml
        try {
            val rawId = resources.getIdentifier("appfilter", "raw", packageName)
            if (rawId != 0) {
                val rawStream = resources.openRawResource(rawId)
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(rawStream, "UTF-8")
                parseXml(parser, map)
                rawStream.close()
            }
        } catch (_: Exception) {}

        return map
    }

    private fun parseXml(parser: XmlPullParser, map: MutableMap<String, String>) {
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("item", ignoreCase = true)) {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (!drawable.isNullOrBlank() && !component.isNullOrBlank()) {
                    val clean = component
                        .removePrefix("ComponentInfo{")
                        .removeSuffix("}")
                    val slashIdx = clean.indexOf('/')
                    if (slashIdx != -1) {
                        val pkg = clean.substring(0, slashIdx).trim()
                        val cls = clean.substring(slashIdx + 1).trim()
                        map["$pkg/$cls"] = drawable
                        map.putIfAbsent(pkg, drawable)
                    } else {
                        map.putIfAbsent(clean.trim(), drawable)
                    }
                }
            }
            eventType = parser.next()
        }
    }
}
