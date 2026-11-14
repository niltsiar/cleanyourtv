package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.width
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.InputArrival
import dev.niltsiar.tvhome.core.model.InputTarget
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs

private val Console = InputArrival("PlayStation 5 · HDMI 2", InputTarget.TvInput("tv/.Cec/HDMI2"), token = 1)

/** The Inputs button offering a device, on the real presenter and the real screen, with Home's keys and clock. */
val InputArrivalUiTest by testSuite {
    robolectricTestSuite<InputArrivalUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class InputArrivalUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { InputArrivalUiTestFixture() } asContextForEach {
            test("up from the last dock tile lands on the inputs button only while it offers a device") {
                showHome()
                repeat(5) { press(Key.DirectionRight) }
                compose.onNodeWithTag("tile:pkg.6").assertIsFocused()
                press(Key.DirectionUp)
                inputsButton().assertIsNotFocused()
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.6").assertIsFocused()

                arrive()
                press(Key.DirectionUp)

                inputsButton().assertIsFocused()
            }

            test("the offered pill is grown and does not take focus") {
                showHome()
                val round = inputsButton().getBoundsInRoot().width

                arrive()

                val grown = inputsButton().getBoundsInRoot().width
                withClue("grown $grown from $round") { grown.value shouldBeGreaterThan round.value * 3 }
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                inputsButton().assertIsNotFocused()
            }

            test("the pill goes back to a round button when the offer ends") {
                showHome()
                val round = inputsButton().getBoundsInRoot().width
                arrive()
                inputsButton().getBoundsInRoot().width.value shouldBeGreaterThan round.value * 3

                inputs.value = FourPorts
                compose.mainClock.advanceTimeBy(1_000)

                inputsButton().getBoundsInRoot().width.value shouldBe (round.value plusOrMinus 0.5f)
            }

            test("the rim lights once while the offer appears and is still afterwards") {
                showHome()
                compose.mainClock.autoAdvance = false
                inputs.value = FourPorts.copy(arrival = Console)
                compose.mainClock.advanceTimeBy(2_000)
                val samples = mutableListOf<Float>()
                repeat(40) {
                    compose.mainClock.advanceTimeBy(50)
                    samples += rim()
                }
                compose.mainClock.advanceTimeBy(2_000)
                val settled = rim()

                val peak = samples.max()
                withClue("the rim never lit: peak $peak, settled $settled") { peak shouldBeGreaterThan settled + 0.3f }
                samples.last() shouldBe (settled plusOrMinus 0.01f)
                val lit = samples.map { it > settled + 0.05f }
                val edges = lit.zipWithNext().count { (a, b) -> a != b }
                withClue("the rim lit more than once: $samples") { edges shouldBe 2 }
            }

            test("with animations off the pill appears without a pulse") {
                showHome(Motion(animations = false))
                compose.mainClock.autoAdvance = false
                inputs.value = FourPorts.copy(arrival = Console)
                compose.mainClock.advanceTimeBy(2_000)
                compose.mainClock.advanceTimeBy(34)
                val samples = mutableListOf<Float>()
                repeat(20) {
                    samples += rim()
                    compose.mainClock.advanceTimeBy(50)
                }

                inputsButton().getBoundsInRoot().width.value shouldBeGreaterThan 100f
                withClue("the rim moved: $samples") { (samples.all { abs(it - samples.first()) < 0.01f }).shouldBeTrue() }
            }

            test("ok on the focused button switches to the offered input without a list") {
                showHome()
                arrive()
                press(Key.DirectionUp)
                inputsButton().assertIsFocused()

                press(Key.DirectionCenter)

                compose.onNodeWithTag("menu").assertDoesNotExist()
                effects shouldBe listOf<HomeEffect>(HomeEffect.OpenInput(Console.target))
            }
        }
    })

private class InputArrivalUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val effects = mutableListOf<HomeEffect>()

    val inputs = MutableStateFlow(FourPorts)

    val config = SixColumns.copy(inputArrival = true)

    fun showHome(motion: Motion = Motion()) {
        val sources = sources(inputs = inputs)
        compose.setContent {
            TvTheme(motion = motion) {
                val viewModel = rememberHomeViewModel(sources, config, effects)
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = config.topBarLayout().copy(animated = motion.animated),
                    actions = viewModel.actions,
                )
            }
        }
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(1_000)
    }

    fun arrive() {
        inputs.value = FourPorts.copy(arrival = Console)
        compose.mainClock.advanceTimeBy(2_100)
    }

    fun inputsButton(): SemanticsNodeInteraction = compose.onNode(hasContentDescription("Inputs", substring = true))

    fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** The pixel on the top edge of the Inputs button, where its rim runs, at the middle of its width. */
    fun rim(): Float {
        val density = compose.activity.resources.displayMetrics.density
        val bounds = inputsButton().getBoundsInRoot()
        val pixel = screen()[((bounds.left.value + bounds.width.value / 2) * density).toInt(), (bounds.top.value * density).toInt() + 2]
        return pixel.red + pixel.green + pixel.blue
    }
}
