package dev.metro.launcher

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metro.launcher.ui.tiles.WeatherIcon
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Регрессия кропа nerd-иконок погоды.
 *
 * Контекст (зафиксировано замерами на устройстве): у Noto Sans Nerd Font
 * естественная строка 1.36em (157px при 42sp), а lineHeight меньше
 * естественной Compose игнорирует (кламп к natural: с lineHeight=1em намерялось
 * 158px вместо 116px). Поэтому Text распухал за свой Box и резался плиткой —
 * Text'ом это не чинится. WeatherIcon рисует глиф на канве, центрируя
 * ink-бокс: метрики шрифта не важны, вылезать нечему.
 *
 * Тест guards layout: иконка занимает ровно запрошенный size (Box не
 * распухает и не схлопывается) для нескольких глифов.
 */
class WeatherIconTest {

    @get:Rule
    val rule = createComposeRule()

    private fun assertIconFitsBox(code: Int) {
        rule.setContent {
            WeatherIcon(
                code = code,
                color = Color.White,
                modifier = Modifier.testTag("icon"),
                size = 48.dp,
                fontSize = 42.sp,
            )
        }
        val sizePx = rule.onNodeWithTag("icon", useUnmergedTree = true)
            .fetchSemanticsNode().size
        val expectedPx = with(rule.density) { 48.dp.roundToPx() }
        assertEquals("ширина иконки", expectedPx, sizePx.width)
        assertEquals("высота иконки", expectedPx, sizePx.height)
    }

    @Test
    fun overcastIconFitsItsBox() = assertIconFitsBox(3)

    @Test
    fun thunderstormIconFitsItsBox() = assertIconFitsBox(95)

    @Test
    fun snowIconFitsItsBox() = assertIconFitsBox(71)
}
