package dev.niltsiar.tvhome.service.keys

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import io.kotest.matchers.shouldBe

val QuickPanelContentTest by testSuite {
    robolectricTestSuite<QuickPanelContentTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

private val MainRows =
    listOf(PanelRow.SleepTimer, PanelRow.Inputs, PanelRow.Screensaver, PanelRow.Mute, PanelRow.Restart, PanelRow.LauncherSettings)

class QuickPanelContentTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createComposeRule())
                val picked = mutableListOf<PanelRow>()
                var backs = 0
                val muted = mutableStateOf(false)

                fun show(
                    rows: List<PanelRow> = MainRows,
                    confirming: Boolean = false,
                    focus: PanelRow? = null,
                    sleepStep: Int = 0,
                ) {
                    compose.setContent {
                        TvTheme(motion = Motion(animations = false)) {
                            QuickPanelContent(rows, confirming, focus, sleepStep, muted.value, { picked += it }, { backs += 1 })
                        }
                    }
                    compose.mainClock.advanceTimeBy(1_000)
                    compose.waitForIdle()
                }

                fun press(key: Key) {
                    compose.onRoot().performKeyInput { pressKey(key) }
                    compose.waitForIdle()
                }
            }
        } asContextForEach {
            test("the first row has focus on open and the sleep row shows the timer's step") {
                show(sleepStep = 30)

                compose.onNodeWithTag("panel:SleepTimer").assertIsFocused()
                compose.onNode(hasTestTag("panel:SleepTimer") and hasText("30 min", substring = true)).assertExists()
                compose.onNodeWithText("Quick panel").assertExists()
            }

            test("ok on a row the d-pad moved to picks that row") {
                show()

                press(Key.DirectionDown)
                press(Key.DirectionCenter)

                picked shouldBe listOf(PanelRow.Inputs)
            }

            test("the restart question shows under its title with cancel focused") {
                show(listOf(PanelRow.CancelRestart, PanelRow.ConfirmRestart), confirming = true, focus = PanelRow.CancelRestart)

                compose.onNodeWithText("Restart the TV?").assertExists()
                compose.onNodeWithTag("panel:CancelRestart").assertIsFocused()
            }

            test("the mute row says unmute while the stream is muted and mute while not") {
                show()
                compose.onNodeWithTag("panel:Mute").assertTextEquals("Mute")

                muted.value = true
                compose.waitForIdle()

                compose.onNodeWithTag("panel:Mute").assertTextEquals("Unmute")
            }

            test("back that reaches the panel window closes it") {
                show()

                press(Key.Back)

                backs shouldBe 1
                picked shouldBe emptyList()
            }
        }
    })
