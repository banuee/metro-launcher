package dev.metro.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Совместимая точка инвалидации старого IconCache.
 */
object IconCache {
    fun evict(packageName: String) {
        AppIconLoader.evict(packageName)
    }

    fun evictAll() {
        AppIconLoader.evictAll()
    }
}

/**
 * Весь "бэкенд" лаунчера: список приложений через PackageManager + запуск.
 * Тяжелая работа строго на Dispatchers.IO.
 */
class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val customizationRepo = AppCustomizationRepository.getInstance(context)
        val customLabels = customizationRepo.getAllCustomLabels()
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null // себя прячем
                AppInfo(
                    label = customLabels[pkg] ?: ri.loadLabel(pm).toString(),
                    packageName = pkg,
                    icon = ri.loadIcon(pm),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
        AppIconLoader.primeAppIcons(context, apps)
        apps
    }

    fun launch(packageName: String) {
        try {
            val intent = pm.getLaunchIntentForPackage(packageName) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
