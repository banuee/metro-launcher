package dev.metro.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.ColorUtils
import dev.metro.launcher.ui.theme.DialogWindowBlurEffect
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.metroClickable

private val METRO_PRESETS = listOf(
    0xFF00ABA9.toInt(), // Teal
    0xFF0050EF.toInt(), // Cobalt
    0xFFA200FF.toInt(), // Purple
    0xFF10893E.toInt(), // Green
    0xFF7E9600.toInt(), // Lime
    0xFFF09609.toInt(), // Orange
    0xFFE51400.toInt(), // Crimson
    0xFFD80073.toInt(), // Magenta
)

@Composable
fun MetroColorPickerSheet(
    initialColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = LocalMetroScheme.current

    val initialHsl = remember(initialColor) {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(initialColor, hsl)
        hsl
    }

    var hue by remember { mutableFloatStateOf(initialHsl[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsl[1]) }
    var lightness by remember { mutableFloatStateOf(initialHsl[2]) }

    val currentColor = remember(hue, saturation, lightness) {
        ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness))
    }

    var hexText by remember(currentColor) {
        mutableStateOf("%06X".format(currentColor and 0xFFFFFF))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogWindowBlurEffect(blurRadiusPx = 70, dimAmount = 0.40f)

        Box(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.glassDeep)
                .border(1.dp, scheme.strokeStrong, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Шапка
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "ВЫБОР ЦВЕТА",
                            fontSize = 18.sp,
                            fontFamily = MetroFonts.headline,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 1.5.sp,
                            color = scheme.text,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(3.dp)
                                .background(Color(currentColor), RoundedCornerShape(1.5.dp)),
                        )
                    }

                    // Превью текущего цвета
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(currentColor))
                            .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Ползунок Оттенка (Hue)
                Text(
                    text = "Оттенок (Hue): ${hue.toInt()}°",
                    color = scheme.textDim,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                )
                val hueColors = remember {
                    listOf(
                        Color.Red, Color.Yellow, Color.Green,
                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Brush.horizontalGradient(hueColors)),
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(currentColor),
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                    ),
                )

                // Ползунок Насыщенности (Saturation)
                Text(
                    text = "Насыщенность: ${(saturation * 100).toInt()}%",
                    color = scheme.textDim,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                )
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0.15f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(currentColor),
                        activeTrackColor = scheme.accent,
                        inactiveTrackColor = scheme.glassHover,
                    ),
                )

                // Ползунок Светлоты (Lightness)
                Text(
                    text = "Яркость / Светлота: ${(lightness * 100).toInt()}%",
                    color = scheme.textDim,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                )
                Slider(
                    value = lightness,
                    onValueChange = { lightness = it },
                    valueRange = 0.25f..0.75f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(currentColor),
                        activeTrackColor = scheme.accent,
                        inactiveTrackColor = scheme.glassHover,
                    ),
                )

                Spacer(Modifier.height(8.dp))

                // Ввод HEX
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "HEX:",
                        color = scheme.textDim,
                        fontSize = 13.sp,
                        fontFamily = MetroFonts.text,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.glassHover)
                            .border(1.dp, scheme.stroke, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        BasicTextField(
                            value = hexText,
                            onValueChange = { str ->
                                val filtered = str.filter { it in "0123456789abcdefABCDEF" }.take(6)
                                hexText = filtered
                                if (filtered.length == 6) {
                                    try {
                                        val parsed = (0xFF000000.toInt() or filtered.toLong(16).toInt())
                                        val h = FloatArray(3)
                                        ColorUtils.colorToHSL(parsed, h)
                                        hue = h[0]
                                        saturation = h[1]
                                        lightness = h[2]
                                    } catch (_: Exception) {}
                                }
                            },
                            textStyle = TextStyle(
                                color = scheme.text,
                                fontSize = 14.sp,
                                fontFamily = MetroFonts.text,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            cursorBrush = SolidColor(scheme.accent),
                            singleLine = true,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Фирменные пресеты Metro
                Text(
                    text = "КЛАССИЧЕСКИЕ ПРЕСЕТЫ METRO",
                    fontSize = 11.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.textDim,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    METRO_PRESETS.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(preset))
                                .border(
                                    width = if ((preset and 0xFFFFFF) == (currentColor and 0xFFFFFF)) 2.dp else 1.dp,
                                    color = if ((preset and 0xFFFFFF) == (currentColor and 0xFFFFFF)) Color.White else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .clickable {
                                    val h = FloatArray(3)
                                    ColorUtils.colorToHSL(preset, h)
                                    hue = h[0]
                                    saturation = h[1]
                                    lightness = h[2]
                                },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.glassHover)
                            .metroClickable(onClick = onDismiss)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(text = "Отмена", color = scheme.textDim, fontSize = 13.sp, fontFamily = MetroFonts.text)
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.accent)
                            .metroClickable(
                                onClick = {
                                    onColorSelected(currentColor)
                                    onDismiss()
                                },
                            )
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "Выбрать",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = MetroFonts.text,
                        )
                    }
                }
            }
        }
    }
}
