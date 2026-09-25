package dev.metro.launcher.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.MetroIcon
import dev.metro.launcher.ui.theme.metroClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun WallpaperCropScreen(
    imageUri: Uri,
    onApply: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = LocalMetroScheme.current

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val bmp = decodeSampledBitmap(context, imageUri, reqMaxDim = 2560)
                if (bmp != null) {
                    sourceBitmap = bmp
                } else {
                    loadError = "Не удалось декодировать изображение"
                }
            } catch (e: Exception) {
                Log.e("WallpaperCrop", "Decode error: ${e.message}", e)
                loadError = e.localizedMessage ?: "Ошибка загрузки"
            }
        }
    }

    BackHandler { onCancel() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}, // Полный перехват жестов и тапов
            ),
    ) {
        val bmp = sourceBitmap
        if (bmp != null) {
            CropContent(
                bitmap = bmp,
                onApply = onApply,
                onCancel = onCancel,
            )
        } else if (loadError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Ошибка загрузки фото",
                    color = scheme.red,
                    fontSize = 18.sp,
                    fontFamily = MetroFonts.headline,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = loadError ?: "",
                    color = scheme.textDim,
                    fontSize = 13.sp,
                    fontFamily = MetroFonts.text,
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.glassHover)
                        .metroClickable(onClick = onCancel)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(text = "Назад", color = scheme.text, fontFamily = MetroFonts.text)
                }
            }
        } else {
            Text(
                text = "Загрузка изображения...",
                color = scheme.textDim,
                fontSize = 15.sp,
                fontFamily = MetroFonts.text,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun CropContent(
    bitmap: Bitmap,
    onApply: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    val context = LocalContext.current
    val density = LocalDensity.current

    // Отношение сторон экрана телефона
    val (screenWidth, screenHeight) = remember { getScreenDimensions(context) }
    val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalW = constraints.maxWidth.toFloat()
        val totalH = constraints.maxHeight.toFloat()

        // Рамка кадрирования по центру (с отступами для шапки и футера)
        val maxCropH = totalH * 0.70f
        val maxCropW = totalW * 0.85f

        val (cropBoxW, cropBoxH) = remember(totalW, totalH, screenAspectRatio) {
            var h = maxCropH
            var w = h * screenAspectRatio
            if (w > maxCropW) {
                w = maxCropW
                h = w / screenAspectRatio
            }
            Pair(w, h)
        }

        val cropLeft = (totalW - cropBoxW) / 2f
        val cropTop = (totalH - cropBoxH) / 2f
        val cropRect = Rect(cropLeft, cropTop, cropLeft + cropBoxW, cropTop + cropBoxH)

        // Начальный масштаб, покрывающий всю рамку
        val baseScale = remember(bitmap, cropBoxW, cropBoxH) {
            max(cropBoxW / bitmap.width.toFloat(), cropBoxH / bitmap.height.toFloat())
        }

        var userScale by remember { mutableFloatStateOf(1f) }
        var panOffset by remember { mutableStateOf(Offset.Zero) }

        val currentScale = baseScale * userScale
        val renderedW = bitmap.width * currentScale
        val renderedH = bitmap.height * currentScale

        // Ограничиваем сдвиг, чтобы фото не уходило за пределы рамки кадрирования
        val maxPanX = max(0f, (renderedW - cropBoxW) / 2f)
        val maxPanY = max(0f, (renderedH - cropBoxH) / 2f)

        // Интерактивные жесты
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(baseScale) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        userScale = (userScale * zoom).coerceIn(1f, 4.0f)
                        val newScale = baseScale * userScale
                        val newRenderedW = bitmap.width * newScale
                        val newRenderedH = bitmap.height * newScale
                        val curMaxPanX = max(0f, (newRenderedW - cropBoxW) / 2f)
                        val curMaxPanY = max(0f, (newRenderedH - cropBoxH) / 2f)

                        panOffset = Offset(
                            x = (panOffset.x + pan.x).coerceIn(-curMaxPanX, curMaxPanX),
                            y = (panOffset.y + pan.y).coerceIn(-curMaxPanY, curMaxPanY),
                        )
                    }
                },
        ) {
            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

            // Отрисовка фото и затемняющей маски
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerPanX = (totalW - renderedW) / 2f + panOffset.x
                val centerPanY = (totalH - renderedH) / 2f + panOffset.y

                // 1. Отрисовка масштабированного фото
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(centerPanX.roundToInt(), centerPanY.roundToInt()),
                    dstSize = IntSize(renderedW.roundToInt(), renderedH.roundToInt()),
                )

                // 2. Затемненная маска снаружи рамки
                val scrimPath = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, totalW, totalH))
                    addRect(cropRect)
                }
                drawPath(scrimPath, color = Color.Black.copy(alpha = 0.65f))
            }

            // Рамка видоискателя
            Box(
                modifier = Modifier
                    .offset { IntOffset(cropLeft.roundToInt(), cropTop.roundToInt()) }
                    .size(with(density) { cropBoxW.toDp() }, with(density) { cropBoxH.toDp() })
                    .border(2.dp, scheme.accent, RoundedCornerShape(2.dp)),
            )
        }

        // Верхняя панель заголовка с кнопкой «Назад»
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.glassHover)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp))
                    .metroClickable(targetScale = 0.90f, onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                MetroIcon(icon = "\uf060", fontSize = 15.sp, color = scheme.text)
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    text = "КАДРИРОВАНИЕ ОБОЕВ",
                    color = scheme.text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = MetroFonts.headline,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .background(scheme.accent, RoundedCornerShape(1.5.dp)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Масштабируйте и двигайте фото под рамку",
                    color = scheme.textDim,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                )
            }
        }

        // Нижняя панель действий
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Кнопка Отмена
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.glassHover)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp))
                    .metroClickable(targetScale = 0.94f, onClick = onCancel)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Отмена",
                    color = scheme.text,
                    fontSize = 14.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Кнопка Применить
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.accent)
                    .metroClickable(
                        targetScale = 0.94f,
                        onClick = {
                            // Вычисляем координаты среза в координатах исходного bitmap
                            val centerPanX = (totalW - renderedW) / 2f + panOffset.x
                            val centerPanY = (totalH - renderedH) / 2f + panOffset.y

                            val cropSrcX = ((cropLeft - centerPanX) / currentScale).coerceIn(0f, bitmap.width.toFloat())
                            val cropSrcY = ((cropTop - centerPanY) / currentScale).coerceIn(0f, bitmap.height.toFloat())
                            val cropSrcW = (cropBoxW / currentScale).coerceAtMost(bitmap.width - cropSrcX)
                            val cropSrcH = (cropBoxH / currentScale).coerceAtMost(bitmap.height - cropSrcY)

                            try {
                                val cropped = Bitmap.createBitmap(
                                    bitmap,
                                    cropSrcX.roundToInt(),
                                    cropSrcY.roundToInt(),
                                    cropSrcW.roundToInt().coerceAtLeast(1),
                                    cropSrcH.roundToInt().coerceAtLeast(1),
                                )
                                onApply(cropped)
                            } catch (e: Exception) {
                                Log.e("WallpaperCrop", "Crop extract error: ${e.message}", e)
                                onCancel()
                            }
                        },
                    )
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Применить",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun decodeSampledBitmap(context: Context, uri: Uri, reqMaxDim: Int): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }

    var inSampleSize = 1
    val maxDim = max(options.outWidth, options.outHeight)
    if (maxDim > reqMaxDim) {
        while ((maxDim / (inSampleSize * 2)) >= reqMaxDim) {
            inSampleSize *= 2
        }
    }

    options.inJustDecodeBounds = false
    options.inSampleSize = inSampleSize
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}

private fun getScreenDimensions(context: Context): Pair<Int, Int> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val bounds = context.getSystemService(android.view.WindowManager::class.java)
                .currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } catch (_: Exception) {
            val dm = context.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    } else {
        val dm = context.resources.displayMetrics
        dm.widthPixels to dm.heightPixels
    }
}
