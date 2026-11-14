package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf

/** With focus on the top bar the band over the grid names the focused button, and says one line about what it stands for. */
val TopBarHeaderUiTest by testSuite {
    robolectricTestSuite<TopBarHeaderUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class TopBarHeaderUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { TopBarHeaderUiTestFixture() } asContextForEach {
            test("on the inputs button the band names the inputs and the device on the only connected port") {
                showHome(ports(InputConnection.Disconnected, InputConnection.Connected, InputConnection.Unknown))

                press(Key.DirectionUp)

                compose.onNodeWithContentDescription("Inputs").assertIsFocused()
                textOf("shelf:title") shouldBe "Inputs"
                textOf("shelf:title-meta") shouldBe "HDMI 2 · Connected"
            }

            test("on the inputs button the band counts the connected ports when there are several") {
                showHome(FourPorts)

                press(Key.DirectionUp)

                textOf("shelf:title-meta") shouldBe "2 inputs connected"
            }

            test("on the inputs button the band says when no port has a device") {
                showHome(ports(InputConnection.Disconnected, InputConnection.Unknown))

                press(Key.DirectionUp)

                textOf("shelf:title-meta") shouldBe "No input connected"
            }

            test("on the network button the band says connected and never the network name") {
                showHome()

                press(Key.DirectionUp)
                press(Key.DirectionRight)

                textOf("shelf:title") shouldBe "Wi-Fi"
                textOf("shelf:title-meta") shouldBe "Connected"
                (textOf("shelf:title") + textOf("shelf:title-meta")).contains("Living room").shouldBeFalse()
            }

            test("on the network button without a link the band says offline") {
                showHome(network = NetworkState(NetworkLink.Offline))

                press(Key.DirectionUp)
                press(Key.DirectionRight)

                textOf("shelf:title") shouldBe "Wi-Fi"
                textOf("shelf:title-meta") shouldBe "Offline"
            }

            test("on the settings button the band names home and system") {
                showHome()

                press(Key.DirectionUp)
                press(Key.DirectionRight)
                press(Key.DirectionRight)
                press(Key.DirectionRight)

                compose.onNodeWithContentDescription("Settings").assertIsFocused()
                textOf("shelf:title") shouldBe "Settings"
                textOf("shelf:title-meta") shouldBe "Home and system"
            }

            test("with launcher settings button the band on the settings button names launcher settings") {
                showHome(config = cinematic.copy(launcherSettingsButton = true))

                press(Key.DirectionUp)
                repeat(3) { press(Key.DirectionRight) }

                compose.onNodeWithContentDescription("Launcher settings").assertIsFocused()
                textOf("shelf:title") shouldBe "Launcher settings"
                textOf("shelf:title-meta") shouldBe "Layout, clock and motion"
            }

            test("with launcher settings button the band keeps naming launcher settings while the settings menu is open") {
                showHome(config = cinematic.copy(launcherSettingsButton = true))
                press(Key.DirectionUp)
                repeat(3) { press(Key.DirectionRight) }

                compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                compose.mainClock.advanceTimeBy(600)
                compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
                compose.waitForIdle()

                compose.onNodeWithTag("menu").assertExists()
                textOf("shelf:title") shouldBe "Launcher settings"
                textOf("shelf:title-meta") shouldBe "Layout, clock and motion"
            }

            test("on any other button the band shows its name and no second line") {
                showHome()

                press(Key.DirectionUp)
                press(Key.DirectionRight)
                press(Key.DirectionRight)

                textOf("shelf:title") shouldBe "App library"
                textOf("shelf:title-meta") shouldBe " "
            }

            test("the band keeps its height and goes back to the dock app when focus returns") {
                showHome()
                val bottom = compose.onNodeWithTag("shelf:name").getBoundsInRoot().bottom

                press(Key.DirectionUp)
                compose.onNodeWithTag("shelf:title").getBoundsInRoot().bottom shouldBe bottom

                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                textOf("shelf:name") shouldBe "App 1"
            }

            test("the inputs list the remote opens is named in the band whatever button had focus last") {
                showHome()
                press(Key.DirectionUp)
                press(Key.DirectionRight)
                textOf("shelf:title") shouldBe "Wi-Fi"

                inputsRequests++
                compose.waitForIdle()

                compose.onNodeWithTag("menu").assertExists()
                textOf("shelf:title") shouldBe "Inputs"
                textOf("shelf:title-meta") shouldBe "2 inputs connected"
            }

            test("a button in the top bar gets a title of 32 sp where a tile gets 48") {
                showHome()
                titleSize() shouldBe 48.sp

                press(Key.DirectionUp)
                titleSize() shouldBe 32.sp
                press(Key.DirectionRight)
                titleSize() shouldBe 32.sp

                press(Key.DirectionDown)
                titleSize() shouldBe 48.sp
            }

            test("the library pill gets the chrome title size too") {
                showHome()

                press(Key.DirectionDown)
                press(Key.DirectionRight)
                press(Key.DirectionDown)
                compose.onNodeWithTag("library:pill").assertIsFocused()

                textOf("shelf:title") shouldBe "App library"
                titleSize() shouldBe 32.sp
            }
        }
    })

private class TopBarHeaderUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val cinematic = SixColumns.copy(cinematicHome = true, appLibrary = true, libraryButton = true)

    var inputsRequests by mutableIntStateOf(0)

    fun showHome(
        inputs: InputsMenu = FourPorts,
        network: NetworkState = NetworkState(NetworkLink.Wifi, ssid = "Living room", validated = true),
        config: TvConfig = cinematic,
    ) {
        val sources = sources(inputs = flowOf(inputs), network = flowOf(network), libraryCount = flowOf(23))
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme {
                val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = config.topBarLayout(),
                    shelfLayout = config.shelfLayout().copy(scrimAlpha = 0f),
                    actions = viewModel.actions,
                    inputsRequests = { inputsRequests },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    fun textOf(tag: String): String =
        compose
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString("")
            .orEmpty()

    fun titleSize(): TextUnit {
        val results = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithTag("shelf:title").fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results
            .single()
            .layoutInput.style.fontSize
    }

    fun ports(vararg connections: InputConnection) = InputsMenu(available = true, rows = connections.mapIndexed { i, c -> port(i + 1, c) })
}
