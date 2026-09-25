package dev.metro.launcher.ui.settings

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metro.launcher.BuildConfig
import dev.metro.launcher.data.DeviceWallpaper
import dev.metro.launcher.data.MetroSettingsRepository
import dev.metro.launcher.data.UpdateRepository
import dev.metro.launcher.data.UpdateState
import dev.metro.launcher.data.WallpaperRepository
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroAnimations
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.MetroIcon
import dev.metro.launcher.ui.theme.metroClickable
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class SettingsSection {
    HUB,
    WALLPAPER,
    COLORS,
    GLASS,
    UPDATES,
    ABOUT,
}

@Composable
fun MetroSettingsScreen(
    settingsRepo: MetroSettingsRepository,
    wallpaperRepo: WallpaperRepository,
    updateRepo: UpdateRepository,
    onPickWallpaper: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    val settings by settingsRepo.settings.collectAsState()
    var currentSection by remember { mutableStateOf(SettingsSection.HUB) }

    BackHandler(enabled = true) {
        if (currentSection == SettingsSection.HUB) {
            onDismiss()
        } else {
            currentSection = SettingsSection.HUB
        }
    }

    // Тёмный акриловый фрост + поглощение всех кликов (чтобы ничего не протекало вниз)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color(0xFF0C0C0C).copy(
                    alpha = (0.84f + settings.glassDeepAlpha * 0.12f).coerceIn(0.75f, 0.96f),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}, // Полный перехват тапов, исключающий случайные нажатия на рабочий стол
            ),
    ) {
        // Верхний акцентный световой штрих Metro (Top accent glow 2.5dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(scheme.accent),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Верхняя акцентная шапка Metro
            SettingsHeader(
                currentSection = currentSection,
                onBack = { currentSection = SettingsSection.HUB },
                onClose = onDismiss,
            )

            // Контент с плавным переходом между разделами по кривым Metro
            AnimatedContent(
                targetState = currentSection,
                transitionSpec = {
                    if (targetState == SettingsSection.HUB) {
                        (slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = tween(280, easing = MetroAnimations.OpenEasing),
                        ) + fadeIn(animationSpec = tween(220, easing = MetroAnimations.OpenEasing)))
                            .togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(240, easing = MetroAnimations.CloseEasing),
                                ) + fadeOut(animationSpec = tween(180, easing = MetroAnimations.CloseEasing)),
                            )
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(280, easing = MetroAnimations.OpenEasing),
                        ) + fadeIn(animationSpec = tween(220, easing = MetroAnimations.OpenEasing)))
                            .togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { -it / 3 },
                                    animationSpec = tween(240, easing = MetroAnimations.CloseEasing),
                                ) + fadeOut(animationSpec = tween(180, easing = MetroAnimations.CloseEasing)),
                            )
                    }
                },
                label = "settings-nav",
                modifier = Modifier.fillMaxSize(),
            ) { section ->
                when (section) {
                    SettingsSection.HUB -> HubSection(onNavigate = { currentSection = it })
                    SettingsSection.WALLPAPER -> WallpaperSection(
                        wallpaperRepo = wallpaperRepo,
                        onPickWallpaper = onPickWallpaper,
                    )
                    SettingsSection.COLORS -> ColorsSection(
                        settingsRepo = settingsRepo,
                    )
                    SettingsSection.GLASS -> GlassSection(
                        settingsRepo = settingsRepo,
                    )
                    SettingsSection.UPDATES -> UpdatesSection(
                        updateRepo = updateRepo,
                    )
                    SettingsSection.ABOUT -> AboutSection(
                        settingsRepo = settingsRepo,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    currentSection: SettingsSection,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    val title = when (currentSection) {
        SettingsSection.HUB -> "ПАРАМЕТРЫ"
        SettingsSection.WALLPAPER -> "ОБОИ"
        SettingsSection.COLORS -> "ЦВЕТА И АКЦЕНТЫ"
        SettingsSection.GLASS -> "БЛЮР И СТЕКЛО"
        SettingsSection.UPDATES -> "ОБНОВЛЕНИЯ"
        SettingsSection.ABOUT -> "О ЛАУНЧЕРЕ"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Кнопка «Назад» (плавное появление только в подразделах)
            AnimatedVisibility(
                visible = currentSection != SettingsSection.HUB,
                enter = fadeIn(tween(180, easing = MetroAnimations.OpenEasing)) +
                        scaleIn(initialScale = 0.8f, animationSpec = tween(180, easing = MetroAnimations.OpenEasing)),
                exit = fadeOut(tween(140, easing = MetroAnimations.CloseEasing)) +
                        scaleOut(targetScale = 0.8f, animationSpec = tween(140, easing = MetroAnimations.CloseEasing)),
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.glassHover)
                            .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp))
                            .metroClickable(targetScale = 0.90f, onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        MetroIcon(icon = "\uf060", fontSize = 15.sp, color = scheme.text)
                    }
                    Spacer(Modifier.width(12.dp))
                }
            }

            Column {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(200, easing = MetroAnimations.OpenEasing)))
                            .togetherWith(fadeOut(animationSpec = tween(150, easing = MetroAnimations.CloseEasing)))
                    },
                    label = "header-title",
                ) { targetTitle ->
                    Text(
                        text = targetTitle,
                        color = scheme.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = MetroFonts.headline,
                        letterSpacing = 1.5.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .background(scheme.accent, RoundedCornerShape(1.5.dp)),
                )
            }
        }

        // Кнопка закрытия [×] с комфортной тач-зоной 44x44dp
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(scheme.glassHover)
                .border(1.dp, scheme.strokeStrong, CircleShape)
                .metroClickable(targetScale = 0.88f, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            MetroIcon(icon = "\uf00d", fontSize = 15.sp, color = scheme.text)
        }
    }
}

@Composable
private fun HubSection(onNavigate: (SettingsSection) -> Unit) {
    val scheme = LocalMetroScheme.current

    val items = remember {
        listOf(
            HubItem(
                section = SettingsSection.WALLPAPER,
                glyph = "\uf03e",
                title = "Обои и кадрирование",
                subtitle = "Выбор фото, кадрирование под экран и размытие",
            ),
            HubItem(
                section = SettingsSection.COLORS,
                glyph = "\uf1fc",
                title = "Цветовая палитра и акцент",
                subtitle = "Палитра из обоев, акцентные оттенки, произвольный цвет",
            ),
            HubItem(
                section = SettingsSection.GLASS,
                glyph = "\uf2d0",
                title = "Блюр и прозрачность стекла",
                subtitle = "Радиус блюра, прозрачность плиток, меню и границ",
            ),
            HubItem(
                section = SettingsSection.UPDATES,
                glyph = "\uf021",
                title = "Обновления лаунчера",
                subtitle = "Автоматические OTA-обновления по воздуху через GitHub",
            ),
            HubItem(
                section = SettingsSection.ABOUT,
                glyph = "\uf109",
                title = "О лаунчере",
                subtitle = "Версия, архитектура Quickshell Metro, сброс параметров",
            ),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items) { item ->
            HubCard(item = item, onClick = { onNavigate(item.section) })
        }
    }
}

private data class HubItem(
    val section: SettingsSection,
    val glyph: String,
    val title: String,
    val subtitle: String,
)

@Composable
private fun HubCard(item: HubItem, onClick: () -> Unit) {
    val scheme = LocalMetroScheme.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.glass)
            .border(1.dp, scheme.stroke, RoundedCornerShape(12.dp))
            .metroClickable(targetScale = 0.97f, onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.glassHover)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                MetroIcon(icon = item.glyph, fontSize = 20.sp, color = scheme.accent)
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = scheme.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = MetroFonts.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    color = scheme.textDim,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                )
            }

            MetroIcon(icon = "\uf054", fontSize = 12.sp, color = scheme.textDim)
        }
    }
}

@Composable
private fun WallpaperSection(
    wallpaperRepo: WallpaperRepository,
    onPickWallpaper: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    val wallpaper by wallpaperRepo.wallpaper.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "ТЕКУЩИЕ ОБОИ",
                fontSize = 11.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.SemiBold,
                color = scheme.textDim,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.glass)
                    .border(1.dp, scheme.strokeStrong, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val wp = wallpaper
                AnimatedContent(
                    targetState = wp,
                    transitionSpec = {
                        (fadeIn(tween(250, easing = MetroAnimations.OpenEasing)))
                            .togetherWith(fadeOut(tween(180, easing = MetroAnimations.CloseEasing)))
                    },
                    label = "wp-preview-fade",
                ) { currentWp ->
                    if (currentWp != null) {
                        Image(
                            bitmap = currentWp.sharp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            MetroIcon(icon = "\uf03e", fontSize = 32.sp, color = scheme.textDim)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Системные обои или прозрачный фон",
                                color = scheme.textDim,
                                fontSize = 13.sp,
                                fontFamily = MetroFonts.text,
                            )
                        }
                    }
                }
            }
        }

        item {
            // Кнопка выбора и кадрирования
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.accent)
                    .metroClickable(targetScale = 0.96f, onClick = onPickWallpaper)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetroIcon(icon = "\uf03e", fontSize = 16.sp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Выбрать и кадрировать фото...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = MetroFonts.text,
                    )
                }
            }
        }

        item {
            // Кнопка сброса
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.glassHover)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp))
                    .metroClickable(targetScale = 0.96f, onClick = { wallpaperRepo.resetToSystemWallpaper() })
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Сбросить к системным обоям",
                    color = scheme.textDim,
                    fontSize = 13.sp,
                    fontFamily = MetroFonts.text,
                )
            }
        }
    }
}

@Composable
private fun ColorsSection(
    settingsRepo: MetroSettingsRepository,
) {
    val scheme = LocalMetroScheme.current
    val settings by settingsRepo.settings.collectAsState()
    var showColorPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Тумблер Авто-акцент
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.glass)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Авто-акцент из обоев",
                            color = scheme.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = MetroFonts.text,
                        )
                        Text(
                            text = "Автоматически брать доминантный сочный цвет",
                            color = scheme.textDim,
                            fontSize = 12.sp,
                            fontFamily = MetroFonts.text,
                        )
                    }
                    Switch(
                        checked = settings.autoAccent,
                        onCheckedChange = { settingsRepo.setAutoAccent(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = scheme.accent,
                            checkedTrackColor = scheme.glassHover,
                        ),
                    )
                }
            }
        }

        // Палитра из текущих обоев
        item {
            Text(
                text = "ПАЛИТРА ИЗ ТЕКУЩИХ ОБОЕВ",
                fontSize = 11.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.SemiBold,
                color = scheme.textDim,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))

            val palette = settings.generatedPalette
            if (palette.isNotEmpty()) {
                val paletteAlpha by animateFloatAsState(
                    targetValue = if (settings.autoAccent) 0.50f else 1.0f,
                    animationSpec = tween(200),
                    label = "palette-alpha",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(paletteAlpha),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    palette.forEach { colorInt ->
                        val isSelected = !settings.autoAccent && settings.accentColor == colorInt
                        val borderW by animateDpAsState(
                            targetValue = if (isSelected) 3.dp else 1.dp,
                            animationSpec = tween(200, easing = MetroAnimations.OpenEasing),
                            label = "swatch-bw",
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                            animationSpec = tween(200),
                            label = "swatch-bc",
                        )
                        val swatchScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "swatch-scale",
                        )

                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .graphicsLayer(scaleX = swatchScale, scaleY = swatchScale)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(colorInt))
                                .border(borderW, borderColor, RoundedCornerShape(8.dp))
                                .metroClickable(
                                    targetScale = 0.90f,
                                    onClick = { settingsRepo.setAccentColor(colorInt, auto = false) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.5f),
                                exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.5f),
                            ) {
                                MetroIcon(icon = "\uf00c", fontSize = 16.sp, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Палитра формируется при установке фото-обоев",
                    color = scheme.textDim,
                    fontSize = 13.sp,
                    fontFamily = MetroFonts.text,
                )
            }
        }

        // Произвольный цвет через пикер
        item {
            val animatedAccent by animateColorAsState(
                targetValue = scheme.accent,
                animationSpec = tween(280, easing = MetroAnimations.OpenEasing),
                label = "custom-accent",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.glassHover)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp))
                    .metroClickable(targetScale = 0.96f, onClick = { showColorPicker = true })
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetroIcon(icon = "\uf1fc", fontSize = 16.sp, color = animatedAccent)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Выбрать произвольный цвет (HEX/Спектр)...",
                            color = scheme.text,
                            fontSize = 13.sp,
                            fontFamily = MetroFonts.text,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(animatedAccent)
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    )
                }
            }
        }

        // Превью плитки с выбранным цветом
        item {
            Text(
                text = "ПРЕДПРОСМОТР ПЛИТКИ",
                fontSize = 11.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.SemiBold,
                color = scheme.textDim,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))

            val animatedAccent by animateColorAsState(
                targetValue = scheme.accent,
                animationSpec = tween(280, easing = MetroAnimations.OpenEasing),
                label = "preview-tile-accent",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.glass)
                    .border(1.dp, animatedAccent, RoundedCornerShape(10.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(animatedAccent),
                        contentAlignment = Alignment.Center,
                    ) {
                        MetroIcon(icon = "\uf009", fontSize = 22.sp, color = Color.White)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Живая плитка Metro",
                            color = scheme.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = MetroFonts.text,
                        )
                        Text(
                            text = "Акцентный контур и яркая плашка",
                            color = scheme.textDim,
                            fontSize = 12.sp,
                            fontFamily = MetroFonts.text,
                        )
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        MetroColorPickerSheet(
            initialColor = settings.accentColor ?: 0xFF00ABA9.toInt(),
            onColorSelected = { color ->
                settingsRepo.setAccentColor(color, auto = false)
            },
            onDismiss = { showColorPicker = false },
        )
    }
}

@Composable
private fun GlassSection(
    settingsRepo: MetroSettingsRepository,
) {
    val scheme = LocalMetroScheme.current
    val settings by settingsRepo.settings.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Тумблер блюра
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.glass)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Эффект размытия (блюр)",
                            color = scheme.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = MetroFonts.text,
                        )
                        Text(
                            text = "Акриловый фрост-эффект на плитках и фоне",
                            color = scheme.textDim,
                            fontSize = 12.sp,
                            fontFamily = MetroFonts.text,
                        )
                    }
                    Switch(
                        checked = settings.blurEnabled,
                        onCheckedChange = { settingsRepo.setBlurEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = scheme.accent,
                            checkedTrackColor = scheme.glassHover,
                        ),
                    )
                }
            }
        }

        // Радиус блюра
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.glass)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Радиус блюра обоев",
                            color = scheme.text,
                            fontSize = 14.sp,
                            fontFamily = MetroFonts.text,
                        )
                        Text(
                            text = "${settings.blurRadius} px",
                            color = scheme.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = MetroFonts.text,
                        )
                    }
                    Slider(
                        value = settings.blurRadius.toFloat(),
                        onValueChange = { settingsRepo.setBlurRadius(it.roundToInt()) },
                        valueRange = 4f..32f,
                        enabled = settings.blurEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = scheme.accent,
                            activeTrackColor = scheme.accent,
                            inactiveTrackColor = scheme.glassHover,
                        ),
                    )
                }
            }
        }

        // Прозрачность плиток
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.glass)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Подложка плиток (glass)",
                            color = scheme.text,
                            fontSize = 14.sp,
                            fontFamily = MetroFonts.text,
                        )
                        Text(
                            text = "${(settings.glassAlpha * 100).toInt()}%",
                            color = scheme.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = MetroFonts.text,
                        )
                    }
                    Slider(
                        value = settings.glassAlpha,
                        onValueChange = { settingsRepo.setGlassAlpha(it) },
                        valueRange = 0.00f..0.30f,
                        colors = SliderDefaults.colors(
                            thumbColor = scheme.accent,
                            activeTrackColor = scheme.accent,
                            inactiveTrackColor = scheme.glassHover,
                        ),
                    )
                }
            }
        }

        // Прозрачность меню и диалогов
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.glass)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Фон меню и шторок (glassDeep)",
                            color = scheme.text,
                            fontSize = 14.sp,
                            fontFamily = MetroFonts.text,
                        )
                        Text(
                            text = "${(settings.glassDeepAlpha * 100).toInt()}%",
                            color = scheme.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = MetroFonts.text,
                        )
                    }
                    Slider(
                        value = settings.glassDeepAlpha,
                        onValueChange = { settingsRepo.setGlassDeepAlpha(it) },
                        valueRange = 0.15f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = scheme.accent,
                            activeTrackColor = scheme.accent,
                            inactiveTrackColor = scheme.glassHover,
                        ),
                    )
                }
            }
        }

        // Яркость границ
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.glass)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Границы и разделители (stroke)",
                            color = scheme.text,
                            fontSize = 14.sp,
                            fontFamily = MetroFonts.text,
                        )
                        Text(
                            text = "${(settings.strokeAlpha * 100).toInt()}%",
                            color = scheme.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = MetroFonts.text,
                        )
                    }
                    Slider(
                        value = settings.strokeAlpha,
                        onValueChange = { settingsRepo.setStrokeAlpha(it) },
                        valueRange = 0.02f..0.30f,
                        colors = SliderDefaults.colors(
                            thumbColor = scheme.accent,
                            activeTrackColor = scheme.accent,
                            inactiveTrackColor = scheme.glassHover,
                        ),
                    )
                }
            }
        }

        // Живой предпросмотр эффекта стекла
        item {
            Text(
                text = "ПРЕДПРОСМОТР СТЕКЛА",
                fontSize = 11.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.SemiBold,
                color = scheme.textDim,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))

            val previewGlassAlpha by animateFloatAsState(settings.glassAlpha, tween(150), label = "prev-glass")
            val previewStrokeAlpha by animateFloatAsState(settings.strokeAlpha, tween(150), label = "prev-stroke")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = previewGlassAlpha))
                    .border(1.dp, Color.White.copy(alpha = previewStrokeAlpha), RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.accent.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MetroIcon(icon = "\uf2d0", fontSize = 22.sp, color = Color.White)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Акриловая плитка",
                            color = scheme.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = MetroFonts.text,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Стекло ${(previewGlassAlpha * 100).toInt()}% • Граница ${(previewStrokeAlpha * 100).toInt()}%",
                            color = scheme.textDim,
                            fontSize = 12.sp,
                            fontFamily = MetroFonts.text,
                        )
                    }
                }
            }
        }

        // Кнопка сброса стекла
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.glassHover)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp))
                    .metroClickable(targetScale = 0.96f, onClick = { settingsRepo.resetGlassDefaults() })
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Сбросить параметры стекла по умолчанию",
                    color = scheme.textDim,
                    fontSize = 13.sp,
                    fontFamily = MetroFonts.text,
                )
            }
        }
    }
}

@Composable
private fun UpdatesSection(
    updateRepo: UpdateRepository,
) {
    val scheme = LocalMetroScheme.current
    val scope = rememberCoroutineScope()
    val state by updateRepo.updateState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            // Карточка текущей версии
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.glass)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "УСТАНОВЛЕННАЯ ВЕРСИЯ",
                            fontSize = 11.sp,
                            fontFamily = MetroFonts.text,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.textDim,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (код ${BuildConfig.VERSION_CODE})",
                            fontSize = 17.sp,
                            fontFamily = MetroFonts.headline,
                            fontWeight = FontWeight.Light,
                            color = scheme.text,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.glassHover)
                            .metroClickable(
                                targetScale = 0.94f,
                                onClick = { scope.launch { updateRepo.checkForUpdates() } },
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "Проверить",
                            color = scheme.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = MetroFonts.text,
                        )
                    }
                }
            }
        }

        // Состояния обновления с плавной анимацией перехода
        item {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220, easing = MetroAnimations.OpenEasing)))
                        .togetherWith(fadeOut(animationSpec = tween(160, easing = MetroAnimations.CloseEasing)))
                },
                label = "update-state-anim",
            ) { s ->
                when (s) {
                    is UpdateState.Idle -> {
                        Text(
                            text = "Нажмите «Проверить», чтобы узнать о доступности новых версий на GitHub.",
                            color = scheme.textDim,
                            fontSize = 13.sp,
                            fontFamily = MetroFonts.text,
                        )
                    }
                    is UpdateState.Checking -> {
                        val infiniteTransition = rememberInfiniteTransition(label = "update-spin")
                        val spinRotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "spin-rot",
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MetroIcon(
                                icon = "\uf021",
                                fontSize = 18.sp,
                                color = scheme.accent,
                                modifier = Modifier.graphicsLayer(rotationZ = spinRotation),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Проверка обновлений на GitHub...",
                                color = scheme.text,
                                fontSize = 14.sp,
                                fontFamily = MetroFonts.text,
                            )
                        }
                    }
                    is UpdateState.UpToDate -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.glass)
                                .border(1.dp, scheme.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MetroIcon(icon = "\uf00c", fontSize = 20.sp, color = scheme.accent)
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = "У вас установлена последняя версия!",
                                    color = scheme.text,
                                    fontSize = 14.sp,
                                    fontFamily = MetroFonts.text,
                                )
                            }
                        }
                    }
                    is UpdateState.Available -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.glass)
                                .border(1.dp, scheme.accent, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MetroIcon(icon = "\uf019", fontSize = 20.sp, color = scheme.accent)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Доступно обновление: v${s.info.versionName}",
                                    color = scheme.text,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = MetroFonts.text,
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Что нового:",
                                color = scheme.textDim,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = MetroFonts.text,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = s.info.changelog,
                                color = scheme.text,
                                fontSize = 13.sp,
                                fontFamily = MetroFonts.text,
                            )

                            Spacer(Modifier.height(16.dp))
                            val sizeMb = "%.1f".format(s.info.apkSize / (1024f * 1024f))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(scheme.accent)
                                    .metroClickable(
                                        targetScale = 0.96f,
                                        onClick = { scope.launch { updateRepo.downloadUpdate(s.info) } },
                                    )
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Скачать и обновить ($sizeMb МБ)",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = MetroFonts.text,
                                )
                            }
                        }
                    }
                    is UpdateState.Downloading -> {
                        val animProgress by animateFloatAsState(
                            targetValue = s.progress,
                            animationSpec = tween(150, easing = LinearEasing),
                            label = "dl-progress",
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.glass)
                                .border(1.dp, scheme.stroke, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Загрузка обновления...",
                                    color = scheme.text,
                                    fontSize = 14.sp,
                                    fontFamily = MetroFonts.text,
                                )
                                Text(
                                    text = "${(animProgress * 100).toInt()}%",
                                    color = scheme.accent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = MetroFonts.text,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(scheme.glassHover),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animProgress)
                                        .height(6.dp)
                                        .background(scheme.accent),
                                )
                            }
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.accent)
                                .metroClickable(
                                    targetScale = 0.96f,
                                    onClick = { updateRepo.installApk(s.apkFile) },
                                )
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Установить обновление сейчас",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = MetroFonts.text,
                            )
                        }
                    }
                    is UpdateState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.glass)
                                .border(1.dp, scheme.red.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        ) {
                            Text(
                                text = "Ошибка обновления: ${s.message}",
                                color = scheme.red,
                                fontSize = 13.sp,
                                fontFamily = MetroFonts.text,
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(scheme.glassHover)
                                    .metroClickable(
                                        targetScale = 0.94f,
                                        onClick = { scope.launch { updateRepo.checkForUpdates() } },
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(text = "Повторить", color = scheme.text, fontSize = 12.sp, fontFamily = MetroFonts.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection(
    settingsRepo: MetroSettingsRepository,
) {
    val scheme = LocalMetroScheme.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.glass)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(12.dp))
                    .padding(20.dp),
            ) {
                Column {
                    Text(
                        text = "METRO LAUNCHER",
                        color = scheme.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = MetroFonts.headline,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .background(scheme.accent, RoundedCornerShape(1.5.dp)),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Лаунчер для Android в стиле quickshell metro desktop environment (Fluent Acrylic Metro). Полупрозрачные плитки с аппаратным блюром, список приложений с Jump Grid и виджеты.",
                        color = scheme.textDim,
                        fontSize = 13.sp,
                        fontFamily = MetroFonts.text,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Версия: ${BuildConfig.VERSION_NAME} (сборка ${BuildConfig.VERSION_CODE})",
                        color = scheme.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = MetroFonts.text,
                    )
                    Text(
                        text = "Репозиторий: github.com/banuee/metro-launcher",
                        color = scheme.textDim,
                        fontSize = 12.sp,
                        fontFamily = MetroFonts.text,
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.glassHover)
                    .border(1.dp, scheme.red.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .metroClickable(
                        targetScale = 0.96f,
                        onClick = { settingsRepo.resetAllDefaults() },
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Сбросить все настройки лаунчера",
                    color = scheme.red,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = MetroFonts.text,
                )
            }
        }
    }
}
