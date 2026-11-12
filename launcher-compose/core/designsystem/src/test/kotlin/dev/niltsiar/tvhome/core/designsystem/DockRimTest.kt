package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "xhdpi")
class DockRimTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun the_dock_panels_side_rim_changes_by_no_more_than_0_03_between_samples_10_px_apart_down_its_straight_part() {
        compose.setContent {
            TvTheme {
                Box(Modifier.background(Color(0xFF404040))) {
                    DockSurface(
                        rememberGlassState(),
                        { Color(0xFF808080) },
                        RoundedCornerShape(32.dp),
                        Modifier.size(300.dp, 117.dp).testTag("dock"),
                    ) {}
                }
            }
        }
        val pixels = compose.onNodeWithTag("dock").captureToImage().toPixelMap()
        // Below the top corner's curve and above the bottom one's, 32 dp at 2 px to a dp.
        val rows = (70..pixels.height - 70 step 10).toList()
        for (x in listOf(0, pixels.width - 1)) {
            val rim = rows.map { pixels[x, it].red }
            val step = rim.zipWithNext { above, below -> abs(above - below) }.max()
            assertTrue("column $x, rim down the side: $rim", step <= 0.03f)
        }
    }
}
