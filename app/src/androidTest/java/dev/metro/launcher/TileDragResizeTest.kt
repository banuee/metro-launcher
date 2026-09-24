package dev.metro.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import dev.metro.launcher.data.HomeLayoutRepository
import dev.metro.launcher.data.HomeTileItem
import dev.metro.launcher.data.WallpaperRepository
import dev.metro.launcher.ui.EMPTY_SPACE_TAG
import dev.metro.launcher.ui.edit.RESIZE_RIGHT_TAG
import dev.metro.launcher.ui.edit.TILE_MENU_TAG
import dev.metro.launcher.ui.edit.tileTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Регрессионные тесты свободного перемещения и ресайза плиток:
 * - перетаскивание в любое место сетки (без кнопок «Выше»/«Ниже»);
 * - в меню нет пресетов размеров — только тягание за края;
 * - ресайз правой рукояткой меняет colSpan плитки.
 *
 * Жесты настоящие (инжекция MotionEvent через performTouchInput), поэтому
 * тесты ловят то, что ломается только на устройстве: потерю жеста,
 * конкуренцию детекторов, застревание dragging-состояния.
 * Порядок/размеры читаются из того же HomeLayoutRepository, что и UI,
 * поэтому проверки не зависят от пиксельной геометрии.
 */
class TileDragResizeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val repo: HomeLayoutRepository by lazy {
        HomeLayoutRepository(ApplicationProvider.getApplicationContext())
    }
    private fun currentIds(): List<String> = runBlocking { repo.tiles.first().map { it.id } }

    private fun colSpanOf(id: String): Int = runBlocking {
        repo.tiles.first().first { it.id == id }.colSpan
    }

    /** Центр узла в его локальных координатах — для performTouchInput на этом же узле. */
    private fun SemanticsNodeInteraction.localCenter(): Offset {
        val size = fetchSemanticsNode().size
        return Offset(size.width / 2f, size.height / 2f)
    }

    private fun longPressTile(id: String) {
        val node = rule.onNodeWithTag(tileTag(id))
        val center = node.localCenter()
        node.performTouchInput { longClick(center) }
        rule.onNodeWithTag(TILE_MENU_TAG).assertIsDisplayed()
    }

    /** Детерминированный старт: сбрасываем сетку к дефолту перед каждым тестом. */
    @Before
    fun resetLayout() {
        runBlocking {
            repo.resetToDefault()
            // Гасим first-run промпт выбора обоев: иначе на свежей установке
            // он через ~800мс открывает системный photo picker поверх
            // активности и тесты теряют compose-иерархию.
            WallpaperRepository(ApplicationProvider.getApplicationContext())
                .suppressPickerPromptForTests()
        }
        rule.waitUntil(5000) { currentIds().firstOrNull() == "widget_clock" }
    }

    @Test
    fun dragMovesTileToArbitraryPosition() {
        val id = "pin_phone"
        val before = currentIds().indexOf(id)
        longPressTile(id)

        // Свободное перетаскивание вправо через соседнюю плитку —
        // порядок в репозитории обязан измениться.
        rule.onNodeWithTag(tileTag(id)).let { node ->
            val c = node.localCenter()
            node.performTouchInput { swipe(c, c + Offset(600f, 0f), 900) }
        }
        rule.waitUntil(5000) { currentIds().indexOf(id) > before }
    }

    @Test
    fun steadyDragThroughSmallIconsCommitsBoundedReorders() {
        // Часы 2x2 тянутся вниз через мелкие иконки 1x1 длинным медленным
        // свайпом (максимум move-событий на фиксированный путь). Порядок
        // коммитится один раз, в drop: mid-drag коммиты при разных спанах
        // давали рефлоу, сдвигавший ячейки под пальцем сильнее его движения
        // (пинг-понг ->2, ->1, ->2..., пружины наслаивались — дёргание).
        // Здесь считаем переходы порядка в фоне: их обязан быть ровно один.
        val id = "widget_clock"
        val seen = Collections.synchronizedList(mutableListOf<List<String>>())
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val sampler = thread {
            while (!stop.get()) {
                try {
                    seen.add(currentIds())
                } catch (_: Exception) {
                }
                Thread.sleep(30)
            }
        }
        try {
            // Сначала выделяем долгим кликом: дальше в режиме редактирования
            // тело тянется сразу, без второго лонг-пресса.
            longPressTile(id)
            rule.onNodeWithTag(tileTag(id)).let { node ->
                val c = node.localCenter()
                // Вниз на 600px: дроп приходится на ряд мелких иконок, не на
                // пустой футер (там коммита и не должно быть).
                node.performTouchInput { swipe(c, c + Offset(0f, 600f), 1500) }
            }
            // Даём DataStore дописать все коммиты жеста.
            Thread.sleep(2000)
            // Жест валиден: плитка уехала с первого места.
            rule.waitUntil(5000) { currentIds().indexOf(id) > 0 }
        } finally {
            stop.set(true)
            sampler.join()
        }
        val commits = seen.zipWithNext().count { (a, b) -> a != b }
        // Ровно один переход: коммит в drop. Было: десятки (каждое событие —
        // коммит, часть с прошлыми индексами — скрембл).
        assertTrue("жест не сдвинул плитку?", commits >= 1)
        assertTrue("коммитов $commits, ожидался ровно 1 (коммит только в drop)", commits == 1)
    }

    @Test
    fun dropOnEmptySpaceMovesTileToEnd() {
        // Дроп в пустой футер (там, где нет других виджетов): плитка обязана
        // встать в конец, а не прыгнуть обратно. Координаты считаем из
        // реальной геометрии, а не наугад.
        val id = "pin_phone"
        longPressTile(id)
        val pinNode = rule.onNodeWithTag(tileTag(id))
        val pinPos = pinNode.fetchSemanticsNode().positionInRoot
        val pinSize = pinNode.fetchSemanticsNode().size
        val footNode = rule.onNodeWithTag(EMPTY_SPACE_TAG)
        val footPos = footNode.fetchSemanticsNode().positionInRoot
        val footSize = footNode.fetchSemanticsNode().size
        val start = Offset(pinSize.width / 2f, pinSize.height / 2f)
        val end = (footPos - pinPos) + Offset(footSize.width / 2f, footSize.height / 2f)
        pinNode.performTouchInput { swipe(start, end, 1500) }
        rule.waitUntil(5000) { currentIds().lastOrNull() == id }
    }

    @Test
    fun dropOnEmptySpaceWithScrolledGridMovesTileToEnd() {
        // Длинная сетка + скролл: верхние плитки уходят из вьюпорта и теряют
        // слоты в layoutInfo. Дроп в пустой низ обязан всё равно встать
        // в конец: всё, что выше видимого окна, — раньше точки дропа.
        val id = "pin_phone"
        runBlocking {
            repeat(8) { i ->
                repo.addTile(HomeTileItem.AppPin(id = "tmp_pin_$i", packageName = "com.example.tmp$i"))
            }
        }
        rule.waitUntil(5000) { currentIds().size == 23 }
        // Скроллим контент вверх, чтобы верхние ряды ушли из вьюпорта
        // (свайп без лонг-пресса — жест уходит скроллу, не drag'у).
        rule.onNodeWithTag(tileTag("widget_notes")).let { node ->
            val c = node.localCenter()
            node.performTouchInput { swipe(c, c + Offset(0f, -500f), 800) }
        }
        rule.waitForIdle()
        // Тащим pin в пустой футер.
        longPressTile(id)
        rule.waitForIdle()
        val pinNode = rule.onNodeWithTag(tileTag(id))
        val pinPos = pinNode.fetchSemanticsNode().positionInRoot
        val pinSize = pinNode.fetchSemanticsNode().size
        val footNode = rule.onNodeWithTag(EMPTY_SPACE_TAG)
        val footPos = footNode.fetchSemanticsNode().positionInRoot
        val footSize = footNode.fetchSemanticsNode().size
        val start = Offset(pinSize.width / 2f, pinSize.height / 2f)
        val end = (footPos - pinPos) + Offset(footSize.width / 2f, footSize.height / 2f)
        pinNode.performTouchInput {
            swipe(start, end, 1500)
        }
        rule.waitUntil(5000) { currentIds().lastOrNull() == id }
    }

    @Test
    fun dropIntoHeightHoleCommitsInsteadOfSnappingBack() {
        // Дыра от разной высоты плиток в одном ряду: заметки 2x2 рядом
        // с короткими телефоном/сообщениями 1x1. Дроп в дыру под своей
        // плиткой давал toIndex == fromIndex и жест молча откатывался
        // (снэпбэк) — с видео. Теперь коммитится шаг вниз на один индекс.
        runBlocking {
            repo.moveTileTo("pin_phone", 3)
            repo.moveTileTo("pin_messages", 4)
        }
        rule.waitUntil(5000) {
            currentIds().subList(2, 6) == listOf("widget_notes", "pin_phone", "pin_messages", "widget_player")
        }
        val id = "pin_messages"
        longPressTile(id)
        rule.onNodeWithTag(tileTag(id)).let { node ->
            val c = node.localCenter()
            // 0.75 высоты 1x1: центр уходит ниже собственного слота
            // (своя нижняя граница — 0.5 высоты), но не достаёт до
            // следующего ряда — строго в дыру.
            val h = node.fetchSemanticsNode().size.height
            node.performTouchInput { swipe(c, c + Offset(0f, h * 0.75f), 900) }
        }
        rule.waitUntil(5000) { currentIds().indexOf(id) == 5 }
    }

    @Test
    fun contextMenuHasNoMoveButtonsAndNoSizePresets() {
        longPressTile("widget_clock")

        // Кнопок перемещения быть не должно — только тягание.
        rule.onNodeWithText("Выше").assertDoesNotExist()
        rule.onNodeWithText("Ниже").assertDoesNotExist()

        // Пресетов размеров быть не должно — только тягание за края.
        rule.onNodeWithText("2×1", substring = true).assertDoesNotExist()
        rule.onNodeWithText("2×2", substring = true).assertDoesNotExist()
        rule.onNodeWithText("4×2", substring = true).assertDoesNotExist()

        // А действия открыть/удалить — на месте.
        rule.onNodeWithText("Открыть приложение").assertIsDisplayed()
        rule.onNodeWithText("Удалить виджет").assertIsDisplayed()
    }

    @Test
    fun rightEdgeHandleResizesTile() {
        val id = "widget_weather"
        longPressTile(id)
        val before = colSpanOf(id)

        rule.onNodeWithTag(RESIZE_RIGHT_TAG).let { handle ->
            val c = handle.localCenter()
            handle.performTouchInput { swipe(c, c + Offset(300f, 0f), 900) }
        }

        rule.waitUntil(5000) { colSpanOf(id) > before }
    }
}
