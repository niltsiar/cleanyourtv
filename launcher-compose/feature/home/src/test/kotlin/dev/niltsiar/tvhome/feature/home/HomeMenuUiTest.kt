package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** Home on the real presenter: keys go in at the screen, the menus and modes come out of the state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeMenuUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val events = Channel<HomeEvent>(Channel.UNLIMITED)
    private val effects = mutableListOf<HomeEffect>()
    private val saved = mutableListOf<Pair<List<String>, Int>>()
    private val clicked = mutableListOf<String>()

    private fun showHome(config: TvConfig = SixColumns) {
        val sources =
            sources(
                saveOrder = { keys, docked ->
                    saved += keys to docked
                    true
                },
            )
        compose.setContent {
            TvTheme(glassKind = GlassKind.Flat) {
                val state = rememberUpdatedState(homePresenter(events.receiveAsFlow(), sources, config, { effects += it }, Locale.ENGLISH))
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = { events.trySend(HomeEvent.Focus(it)) },
                    onClick = { clicked += it },
                    topBar = config.topBarLayout(),
                    actions = actionsTo(events),
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
}

internal fun actionsTo(events: Channel<HomeEvent>) =
    HomeActions(
        onTileMenu = { key, held -> events.trySend(HomeEvent.TileMenu(key, held)) },
        onSettingsMenu = { events.trySend(HomeEvent.SettingsMenu) },
        onInputs = { events.trySend(HomeEvent.OpenInputs) },
        onInputPick = { events.trySend(HomeEvent.PickInput(it)) },
        onMenuPick = { events.trySend(HomeEvent.MenuPick(it)) },
        onBack = { events.trySend(HomeEvent.Back) },
        onToggleReorder = { events.trySend(HomeEvent.ToggleReorder) },
        onReorderMove = { events.trySend(HomeEvent.ReorderMove(it)) },
        onReorderOk = { events.trySend(HomeEvent.ReorderOk) },
    )
