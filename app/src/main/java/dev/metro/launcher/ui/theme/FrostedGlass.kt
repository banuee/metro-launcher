package dev.metro.launcher.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Предблюренные обои на весь экран. Плитки рисуют срез обоев 1:1 ровно под своей позицией в окне.
 * null = живые обои / не загрузилось — стекло без блюра (полупрозрачная подложка).
 */
val LocalBlurredWallpaper: ProvidableCompositionLocal<ImageBitmap?> =
    compositionLocalOf { null }

/**
 * Стеклянный бокс с фрост-подложкой: идеальный 1:1 срез блюра под своей позицией в окне.
 * Никаких сдвигов, искажений масштаба или лишних слоев разметки.
 */
@Composable
fun FrostedGlassBox(
    modifier: Modifier = Modifier,
    shape: Dp = MetroDimens.radius,
    /** Тонировка поверх фроста (Color.Transparent = чистое прозрачное стекло как в Square Home). */
    tint: Color = Color.Transparent,
    /** null = без бордера. */
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    /** Как лежал контент в старом TileFrame — по центру. */
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val blurred = LocalBlurredWallpaper.current
    var pos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                pos = coordinates.positionInWindow()
            }
            .clip(RoundedCornerShape(shape))
            .drawBehind {
                if (blurred != null) {
                    // Срез блюра ровно в экранных координатах плитки — 1:1 совпадение с фоном
                    drawImage(
                        image = blurred,
                        topLeft = Offset(-pos.x, -pos.y),
                    )
                    if (tint != Color.Transparent) {
                        drawRect(tint)
                    }
                } else {
                    // Фолбэк когда нет фото-обоев: полупрозрачная подложка
                    drawRect(if (tint != Color.Transparent) tint else Color.White.copy(alpha = 0.08f))
                }
            }
            .then(
                if (borderColor != null) {
                    Modifier.border(borderWidth, borderColor, RoundedCornerShape(shape))
                } else Modifier
            ),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}
