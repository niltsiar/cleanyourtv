package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.OkHold
import dev.niltsiar.tvhome.core.designsystem.holdOk
import io.kotest.matchers.shouldBe

val HoldOkTimerTest by testSuite {
    robolectricTestSuite<HoldOkTimerTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HoldOkTimerTestContent :
    RobolectricTestSuiteContent({
        testFixture { HoldOkTimerTestFixture() } asContextForEach {
            test("the timer of a remote that never repeats opens the menu when the holds own time is up") {
                holdOk(holdMs = 200)

                compose.mainClock.advanceTimeBy(150)
                held shouldBe 0
                compose.mainClock.advanceTimeBy(100)
                held shouldBe 1
            }
        }
    })

private class HoldOkTimerTestFixture : JUnit4RulesContext() {
    val compose = rule(createComposeRule())

    val requester = FocusRequester()

    var held = 0

    fun holdOk(holdMs: Long) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .testTag("tile")
                    .focusRequester(requester)
                    .holdOk(OkHold(holdMs), onHold = { held++ })
                    .focusable(),
            )
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { requester.requestFocus() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tile").performKeyInput { keyDown(Key.DirectionCenter) }
    }
}
