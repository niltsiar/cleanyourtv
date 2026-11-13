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
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import kotlin.math.abs

val DockRimTest by testSuite {
    robolectricTestSuite<DockRimTestContent>(
        "sdk 31, xhdpi",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "xhdpi"
            },
    )
}

class DockRimTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createComposeRule())
            }
        } asContextForEach {
            test("the dock panels side rim changes by no more than 0.03 between samples 10 px apart down its straight part") {
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
                    withClue("column $x, rim down the side: $rim") { step shouldBeLessThanOrEqual 0.03f }
                }
            }
        }
    })
