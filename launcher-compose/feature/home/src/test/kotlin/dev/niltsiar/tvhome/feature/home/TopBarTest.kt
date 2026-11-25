package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.config.parseTvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.util.Locale

private val Wifi = NetworkState(NetworkLink.Wifi, ssid = "Home", validated = true)
private val LongWifi = Wifi.copy(ssid = "guest network ".repeat(40))

val NetworkTintTest by testSuite {
    fun tint(
        network: NetworkState,
        colour: Boolean = true,
        warnings: Boolean = false,
    ) = networkStatus(network, TopBarLayout(wifiStatusColor = colour, wifiStatusWarnings = warnings))

    test("the icon keeps its colour unless wifi status color is on") {
        tint(Wifi, colour = false, warnings = true) shouldBe NetworkStatus.Plain
    }

    test("online is green and offline stays plain without warnings") {
        tint(Wifi.copy(validated = false)) shouldBe NetworkStatus.Online
        tint(NetworkState()) shouldBe NetworkStatus.Plain
    }

    test("warnings make a captive portal orange and offline red") {
        tint(Wifi.copy(validated = false), warnings = true) shouldBe NetworkStatus.NoInternet
        tint(Wifi, warnings = true) shouldBe NetworkStatus.Online
        tint(NetworkState(), warnings = true) shouldBe NetworkStatus.Offline
    }
}

val TopBarTest by testSuite {
    robolectricTestSuite<TopBarTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class TopBarTestContent :
    RobolectricTestSuiteContent({
        testFixture { TopBarTestFixture() } asContextForEach {
            for (theme in Look.entries) {
                val named = if (theme == Look.Expressive) ", in Expressive" else ""

                test("the settings and network buttons report their clicks$named") {
                    testLook = theme

                    show()

                    compose.onNodeWithContentDescription("Settings").performClick()
                    compose.onNodeWithContentDescription("Home").performClick()

                    (settingsOpened to networkOpened) shouldBe (1 to 1)
                }

                test("holding ok on the network button opens wifi settings and not what a press opens$named") {
                    testLook = theme

                    show()

                    pressOkOnNetworkButton(holdMillis = 600)

                    (networkOpened to wifiSettingsOpened) shouldBe (0 to 1)
                }

                test("a short ok on the network button still opens what it opened before$named") {
                    testLook = theme

                    show()

                    pressOkOnNetworkButton(holdMillis = 100)

                    (networkOpened to wifiSettingsOpened) shouldBe (1 to 0)
                }

                test("the network button is a round icon at rest and grows into its pill while focused$named") {
                    testLook = theme

                    show()
                    compose.onNodeWithText("Home").assertDoesNotExist()
                    compose.onNodeWithContentDescription("Home").assertIsDisplayed()
                    val rest = compose.onNodeWithContentDescription("Home").getBoundsInRoot()
                    rest.height shouldBe rest.width

                    compose.onNodeWithContentDescription("Home").requestFocus()
                    compose.mainClock.advanceTimeBy(1_000)

                    val grown = compose.onNodeWithContentDescription("Home").getBoundsInRoot().width
                    withClue("rest ${rest.width}, focused $grown") { grown shouldBeGreaterThan rest.width + 20.dp }
                }

                test("a wifi label width keeps the network name beside the icon at rest$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, wifiLabelWidth = 120))
                    compose.onNodeWithText("Home").assertIsDisplayed()
                }

                test("icon only buttons true leaves the network button an icon at rest$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, iconOnlyButtons = true))
                    compose.onNodeWithText("Home").assertDoesNotExist()
                }

                test("icon only buttons true with a wifi label width keeps the network name at rest$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, iconOnlyButtons = true, wifiLabelWidth = 120))
                    compose.onNodeWithText("Home").assertIsDisplayed()
                }

                test("icon only buttons false shows the network name at rest$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, iconOnlyButtons = false))
                    compose.onNodeWithText("Home").assertIsDisplayed()
                }

                test("icon only buttons false shows the settings label at rest$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, iconOnlyButtons = false))
                    compose.onNodeWithText("Settings").assertIsDisplayed()
                    compose.onNodeWithContentDescription("Settings").performClick()
                    settingsOpened shouldBe 1
                }

                test("launcher settings button names the settings button launcher settings and its tap is reported$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, launcherSettingsButton = true))
                    compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
                    compose.onNodeWithContentDescription("Launcher settings").performClick()
                    settingsOpened shouldBe 1
                }

                test("launcher settings button with icon only buttons false shows the launcher settings label at rest$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, iconOnlyButtons = false, launcherSettingsButton = true))
                    compose.onNodeWithText("Launcher settings").assertIsDisplayed()
                    compose.onNodeWithText("Settings").assertDoesNotExist()
                }

                test("the settings button is a round icon at rest with no theme$named") {
                    testLook = theme

                    show(TvConfig(columns = 6))
                    compose.onNodeWithText("Settings").assertDoesNotExist()
                    val rest = compose.onNodeWithContentDescription("Settings").getBoundsInRoot()
                    rest.height shouldBe rest.width
                }

                test("icon only buttons true leaves the settings button a round icon at rest$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, iconOnlyButtons = true))
                    compose.onNodeWithText("Settings").assertDoesNotExist()
                    val rest = compose.onNodeWithContentDescription("Settings").getBoundsInRoot()
                    rest.height shouldBe rest.width
                }

                test("the inputs button is a round icon at rest with no theme$named") {
                    testLook = theme

                    show(TvConfig(columns = 6), inputs = FourPorts)
                    compose.onNodeWithText("Inputs").assertDoesNotExist()
                    val rest = compose.onNodeWithContentDescription("Inputs").getBoundsInRoot()
                    rest.height shouldBe rest.width
                }

                test("icon only inputs true leaves the inputs button a round icon at rest$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, iconOnlyInputs = true), inputs = FourPorts)
                    compose.onNodeWithText("Inputs").assertDoesNotExist()
                    val rest = compose.onNodeWithContentDescription("Inputs").getBoundsInRoot()
                    rest.height shouldBe rest.width
                }

                test("icon only inputs false keeps the inputs label at rest$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, iconOnlyInputs = false), inputs = FourPorts)
                    compose.onNodeWithText("Inputs").assertIsDisplayed()
                    val rest = compose.onNodeWithContentDescription("Inputs").getBoundsInRoot()
                    rest.width shouldBeGreaterThan rest.height
                }

                test("a theme that leaves icon only inputs out keeps the inputs label at rest$named") {
                    testLook = theme

                    show(parseTvConfig("""{"columns": 6}""").config, inputs = FourPorts)
                    compose.onNodeWithText("Inputs").assertIsDisplayed()
                }

                test("icon only buttons true leaves the inputs label at rest$named") {
                    testLook = theme

                    show(parseTvConfig("""{"columns": 6, "icon_only_buttons": true}""").config, inputs = FourPorts)
                    compose.onNodeWithText("Inputs").assertIsDisplayed()
                }

                test("a theme that leaves icon only buttons out shows the network and settings labels at rest$named") {
                    testLook = theme

                    show(parseTvConfig("""{"columns": 6}""").config)
                    compose.onNodeWithText("Home").assertIsDisplayed()
                    compose.onNodeWithText("Settings").assertIsDisplayed()
                }

                test("icon only buttons false caps a long network name at 180 dp$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, iconOnlyButtons = false), network = LongWifi)
                    compose.onNodeWithContentDescription(LongWifi.ssid!!).getBoundsInRoot().width shouldBe 180.dp
                }

                test("icon only buttons false with a wifi label width caps the network name at that width$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, iconOnlyButtons = false, wifiLabelWidth = 120), network = LongWifi)
                    compose.onNodeWithContentDescription(LongWifi.ssid!!).getBoundsInRoot().width shouldBe 120.dp
                }

                test("show network pill false and show inputs pill false remove those buttons$named") {
                    testLook = theme

                    show(TvConfig(columns = 6, showNetworkPill = false, showInputsPill = false))

                    compose.onNodeWithContentDescription("Home").assertDoesNotExist()
                    compose.onNodeWithContentDescription("Inputs").assertDoesNotExist()
                    compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
                }

                test("a top bar app has a button that clicks with its key$named") {
                    testLook = theme

                    show(topBarApps = listOf(AppEntry(key = "com.chat/.Main", packageName = "com.chat", label = "Chat")))

                    compose.onNodeWithContentDescription("Chat").performClick()

                    clicked shouldBe "com.chat/.Main"
                }
            }
        }
    })

private class TopBarTestFixture : JUnit4RulesContext() {
    /** The look the test runs in. */
    var testLook = Look.Glass

    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    var settingsOpened = 0

    var networkOpened = 0

    var wifiSettingsOpened = 0

    var clicked: String? = null

    fun show(
        config: TvConfig = SixColumns,
        network: NetworkState = Wifi,
        topBarApps: List<AppEntry> = emptyList(),
        inputs: InputsMenu = InputsMenu(available = false),
    ) {
        val state =
            homeUiState(
                homeApps(config = config),
                Evening,
                null,
                Locale.ENGLISH,
                network = network,
                topBarApps = topBarApps,
                inputs = inputs,
            )
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme(look = testLook) {
                HomeScreen(
                    state = { state },
                    layout = config.homeLayout(),
                    onFocus = {},
                    onClick = { clicked = it },
                    topBar = config.topBarLayout(),
                    onOpenSettings = { settingsOpened++ },
                    onOpenNetwork = { networkOpened++ },
                    onOpenWifiSettings = { wifiSettingsOpened++ },
                )
            }
        }
        compose.waitForIdle()
    }

    fun pressOkOnNetworkButton(holdMillis: Long) {
        compose.onNodeWithContentDescription("Home").requestFocus()
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(holdMillis)
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
    }
}
