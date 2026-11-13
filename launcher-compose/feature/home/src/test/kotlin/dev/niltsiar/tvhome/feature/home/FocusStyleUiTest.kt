package dev.niltsiar.tvhome.feature.home

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private val coral = Color(0xFFFF6040)
    private var style by mutableStateOf(FocusStyle.Glass)

    private fun showFocusedTile(
        style: FocusStyle,
        ring: Color? = null,
    ) {
        this.style = style
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            TvTheme(focusStyle = this.style) {
                Box(Modifier.size(180.dp).background(frame).testTag("frame"), contentAlignment = Alignment.Center) {
                    Box(Modifier.align(Alignment.TopStart).size(1.dp).focusable())
                    FocusSurface(
                        onClick = {},
                        modifier = Modifier.size(100.dp).focusRequester(requester),
                        ring = ring?.let { colour -> { colour } },
                    ) {
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

    /** How much redder than the frame it is two dp past the keyline under the tile, and beside it. */
    private fun redUnder(): Float = at(90f, 90f + 1.08f * (50f + 9f) + 2f).red - frame.red

    private fun redBeside(): Float = at(90f + 1.08f * (50f + 9f) + 2f, 90f).red - frame.red

    /** The pixels of the white ring down the middle of the tile, from the frame above it to the grown tile's top edge. */
    private fun ringPixels(): Int {
        val pixels = compose.onNodeWithTag("frame").captureToImage().toPixelMap()
        return (0 until (above(0f) * 2).toInt()).count { y -> pixels[180, y].let { it.red > 0.9f && it.blue > 0.9f } }
    }

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
    fun rim_draws_its_ring_at_least_2_px_wider_than_glass_on_the_same_tile() {
        showFocusedTile(FocusStyle.Glass)
        val glass = ringPixels()
        style = FocusStyle.Rim
        compose.waitForIdle()
        val rim = ringPixels()

        assertTrue("glass $glass px, rim $rim px", glass >= 6 && rim >= glass + 2)
    }

    @Test
    fun halo_draws_the_ring_and_a_glow_in_its_colour_under_the_tile_half_as_strong_beside_it_where_rim_draws_none() {
        showFocusedTile(FocusStyle.Halo, ring = coral)

        assertEquals(tile.red, sheen().red, 0.01f)
        assertTrue("ring ${ring()}", ring().red > 0.95f && ring().blue in 0.2f..0.3f)
        assertTrue("keyline ${keyline()}", keyline().red < frame.red - 0.04f)
        val under = redUnder()
        assertTrue("under $under, bluer by ${glow()}", under >= 0.3f && under >= 3f * glow())
        assertTrue("beside ${redBeside()}, under $under", redBeside() / under in 0.4f..0.6f)

        style = FocusStyle.Rim
        compose.waitForIdle()
        assertEquals(0f, redUnder(), 0.003f)
    }
}
