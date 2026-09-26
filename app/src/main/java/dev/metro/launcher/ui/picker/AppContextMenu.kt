package dev.metro.launcher.ui.picker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.metro.launcher.data.AppIconLoader
import dev.metro.launcher.data.AppInfo
import dev.metro.launcher.ui.theme.FrostedGlassBox
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroAnimations
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.MetroIcon
import dev.metro.launcher.ui.theme.MetroIcons
import dev.metro.launcher.ui.theme.metroClickable
import androidx.compose.foundation.Image as ComposeImage

/**
 * Позиционирование меню приложения относительно строки или по центру экрана.
 */
private class DrawerMenuPopupPositionProvider(
    private val anchor: Rect?,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = 16
        val anchorRect = anchor?.let {
            IntRect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
        } ?: IntRect(
            windowSize.width / 2,
            windowSize.height / 2,
            windowSize.width / 2,
            windowSize.height / 2,
        )

        val spaceBelow = windowSize.height - anchorRect.bottom
        val spaceAbove = anchorRect.top
        val fitsBelow = spaceBelow >= popupContentSize.height + margin * 2
        val fitsAbove = spaceAbove >= popupContentSize.height + margin * 2

        val x = (anchorRect.left + anchorRect.right - popupContentSize.width) / 2
        val maxXWithoutMargin = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val minX = margin.coerceAtMost(maxXWithoutMargin)
        val maxX = (maxXWithoutMargin - margin).coerceAtLeast(minX)
        val clampedX = x.coerceIn(minX, maxX)

        val preferredY = when {
            fitsBelow -> anchorRect.bottom + margin
            fitsAbove -> anchorRect.top - popupContentSize.height - margin
            spaceBelow >= spaceAbove -> anchorRect.bottom + margin
            else -> anchorRect.top - popupContentSize.height - margin
        }
        val maxYWithoutMargin = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val minY = margin.coerceAtMost(maxYWithoutMargin)
        val maxY = (maxYWithoutMargin - margin).coerceAtLeast(minY)
        val clampedY = preferredY.coerceIn(minY, maxY)

        return IntOffset(clampedX, clampedY)
    }
}

/**
 * Контекстное меню приложения в меню приложений (стиль идентичен TileContextMenu).
 */
@Composable
fun AppContextMenu(
    app: AppInfo,
    anchorBounds: Rect?,
    isPinned: Boolean,
    isHidden: Boolean,
    onOpenApp: () -> Unit,
    onTogglePin: () -> Unit,
    onEditApp: () -> Unit,
    onToggleHide: () -> Unit,
    onOpenSettings: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconRevision by AppIconLoader.revisions.collectAsState()
    val bitmap by produceState(
        initialValue = AppIconLoader.cachedAppIcon(app.packageName, app),
        app.packageName,
        app.icon,
        iconRevision,
    ) {
        value = AppIconLoader.loadAppIcon(context, app.packageName, app)
    }

    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = MetroAnimations.OpenEasing),
        )
    }
    val slidePx = with(density) { 8.dp.toPx() }

    Popup(
        popupPositionProvider = remember(anchorBounds) { DrawerMenuPopupPositionProvider(anchorBounds) },
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
        ),
        onDismissRequest = onDismiss,
    ) {
        FrostedGlassBox(
            modifier = Modifier
                .graphicsLayer {
                    val p = anim.value
                    scaleX = 0.75f + 0.25f * p
                    scaleY = 0.75f + 0.25f * p
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * slidePx
                }
                .widthIn(min = 260.dp, max = 310.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(MetroDimens.panelRadius)),
            shape = MetroDimens.panelRadius,
            tint = Color(0xFF141414).copy(alpha = 0.94f),
            borderColor = scheme.strokeStrong,
            borderWidth = 1.dp,
        ) {
            // Тонкий акцентный блик сверху
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.5f)
                    .height(1.5.dp)
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
                    .padding(14.dp),
            ) {
                // Шапка приложения: иконка + имя + package
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    bitmap?.let { icon ->
                        ComposeImage(
                            bitmap = icon,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            color = scheme.text,
                            fontSize = 14.sp,
                            fontFamily = MetroFonts.headline,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = app.packageName,
                            color = scheme.textDim,
                            fontSize = 11.sp,
                            fontFamily = MetroFonts.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                HorizontalDivider(color = scheme.stroke, thickness = 1.dp)
                Spacer(Modifier.height(8.dp))

                // 1. Открыть приложение
                ContextMenuActionItem(
                    icon = MetroIcons.ExternalLink,
                    label = "Открыть приложение",
                    onClick = onOpenApp,
                )

                Spacer(Modifier.height(6.dp))

                // 2. Закрепить на экране / Открепить от экрана
                ContextMenuActionItem(
                    icon = MetroIcons.Pin,
                    label = if (isPinned) "Открепить от экрана" else "Закрепить на экране",
                    onClick = onTogglePin,
                )

                Spacer(Modifier.height(6.dp))

                // 3. Изменить название или иконку
                ContextMenuActionItem(
                    icon = MetroIcons.Edit,
                    label = "Изменить значок и имя",
                    onClick = onEditApp,
                )

                Spacer(Modifier.height(6.dp))

                // 4. Скрыть приложение / Показать в списке
                ContextMenuActionItem(
                    icon = if (isHidden) MetroIcons.Eye else MetroIcons.EyeSlash,
                    label = if (isHidden) "Показать в списке" else "Скрыть приложение",
                    onClick = onToggleHide,
                )

                Spacer(Modifier.height(6.dp))

                // 5. Настройки приложения в системе
                ContextMenuActionItem(
                    icon = MetroIcons.Settings,
                    label = "Настройки приложения",
                    onClick = onOpenSettings,
                )

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = scheme.stroke, thickness = 1.dp)
                Spacer(Modifier.height(8.dp))

                // 6. Удалить приложение (красный Metro Red)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MetroDimens.radiusSmall))
                        .background(scheme.red.copy(alpha = 0.12f))
                        .border(1.dp, scheme.red.copy(alpha = 0.35f), RoundedCornerShape(MetroDimens.radiusSmall))
                        .metroClickable(targetScale = 0.96f, onClick = onUninstall)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.red.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MetroIcon(
                            icon = MetroIcons.Trash,
                            color = scheme.red,
                            fontSize = 16.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Удалить приложение",
                        color = scheme.red,
                        fontSize = 14.sp,
                        fontFamily = MetroFonts.text,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextMenuActionItem(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MetroDimens.radiusSmall))
            .background(scheme.glass)
            .border(1.dp, scheme.stroke, RoundedCornerShape(MetroDimens.radiusSmall))
            .metroClickable(targetScale = 0.96f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(scheme.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            MetroIcon(
                icon = icon,
                color = scheme.accent,
                fontSize = 15.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = scheme.text,
            fontSize = 14.sp,
            fontFamily = MetroFonts.text,
            fontWeight = FontWeight.Medium,
        )
    }
}
