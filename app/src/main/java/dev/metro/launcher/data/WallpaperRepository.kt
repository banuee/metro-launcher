package dev.metro.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private val Context.wallpaperStore by preferencesDataStore(name = "metro_wallpaper")

/**
 * Системные обои двумя слоями: резкий + предблюренный (для фрост-плиток
 * и кросфейда при открытии меню). Живые обои — null, тогда откат на
 * прозрачное окно + оконный блюр (старое поведение).
 */
data class DeviceWallpaper(
    val sharp: ImageBitmap,
    val blurred: ImageBitmap,
)

/**
 * Обои лаунчера.
 *
 * Системный битмап на API 33+ сторонним приложениям недоступен в принципе:
 * WallpaperManagerService требует legacy READ_EXTERNAL_STORAGE, который
 * новым приложениям не выдаётся (StorageManager.checkPermissionReadImages).
 * Поэтому: приоритет — своя копия (выбор через Photo Picker, ему вообще
 * не нужно разрешение), затем попытка системных (срабатывает на старых
 * API / если повезло), иначе null = прозрачное окно.
 */
class WallpaperRepository(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val askedKey = booleanPreferencesKey("picker_asked")

    private val _wallpaper = MutableStateFlow<DeviceWallpaper?>(null)
    val wallpaper: StateFlow<DeviceWallpaper?> = _wallpaper

    // last-wins: новый reload отменяет предыдущий декод, иначе параллельные
    // вызовы (ресивер + onResume + setCustom) кодируют полноэкранные битмапы
    // одновременно — пик памяти и OOM на слабых устройствах.
    private val loadLock = Any()
    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        reload()
    }

    fun reload() {
        val job = synchronized(loadLock) {
            loadJob?.cancel()
            scope.launch {
                _wallpaper.value = load()
            }.also { loadJob = it }
        }
        job.invokeOnCompletion { synchronized(loadLock) { if (loadJob === job) loadJob = null } }
    }

    /** Освободить scope; вызывать при уничтожении владельца репозитория. */
    fun close() {
        synchronized(loadLock) { loadJob?.cancel() }
        scope.cancel()
    }

    /** Одноразовый автопромпт выбора обоев (первый запуск без фроста). */
    suspend fun consumePickerPrompt(): Boolean {
        val asked = app.wallpaperStore.data.map { it[askedKey] == true }.first()
        if (asked) return false
        app.wallpaperStore.edit { it[askedKey] = true }
        return true
    }

    /**
     * Только для инструментальных тестов: пометить промпт потреблённым, чтобы
     * first-run автозапуск системного photo picker'а не перекрывал активность
     * посреди жестов (тесты теряют compose-иерархию). Продакшн не использует.
     */
    suspend fun suppressPickerPromptForTests() {
        app.wallpaperStore.edit { it[askedKey] = true }
    }

    /** Сохранить выбранную в пикере картинку как обои лаунчера. */
    fun setCustom(uri: Uri) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        app.openFileOutput("launcher_wallpaper.jpg", Context.MODE_PRIVATE).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("MetroLauncher", "wallpaper: custom image saved")
                } catch (e: Exception) {
                    Log.d("MetroLauncher", "wallpaper: custom save failed: ${e.message}")
                }
            }
            reload()
        }
    }

    private suspend fun load(): DeviceWallpaper? {
        val custom = loadCustom()
        if (custom != null) return custom
        return loadSystem()
    }

    private fun customFile() = java.io.File(app.filesDir, "launcher_wallpaper.jpg")

    private fun loadCustom(): DeviceWallpaper? {
        val f = customFile()
        if (!f.exists()) return null
        var src: Bitmap? = null
        return try {
            val (sw, sh) = screenSize()
            src = BitmapFactory.decodeFile(f.absolutePath) ?: return null
            Log.d("MetroLauncher", "wallpaper: custom ${src.width}x${src.height}")
            // centerCrop владеет src: либо отдаёт его как есть (совпал размер),
            // либо сам переиспользует и утилизирует промежуточные — здесь
            // утилизировать НЕчего, ownership передан.
            val bmp = centerCrop(src, sw, sh) ?: return null
            src = null
            DeviceWallpaper(bmp.asImageBitmap(), blurBitmap(bmp, sw, sh).asImageBitmap())
        } catch (e: OutOfMemoryError) {
            Log.d("MetroLauncher", "wallpaper: custom OOM, fallback")
            null
        } catch (e: Exception) {
            Log.d("MetroLauncher", "wallpaper: custom failed: ${e.message}")
            null
        } finally {
            src?.recycle()
        }
    }

    private fun loadSystem(): DeviceWallpaper? {
        val wm = app.getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
        if (wm.wallpaperInfo != null) {
            Log.d("MetroLauncher", "wallpaper: live wallpaper, fallback to transparent")
            return null
        }
        val drawable = try {
            wm.drawable ?: run {
                Log.d("MetroLauncher", "wallpaper: system drawable is null, fallback")
                return null
            }
        } catch (e: Exception) {
            Log.d("MetroLauncher", "wallpaper: system blocked (${e.message}), pick custom")
            return null
        }
        val (sw, sh) = screenSize()
        if (sw <= 0 || sh <= 0) return null
        val bmp = drawableToBitmap(drawable, sw, sh)
        if (bmp == null) {
            Log.d("MetroLauncher", "wallpaper: bitmap convert failed")
            return null
        }
        Log.d("MetroLauncher", "wallpaper: system ${bmp.width}x${bmp.height}")
        return try {
            DeviceWallpaper(
                sharp = bmp.asImageBitmap(),
                blurred = blurBitmap(bmp, sw, sh).asImageBitmap(),
            )
        } catch (e: OutOfMemoryError) {
            Log.d("MetroLauncher", "wallpaper: system OOM, transparent fallback")
            bmp.recycle()
            null
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val metrics = app.getSystemService(android.view.WindowManager::class.java)
                    .currentWindowMetrics.bounds
                metrics.width() to metrics.height()
            } catch (_: Exception) {
                fallbackSize()
            }
        } else {
            fallbackSize()
        }
    }

    private fun fallbackSize(): Pair<Int, Int> {
        val dm = app.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    /**
     * Center-crop битмапа ровно под экран.
     *
     * recycleSrc=true означает владение: промежуточные битмапы и (если результат
     * не сам src) исходник утилизируются. Для битмапа чужого Drawable
     * (BitmapDrawable из WallpaperManager) передавай false — recycle чужого
     * кэшированного битмапа убьёт отрисовку.
     */
    private fun centerCrop(src: Bitmap, sw: Int, sh: Int, recycleSrc: Boolean = true): Bitmap? {
        var scaled: Bitmap? = null
        return try {
            if (src.width == sw && src.height == sh) return src
            val scale = maxOf(sw / src.width.toFloat(), sh / src.height.toFloat())
            val dw = (src.width * scale).roundToInt()
            val dh = (src.height * scale).roundToInt()
            scaled = Bitmap.createScaledBitmap(src, dw, dh, true)
            val x = ((dw - sw) / 2).coerceAtLeast(0)
            val y = ((dh - sh) / 2).coerceAtLeast(0)
            val result = Bitmap.createBitmap(
                scaled, x, y, sw.coerceAtMost(dw - x), sh.coerceAtMost(dh - y),
            )
            // createBitmap из подмножества копирует пиксели; если вдруг вернул
            // сам scaled — утилизировать его нельзя.
            if (result !== scaled) {
                scaled.recycle()
                scaled = null
            }
            if (recycleSrc && result !== src) src.recycle()
            result
        } catch (_: Exception) {
            try { scaled?.recycle() } catch (_: Exception) {}
            if (recycleSrc && (src.width != sw || src.height != sh)) {
                try { src.recycle() } catch (_: Exception) {}
            }
            null
        }
    }

    /** Center-crop системных обоев ровно под экран. */
    private fun drawableToBitmap(
        drawable: android.graphics.drawable.Drawable,
        sw: Int,
        sh: Int,
    ): Bitmap? {
        return try {
            val src = (drawable as? BitmapDrawable)?.bitmap ?: run {
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: sw
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: sh
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(Canvas(it))
                }
            }
            centerCrop(src, sw, sh, recycleSrc = false)
        } catch (_: Exception) {
            null
        }
    }

    /** Высококачественный StackBlur без артефактов и полос. */
    private fun blurBitmap(src: Bitmap, sw: Int, sh: Int): Bitmap {
        val scaleFactor = 4
        val w = (sw / scaleFactor).coerceAtLeast(1)
        val h = (sh / scaleFactor).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val blurredSmall = try {
            stackBlur(small, 24)
        } catch (e: Exception) {
            if (small !== src) small.recycle()
            throw e
        }
        // stackBlur копирует битмап; если вдруг вернул сам small — не recycle.
        if (blurredSmall !== small) small.recycle()
        return try {
            Bitmap.createScaledBitmap(blurredSmall, sw, sh, true)
        } finally {
            if (blurredSmall !== src) blurredSmall.recycle()
        }
    }

    private fun stackBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return sentBitmap
        val bitmap = sentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(maxOf(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        yw = 0
        yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (yIdx in 0 until h) {
            y = yIdx
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0
            for (iStep in -radius..radius) {
                p = pix[yi + minOf(wm, maxOf(iStep, 0))]
                sir = stack[iStep + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - abs(iStep)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (iStep > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (xIdx in 0 until w) {
                x = xIdx
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = minOf(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }

        for (xIdx in 0 until w) {
            x = xIdx
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0
            yp = -radius * w
            for (iStep in -radius..radius) {
                yi = maxOf(0, yp) + x
                sir = stack[iStep + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - abs(iStep)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (iStep > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (iStep < hm) {
                    yp += w
                }
            }
            yi = x
            stackpointer = radius
            for (yIdx in 0 until h) {
                y = yIdx
                pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = minOf(y + r1, hm) * w
                }
                p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}
