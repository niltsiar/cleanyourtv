package dev.niltsiar.tvhome.feature.home

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.FocusSurface
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Widths are what the tile draws, read from pixels: the growth is drawn, so the native graphics mode is needed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class FocusSurfaceMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private val requester = FocusRequester()
    private lateinit var view: View
    private var restRedness = 1f

    /** A 100 dp red tile on black, with room around it to grow, and a focusable dot before it. */
    private fun showTile(motion: Motion) {
        compose.setContent {
            view = LocalView.current
            TvTheme(motion = motion) {
                Box(Modifier.size(140.dp).background(Color.Black).testTag("frame"), contentAlignment = Alignment.Center) {
                    Box(Modifier.align(Alignment.TopStart).size(1.dp).focusable())
                    FocusSurface(onClick = {}, glow = false, modifier = Modifier.size(100.dp).focusRequester(requester).testTag("tile")) {
                        Box(Modifier.fillMaxSize().background(Color.Red))
                    }
                }
            }
        }
        // The native graphics mode starts in touch mode, where nothing takes key focus; leaving it focuses the first
        // target, a dot beside the tile, so the tile is at rest before the focus that is measured.
        compose.runOnUiThread { view.requestFocusFromTouch() }
        compose.waitForIdle()
        restRedness = redness()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { requester.requestFocus() }
    }

    /** The width in dp the tile draws, against its 100 dp at rest. */
    private fun drawnWidth(): Float = 100f * redness() / restRedness

    /** How much red the tile draws over green across its middle row (the white ring adds none), in pixels of pure red. */
    private fun redness(): Float {
        val image = compose.onNodeWithTag("frame").captureToImage().toPixelMap()
        val row = image.height / 2
        val redness = { x: Int -> image[x, row].let { it.red - it.green } }
        return (0 until image.width).sumOf { x -> redness(x).toDouble() }.toFloat() / redness(image.width / 2)
    }

    /** Focuses a 100 dp tile and returns its drawn width in dp after each 10 ms. */
    private fun widthsAfterFocus(motion: Motion): List<Float> {
        showTile(motion)
        return List(100) {
            compose.mainClock.advanceTimeBy(10)
            drawnWidth()
        }
    }

    @Test
    fun a_focused_tile_grows_to_108_percent_without_passing_it_by_default() {
        val widths = widthsAfterFocus(Motion())

        assertTrue("half way ${widths[4]}", widths[4] in 102f..107f)
        assertEquals(108f, widths.last(), 0.5f)
        assertTrue("peaked at ${widths.max()}", widths.max() <= 108.5f)
    }

    @Test
    fun a_focused_tile_grows_to_108_percent_and_overshoots_when_focus_overshoot_is_on() {
        val widths = widthsAfterFocus(Motion(focusOvershoot = true))

        assertEquals(108f, widths.last(), 0.5f)
        assertTrue("peaked at ${widths.max()}", widths.max() > widths.last() + 0.5f)
    }

    @Test
    fun a_focused_tile_does_not_grow_when_animations_are_off() {
        val widths = widthsAfterFocus(Motion(animations = false))

        assertTrue("widths ${widths.distinct()}", widths.all { it in 99.5f..100.5f })
    }

    @Test
    fun a_focused_tile_reaches_108_percent_in_100_ms_without_overshoot_with_reduced_motion() {
        val widths = widthsAfterFocus(Motion(reducedMotion = true))

        assertEquals(108f, widths[9], 0.5f)
        assertTrue("peaked at ${widths.max()}", widths.max() <= 108.5f)
    }

    @Test
    fun a_focused_tile_is_already_growing_in_the_second_frame_after_focus() {
        showTile(Motion())
        repeat(2) { compose.mainClock.advanceTimeByFrame() }

        val width = drawnWidth()
        assertTrue("width $width", width > 100f)
    }

    @Test
    fun a_growing_tile_keeps_its_bounds_so_accessibility_has_nothing_to_update() {
        showTile(Motion())
        repeat(5) { compose.mainClock.advanceTimeByFrame() }
        val growing = drawnWidth()
        val bounds = compose.onNodeWithTag("tile").getBoundsInRoot()

        assertTrue("drawn $growing", growing > 100f)
        assertEquals(100f, (bounds.right - bounds.left).value, 0.01f)
    }
}
