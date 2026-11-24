package dev.niltsiar.tvhome

import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import app.cash.molecule.RecompositionMode
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.designsystem.focus.focusedTag
import dev.niltsiar.tvhome.feature.settings.MoleculeSettingsViewModel
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/** [SettingsHost] over the real graph while the look changes under a focused chip: focus stays where it was. */
val LookSwitchTest by testSuite {
    robolectricTestSuite<LookSwitchTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                application = TvHomeApplication::class
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class LookSwitchTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())

                val graph get() = (compose.activity.application as TvHomeApplication).graph

                /** What the box around the page heard, in order. */
                val pageFocus = mutableListOf<FocusState>()

                fun press(key: Key) {
                    compose.onRoot().performKeyInput { pressKey(key) }
                    settle()
                }

                fun settle() {
                    compose.mainClock.advanceTimeBy(1_000)
                    compose.waitForIdle()
                }

                fun show() {
                    ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
                    File(ThemeDirectory(compose.activity).dir!!, "config.json").writeText("{}")
                    // A TV has no touch screen; in touch mode nothing takes key focus.
                    compose.runOnUiThread {
                        compose.activity.window.decorView
                            .requestFocusFromTouch()
                    }
                    compose.setContent {
                        val scope = rememberCoroutineScope()
                        val viewModel =
                            remember {
                                MoleculeSettingsViewModel(
                                    graph.configRepository,
                                    graph.settingsSources,
                                    scope,
                                    RecompositionMode.Immediate,
                                    EmptyCoroutineContext,
                                )
                            }
                        Box(Modifier.onFocusChanged { pageFocus += it }) {
                            SettingsHost(
                                graph = graph,
                                viewModel = viewModel,
                                onChooseDefaultHome = {},
                                onOpenTvSettings = { false },
                                onReorder = {},
                            )
                        }
                    }
                    settle()
                }
            }
        } asContextForEach {
            test("picking expressive keeps focus on the chip far down the page at the same place") {
                show()
                repeat(16) { press(Key.DirectionDown) }
                press(Key.DirectionRight)
                val tag = compose.focusedTag()
                tag shouldBe "option:ReducedMotion:1"
                val bounds = compose.onNode(isFocused()).getBoundsInRoot()
                pageFocus.clear()

                runBlocking { graph.configRepository.set(Choices.Look, Look.Expressive) }
                settle()

                graph.configRepository.config.value.look shouldBe Look.Expressive
                compose.focusedTag() shouldBe tag
                compose.onNode(isFocused()).getBoundsInRoot() shouldBe bounds
                withClue("the page's focus events: $pageFocus") { pageFocus.map { it.hasFocus } shouldNotContain false }
            }
        }
    })
