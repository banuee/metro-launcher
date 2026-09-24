package dev.metro.launcher.ui.tiles

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.metro.launcher.data.HomeTileItem
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts

/**
 * FrameLayout, перехватывающий долгое нажатие (long press) на системном виджете,
 * предотвращая ложные клики внутри дочерних View виджета при удержании.
 */
class InterceptingWidgetContainer(context: android.content.Context) : android.widget.FrameLayout(context) {
    var onLongPressAction: (() -> Unit)? = null
    private var hasLongPressed = false

    private val gestureDetector = android.view.GestureDetector(
        context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: android.view.MotionEvent) {
                hasLongPressed = true
                onLongPressAction?.invoke()
            }
        }
    )

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            hasLongPressed = false
        }
        return hasLongPressed
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP || event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
            hasLongPressed = false
        }
        return true
    }
}

/**
 * Хост для системных Android-виджетов (AppWidgetHostView).
 */
@Composable
fun AndroidWidgetTile(
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    item: HomeTileItem.AndroidWidget,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = LocalMetroScheme.current
    val providerInfo = remember(item.appWidgetId) {
        try {
            appWidgetManager.getAppWidgetInfo(item.appWidgetId)
        } catch (_: Exception) {
            null
        }
    }

    // Сообщаем виджету его обновленный размер
    LaunchedEffect(item.colSpan, item.rowSpan) {
        val widthDp = MetroDimens.tileW(item.colSpan).value.toInt()
        val heightDp = MetroDimens.tileH(item.rowSpan).value.toInt()
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        try {
            appWidgetManager.updateAppWidgetOptions(item.appWidgetId, options)
        } catch (_: Exception) {}
    }

    val height = MetroDimens.tileH(item.rowSpan)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(MetroDimens.radius)),
        contentAlignment = Alignment.Center,
    ) {
        if (providerInfo == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.05f))
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress,
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Виджет недоступен",
                    color = scheme.textDim,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                )
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    val container = InterceptingWidgetContainer(ctx).apply {
                        onLongPressAction = onLongPress
                    }
                    val hostView = appWidgetHost.createView(ctx, item.appWidgetId, providerInfo).apply {
                        setAppWidget(item.appWidgetId, providerInfo)
                    }
                    container.addView(
                        hostView,
                        android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    container
                },
                update = { container ->
                    (container as? InterceptingWidgetContainer)?.onLongPressAction = onLongPress
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
