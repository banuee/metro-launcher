package dev.metro.launcher.ui.picker

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.metroClickable

/**
 * Меню добавления на главный экран в стиле Quickshell Metro.
 * Акриловое стекло, тонкие границы, акцентная риска и тактильный отклик плиток.
 */
@Composable
fun HomeAddDialog(
    onAddWidgetClick: () -> Unit,
    onAddAppClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = LocalMetroScheme.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.50f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .clickable(enabled = false, onClick = {})
                    .clip(RoundedCornerShape(MetroDimens.panelRadius))
                    .background(scheme.glassDeep)
                    .border(
                        width = 1.dp,
                        color = scheme.strokeStrong,
                        shape = RoundedCornerShape(MetroDimens.panelRadius),
                    ),
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
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = scheme.textDim,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    AddMenuOption(
                        icon = Icons.Default.Widgets,
                        title = "Добавить виджет",
                        subtitle = "Виджеты Metro или виджеты приложений",
                        onClick = {
                            onDismiss()
                            onAddWidgetClick()
                        },
                    )

                    Spacer(Modifier.height(10.dp))

                    AddMenuOption(
                        icon = Icons.Default.Apps,
                        title = "Добавить значок",
                        subtitle = "Ярлык любого установленного приложения",
                        onClick = {
                            onDismiss()
                            onAddAppClick()
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
    icon: ImageVector,
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.accent,
                modifier = Modifier.size(22.dp),
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.textDim.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}
