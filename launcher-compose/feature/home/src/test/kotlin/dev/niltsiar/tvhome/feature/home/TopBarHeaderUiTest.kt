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
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** With focus on the top bar the band over the grid names the focused button, and says one line about what it stands for. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class TopBarHeaderUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val cinematic = SixColumns.copy(cinematicHome = true, appLibrary = true, libraryButton = true)
    private var inputsRequests by mutableIntStateOf(0)

    private fun showHome(
        inputs: InputsMenu = FourPorts,
        network: NetworkState = NetworkState(NetworkLink.Wifi, ssid = "Living room", validated = true),
        config: TvConfig = cinematic,
    ) {
        val sources = sources(inputs = flowOf(inputs), network = flowOf(network), libraryCount = flowOf(23))
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

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun textOf(tag: String): String =
        compose
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString("")
            .orEmpty()

    private fun titleSize(): TextUnit {
        val results = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithTag("shelf:title").fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results
            .single()
            .layoutInput.style.fontSize
    }

    private fun ports(vararg connections: InputConnection) =
        InputsMenu(available = true, rows = connections.mapIndexed { i, c -> port(i + 1, c) })

    @Test
    fun on_the_inputs_button_the_band_names_the_inputs_and_the_device_on_the_only_connected_port() {
        showHome(ports(InputConnection.Disconnected, InputConnection.Connected, InputConnection.Unknown))

        press(Key.DirectionUp)

        compose.onNodeWithContentDescription("Inputs").assertIsFocused()
        assertEquals("Inputs", textOf("shelf:title"))
        assertEquals("HDMI 2 · Connected", textOf("shelf:title-meta"))
    }

    @Test
    fun on_the_inputs_button_the_band_counts_the_connected_ports_when_there_are_several() {
        showHome(FourPorts)

        press(Key.DirectionUp)

        assertEquals("2 inputs connected", textOf("shelf:title-meta"))
    }

    @Test
    fun on_the_inputs_button_the_band_says_when_no_port_has_a_device() {
        showHome(ports(InputConnection.Disconnected, InputConnection.Unknown))

        press(Key.DirectionUp)

        assertEquals("No input connected", textOf("shelf:title-meta"))
    }

    @Test
    fun on_the_network_button_the_band_says_connected_and_never_the_network_name() {
        showHome()

        press(Key.DirectionUp)
        press(Key.DirectionRight)

        assertEquals("Wi-Fi", textOf("shelf:title"))
        assertEquals("Connected", textOf("shelf:title-meta"))
        assertFalse((textOf("shelf:title") + textOf("shelf:title-meta")).contains("Living room"))
    }

    @Test
    fun on_the_network_button_without_a_link_the_band_says_offline() {
        showHome(network = NetworkState(NetworkLink.Offline))

        press(Key.DirectionUp)
        press(Key.DirectionRight)

        assertEquals("Wi-Fi", textOf("shelf:title"))
        assertEquals("Offline", textOf("shelf:title-meta"))
    }

    @Test
    fun on_the_settings_button_the_band_names_home_and_system() {
        showHome()

        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        compose.onNodeWithContentDescription("Settings").assertIsFocused()
        assertEquals("Settings", textOf("shelf:title"))
        assertEquals("Home and system", textOf("shelf:title-meta"))
    }

    @Test
    fun with_launcher_settings_button_the_band_on_the_settings_button_names_launcher_settings() {
        showHome(config = cinematic.copy(launcherSettingsButton = true))

        press(Key.DirectionUp)
        repeat(3) { press(Key.DirectionRight) }

        compose.onNodeWithContentDescription("Launcher settings").assertIsFocused()
        assertEquals("Launcher settings", textOf("shelf:title"))
        assertEquals("Layout, clock and motion", textOf("shelf:title-meta"))
    }

    @Test
    fun with_launcher_settings_button_the_band_keeps_naming_launcher_settings_while_the_settings_menu_is_open() {
        showHome(config = cinematic.copy(launcherSettingsButton = true))
        press(Key.DirectionUp)
        repeat(3) { press(Key.DirectionRight) }

        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()

        compose.onNodeWithTag("menu").assertExists()
        assertEquals("Launcher settings", textOf("shelf:title"))
        assertEquals("Layout, clock and motion", textOf("shelf:title-meta"))
    }

    @Test
    fun on_any_other_button_the_band_shows_its_name_and_no_second_line() {
        showHome()

        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        assertEquals("App library", textOf("shelf:title"))
        assertEquals(" ", textOf("shelf:title-meta"))
    }

    @Test
    fun the_band_keeps_its_height_and_goes_back_to_the_dock_app_when_focus_returns() {
        showHome()
        val bottom = compose.onNodeWithTag("shelf:name").getBoundsInRoot().bottom

        press(Key.DirectionUp)
        assertEquals(bottom, compose.onNodeWithTag("shelf:title").getBoundsInRoot().bottom)

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        assertEquals("App 1", textOf("shelf:name"))
    }

    @Test
    fun the_inputs_list_the_remote_opens_is_named_in_the_band_whatever_button_had_focus_last() {
        showHome()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        assertEquals("Wi-Fi", textOf("shelf:title"))

        inputsRequests++
        compose.waitForIdle()

        compose.onNodeWithTag("menu").assertExists()
        assertEquals("Inputs", textOf("shelf:title"))
        assertEquals("2 inputs connected", textOf("shelf:title-meta"))
    }

    @Test
    fun a_button_in_the_top_bar_gets_a_title_of_32_sp_where_a_tile_gets_48() {
        showHome()
        assertEquals(48.sp, titleSize())

        press(Key.DirectionUp)
        assertEquals(32.sp, titleSize())
        press(Key.DirectionRight)
        assertEquals(32.sp, titleSize())

        press(Key.DirectionDown)
        assertEquals(48.sp, titleSize())
    }

    @Test
    fun the_library_pill_gets_the_chrome_title_size_too() {
        showHome()

        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        compose.onNodeWithTag("library:pill").assertIsFocused()

        assertEquals("App library", textOf("shelf:title"))
        assertEquals(32.sp, titleSize())
    }
}
