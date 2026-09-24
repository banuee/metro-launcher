package dev.metro.launcher.ui.edit

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Детектор «долгий клик → тягание» для плиток главного экрана.
 *
 * Зачем свой, а не detectDragGesturesAfterLongPress: внутри плиток живут
 * tap-only дети (строки заметок, кнопки плеера и т.п.). После long-press'а
 * они остаются вооружены и при отпускании без движения съедают up:
 * встроенный детектор в этом случае молча отменяется — не зовёт ни onDragEnd,
 * ни onDragCancel — и плитка навсегда застревает в состоянии dragging
 * (спрятанное меню, зависший scale). Плюс тот же съеденный up кликает
 * по строке/кнопке сразу после перетаскивания.
 *
 * Этот детектор:
 * - до long-press'а ничего не consume'ит: табы обрабатывают дети,
 *   вертикальное движение уходит скроллу сетки;
 * - в момент захвата consume'ит press — tap-only дети отменяются и не смогут
 *   ни съесть отпускание, ни кликнуть после drag'а;
 * - onRelease зовёт ВСЕГДА (даже при чистом long-press без движения),
 *   зависших состояний нет по построению.
 */
suspend fun PointerInputScope.detectTilePressDrag(
    onGrab: (grabPosition: Offset) -> Unit,
    onDrag: (dragAmount: Offset) -> Unit,
    onRelease: () -> Unit,
) {
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown()

        // Фаза 1: ждём long-press. Отпускание раньше таймаута — обычный тап,
        // движение раньше — скролл: в обоих случаях выходим молча, ничего не трогая.
        var grabbed: PointerInputChange? = null
        try {
            withTimeout(longPressTimeout) {
                awaitTouchSlopOrCancellation(down.id) { _, _ -> }
            }
        } catch (_: TimeoutCancellationException) {
            grabbed = down
        }
        if (grabbed == null) return@awaitEachGesture

        // Фаза 2: захват. Гасим press, чтобы дети не съели отпускание.
        down.consume()
        onGrab(down.position)

        // Фаза 3: сдвиг и ведение до отпускания/отмены.
        val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
        if (slop != null) {
            drag(slop.id) { change ->
                // change.positionChange() в разных версиях Compose — функция или
                // свойство, поэтому считаем дельту вручную из стабильных полей.
                onDrag(change.position - change.previousPosition)
                change.consume()
            }
        }
        onRelease()
    }
}
