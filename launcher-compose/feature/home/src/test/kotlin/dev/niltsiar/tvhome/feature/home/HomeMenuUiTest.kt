package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputsMenu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home on the real presenter: keys go in at the screen, the menus and modes come out of the state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeMenuUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val effects = mutableListOf<HomeEffect>()
    private val saved = mutableListOf<Pair<List<String>, Int>>()
    private val clicked = mutableListOf<String>()
    private val installed = MutableStateFlow(AppList(NineApps))
    private var homePresses by mutableIntStateOf(0)
    private var inputsRequests by mutableIntStateOf(0)

    private fun showHome(
        config: TvConfig = SixColumns,
        apps: List<AppEntry> = NineApps,
    ) {
        installed.value = AppList(apps)
        val sources =
            sources(
                apps = installed,
                inputs = flowOf(InputsMenu(available = true, rows = listOf(InputRow.Panel), panelOnly = true)),
                saveOrder = { keys, docked ->
                    saved += keys to docked
                    true
                },
            )
        compose.setContent {
            TvTheme {
                val viewModel = rememberHomeViewModel(sources, config, effects)
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = { clicked += it },
                    topBar = config.topBarLayout(),
                    actions = viewModel.actions,
                    homePresses = { homePresses },
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

    private fun holdOk(millis: Long = 600) {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun releaseOk() {
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun holding_ok_on_a_tile_opens_its_menu_and_back_returns_focus_to_the_tile() {
        showHome()

        holdOk()
        compose.onNodeWithTag("menu").assertExists()
        compose.onNodeWithTag("menu:ReplaceInDock").assertIsFocused()

        releaseOk()
        compose.onNodeWithTag("menu").assertExists()
        assertEquals(emptyList<String>(), clicked)

        back()
        compose.onNodeWithTag("menu").assertDoesNotExist()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        assertEquals(emptyList<String>(), clicked)
    }

    @Test
    fun the_repeats_and_the_release_of_a_hold_do_not_press_the_row_the_menu_opened_under_them() {
        showHome()

        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.onRoot().performKeyInput { advanceEventTime(560) }
        compose.waitForIdle()
        compose.onNodeWithTag("menu:ReplaceInDock").assertIsFocused()
        compose.onRoot().performKeyInput { advanceEventTime(200) }
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()

        compose.onNodeWithTag("menu").assertExists()
        compose.onNodeWithTag("reorder:hint").assertDoesNotExist()
        assertEquals(emptyList<String>(), clicked)
    }

    @Test
    fun a_short_press_opens_the_app_and_no_menu() {
        showHome()

        holdOk(millis = 100)
        releaseOk()

        compose.onNodeWithTag("menu").assertDoesNotExist()
        assertEquals(listOf("pkg.1"), clicked)
    }

    @Test
    fun the_menu_key_opens_the_menu_and_a_row_fires_on_its_own_press() {
        showHome()

        press(Key.Menu)
        compose.onNodeWithTag("menu").assertExists()

        press(Key.DirectionDown)
        compose.onNodeWithTag("menu:Move").assertIsFocused()
        press(Key.DirectionCenter)

        compose.onNodeWithTag("menu").assertDoesNotExist()
        compose.onNodeWithTag("reorder:hint").assertExists()
    }

    @Test
    fun without_tile_menu_the_menu_key_does_nothing_and_a_held_ok_asks_for_app_info() {
        showHome(SixColumns.copy(tileMenu = false))

        press(Key.Menu)
        compose.onNodeWithTag("menu").assertDoesNotExist()

        holdOk()
        releaseOk()

        compose.onNodeWithTag("menu").assertDoesNotExist()
        assertEquals(listOf<HomeEffect>(HomeEffect.OpenAppInfo("pkg.1")), effects)
        assertEquals(emptyList<String>(), clicked)
    }

    @Test
    fun a_grid_tile_carried_into_the_dock_keeps_focus_and_the_order_is_saved_when_the_mode_ends() {
        showHome()
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        holdOk()
        releaseOk()
        press(Key.DirectionDown)
        compose.onNodeWithTag("menu:Move").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        press(Key.DirectionUp)

        compose.onNode(hasTestTag("tile:pkg.7") and hasAnyAncestor(hasTestTag("dock"))).assertIsFocused()
        assertEquals(emptyList<Pair<List<String>, Int>>(), saved)

        press(Key.DirectionCenter)
        back()

        compose.onNodeWithTag("reorder:hint").assertDoesNotExist()
        assertEquals(listOf((listOf(7, 1, 2, 3, 4, 5, 6, 8, 9).map { "pkg.$it" }) to -1), saved)
    }

    @Test
    fun the_settings_button_opens_its_menu_on_a_held_ok() {
        showHome()
        press(Key.DirectionUp)
        repeat(2) { press(Key.DirectionRight) }

        holdOk()

        compose.onNodeWithTag("menu").assertExists()
        compose.onNodeWithTag("menu:EditDock").assertExists()
        compose.onNode(hasTestTag("menu:Reorder") and hasAnyAncestor(hasTestTag("menu"))).assertExists()
    }

    private fun boundsOf(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun a_tile_menu_opens_beside_its_tile_without_covering_the_top_bar() {
        showHome()
        val tile = boundsOf("tile:pkg.1")

        holdOk()

        val menu = boundsOf("menu")
        assertTrue(menu.left >= tile.right)
        assertTrue(menu.top >= topBarBottom())
    }

    @Test
    @Config(qualifiers = "w960dp-h360dp-xhdpi")
    fun a_tile_menu_with_no_room_below_or_above_its_tile_still_stays_under_the_top_bar() {
        showHome()

        holdOk()

        assertTrue(boundsOf("menu").top >= topBarBottom())
    }

    @Test
    fun the_reorder_hint_sits_clear_of_every_tile_and_inside_the_screen() {
        showHome(apps = (1..40).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") })

        press(Key.Menu)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        val hint = boundsOf("reorder:hint")
        val tiles =
            compose
                .onAllNodes(SemanticsMatcher("is a tile") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tile:") == true })
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
                .filter { it.width > 0f && it.height > 0f }
        assertTrue(tiles.isNotEmpty())
        assertEquals(emptyList<Rect>(), tiles.filter { it.overlaps(hint) })
        assertTrue(
            hint.bottom <= compose
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.bottom - screenMargin(),
        )
    }

    private fun topBarBottom() = with(compose.density) { (TopBarTop + TopBarHeight).toPx() }

    private fun screenMargin() = with(compose.density) { 20.dp.toPx() }

    /** The focused tile goes away, so focus comes back into Home from outside, as when the window regains it. */
    private fun removeTheFocusedTile() {
        installed.value = AppList(installed.value.apps.filterNot { it.key == "pkg.1" })
        compose.waitForIdle()
    }

    private fun assertFocusInTheDock() = compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("dock"))).assertIsFocused()

    private fun openSettingsMenu() {
        press(Key.DirectionUp)
        repeat(2) { press(Key.DirectionRight) }
        holdOk()
        releaseOk()
        compose.onNodeWithTag("menu").assertExists()
    }

    @Test
    fun focus_coming_back_into_home_goes_to_the_dock_after_the_home_key_closed_a_grid_tile_menu() {
        showHome()
        press(Key.DirectionDown)
        repeat(2) { press(Key.DirectionRight) }
        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
        holdOk()
        releaseOk()
        compose.onNodeWithTag("menu").assertExists()
        homePresses++
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

        removeTheFocusedTile()

        assertFocusInTheDock()
    }

    @Test
    fun focus_coming_back_into_home_goes_to_the_dock_after_the_home_key_closed_the_settings_menu() {
        showHome()
        openSettingsMenu()
        homePresses++
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

        removeTheFocusedTile()

        assertFocusInTheDock()
    }

    @Test
    fun focus_coming_back_into_home_goes_to_the_dock_after_the_inputs_key_closed_the_settings_menu_for_a_source_panel() {
        showHome()
        openSettingsMenu()
        inputsRequests++
        compose.waitForIdle()
        compose.onNodeWithTag("menu").assertDoesNotExist()
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

        removeTheFocusedTile()

        assertFocusInTheDock()
    }

    @Test
    fun focus_coming_back_into_home_goes_to_the_dock_after_the_inputs_key_closed_a_grid_tile_menu_for_a_source_panel() {
        showHome()
        press(Key.DirectionDown)
        repeat(2) { press(Key.DirectionRight) }
        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
        holdOk()
        releaseOk()
        compose.onNodeWithTag("menu").assertExists()
        inputsRequests++
        compose.waitForIdle()
        compose.onNodeWithTag("menu").assertDoesNotExist()
        press(Key.DirectionUp)
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

        removeTheFocusedTile()

        assertFocusInTheDock()
    }
}
