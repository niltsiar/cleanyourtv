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
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

val OverlayHostTest by testSuite {
    robolectricTestSuite<OverlayHostTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class OverlayHostTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createComposeRule())

                fun settle() {
                    compose.mainClock.advanceTimeBy(1_000)
                    compose.waitForIdle()
                }

                /** The red channel, 0 to 255, at the middle of the white ground. */
                fun red(): Float {
                    val shot = compose.onNodeWithTag("ground").captureToImage().toPixelMap()
                    return shot[shot.width / 2, shot.height / 2].red * 255f
                }
            }
        } asContextForEach {
            test("the scrim follows an open lambda that reads a plain value and changes on recomposition") {
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

                withClue("no scrim while closed") { closed shouldBe (255f plusOrMinus 1f) }
                withClue("half white, half scrim while open") { open shouldBe ((255f + 0x07) / 2f plusOrMinus 2f) }
                withClue("no scrim once closed again") { closedAgain shouldBe (255f plusOrMinus 1f) }
            }
        }
    })
