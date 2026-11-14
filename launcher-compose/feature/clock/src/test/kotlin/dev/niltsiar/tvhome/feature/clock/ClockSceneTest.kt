package dev.niltsiar.tvhome.feature.clock

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
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

                val face = ClockFace(hours = "9", minutes = "41", amPm = "PM", date = "WEDNESDAY  4 NOVEMBER")
            }
        } asContextForEach {
            test("the scene shows the time the am pm marker and the date") {
                compose.mainClock.autoAdvance = false
                compose.setContent { ClockScene(face, glow = true, picture = null, random = Random(2)) }

                for (text in listOf("9", ":", "41", "PM", "WEDNESDAY  4 NOVEMBER")) compose.onNodeWithText(text).assertExists()
            }

            test("the 24 hour clock shows no marker and no time shows nothing") {
                compose.mainClock.autoAdvance = false
                compose.setContent { ClockScene(face.copy(amPm = ""), glow = false, picture = null, random = Random(2)) }
                compose.onNodeWithText("PM").assertDoesNotExist()
                compose.onNodeWithText("41").assertExists()
            }

            test("a scene without a time yet draws no text") {
                compose.mainClock.autoAdvance = false
                compose.setContent { ClockScene(null, glow = true, picture = null, random = Random(2)) }
                compose.onAllNodesWithText("9").assertCountEquals(0)
            }
        }
    })
