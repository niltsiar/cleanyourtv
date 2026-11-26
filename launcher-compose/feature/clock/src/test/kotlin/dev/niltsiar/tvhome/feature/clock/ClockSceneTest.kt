package dev.niltsiar.tvhome.feature.clock

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.random.Random

val ClockSceneTest by testSuite {
    robolectricTestSuite<ClockSceneTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class ClockSceneTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())

                val face = ClockFace(hours = "9", minutes = "41", amPm = "PM", date = "Wednesday  4 November")
            }
        } asContextForEach {
            test("the scene shows the time the am pm marker and the date") {
                compose.mainClock.autoAdvance = false
                compose.setContent { ClockScene(face, glow = true, picture = null, random = Random(2)) }

                for (text in listOf("9", ":", "41", "PM", "Wednesday  4 November")) compose.onNodeWithText(text).assertExists()
            }

            test("the 24 hour clock shows no marker and no time shows nothing") {
                compose.mainClock.autoAdvance = false
                compose.setContent { ClockScene(face.copy(amPm = ""), glow = false, picture = null, random = Random(2)) }
                compose.onNodeWithText("PM").assertDoesNotExist()
                compose.onNodeWithText("41").assertExists()
            }

            test("in a right to left language the time reads left to right and the marker moves before it on the same baseline") {
                compose.mainClock.autoAdvance = false
                compose.setContent {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        ClockScene(face, glow = false, picture = null, random = Random(2))
                    }
                }
                val (hours, colon, minutes, marker) = listOf("9", ":", "41", "PM").map { compose.onNodeWithText(it).getBoundsInRoot() }

                withClue("hours $hours, colon $colon, minutes $minutes") {
                    hours.right shouldBeLessThanOrEqualTo colon.left
                    colon.right shouldBeLessThanOrEqualTo minutes.left
                }
                withClue("marker $marker, hours $hours") { marker.right shouldBeLessThanOrEqualTo hours.left }
                val baseline = { text: String ->
                    compose.onNodeWithText(text).run {
                        getBoundsInRoot().top +
                            getAlignmentLinePosition(FirstBaseline)
                    }
                }
                baseline("PM") shouldBe baseline("41")
            }

            test("in Expressive with reduced motion the screensaver asks for no frame at rest and goes idle through a shape change") {
                compose.setContent {
                    TvTheme(look = Look.Expressive, motion = Motion(reducedMotion = true)) {
                        ClockScene(face, glow = true, picture = null, random = Random(2))
                    }
                }
                compose.waitForIdle()
                val rest = compose.mainClock.currentTime
                compose.waitForIdle()
                withClue("an idle wait at rest ran no frames") { compose.mainClock.currentTime shouldBe rest }

                compose.mainClock.autoAdvance = false
                compose.mainClock.advanceTimeBy(FADE_IN_MS + MOVE_MS + SHAPE_STEP_MS * 2)
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                compose.onNodeWithText("41").assertExists()
            }

            test("a scene without a time yet draws no text") {
                compose.mainClock.autoAdvance = false
                compose.setContent { ClockScene(null, glow = true, picture = null, random = Random(2)) }
                compose.onAllNodesWithText("9").assertCountEquals(0)
            }
        }
    })
