package dev.metro.launcher

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.metro.launcher.data.AppInfo
import dev.metro.launcher.ui.theme.MetroTheme
import dev.metro.launcher.ui.transition.AppTransitionManager
import dev.metro.launcher.ui.transition.AppTransitionOverlay
import dev.metro.launcher.ui.transition.TransitionPhase
import dev.metro.launcher.ui.transition.TransitionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class AppTransitionTest {

    @get:Rule
    val rule = createComposeRule()

    private val testApp = AppInfo(
        label = "Metro Calculator",
        packageName = "com.metro.calc",
        icon = ColorDrawable(Color.RED),
    )

    @Test
    fun transitionManagerLifecyclePhases() {
        val tm = AppTransitionManager()
        assertEquals(TransitionPhase.IDLE, tm.state.value.phase)

        // findTileRect provider
        tm.findTileRect = { pkg ->
            if (pkg == testApp.packageName) Rect(10f, 20f, 110f, 120f) else null
        }

        // Simulate onResume without prior launch -> stays IDLE
        tm.onResume()
        assertEquals(TransitionPhase.IDLE, tm.state.value.phase)

        // When transition completes
        tm.onTransitionFinished()
        assertEquals(TransitionPhase.IDLE, tm.state.value.phase)
    }

    @Test
    fun overlayDisplaysAppLabelDuringTransition() {
        val state = TransitionState(
            phase = TransitionPhase.OPENING,
            app = testApp,
            targetRect = Rect(50f, 100f, 250f, 300f),
        )

        rule.setContent {
            MetroTheme {
                AppTransitionOverlay(
                    transitionState = state,
                    onFinished = {},
                )
            }
        }

        // Rule should compose without crashing
        assertNotNull(rule.onNodeWithText("Metro Calculator"))
    }

    @Test
    fun overlayDisplaysAppDuringClosingTransition() {
        val state = TransitionState(
            phase = TransitionPhase.CLOSING,
            app = testApp,
            targetRect = Rect(50f, 100f, 250f, 300f),
        )

        rule.setContent {
            MetroTheme {
                AppTransitionOverlay(
                    transitionState = state,
                    onFinished = {},
                )
            }
        }

        assertNotNull(rule.onNodeWithText("Metro Calculator"))
    }
}
