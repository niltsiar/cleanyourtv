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
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import java.util.Locale
import kotlin.math.abs

/** The icon-only top bar of Inputs, Wi-Fi and Settings as focus moves along it, read from the layout and the pixels. */
val TopBarMotionUiTest by testSuite {
    robolectricTestSuite<TopBarMotionUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class TopBarMotionUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { TopBarMotionUiTestFixture() } asContextForEach {
            test("a grown labelled pill keeps 13 dp to its neighbour") {
                showHome()
                press(Key.DirectionUp)
                compose.mainClock.advanceTimeBy(1_000)
                compose.onNodeWithContentDescription("Inputs").assertIsFocused()

                val gap = button("Home").left - button("Inputs").right
                withClue("gap $gap") { abs(gap.value - 13f) shouldBeLessThanOrEqualTo 0.5f }
            }

            test("the inputs arrow and the settings gear are still moving 450 ms after taking focus") {
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
                        withClue("$name is already still at 450 ms: $change of 255") { change shouldBeGreaterThan 8f }
                    }
                }
            }

            test("inputs wifi and settings each settle within 750 ms of taking focus") {
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
                    withClue("$name still moves after 750 ms: $change of 255") { change shouldBeLessThanOrEqualTo 2f }
                }
            }
        }
    })

private class TopBarMotionUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val config = SixColumns.copy(iconOnlyButtons = true)

    fun showHome() {
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

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
    }

    fun button(name: String): DpRect = compose.onNodeWithContentDescription(name).getBoundsInRoot()

    fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    fun worstChange(
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

    val focusOrder = listOf(Key.DirectionUp to "Inputs", Key.DirectionRight to "Home", Key.DirectionRight to "Settings")

    fun aroundButton(name: String): DpRect =
        button(name).let {
            DpRect(
                it.left - 8.dp,
                it.top - 8.dp,
                it.right + 8.dp,
                it.bottom + 8.dp,
            )
        }
}
