package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.FocusSurface
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class FocusSurfaceMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private val requester = FocusRequester()

    /** Focuses a 100 dp tile and returns its on-screen width in dp after each 10 ms. */
    private fun widthsAfterFocus(motion: Motion): List<Float> {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TvTheme(glassKind = GlassKind.Flat, motion = motion) {
                FocusSurface(onClick = {}, modifier = Modifier.size(100.dp).focusRequester(requester).testTag("tile")) {}
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { requester.requestFocus() }
        return List(100) {
            compose.mainClock.advanceTimeBy(10)
            compose
                .onNodeWithTag("tile")
                .getBoundsInRoot()
                .let { it.right - it.left }
                .value
        }
    }

    @Test
    fun a_focused_tile_grows_to_110_percent_and_overshoots_by_default() {
        val widths = widthsAfterFocus(Motion())

        assertEquals(110f, widths.last(), 0.1f)
        assertTrue("peaked at ${widths.max()}", widths.max() > 111f)
    }

    @Test
    fun a_focused_tile_grows_to_110_percent_without_passing_it_when_focus_overshoot_is_off() {
        val widths = widthsAfterFocus(Motion(focusOvershoot = false))

        assertEquals(110f, widths.last(), 0.1f)
        assertTrue("peaked at ${widths.max()}", widths.max() <= 110.1f)
    }

    @Test
    fun a_focused_tile_does_not_grow_when_animations_are_off() {
        val widths = widthsAfterFocus(Motion(animations = false))

        assertTrue("widths ${widths.distinct()}", widths.all { it == 100f })
    }

    @Test
    fun a_focused_tile_reaches_110_percent_in_100_ms_without_overshoot_with_reduced_motion() {
        val widths = widthsAfterFocus(Motion(reducedMotion = true))

        assertEquals(110f, widths[9], 0.1f)
        assertTrue("peaked at ${widths.max()}", widths.max() <= 110.1f)
    }
}
