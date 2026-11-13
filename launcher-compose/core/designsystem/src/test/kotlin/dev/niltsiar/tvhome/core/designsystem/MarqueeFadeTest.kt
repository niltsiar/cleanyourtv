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
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan

/**
 * A long name scrolled in a 120 dp box, 2 px to a dp. The text has a white background as well as white glyphs, so
 * wherever the line lies, it is solid white, and its pixels read its alpha on the black box.
 */
val MarqueeFadeTest by testSuite {
    robolectricTestSuite<MarqueeFadeTestContent>(
        "sdk 31, xhdpi",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "xhdpi"
            },
    )
}

class MarqueeFadeTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createComposeRule())

                fun scrolledFor(millis: Long): PixelMap {
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

                fun PixelMap.middle(x: Int) = this[x, height / 2].red
            }
        } asContextForEach {
            test("while the name scrolls it fades out at both clip edges") {
                // 1.2 s at its start position, then 1 s at 30 dp a second: 60 dp along, well past the 12 dp fade.
                val pixels = scrolledFor(2_200)

                withClue("inside the fades: ${pixels.middle(120)}") { pixels.middle(120) shouldBeGreaterThan 0.95f }
                withClue("at the left clip: ${pixels.middle(1)}") { pixels.middle(1) shouldBeLessThan 0.15f }
                withClue("at the right clip: ${pixels.middle(pixels.width - 2)}") {
                    pixels.middle(pixels.width - 2) shouldBeLessThan 0.15f
                }
            }

            test("at its start position the name is not faded at the left but is at the right") {
                val pixels = scrolledFor(600)

                withClue("at the left clip: ${pixels.middle(1)}") { pixels.middle(1) shouldBeGreaterThan 0.95f }
                withClue("at the right clip: ${pixels.middle(pixels.width - 2)}") {
                    pixels.middle(pixels.width - 2) shouldBeLessThan 0.15f
                }
            }
        }
    })
