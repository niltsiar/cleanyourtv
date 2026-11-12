package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A long name scrolled in a 120 dp box, 2 px to a dp. The text has a white background as well as white glyphs, so
 * wherever the line lies, it is solid white, and its pixels read its alpha on the black box.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "xhdpi")
class MarqueeFadeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun scrolledFor(millis: Long): PixelMap {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MarqueeLine(
                "Control de TV con mando a distancia",
                TextStyle(color = Color.White, background = Color.White, fontSize = 16.sp),
                scrolling = { true },
                modifier = Modifier.size(120.dp, 30.dp).background(Color.Black).testTag("line"),
            )
        }
        compose.mainClock.advanceTimeBy(millis)
        return compose.onNodeWithTag("line").captureToImage().toPixelMap()
    }

    private fun PixelMap.middle(x: Int) = this[x, height / 2].red

    @Test
    fun while_the_name_scrolls_it_fades_out_at_both_clip_edges() {
        // 1.2 s at its start position, then 1 s at 30 dp a second: 60 dp along, well past the 12 dp fade.
        val pixels = scrolledFor(2_200)

        assertTrue("inside the fades: ${pixels.middle(120)}", pixels.middle(120) > 0.95f)
        assertTrue("at the left clip: ${pixels.middle(1)}", pixels.middle(1) < 0.15f)
        assertTrue("at the right clip: ${pixels.middle(pixels.width - 2)}", pixels.middle(pixels.width - 2) < 0.15f)
    }

    @Test
    fun at_its_start_position_the_name_is_not_faded_at_the_left_but_is_at_the_right() {
        val pixels = scrolledFor(600)

        assertTrue("at the left clip: ${pixels.middle(1)}", pixels.middle(1) > 0.95f)
        assertTrue("at the right clip: ${pixels.middle(pixels.width - 2)}", pixels.middle(pixels.width - 2) < 0.15f)
    }
}
