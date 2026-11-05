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

    private fun assertSame(
        settled: PixelMap,
        later: PixelMap,
        area: DpRect,
        name: String,
    ) {
        val density = compose.activity.resources.displayMetrics.density
        var worst = 0f
        for (y in (area.top.value * density).toInt() until (area.bottom.value * density).toInt()) {
            for (x in (area.left.value * density).toInt() until (area.right.value * density).toInt()) {
                val a = settled[x, y]
                val b = later[x, y]
                worst = maxOf(worst, abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
            }
        }
        assertTrue("$name still moves after 450 ms: ${Math.round(worst * 255)} of 255", worst * 255 <= 2f)
    }

    @Test
    fun inputs_wifi_and_settings_each_settle_within_450_ms_of_taking_focus() {
        showHome()
        compose.mainClock.autoAdvance = false
        for ((key, name) in listOf(Key.DirectionUp to "Inputs", Key.DirectionRight to "Home", Key.DirectionRight to "Settings")) {
            press(key)
            compose.mainClock.advanceTimeBy(450 + 34)
            compose.onNodeWithContentDescription(name).assertIsFocused()
            val area = button(name).let { DpRect(it.left - 8.dp, it.top - 8.dp, it.right + 8.dp, it.bottom + 8.dp) }
            val settled = screen()
            compose.mainClock.advanceTimeBy(2_000)
            assertSame(settled, screen(), area, name)
        }
    }
}
