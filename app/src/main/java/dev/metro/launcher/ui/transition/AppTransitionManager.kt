package dev.metro.launcher.ui.transition

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.metro.launcher.data.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TransitionPhase {
    IDLE,
    OPENING,
    CLOSING,
}

data class TransitionState(
    val phase: TransitionPhase = TransitionPhase.IDLE,
    val app: AppInfo? = null,
    val iconBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    val targetRect: Rect? = null,
)

/**
 * Менеджер переходов между Metro Launcher и внешними приложениями.
 *
 * Управляет фазами OPENING (расширение плитки во весь экран) и
 * CLOSING (схлопывание карточки закрывшегося приложения обратно в плитку при onResume).
 */
class AppTransitionManager {
    private val _state = MutableStateFlow(TransitionState())
    val state: StateFlow<TransitionState> = _state.asStateFlow()

    private var lastApp: AppInfo? = null
    private var lastIconBitmap: androidx.compose.ui.graphics.ImageBitmap? = null
    private var lastSourceRect: Rect? = null
    private var lastLaunchTime: Long = 0L

    /** Коллбэк для поиска актуальных координат плитки по имени пакета на рабочем столе. */
    var findTileRect: ((packageName: String) -> Rect?)? = null

    /**
     * Запуск приложения с анимацией WindowManager из [sourceRect].
     */
    fun launchApp(
        activity: Activity,
        app: AppInfo,
        sourceRect: Rect?,
        intent: Intent? = null,
    ) {
        val launchIntent = intent
            ?: activity.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        lastApp = app
        val cached: ImageBitmap? = dev.metro.launcher.data.AppIconLoader.cachedAppIcon(app.packageName, app)
        lastIconBitmap = cached ?: runCatching {
            app.icon.toBitmap(144, 144).asImageBitmap()
        }.getOrNull()
        lastSourceRect = sourceRect
        lastLaunchTime = SystemClock.elapsedRealtime()

        if (sourceRect != null) {
            launchIntent.sourceBounds = android.graphics.Rect(
                kotlin.math.round(sourceRect.left).toInt(),
                kotlin.math.round(sourceRect.top).toInt(),
                kotlin.math.round(sourceRect.right).toInt(),
                kotlin.math.round(sourceRect.bottom).toInt(),
            )
        }

        val options = if (sourceRect != null) {
            try {
                ActivityOptions.makeClipRevealAnimation(
                    activity.window.decorView,
                    kotlin.math.round(sourceRect.left).toInt(),
                    kotlin.math.round(sourceRect.top).toInt(),
                    kotlin.math.round(sourceRect.width).toInt().coerceAtLeast(1),
                    kotlin.math.round(sourceRect.height).toInt().coerceAtLeast(1),
                )
            } catch (_: Exception) {
                ActivityOptions.makeBasic()
            }
        } else {
            ActivityOptions.makeBasic()
        }

        try {
            activity.startActivity(launchIntent, options.toBundle())
        } catch (_: Exception) {
            lastApp = null
            lastIconBitmap = null
        }
    }

    /**
     * Обработка возврата на рабочий стол (onResume).
     * Если приложение было недавно запущено, активируем анимацию схлопывания (CLOSING).
     */
    fun onResume() {
        val app = lastApp
        val now = SystemClock.elapsedRealtime()

        if (app != null && (now - lastLaunchTime in 300L..600_000L)) {
            // Ищем актуальные координаты плитки на сетке (если она закреплена и видна)
            val currentTileRect = findTileRect?.invoke(app.packageName)
            val target = currentTileRect ?: lastSourceRect
            val icon: ImageBitmap? = lastIconBitmap
                ?: dev.metro.launcher.data.AppIconLoader.cachedAppIcon(app.packageName, app)
                ?: runCatching { app.icon.toBitmap(144, 144).asImageBitmap() }.getOrNull()

            _state.value = TransitionState(
                phase = TransitionPhase.CLOSING,
                app = app,
                iconBitmap = icon,
                targetRect = target,
            )
            lastApp = null
            lastIconBitmap = null
        } else {
            _state.value = TransitionState(TransitionPhase.IDLE)
        }
    }

    /**
     * Вызывается, когда активность лаунчера уходит в фон (onStop).
     */
    fun onStop() {
        if (_state.value.phase == TransitionPhase.OPENING) {
            _state.value = TransitionState(TransitionPhase.IDLE)
        }
    }

    /**
     * Вызывается по завершению анимации перехода.
     */
    fun onTransitionFinished() {
        _state.value = TransitionState(TransitionPhase.IDLE)
    }

    /**
     * Принудительная отмена перехода.
     */
    fun cancelTransition() {
        _state.value = TransitionState(TransitionPhase.IDLE)
        lastApp = null
    }
}
