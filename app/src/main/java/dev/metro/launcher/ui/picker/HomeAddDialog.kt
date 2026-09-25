package dev.metro.launcher.ui.picker

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.metro.launcher.ui.theme.DialogWindowBlurEffect
import dev.metro.launcher.ui.theme.FrostedGlassBox
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroAnimations
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.MetroIcon
import dev.metro.launcher.ui.theme.MetroIcons
import dev.metro.launcher.ui.theme.metroClickable

/**
 * Меню добавления на главный экран в стиле Quickshell Metro.
 * Акриловое стекло, аппаратный блюр фона, Nerd Font иконки и живой отклик.
 */
@Composable
fun HomeAddDialog(
    onAddWidgetClick: () -> Unit,
    onAddAppClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    val density = LocalDensity.current

    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 240, easing = MetroAnimations.OpenEasing),
        )
    }
    val slidePx = with(density) { 48.dp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Аппаратный блюр SurfaceFlinger за окном диалога (Android 12+)
        DialogWindowBlurEffect(blurRadiusPx = 65, dimAmount = 0.20f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.20f * anim.value))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            FrostedGlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .graphicsLayer {
                        val p = anim.value
                        scaleX = 0.92f + 0.08f * p
                        scaleY = 0.92f + 0.08f * p
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * slidePx
                    }
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .clickable(enabled = false, onClick = {}),
                shape = MetroDimens.panelRadius,
                tint = scheme.glassDeep,
                borderColor = scheme.strokeStrong,
                borderWidth = 1.dp,
            ) {
                // Тонкая акцентная линия подсветки сверху (как в metro-shot / metro shell)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.45f)
                        .height(1.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    scheme.accent.copy(alpha = 0.75f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "ДОБАВИТЬ НА ЭКРАН",
                                color = scheme.text,
                                fontSize = 16.sp,
                                fontFamily = MetroFonts.headline,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 28.dp, height = 2.5.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(scheme.accent),
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            MetroIcon(
                                icon = MetroIcons.Close,
                                color = scheme.textDim,
                                fontSize = 16.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    AddMenuOption(
                        icon = MetroIcons.Widgets,
                        title = "Добавить виджет",
                        subtitle = "Виджеты Metro или виджеты приложений",
                        onClick = {
                            onDismiss()
                            onAddWidgetClick()
                        },
                    )

                    Spacer(Modifier.height(10.dp))

                    AddMenuOption(
                        icon = MetroIcons.Apps,
                        title = "Добавить значок",
                        subtitle = "Ярлык любого установленного приложения",
                        onClick = {
                            onDismiss()
                            onAddAppClick()
                        },
                    )

                    Spacer(Modifier.height(10.dp))

                    AddMenuOption(
                        icon = MetroIcons.Settings,
                        title = "Параметры",
                        subtitle = "Обои, цвета, блюр и обновления",
                        onClick = {
                            onDismiss()
                            onSettingsClick()
                        },
                    )

                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun AddMenuOption(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MetroDimens.radius))
            .background(scheme.glass)
            .border(
                width = 1.dp,
                color = scheme.stroke,
                shape = RoundedCornerShape(MetroDimens.radius),
            )
            .metroClickable(targetScale = 0.97f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(MetroDimens.radiusSmall))
                .background(scheme.accent.copy(alpha = 0.14f))
                .border(
                    width = 1.dp,
                    color = scheme.accent.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(MetroDimens.radiusSmall),
                ),
            contentAlignment = Alignment.Center,
        ) {
            MetroIcon(
                icon = icon,
                color = scheme.accent,
                fontSize = 20.sp,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = scheme.text,
                fontSize = 15.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = scheme.textDim,
                fontSize = 12.sp,
                fontFamily = MetroFonts.text,
            )
        }
        MetroIcon(
            icon = MetroIcons.ChevronRight,
            color = scheme.textDim.copy(alpha = 0.5f),
            fontSize = 15.sp,
        )
    }
}
