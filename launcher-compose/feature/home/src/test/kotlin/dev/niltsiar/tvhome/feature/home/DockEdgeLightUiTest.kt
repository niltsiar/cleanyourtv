package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.DockEdgeLight
import dev.niltsiar.tvhome.core.designsystem.DockSurface
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/** The light of the dock edge in isolation: a 400 by 120 dp dock panel with no tint on a black screen, its tile's centre at 200 dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class DockEdgeLightUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val green = Color(0xFF80E0A0)
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun showDock(visible: Boolean = true) {
        compose.setContent {
            TvTheme {
                val scope = rememberCoroutineScope()
                val edge = remember { DockEdgeLight(scope) }
                LaunchedEffect(edge) {
                    edge.focus(centreX = 200f * density) { green }
                    edge.show(visible)
                }
                Box(Modifier.size(960.dp, 540.dp).background(Color.Black)) {
                    DockSurface(
                        glass = rememberGlassState(),
                        tint = { Color.Transparent },
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.size(400.dp, 120.dp),
                        edgeLight = edge,
                    ) {}
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun at(
        x: Float,
        y: Float,
    ): Color = compose.onRoot().captureToImage().toPixelMap()[(x * density).roundToInt(), (y * density).roundToInt()]

    private fun assertChannels(
        expected: List<Int>,
        actual: Color,
        tolerance: Int = 4,
    ) {
        val got = listOf(actual.red, actual.green, actual.blue).map { (it * 255).roundToInt() }
        assertEquals("expected $expected, got $got", true, expected.zip(got).all { (e, g) -> abs(e - g) <= tolerance })
    }

    @Test
    fun the_floor_glint_is_the_light_colour_at_a_fifth_of_its_alpha_under_the_tile_and_gone_100_dp_away() {
        showDock()

        // The ellipse is 200 by 20 dp and sits on the panel's bottom edge, so its middle is 10 dp above it.
        assertChannels(listOf(26, 45, 32), at(200f, 110f))
        assertChannels(listOf(0, 0, 0), at(100f, 110f), tolerance = 2)
    }

    @Test
    fun the_floor_glint_is_flat_and_wide() {
        showDock()

        val side = at(200f + 60f, 110f)
        val up = at(200f, 110f - 8f)

        // 60 dp to the side is 0.6 of the radius across, and 8 dp up is 0.8 of the radius up: the glint is wider than tall.
        assertEquals(true, side.green > up.green * 1.5f)
    }

    @Test
    fun the_rim_highlight_is_the_light_at_085_over_the_panels_rim_at_the_centre_and_gone_60_dp_away() {
        showDock()

        val centre = at(200f, 0.75f)
        val away = at(200f + 62f, 0.75f)

        assertEquals("highlight over the rim", true, centre.green > away.green + 0.3f)
        assertEquals(
            "the rim itself, white at 0.48 under a 0.16 sheen, is the same grey either side",
            true,
            abs(away.red - away.blue) < 0.02f,
        )
    }

    @Test
    fun a_light_that_is_not_shown_draws_nothing() {
        showDock(visible = false)

        assertChannels(listOf(0, 0, 0), at(200f, 110f), tolerance = 2)
    }
}
