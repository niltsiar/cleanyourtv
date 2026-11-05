package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

private val Plain = Color.White
private val Green = Color(0xFF34C759)
private val Orange = Color(0xFFFF9F0A)
private val Red = Color(0xFFFF453A)
private val Wifi = NetworkState(NetworkLink.Wifi, ssid = "Home", validated = true)

class NetworkTintTest {
    private fun tint(
        network: NetworkState,
        colour: Boolean = true,
        warnings: Boolean = false,
    ) = networkTint(network, TopBarLayout(wifiStatusColor = colour, wifiStatusWarnings = warnings), Plain)

    @Test
    fun the_icon_keeps_its_colour_unless_wifi_status_color_is_on() {
        assertEquals(Plain, tint(Wifi, colour = false, warnings = true))
    }

    @Test
    fun online_is_green_and_offline_stays_plain_without_warnings() {
        assertEquals(Green, tint(Wifi.copy(validated = false)))
        assertEquals(Plain, tint(NetworkState()))
    }

    @Test
    fun warnings_make_a_captive_portal_orange_and_offline_red() {
        assertEquals(Orange, tint(Wifi.copy(validated = false), warnings = true))
        assertEquals(Green, tint(Wifi, warnings = true))
        assertEquals(Red, tint(NetworkState(), warnings = true))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class TopBarTest {
    @get:Rule
    val compose = createComposeRule()

    private var settingsOpened = 0
    private var networkOpened = 0
    private var wifiSettingsOpened = 0
    private var clicked: String? = null

    private fun show(
        config: TvConfig = SixColumns,
        network: NetworkState = Wifi,
        topBarApps: List<AppEntry> = emptyList(),
    ) {
        val state = homeUiState(homeApps(config = config), Evening, null, Locale.ENGLISH, network = network, topBarApps = topBarApps)
        compose.setContent {
            TvTheme {
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

    @Test
    fun the_settings_and_network_buttons_report_their_clicks() {
        show()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Home").performClick()

        assertEquals(1 to 1, settingsOpened to networkOpened)
    }

    private fun pressOkOnNetworkButton(holdMillis: Long) {
        compose.onNodeWithContentDescription("Home").requestFocus()
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(holdMillis)
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    @Test
    fun holding_ok_on_the_network_button_opens_wifi_settings_and_not_what_a_press_opens() {
        show()

        pressOkOnNetworkButton(holdMillis = 600)

        assertEquals(0 to 1, networkOpened to wifiSettingsOpened)
    }

    @Test
    fun a_short_ok_on_the_network_button_still_opens_what_it_opened_before() {
        show()

        pressOkOnNetworkButton(holdMillis = 100)

        assertEquals(1 to 0, networkOpened to wifiSettingsOpened)
    }

    @Test
    fun the_network_name_shows_beside_the_icon_unless_icon_only_buttons_is_on() {
        show()
        compose.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test
    fun icon_only_buttons_keeps_the_name_for_accessibility_only() {
        show(TvConfig(columns = 6, iconOnlyButtons = true))
        compose.onNodeWithContentDescription("Home").assertIsDisplayed()
        compose.onNodeWithText("Home").assertDoesNotExist()
    }

    @Test
    fun a_wifi_label_width_keeps_the_name_with_icon_only_buttons() {
        show(TvConfig(columns = 6, iconOnlyButtons = true, wifiLabelWidth = 120))
        compose.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test
    fun show_network_pill_false_and_show_inputs_pill_false_remove_those_buttons() {
        show(TvConfig(columns = 6, showNetworkPill = false, showInputsPill = false))

        compose.onNodeWithContentDescription("Home").assertDoesNotExist()
        compose.onNodeWithContentDescription("Inputs").assertDoesNotExist()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun a_top_bar_app_has_a_button_that_clicks_with_its_key() {
        show(topBarApps = listOf(AppEntry(key = "com.chat/.Main", packageName = "com.chat", label = "Chat")))

        compose.onNodeWithContentDescription("Chat").performClick()

        assertEquals("com.chat/.Main", clicked)
    }
}
