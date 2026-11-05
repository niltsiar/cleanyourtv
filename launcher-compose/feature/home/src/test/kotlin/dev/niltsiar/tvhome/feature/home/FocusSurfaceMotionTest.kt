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
    fun a_focused_tile_grows_to_108_percent_without_passing_it_by_default() {
        val widths = widthsAfterFocus(Motion())

        assertTrue("half way ${widths[4]}", widths[4] in 102f..107f)
        assertEquals(108f, widths.last(), 0.1f)
        assertTrue("peaked at ${widths.max()}", widths.max() <= 108.1f)
    }

    @Test
    fun a_focused_tile_grows_to_108_percent_and_overshoots_when_focus_overshoot_is_on() {
        val widths = widthsAfterFocus(Motion(focusOvershoot = true))

        assertEquals(108f, widths.last(), 0.1f)
        assertTrue("peaked at ${widths.max()}", widths.max() > 109f)
    }

    @Test
    fun a_focused_tile_does_not_grow_when_animations_are_off() {
        val widths = widthsAfterFocus(Motion(animations = false))

        assertTrue("widths ${widths.distinct()}", widths.all { it == 100f })
    }

    @Test
    fun a_focused_tile_reaches_108_percent_in_100_ms_without_overshoot_with_reduced_motion() {
        val widths = widthsAfterFocus(Motion(reducedMotion = true))

        assertEquals(108f, widths[9], 0.1f)
        assertTrue("peaked at ${widths.max()}", widths.max() <= 108.1f)
    }

    @Test
    fun a_focused_tile_is_already_growing_in_the_second_frame_after_focus() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TvTheme(glassKind = GlassKind.Flat, motion = Motion()) {
                FocusSurface(onClick = {}, modifier = Modifier.size(100.dp).focusRequester(requester).testTag("tile")) {}
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { requester.requestFocus() }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }

        val width =
            compose
                .onNodeWithTag("tile")
                .getBoundsInRoot()
                .let { it.right - it.left }
                .value
        assertTrue("width $width", width > 100f)
    }
}
