package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31])
class OverlayHostTest {
    @get:Rule
    val compose = createComposeRule()

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /** The red channel, 0 to 255, at the middle of the white ground. */
    private fun red(): Float {
        val shot = compose.onNodeWithTag("ground").captureToImage().toPixelMap()
        return shot[shot.width / 2, shot.height / 2].red * 255f
    }

    @Test
    fun the_scrim_follows_an_open_lambda_that_reads_a_plain_value_and_changes_on_recomposition() {
        var picked by mutableStateOf(false)
        compose.setContent {
            TvTheme {
                val shown = picked
                Box(Modifier.size(100.dp).background(Color.White).testTag("ground")) {
                    OverlayHost(open = { shown }) {}
                }
            }
        }
        settle()
        val closed = red()

        picked = true
        settle()
        val open = red()

        picked = false
        settle()
        val closedAgain = red()

        assertEquals("no scrim while closed", 255f, closed, 1f)
        assertEquals("half white, half scrim while open", (255f + 0x07) / 2f, open, 2f)
        assertEquals("no scrim once closed again", 255f, closedAgain, 1f)
    }
}
