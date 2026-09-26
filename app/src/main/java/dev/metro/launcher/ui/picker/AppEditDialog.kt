package dev.metro.launcher.ui.picker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.res.ResourcesCompat
import dev.metro.launcher.R
import dev.metro.launcher.data.AppCustomizationRepository
import dev.metro.launcher.data.AppIconLoader
import dev.metro.launcher.data.AppInfo
import dev.metro.launcher.ui.theme.DialogWindowBlurEffect
import dev.metro.launcher.ui.theme.FrostedGlassBox
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroAnimations
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.MetroIcon
import dev.metro.launcher.ui.theme.MetroIcons
import dev.metro.launcher.ui.theme.metroClickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image as ComposeImage

private val METRO_GLYPHS = listOf(
    "\uF095", // Phone
    "\uF075", // Comment/Chat
    "\uF030", // Camera
    "\uF0AC", // Browser/Globe
    "\uF001", // Music
    "\uF03D", // Video
    "\uF013", // Settings
    "\uF07B", // Folder
    "\uF0E0", // Mail
    "\uF120", // Terminal
    "\uF11B", // Gamepad
    "\uF073", // Calendar
    "\uF017", // Clock
    "\uF004", // Heart
    "\uF005", // Star
    "\uF14E", // Compass
    "\uF249", // Notes
    "\uF121", // Code
)

/**
 * Диалог кастомизации названия и иконки приложения.
 */
@Composable
fun AppEditDialog(
    app: AppInfo,
    customizationRepo: AppCustomizationRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Системное исходное название из PackageManager
    val defaultLabel = remember(app.packageName) {
        try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(app.packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            app.label
        }
    }

    var editedLabel by remember {
        mutableStateOf(customizationRepo.getCustomLabel(app.packageName) ?: app.label)
    }

    var customBitmap by remember {
        mutableStateOf<Bitmap?>(customizationRepo.getCustomIconBitmap(app.packageName))
    }
    var isIconCleared by remember { mutableStateOf(false) }
    var selectedGlyph by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }

    // Загрузка дефолтной иконки для превью, если кастомная не выбрана
    val iconRevision by AppIconLoader.revisions.collectAsState()
    val systemBitmap by produceState(
        initialValue = AppIconLoader.cachedAppIcon(app.packageName, app),
        app.packageName,
        iconRevision,
    ) {
        value = AppIconLoader.loadAppIcon(context, app.packageName, app)
    }

    // Фото-пикер для выбора пользовательской картинки из галереи
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            val loaded = loadSquareBitmap(context, uri)
            if (loaded != null) {
                customBitmap = loaded
                isIconCleared = false
                selectedGlyph = null
            }
        }
    }

    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 240, easing = MetroAnimations.OpenEasing),
        )
    }
    val slidePx = with(density) { 32.dp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        DialogWindowBlurEffect(blurRadiusPx = 65, dimAmount = 0.35f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f * anim.value))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            FrostedGlassBox(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 400.dp)
                    .graphicsLayer {
                        val p = anim.value
                        scaleX = 0.90f + 0.10f * p
                        scaleY = 0.90f + 0.10f * p
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * slidePx
                    }
                    .clickable(enabled = false, onClick = {}) // не закрывать при клике внутри
                    .navigationBarsPadding(),
                shape = MetroDimens.panelRadius,
                tint = scheme.glassDeep,
                borderColor = scheme.strokeStrong,
                borderWidth = 1.dp,
            ) {
                // Акцентный блик сверху
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    scheme.accent.copy(alpha = 0.85f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                ) {
                    // Заголовок окна
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(width = 3.dp, height = 16.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(scheme.accent),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "РЕДАКТИРОВАНИЕ",
                                color = scheme.text,
                                fontSize = 14.sp,
                                fontFamily = MetroFonts.headline,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .metroClickable(targetScale = 0.88f, onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            MetroIcon(
                                icon = MetroIcons.Close,
                                color = scheme.textDim,
                                fontSize = 16.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Живое превью плитки
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            FrostedGlassBox(
                                modifier = Modifier.size(68.dp),
                                shape = 12.dp,
                                tint = scheme.glass,
                                borderColor = scheme.strokeStrong,
                            ) {
                                val activeBmp = when {
                                    customBitmap != null -> customBitmap?.asImageBitmap()
                                    isIconCleared -> systemBitmap
                                    else -> systemBitmap
                                }
                                if (activeBmp != null) {
                                    ComposeImage(
                                        bitmap = activeBmp,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .align(Alignment.Center)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (editedLabel.isNotBlank()) editedLabel else defaultLabel,
                                color = scheme.text,
                                fontSize = 13.sp,
                                fontFamily = MetroFonts.text,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = scheme.stroke, thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))

                    // 1. Поле ввода названия
                    Text(
                        text = "НАЗВАНИЕ",
                        color = scheme.textDim,
                        fontSize = 11.sp,
                        fontFamily = MetroFonts.headline,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    FrostedGlassBox(
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        tint = scheme.glassHover,
                        borderColor = if (focused) scheme.accent else scheme.stroke,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                BasicTextField(
                                    value = editedLabel,
                                    onValueChange = { editedLabel = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = scheme.text,
                                        fontSize = 14.sp,
                                        fontFamily = MetroFonts.text,
                                    ),
                                    cursorBrush = SolidColor(scheme.accent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focused = it.isFocused },
                                    decorationBox = { inner ->
                                        if (editedLabel.isEmpty()) {
                                            Text(
                                                text = defaultLabel,
                                                color = scheme.textDim,
                                                fontSize = 14.sp,
                                                fontFamily = MetroFonts.text,
                                            )
                                        }
                                        inner()
                                    },
                                )
                            }
                            if (editedLabel != defaultLabel) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .metroClickable(targetScale = 0.88f) { editedLabel = defaultLabel }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Сброс",
                                        color = scheme.accent,
                                        fontSize = 12.sp,
                                        fontFamily = MetroFonts.text,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // 2. Иконка приложения
                    Text(
                        text = "ЗНАЧОК",
                        color = scheme.textDim,
                        fontSize = 11.sp,
                        fontFamily = MetroFonts.headline,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Кнопка выбора из галереи
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(MetroDimens.radiusSmall))
                                .background(scheme.glass)
                                .border(1.dp, scheme.stroke, RoundedCornerShape(MetroDimens.radiusSmall))
                                .metroClickable(targetScale = 0.94f) {
                                    pickPhotoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                }
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            MetroIcon(
                                icon = MetroIcons.Image,
                                color = scheme.accent,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Из галереи",
                                color = scheme.text,
                                fontSize = 12.sp,
                                fontFamily = MetroFonts.text,
                                maxLines = 1,
                            )
                        }

                        // Кнопка сброса значка на системный
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(MetroDimens.radiusSmall))
                                .background(scheme.glass)
                                .border(1.dp, scheme.stroke, RoundedCornerShape(MetroDimens.radiusSmall))
                                .metroClickable(targetScale = 0.94f) {
                                    customBitmap = null
                                    isIconCleared = true
                                    selectedGlyph = null
                                }
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            MetroIcon(
                                icon = MetroIcons.Reset,
                                color = scheme.textDim,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Сбросить значок",
                                color = scheme.textDim,
                                fontSize = 12.sp,
                                fontFamily = MetroFonts.text,
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Символы Metro
                    Text(
                        text = "СИМВОЛЫ METRO",
                        color = scheme.textDim,
                        fontSize = 10.sp,
                        fontFamily = MetroFonts.headline,
                        letterSpacing = 0.8.sp,
                    )
                    Spacer(Modifier.height(6.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.fillMaxWidth().height(88.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(METRO_GLYPHS) { glyph ->
                            val isSelected = selectedGlyph == glyph
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) scheme.accent.copy(alpha = 0.35f)
                                        else scheme.glass,
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) scheme.accent else scheme.stroke,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .metroClickable(targetScale = 0.88f) {
                                        selectedGlyph = glyph
                                        customBitmap = renderGlyphToBitmap(context, glyph, scheme.accent.toArgb())
                                        isIconCleared = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                MetroIcon(
                                    icon = glyph,
                                    color = if (isSelected) scheme.accent else scheme.text,
                                    fontSize = 17.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = scheme.stroke, thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))

                    // Кнопки Сохранить и Отмена
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(MetroDimens.radiusSmall))
                                .background(scheme.glass)
                                .border(1.dp, scheme.stroke, RoundedCornerShape(MetroDimens.radiusSmall))
                                .metroClickable(targetScale = 0.94f, onClick = onDismiss)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Отмена",
                                color = scheme.textDim,
                                fontSize = 14.sp,
                                fontFamily = MetroFonts.text,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(MetroDimens.radiusSmall))
                                .background(scheme.accent.copy(alpha = 0.85f))
                                .border(1.dp, scheme.accent, RoundedCornerShape(MetroDimens.radiusSmall))
                                .metroClickable(targetScale = 0.94f) {
                                    scope.launch {
                                        val trimmed = editedLabel.trim()
                                        if (trimmed.isNotEmpty() && trimmed != defaultLabel) {
                                            customizationRepo.setCustomLabel(app.packageName, trimmed)
                                        } else {
                                            customizationRepo.setCustomLabel(app.packageName, null)
                                        }
                                        if (customBitmap != null) {
                                            customizationRepo.saveCustomIcon(app.packageName, customBitmap!!)
                                        } else if (isIconCleared) {
                                            customizationRepo.clearCustomIcon(app.packageName)
                                        }
                                        onSaved()
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Сохранить",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = MetroFonts.text,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Рендерит глиф из Noto Sans Nerd Font в квадратный Bitmap 144x144 с акцентным фоном.
 */
private fun renderGlyphToBitmap(context: Context, glyph: String, accentColor: Int): Bitmap {
    val size = 144
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val typeface = ResourcesCompat.getFont(context, R.font.noto_sans_nerd_font)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
    }
    val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
    canvas.drawRoundRect(rect, 24f, 24f, bgPaint)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        this.textSize = size * 0.50f
        this.color = android.graphics.Color.WHITE
        this.textAlign = Paint.Align.CENTER
    }
    val bounds = Rect()
    paint.getTextBounds(glyph, 0, glyph.length, bounds)
    val y = (size / 2f) + (bounds.height() / 2f) - bounds.bottom
    canvas.drawText(glyph, size / 2f, y, paint)
    return bmp
}

/**
 * Загружает и масштабирует изображение из Uri в квадратный Bitmap 144x144.
 */
private fun loadSquareBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        val source = BitmapFactory.decodeStream(stream) ?: return null
        stream.close()
        val minDim = minOf(source.width, source.height)
        val startX = (source.width - minDim) / 2
        val startY = (source.height - minDim) / 2
        val square = Bitmap.createBitmap(source, startX, startY, minDim, minDim)
        Bitmap.createScaledBitmap(square, 144, 144, true)
    } catch (_: Exception) {
        null
    }
}
