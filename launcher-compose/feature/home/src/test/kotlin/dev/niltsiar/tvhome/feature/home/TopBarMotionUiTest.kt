package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.math.abs

/** The icon-only top bar of Inputs, Wi-Fi and Settings as focus moves along it, read from the layout and the pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class TopBarMotionUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val config = SixColumns.copy(iconOnlyButtons = true)

    private fun showHome() {
        val network = NetworkState(NetworkLink.Wifi, ssid = "Home", validated = true)
        val state = homeUiState(homeApps(config = config), Evening, null, Locale.ENGLISH, network = network, inputs = FourPorts)
        compose.setContent {
            TvTheme {
                HomeScreen(
                    state = { state },
                    layout = config.homeLayout(),
                    onFocus = {},
                    onClick = {},
                    topBar = config.topBarLayout(),
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
    }

    private fun button(name: String): DpRect = compose.onNodeWithContentDescription(name).getBoundsInRoot()

    @Test
    fun a_grown_labelled_pill_keeps_13_dp_to_its_neighbour() {
        showHome()
        press(Key.DirectionUp)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("Inputs").assertIsFocused()

        val gap = button("Home").left - button("Inputs").right
        assertTrue("gap $gap", abs(gap.value - 13f) <= 0.5f)
    }

    private fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    private fun worstChange(
        before: PixelMap,
        after: PixelMap,
        area: DpRect,
    ): Float {
        val density = compose.activity.resources.displayMetrics.density
        var worst = 0f
        for (y in (area.top.value * density).toInt() until (area.bottom.value * density).toInt()) {
            for (x in (area.left.value * density).toInt() until (area.right.value * density).toInt()) {
                val a = before[x, y]
                val b = after[x, y]
                worst = maxOf(worst, abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
            }
        }
        return Math.round(worst * 255).toFloat()
    }

    private val focusOrder = listOf(Key.DirectionUp to "Inputs", Key.DirectionRight to "Home", Key.DirectionRight to "Settings")

    private fun aroundButton(name: String): DpRect =
        button(name).let {
            DpRect(
                it.left - 8.dp,
                it.top - 8.dp,
                it.right + 8.dp,
                it.bottom + 8.dp,
            )
        }

    @Test
    fun the_inputs_arrow_and_the_settings_gear_are_still_moving_450_ms_after_taking_focus() {
        showHome()
        compose.mainClock.autoAdvance = false
        for ((key, name) in focusOrder) {
            press(key)
            compose.mainClock.advanceTimeBy(450 + 34)
            val early = screen()
            val area = aroundButton(name)
            compose.mainClock.advanceTimeBy(2_000)
            // The Wi-Fi arcs have too little contrast against the focused fill to read from pixels.
            if (name != "Home") {
                val change = worstChange(early, screen(), area)
                assertTrue("$name is already still at 450 ms: $change of 255", change > 8f)
            }
        }
    }

    @Test
    fun inputs_wifi_and_settings_each_settle_within_750_ms_of_taking_focus() {
        showHome()
        compose.mainClock.autoAdvance = false
        for ((key, name) in focusOrder) {
            press(key)
            compose.mainClock.advanceTimeBy(750 + 34)
            compose.onNodeWithContentDescription(name).assertIsFocused()
            val area = aroundButton(name)
            val settled = screen()
            compose.mainClock.advanceTimeBy(2_000)
            val change = worstChange(settled, screen(), area)
            assertTrue("$name still moves after 750 ms: $change of 255", change <= 2f)
        }
    }
}
