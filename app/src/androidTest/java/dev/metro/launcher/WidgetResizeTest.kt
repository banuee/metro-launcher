package dev.metro.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import dev.metro.launcher.data.HomeLayoutRepository
import dev.metro.launcher.data.WallpaperRepository
import dev.metro.launcher.ui.edit.tileTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Тесты адаптивности виджетов лаунчера во всех поддерживаемых размерах (1x1, 2x1, 2x2, 4x2).
 */
class WidgetResizeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val repo: HomeLayoutRepository by lazy {
        HomeLayoutRepository(ApplicationProvider.getApplicationContext())
    }

    @Before
    fun resetLayout() {
        runBlocking {
            repo.resetToDefault()
            WallpaperRepository(ApplicationProvider.getApplicationContext())
                .suppressPickerPromptForTests()
        }
        rule.waitUntil(5000) {
            runBlocking { repo.tiles.first().any { it.id == "widget_player" } }
        }
    }

    @Test
    fun testPlayerWidgetResizesTo2x1() {
        runBlocking {
            repo.updateTileSpan("widget_player", colSpan = 2, rowSpan = 1)
        }
        rule.waitForIdle()
        rule.onNodeWithTag(tileTag("widget_player")).assertIsDisplayed()
        val tile = runBlocking { repo.tiles.first().first { it.id == "widget_player" } }
        assertEquals(2, tile.colSpan)
        assertEquals(1, tile.rowSpan)
    }

    @Test
    fun testPlayerWidgetResizesTo1x1() {
        runBlocking {
            repo.updateTileSpan("widget_player", colSpan = 1, rowSpan = 1)
        }
        rule.waitForIdle()
        rule.onNodeWithTag(tileTag("widget_player")).assertIsDisplayed()
        val tile = runBlocking { repo.tiles.first().first { it.id == "widget_player" } }
        assertEquals(1, tile.colSpan)
        assertEquals(1, tile.rowSpan)
    }

    @Test
    fun testPlayerWidgetResizesTo4x2() {
        runBlocking {
            repo.updateTileSpan("widget_player", colSpan = 4, rowSpan = 2)
        }
        rule.waitForIdle()
        rule.onNodeWithTag(tileTag("widget_player")).assertIsDisplayed()
        val tile = runBlocking { repo.tiles.first().first { it.id == "widget_player" } }
        assertEquals(4, tile.colSpan)
        assertEquals(2, tile.rowSpan)
    }

    @Test
    fun testClockWidgetResizesTo1x1And2x1() {
        runBlocking {
            repo.updateTileSpan("widget_clock", colSpan = 1, rowSpan = 1)
        }
        rule.waitForIdle()
        rule.onNodeWithTag(tileTag("widget_clock")).assertIsDisplayed()

        runBlocking {
            repo.updateTileSpan("widget_clock", colSpan = 2, rowSpan = 1)
        }
        rule.waitForIdle()
        rule.onNodeWithTag(tileTag("widget_clock")).assertIsDisplayed()
    }

    @Test
    fun testWeatherWidgetResizesTo1x1And2x1() {
        runBlocking {
            repo.updateTileSpan("widget_weather", colSpan = 1, rowSpan = 1)
        }
        rule.waitForIdle()
        rule.onNodeWithTag(tileTag("widget_weather")).assertIsDisplayed()

        runBlocking {
            repo.updateTileSpan("widget_weather", colSpan = 2, rowSpan = 1)
        }
        rule.waitForIdle()
        rule.onNodeWithTag(tileTag("widget_weather")).assertIsDisplayed()
    }

    @Test
    fun testNotesWidgetResizesTo1x1And2x1() {
        runBlocking {
            repo.updateTileSpan("widget_notes", colSpan = 1, rowSpan = 1)
        }
        rule.waitForIdle()
        rule.onNodeWithTag(tileTag("widget_notes")).assertIsDisplayed()

        runBlocking {
            repo.updateTileSpan("widget_notes", colSpan = 2, rowSpan = 1)
        }
        rule.waitForIdle()
        rule.onNodeWithTag(tileTag("widget_notes")).assertIsDisplayed()
    }
}
