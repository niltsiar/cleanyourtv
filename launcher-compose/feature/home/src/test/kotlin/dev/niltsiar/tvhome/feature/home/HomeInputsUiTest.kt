package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputsMenu
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Inputs button on the real presenter: keys go in at the screen, the list comes out of the state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeInputsUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val effects = mutableListOf<HomeEffect>()
    private var inputsRequests by mutableIntStateOf(0)

    private fun showHome(inputs: InputsMenu = FourPorts) {
        val sources = sources(inputs = flowOf(inputs))
        compose.setContent {
            TvTheme(glassKind = GlassKind.Flat) {
                val viewModel = rememberHomeViewModel(sources, SixColumns, effects)
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = SixColumns.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = SixColumns.topBarLayout(),
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

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun openList() {
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Inputs").assertIsFocused()
        press(Key.DirectionCenter)
    }

    @Test
    fun ok_on_the_inputs_button_opens_the_list_on_its_first_row_and_back_returns_focus_to_the_button() {
        showHome()

        openList()
        compose.onNodeWithTag("menu").assertExists()
        compose.onNodeWithTag("input:0").assertIsFocused()

        back()

        compose.onNodeWithTag("menu").assertDoesNotExist()
        compose.onNodeWithContentDescription("Inputs").assertIsFocused()
    }

    @Test
    fun the_rows_show_the_cec_device_the_connection_and_live_tv_last() {
        showHome()

        openList()

        compose.onNodeWithText("HDMI 1").assertExists()
        compose.onNodeWithText("Apple TV · HDMI 2").assertExists()
        compose.onNodeWithText("Live TV").assertExists()
        assertEquals(2, compose.onAllNodesWithText("Connected").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("No device").fetchSemanticsNodes().size)
        repeat(4) { press(Key.DirectionDown) }
        compose.onNodeWithTag("input:5").assertIsFocused()
    }

    @Test
    fun ok_on_a_row_closes_the_list_hands_the_input_to_the_host_and_focus_returns_to_the_button() {
        showHome()
        openList()
        press(Key.DirectionDown)

        press(Key.DirectionCenter)

        compose.onNodeWithTag("menu").assertDoesNotExist()
        assertEquals(listOf<HomeEffect>(HomeEffect.OpenInput(InputTarget.TvInput("tv/.Cec/HDMI2"))), effects)
        compose.onNodeWithContentDescription("Inputs").assertIsFocused()
    }

    @Test
    fun more_inputs_shows_the_folded_rows_and_moves_focus_to_the_first_of_them() {
        showHome(
            InputsMenu(
                available = true,
                rows =
                    listOf(
                        port(1, InputConnection.Connected),
                        port(2, InputConnection.Disconnected, folded = true),
                        InputRow.Separator(folded = true),
                        InputRow.Port("AV 1", InputIcon.Av, InputConnection.Unknown, InputTarget.TvInput("tv/.Av/AV1"), folded = true),
                        InputRow.More,
                    ),
            ),
        )
        openList()
        compose.onNodeWithText("HDMI 2").assertDoesNotExist()
        compose.onNodeWithText("AV 1").assertDoesNotExist()

        press(Key.DirectionDown)
        compose.onNodeWithTag("input:more").assertIsFocused()
        press(Key.DirectionCenter)

        compose.onNodeWithText("More inputs").assertDoesNotExist()
        compose.onNodeWithText("AV 1").assertExists()
        compose.onNodeWithTag("input:1").assertIsFocused()
    }

    @Test
    fun the_list_is_at_least_150_dp_wide() {
        showHome(InputsMenu(available = true, rows = listOf(port(1))))

        openList()

        val width = compose.onNodeWithTag("menu").getBoundsInRoot().width
        assertTrue("$width", width >= 150.dp)
    }

    @Test
    fun a_tv_with_nothing_to_list_has_no_inputs_button() {
        showHome(InputsMenu(available = false))

        compose.onNodeWithContentDescription("Inputs").assertDoesNotExist()
    }

    @Test
    fun a_system_request_opens_the_list_once_the_button_is_there() {
        showHome()

        inputsRequests++
        compose.waitForIdle()

        compose.onNodeWithTag("menu").assertExists()
        compose.onNodeWithTag("input:0").assertIsFocused()
    }
}
