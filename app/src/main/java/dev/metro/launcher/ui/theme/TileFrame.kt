package dev.metro.launcher.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Каркас плитки: фрост-срез обоев + стекло, бордер stroke,
 * мгновенный тактильный отклик на тап (pulse-scale 0.93 + всплеск акцента)
 * и удержание (press-scale 0.95 + акцентный бордер).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TileFrame(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = MetroDimens.unit,
    height: Dp = MetroDimens.unit,
    accentBorder: Boolean = false,
    /** null = фрост + стекло (дефолт). Для акцентных — scheme.accent с alpha. */
    background: Color? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = LocalMetroScheme.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val pulseScale = remember { Animatable(1f) }
    val pulseBorder = remember { Animatable(0f) }

    val heldScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 130),
        label = "tile-press",
    )
    val currentScale = if (pressed) heldScale else pulseScale.value

    val borderAlpha = max(if (pressed) 1f else 0f, pulseBorder.value)
    val isBorderActive = accentBorder || borderAlpha > 0.05f
    val borderColor = if (accentBorder) scheme.accent
        else if (isBorderActive) scheme.accent.copy(alpha = borderAlpha)
        else null
    val borderWidth = if (accentBorder) 2.dp else 1.5.dp

    val performClick: () -> Unit = {
        scope.launch {
            pulseScale.animateTo(0.93f, tween(70, easing = FastOutSlowInEasing))
            pulseScale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
        scope.launch {
            pulseBorder.animateTo(1f, tween(50))
            pulseBorder.animateTo(0f, tween(180))
        }
        onClick()
    }

    val sizeModifier = when {
        width != Dp.Unspecified && height != Dp.Unspecified -> Modifier.size(width, height)
        width != Dp.Unspecified -> Modifier.width(width).height(height.takeIf { it != Dp.Unspecified } ?: MetroDimens.unit)
        height != Dp.Unspecified -> Modifier.fillMaxWidth().height(height)
        else -> Modifier.fillMaxSize()
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
            .graphicsLayer(scaleX = currentScale, scaleY = currentScale)
            .clip(RoundedCornerShape(MetroDimens.radius))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = performClick,
                onLongClick = onLongPress,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (background == null) {
            FrostedGlassBox(
                modifier = Modifier.fillMaxSize(),
                tint = scheme.glass,
                borderColor = borderColor,
                borderWidth = borderWidth,
            ) {
                content()
            }
        } else {
            Box(Modifier.fillMaxSize().background(background))
            content()
            if (borderColor != null) {
                Box(
                    Modifier.fillMaxSize()
                        .border(borderWidth, borderColor, RoundedCornerShape(MetroDimens.radius)),
                )
            }
        }
    }
}

/**
 * Тактильный клик-модификатор для элементов списков и кнопок (AppRow, заголовки, плеер).
 * Дает упругий отскок при быстром тапе и плавное сжатие при зажатии.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.metroClickable(
    enabled: Boolean = true,
    targetScale: Float = 0.95f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val pulse = remember { Animatable(1f) }
    val heldScale by animateFloatAsState(
        targetValue = if (pressed) targetScale else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "metro-click-held",
    )
    val scale = if (pressed) heldScale else pulse.value

    return this
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    onClick = {
                        scope.launch {
                            pulse.animateTo(targetScale, tween(60, easing = FastOutSlowInEasing))
                            pulse.animateTo(
                                1f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                        onClick()
                    },
                )
            } else {
                Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                ) {
                    scope.launch {
                        pulse.animateTo(targetScale, tween(60, easing = FastOutSlowInEasing))
                        pulse.animateTo(
                            1f,
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    }
                    onClick()
                }
            }
        )
}

/** Полупрозрачная акцентная заливка (tileAlpha 0.85 из Theme.qml). */
fun tileAccentFill(accent: Color): Color = accent.copy(alpha = 0.85f)
