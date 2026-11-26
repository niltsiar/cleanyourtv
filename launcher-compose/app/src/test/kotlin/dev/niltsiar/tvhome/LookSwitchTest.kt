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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import dev.niltsiar.tvhome.core.designsystem.LookFlip
import dev.niltsiar.tvhome.core.designsystem.focus.focusedTag
import dev.niltsiar.tvhome.core.designsystem.rememberLookFlip
import dev.niltsiar.tvhome.feature.settings.MoleculeSettingsViewModel
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/**
 * [SettingsHost] over the real graph while the look changes under a focused chip, picked on the page or stored from
 * elsewhere: focus stays where it was, and the page draws in the stored look.
 */
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

                lateinit var flip: LookFlip

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

                fun show(theme: String = "{}") {
                    ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
                    File(ThemeDirectory(compose.activity).dir!!, "config.json").writeText(theme)
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
                        flip = rememberLookFlip()
                        Box(Modifier.onFocusChanged { pageFocus += it }) {
                            SettingsHost(
                                graph = graph,
                                viewModel = viewModel,
                                onChooseDefaultHome = {},
                                onOpenTvSettings = { false },
                                onReorder = {},
                                flip = flip,
                            )
                        }
                    }
                    settle()
                }

                fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        } asContextForEach {
            for (from in Look.entries) {
                val to = Look.entries.single { it != from }
                test(
                    "ok on ${to.name} in the style row of a page in ${from.name} switches the look under the chip, which keeps focus and its place",
                ) {
                    show("""{"look": "${from.name.lowercase()}"}""")
                    repeat(12) { if (!compose.focusedTag().startsWith("option:Look:")) press(Key.DirectionDown) }
                    compose.focusedTag() shouldBe "option:Look:${from.ordinal}"
                    press(if (to.ordinal > from.ordinal) Key.DirectionRight else Key.DirectionLeft)
                    val tag = compose.focusedTag()
                    tag shouldBe "option:Look:${to.ordinal}"
                    val bounds = compose.onNode(isFocused()).getBoundsInRoot()
                    pageFocus.clear()
                    // The page draws, so the flip has a picture to take.
                    compose.onRoot().captureToImage()

                    press(Key.DirectionCenter)
                    compose.waitUntil(30_000) { graph.configRepository.config.value.look == to }
                    settle()

                    flip.captures shouldBe 1
                    flip.holdsPicture shouldBe false

                    compose.focusedTag() shouldBe tag
                    compose.onNode(isFocused()).getBoundsInRoot() shouldBe bounds
                    withClue("the page's focus events: $pageFocus") { pageFocus.map { it.hasFocus } shouldNotContain false }
                    exists("setting:ExpressiveTile") shouldBe (to == Look.Expressive)
                }
            }

            for (from in Look.entries) {
                val to = Look.entries.single { it != from }
                test(
                    "ok on ${to.name} in a page in ${from.name} reveals the new look and lets go of the picture well before the arrival limit",
                ) {
                    show("""{"look": "${from.name.lowercase()}"}""")
                    repeat(12) { if (!compose.focusedTag().startsWith("option:Look:")) press(Key.DirectionDown) }
                    press(if (to.ordinal > from.ordinal) Key.DirectionRight else Key.DirectionLeft)
                    compose.onRoot().captureToImage()
                    compose.mainClock.autoAdvance = false
                    try {
                        val pressed = compose.mainClock.currentTime
                        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
                        flip.holdsPicture shouldBe true
                        // The write takes real time on the IO thread; the clock waits for it.
                        val written = System.currentTimeMillis() + 30_000
                        while (graph.configRepository.config.value.look != to && System.currentTimeMillis() < written) {
                            compose.waitForIdle()
                            Thread.sleep(10)
                        }
                        graph.configRepository.config.value.look shouldBe to
                        compose.mainClock.advanceTimeBy(100)
                        withClue("the picture 100 ms after the press") { flip.holdsPicture shouldBe true }
                        while (flip.holdsPicture && compose.mainClock.currentTime - pressed < LookFlip.ARRIVAL_MS) {
                            compose.mainClock.advanceTimeByFrame()
                        }
                        compose.mainClock.currentTime - pressed shouldBeLessThan LookFlip.ARRIVAL_MS - 300
                    } finally {
                        compose.mainClock.autoAdvance = true
                    }
                    flip.captures shouldBe 1
                }
            }

            test("a key pressed during the flip moves focus on the page, under the picture") {
                show()
                repeat(12) { if (!compose.focusedTag().startsWith("option:Look:")) press(Key.DirectionDown) }
                press(Key.DirectionRight)
                compose.onRoot().captureToImage()
                compose.mainClock.autoAdvance = false
                try {
                    compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
                    flip.holdsPicture shouldBe true

                    compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
                    compose.mainClock.advanceTimeByFrame()

                    compose.focusedTag() shouldBe "option:Look:0"
                    flip.holdsPicture shouldBe true
                } finally {
                    compose.mainClock.autoAdvance = true
                }
                compose.waitUntil(30_000) { graph.configRepository.config.value.look == Look.Expressive }
                settle()
                flip.holdsPicture shouldBe false
                compose.focusedTag() shouldBe "option:Look:0"
            }

            for ((theme, key) in listOf(
                """{"reduced_motion": true}""" to "reduced_motion on",
                """{"animations": false}""" to "animations off",
            )) {
                test("with $key ok on Expressive switches the look as a cut, with no picture taken") {
                    show(theme)
                    repeat(12) { if (!compose.focusedTag().startsWith("option:Look:")) press(Key.DirectionDown) }
                    press(Key.DirectionRight)
                    compose.onRoot().captureToImage()

                    press(Key.DirectionCenter)
                    compose.waitUntil(30_000) { graph.configRepository.config.value.look == Look.Expressive }
                    settle()

                    flip.captures shouldBe 0
                    compose.focusedTag() shouldBe "option:Look:1"
                }
            }

            test("ok on the look the page already has takes no picture") {
                show()
                repeat(12) { if (!compose.focusedTag().startsWith("option:Look:")) press(Key.DirectionDown) }
                compose.focusedTag() shouldBe "option:Look:0"
                compose.onRoot().captureToImage()

                press(Key.DirectionCenter)

                flip.captures shouldBe 0
                flip.holdsPicture shouldBe false
            }

            test("the page is drawn in the stored look") {
                show()
                compose.roundness("settings:heading") shouldBe 0f

                runBlocking { graph.configRepository.set(Choices.Look, Look.Expressive) }
                settle()

                compose.roundness("settings:heading") shouldBe 100f
            }

            test("a look stored from elsewhere keeps focus on a chip far down the page, which moves down by the new row") {
                show()
                repeat(17) { press(Key.DirectionDown) }
                press(Key.DirectionRight)
                val tag = compose.focusedTag()
                tag shouldBe "option:ReducedMotion:1"
                val bounds = compose.onNode(isFocused()).getBoundsInRoot()
                pageFocus.clear()

                runBlocking { graph.configRepository.set(Choices.Look, Look.Expressive) }
                settle()

                compose.focusedTag() shouldBe tag
                val row = compose.onNodeWithTag("setting:ExpressiveTile").getUnclippedBoundsInRoot()
                val moved = compose.onNode(isFocused()).getBoundsInRoot()
                moved.left shouldBe bounds.left
                moved.top - bounds.top shouldBe row.bottom - row.top
                withClue("the page's focus events: $pageFocus") { pageFocus.map { it.hasFocus } shouldNotContain false }
            }
        }
    })
