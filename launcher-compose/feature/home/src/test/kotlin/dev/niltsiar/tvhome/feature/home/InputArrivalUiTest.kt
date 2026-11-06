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
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.InputArrival
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputsMenu
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

private val Console = InputArrival("PlayStation 5 · HDMI 2", InputTarget.TvInput("tv/.Cec/HDMI2"), token = 1)

/** The Inputs button offering a device, on the real presenter and the real screen, with Home's keys and clock. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class InputArrivalUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val effects = mutableListOf<HomeEffect>()
    private val inputs = MutableStateFlow(FourPorts)
    private val config = SixColumns.copy(inputArrival = true)

    private fun showHome(motion: Motion = Motion()) {
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

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun arrive() {
        inputs.value = FourPorts.copy(arrival = Console)
        compose.mainClock.advanceTimeBy(2_100)
    }

    private fun inputsButton(): SemanticsNodeInteraction = compose.onNode(hasContentDescription("Inputs", substring = true))

    private fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** The pixel on the top edge of the Inputs button, where its rim runs, at the middle of its width. */
    private fun rim(): Float {
        val density = compose.activity.resources.displayMetrics.density
        val bounds = inputsButton().getBoundsInRoot()
        val pixel = screen()[((bounds.left.value + bounds.width.value / 2) * density).toInt(), (bounds.top.value * density).toInt() + 2]
        return pixel.red + pixel.green + pixel.blue
    }

    @Test
    fun up_from_the_last_dock_tile_lands_on_the_inputs_button_only_while_it_offers_a_device() {
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

    @Test
    fun the_offered_pill_is_grown_and_does_not_take_focus() {
        showHome()
        val round = inputsButton().getBoundsInRoot().width

        arrive()

        val grown = inputsButton().getBoundsInRoot().width
        assertTrue("grown $grown from $round", grown.value > round.value * 3)
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        inputsButton().assertIsNotFocused()
    }

    @Test
    fun the_pill_goes_back_to_a_round_button_when_the_offer_ends() {
        showHome()
        val round = inputsButton().getBoundsInRoot().width
        arrive()
        assertTrue(inputsButton().getBoundsInRoot().width.value > round.value * 3)

        inputs.value = FourPorts
        compose.mainClock.advanceTimeBy(1_000)

        assertEquals(round.value, inputsButton().getBoundsInRoot().width.value, 0.5f)
    }

    @Test
    fun the_rim_lights_once_while_the_offer_appears_and_is_still_afterwards() {
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
        assertTrue("the rim never lit: peak $peak, settled $settled", peak > settled + 0.3f)
        assertEquals(settled, samples.last(), 0.01f)
        val lit = samples.map { it > settled + 0.05f }
        val edges = lit.zipWithNext().count { (a, b) -> a != b }
        assertEquals("the rim lit more than once: $samples", 2, edges)
    }

    @Test
    fun with_animations_off_the_pill_appears_without_a_pulse() {
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

        assertTrue(inputsButton().getBoundsInRoot().width.value > 100f)
        assertTrue("the rim moved: $samples", samples.all { abs(it - samples.first()) < 0.01f })
    }

    @Test
    fun ok_on_the_focused_button_switches_to_the_offered_input_without_a_list() {
        showHome()
        arrive()
        press(Key.DirectionUp)
        inputsButton().assertIsFocused()

        press(Key.DirectionCenter)

        compose.onNodeWithTag("menu").assertDoesNotExist()
        assertEquals(listOf<HomeEffect>(HomeEffect.OpenInput(Console.target)), effects)
    }
}
