package dev.metro.launcher.ui.transition

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.metro.launcher.data.AppIconLoader
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroAnimations
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Плавная кривая замедления закрытия окна в плитку (120 FPS Fluent). */
private val SmoothExitEasing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.0f)

/**
 * Высокопроизводительный полноэкранный оверлей для анимаций входа и выхода из приложений.
 *
 * Отрисовка происходит на уровне Draw Phase (Canvas) без единой рекомпозиции и без
 * замеров layout во время движения. Обеспечивает честные 120 FPS без просадки кадров.
 */
@Composable
fun AppTransitionOverlay(
    transitionState: TransitionState,
    onFinished: () -> Unit,
) {
    if (transitionState.phase == TransitionPhase.IDLE || transitionState.app == null) {
        return
    }

    val app = transitionState.app
    val phase = transitionState.phase
    val scheme = LocalMetroScheme.current
    val density = LocalDensity.current

    // Получаем реальную иконку без асинхронного дрейфа и без дефолтного андроидовского робота
    val iconBitmap = transitionState.iconBitmap
        ?: AppIconLoader.cachedAppIcon(app.packageName, app)
        ?: remember(app.packageName) {
            runCatching { app.icon.toBitmap(144, 144).asImageBitmap() }.getOrNull()
        }

    val progress = remember(app.packageName, phase) {
        Animatable(if (phase == TransitionPhase.CLOSING) 1f else 0f)
    }

    LaunchedEffect(app.packageName, phase) {
        when (phase) {
            TransitionPhase.OPENING -> {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = MetroAnimations.OpenEasing,
                    ),
                )
                delay(800)
                onFinished()
            }
            TransitionPhase.CLOSING -> {
                progress.snapTo(1f)
                // Небольшая задержка перед сжатием, чтобы WindowManager успел
                // переключить поверхности и анимация была видна пользователю целиком
                delay(40)
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 340,
                        easing = SmoothExitEasing,
                    ),
                )
                onFinished()
            }
            TransitionPhase.IDLE -> {}
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val textStyle = remember(scheme.text) {
        TextStyle(
            color = scheme.text,
            fontSize = 16.sp,
            fontFamily = MetroFonts.text,
            fontWeight = FontWeight.Normal,
        )
    }
    val textLayoutResult = remember(app.label, textStyle) {
        textMeasurer.measure(
            text = AnnotatedString(app.label),
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        val fallbackWidth = with(density) { MetroDimens.tileH(1).toPx() }
        val fallbackHeight = with(density) { MetroDimens.tileH(1).toPx() }
        val fallbackLeft = (screenWidthPx - fallbackWidth) / 2f
        val fallbackTop = screenHeightPx * 0.70f

        val rawTarget = transitionState.targetRect
        val isOffscreen = rawTarget != null && (rawTarget.bottom < 0f || rawTarget.top > screenHeightPx)
        val targetRect = if (rawTarget == null || isOffscreen) {
            Rect(
                left = fallbackLeft,
                top = fallbackTop,
                right = fallbackLeft + fallbackWidth,
                bottom = fallbackTop + fallbackHeight,
            )
        } else {
            rawTarget
        }

        val strokeColor = scheme.strokeStrong
        val strokeWidthPx = with(density) { 1.5.dp.toPx() }
        val cornerRadiusTilePx = with(density) { MetroDimens.radius.toPx() }
        val cornerRadiusScreenPx = with(density) { 28.dp.toPx() }
        val minIconSizePx = with(density) { 44.dp.toPx() }
        val maxIconSizePx = with(density) { 76.dp.toPx() }
        val textGapPx = with(density) { 14.dp.toPx() }

        // Чтение progress.value происходит ТОЛЬКО внутри Canvas Draw Scope.
        // Это исключает Recomposition и Layout passes на каждом кадре анимации.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = progress.value

            val cardAlpha = when (phase) {
                TransitionPhase.CLOSING -> {
                    if (p <= 0.08f) (p / 0.08f).coerceIn(0f, 1f) else 1f
                }
                TransitionPhase.OPENING -> {
                    if (p >= 0.85f) ((1f - p) / 0.15f).coerceIn(0f, 1f) else 1f
                }
                TransitionPhase.IDLE -> 0f
            }

            if (cardAlpha <= 0f) return@Canvas

            val l = targetRect.left + (0f - targetRect.left) * p
            val t = targetRect.top + (0f - targetRect.top) * p
            val r = targetRect.right + (screenWidthPx - targetRect.right) * p
            val b = targetRect.bottom + (screenHeightPx - targetRect.bottom) * p
            val w = (r - l).coerceAtLeast(1f)
            val h = (b - t).coerceAtLeast(1f)

            val currentRadius = cornerRadiusTilePx + (cornerRadiusScreenPx - cornerRadiusTilePx) * p

            // 1. Темный акриловый фон карточки
            drawRoundRect(
                color = Color(0xFF141414).copy(alpha = cardAlpha),
                topLeft = Offset(l, t),
                size = Size(w, h),
                cornerRadius = CornerRadius(currentRadius, currentRadius),
            )

            // 2. Metro граница плитки (ровно 1.5dp, без растяжения)
            drawRoundRect(
                color = strokeColor.copy(alpha = cardAlpha * strokeColor.alpha),
                topLeft = Offset(l, t),
                size = Size(w, h),
                cornerRadius = CornerRadius(currentRadius, currentRadius),
                style = Stroke(width = strokeWidthPx),
            )

            // Координаты центра
            val cx = l + w / 2f
            val cy = t + h / 2f

            // 3. Иконка приложения
            val currentIconSize = minIconSizePx + (maxIconSizePx - minIconSizePx) * p
            iconBitmap?.let { bmp ->
                val iconLeft = (cx - currentIconSize / 2f).roundToInt()
                val iconTop = if (p > 0.35f) {
                    (cy - currentIconSize / 2f - (textLayoutResult.size.height + textGapPx) * 0.35f * p).roundToInt()
                } else {
                    (cy - currentIconSize / 2f).roundToInt()
                }

                drawImage(
                    image = bmp,
                    dstOffset = IntOffset(iconLeft, iconTop),
                    dstSize = IntSize(currentIconSize.roundToInt(), currentIconSize.roundToInt()),
                    alpha = cardAlpha,
                    filterQuality = FilterQuality.Medium,
                )

                // 4. Текстовая подпись Segoe UI
                if (p > 0.40f) {
                    val labelAlpha = (((p - 0.40f) / 0.30f).coerceIn(0f, 1f)) * cardAlpha
                    if (labelAlpha > 0f) {
                        val textLeft = cx - textLayoutResult.size.width / 2f
                        val textTop = iconTop + currentIconSize + textGapPx * p
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(textLeft, textTop),
                            alpha = labelAlpha,
                        )
                    }
                }
            }
        }
    }
}
