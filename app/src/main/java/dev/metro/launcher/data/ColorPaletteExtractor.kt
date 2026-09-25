package dev.metro.launcher.data

import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

/**
 * Экстрактор акцентных цветов из обоев — порт алгоритма metro-colors на Kotlin.
 * Находит доминантные насыщенные цвета, нормализует их под эстетику Metro
 * (lightness 0.40..0.56, saturation >= 0.48) и исключает похожие оттенки.
 */
object ColorPaletteExtractor {

    private val FALLBACK_PALETTE = listOf(
        0xFF00ABA9.toInt(), // Teal
        0xFF0050EF.toInt(), // Cobalt
        0xFFA200FF.toInt(), // Purple
        0xFF10893E.toInt(), // Green
        0xFFE51400.toInt(), // Crimson Red
        0xFFF09609.toInt(), // Orange
    )

    fun extractPalette(sourceBitmap: Bitmap, limit: Int = 6): List<Int> {
        return try {
            val sampleSize = 96
            val scaled = Bitmap.createScaledBitmap(sourceBitmap, sampleSize, sampleSize, true)
            val pixels = IntArray(sampleSize * sampleSize)
            scaled.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
            if (scaled !== sourceBitmap) scaled.recycle()

            // 4-битное квантование RGB (4096 корзин)
            val binCounts = HashMap<Int, Int>(256)
            for (p in pixels) {
                val r = (p shr 16 and 0xFF) shr 4
                val g = (p shr 8 and 0xFF) shr 4
                val b = (p and 0xFF) shr 4
                val bin = (r shl 8) or (g shl 4) or b
                binCounts[bin] = (binCounts[bin] ?: 0) + 1
            }

            // Скоринг сочности и контраста
            val hsl = FloatArray(3)
            data class ScoredColor(val rgb: Int, val score: Float, val h: Float, val s: Float, val l: Float)
            val candidates = ArrayList<ScoredColor>(binCounts.size)

            for ((bin, count) in binCounts) {
                val r = ((bin shr 8 and 0xF) shl 4) or 0x8
                val g = ((bin shr 4 and 0xF) shl 4) or 0x8
                val b = ((bin and 0xF) shl 4) or 0x8
                val color = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b

                ColorUtils.colorToHSL(color, hsl)
                val h = hsl[0]
                val s = hsl[1]
                val l = hsl[2]

                // Игнорируем серые, черные и пересвеченные белые пиксели
                if (s < 0.12f || l < 0.10f || l > 0.92f) continue

                // Формула веса из metro-colors: (0.05 + s^2) * (1.5 if 0.20 < l < 0.80 else 0.3)
                val weight = (0.05f + s * s) * (if (l in 0.20f..0.80f) 1.5f else 0.35f)
                val score = count * weight
                candidates.add(ScoredColor(color, score, h, s, l))
            }

            candidates.sortByDescending { it.score }

            // Нормализация и фильтрация дубликатов по цветовому кругу
            val result = ArrayList<Int>(limit)
            val chosenHsl = ArrayList<FloatArray>(limit)

            for (cand in candidates) {
                // Нормализация под Metro Dark Theme: насыщенность >= 0.48, светлота 0.40..0.56
                val normH = cand.h
                val normS = cand.s.coerceIn(0.48f, 1.0f)
                val normL = cand.l.coerceIn(0.40f, 0.56f)

                // Проверка на близость к уже отобранным цветам (минимальное расстояние по тону ~25 градусов)
                var tooClose = false
                for (prev in chosenHsl) {
                    val prevH = prev[0]
                    val prevL = prev[2]
                    var dH = abs(normH - prevH)
                    if (dH > 180f) dH = 360f - dH
                    val dL = abs(normL - prevL)
                    if (dH < 26f && dL < 0.18f) {
                        tooClose = true
                        break
                    }
                }
                if (tooClose) continue

                val normColor = ColorUtils.HSLToColor(floatArrayOf(normH, normS, normL))
                result.add(normColor)
                chosenHsl.add(floatArrayOf(normH, normS, normL))

                if (result.size >= limit) break
            }

            // Дополняем дефолтными акцентами Metro, если найдено мало цветов
            for (fb in FALLBACK_PALETTE) {
                if (result.size >= limit) break
                if (fb !in result) {
                    result.add(fb)
                }
            }

            result
        } catch (_: Exception) {
            FALLBACK_PALETTE.take(limit)
        }
    }
}
