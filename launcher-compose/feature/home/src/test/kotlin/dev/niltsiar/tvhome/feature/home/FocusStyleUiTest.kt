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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.FocusStyle
import dev.niltsiar.tvhome.core.designsystem.FocusSurface
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What a focused tile draws in each [FocusStyle], read from pixels: a 100 dp grey tile on a mid-grey frame, at 2 pixels to the dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class FocusStyleUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val requester = FocusRequester()
    private val frame = Color(0xFF202020)
    private val tile = Color(0xFF404040)

    private fun showFocusedTile(style: FocusStyle) {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            TvTheme(focusStyle = style) {
                Box(Modifier.size(180.dp).background(frame).testTag("frame"), contentAlignment = Alignment.Center) {
                    Box(Modifier.align(Alignment.TopStart).size(1.dp).focusable())
                    FocusSurface(onClick = {}, modifier = Modifier.size(100.dp).focusRequester(requester)) {
                        Box(Modifier.fillMaxSize().background(tile))
                    }
                }
            }
        }
        compose.runOnUiThread { view.requestFocusFromTouch() }
        compose.waitForIdle()
        compose.runOnUiThread { requester.requestFocus() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /** The pixel at ([x], [y]) dp of the frame. */
    private fun at(
        x: Float,
        y: Float,
    ): Color = compose.onNodeWithTag("frame").captureToImage().toPixelMap()[(x * 2).toInt(), (y * 2).toInt()]

    /** The tile grows 1.08 times about the frame's centre, 90 dp; [out] dp past its edge at rest is this far above the centre. */
    private fun above(out: Float): Float = 90f - 1.08f * (50f + out)

    /** Ten dp under the grown tile's top edge, in the tile. */
    private fun sheen(): Color = at(90f, above(0f) + 10f)

    /** The middle of the 3 dp ring, past its 3 dp gap. */
    private fun ring(): Color = at(90f, above(4.5f))

    /** The middle of the 2 dp black stroke that holds the ring on a bright wallpaper. */
    private fun keyline(): Color = at(90f, above(3f + 3f + 1f + 1f))

    /** How much bluer than the frame the glow makes it two dp past the keyline under the tile, where it is strongest: the light is above. */
    private fun glow(): Float = at(90f, 90f + 1.08f * (50f + 9f) + 2f).blue - frame.blue

    @Test
    fun glass_lights_the_top_of_the_tile_and_glows_past_the_ring() {
        showFocusedTile(FocusStyle.Glass)

        assertTrue("sheen ${sheen()}", sheen().red > tile.red + 0.08f)
        assertTrue("glow ${glow()}", glow() > 0.015f)
        assertTrue("ring ${ring()}", ring().red > 0.95f && ring().blue > 0.95f)
    }

    @Test
    fun rim_draws_the_ring_and_its_keyline_and_nothing_else() {
        showFocusedTile(FocusStyle.Rim)

        assertEquals(tile.red, sheen().red, 0.01f)
        assertEquals(0f, glow(), 0.003f)
        assertTrue("ring ${ring()}", ring().red > 0.95f && ring().blue > 0.95f)
        assertTrue("keyline ${keyline()}", keyline().red < frame.red - 0.04f)
    }

    @Test
    fun halo_draws_the_ring_and_half_of_the_glow_glass_does_and_no_lit_top() {
        showFocusedTile(FocusStyle.Halo)

        assertEquals(tile.red, sheen().red, 0.01f)
        assertTrue("glow ${glow()}", glow() in 0.006f..0.018f)
        assertTrue("ring ${ring()}", ring().red > 0.95f && ring().blue > 0.95f)
        assertTrue("keyline ${keyline()}", keyline().red < frame.red - 0.04f)
    }
}
