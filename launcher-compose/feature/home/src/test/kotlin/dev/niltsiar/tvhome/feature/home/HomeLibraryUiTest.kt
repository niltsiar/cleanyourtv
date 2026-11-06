package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.width
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.InputsMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The App library pill and the top-bar button: how focus reaches them and what OK does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeLibraryUiTest {
    @get:Rule
    val compose = createComposeRule()

    private var opened = 0
    private var focusedKey by mutableStateOf<String?>(null)
    private val toggles = mutableListOf<String>()

    private fun showHome(
        config: TvConfig = SixColumns.copy(appLibrary = true),
        reordering: Boolean = false,
        inputs: InputsMenu = InputsMenu(),
    ) {
        val apps = homeApps(config = config)
        val reorder = if (reordering) ReorderView(carried = null, dimmed = emptySet(), hint = ReorderHint.Reorder) else null
        compose.setContent {
            TvTheme {
                HomeScreen(
                    state = { homeUiState(apps, Evening, focusedKey, Locale.ENGLISH, reorder = reorder, inputs = inputs) },
                    layout = config.homeLayout(),
                    onFocus = { focusedKey = it },
                    onClick = {},
                    topBar = config.topBarLayout(),
                    actions = HomeMenuActions(onToggleReorder = { toggles += "toggle" }),
                    onOpenLibrary = { opened++ },
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

    @Test
    fun right_past_the_last_tile_focuses_the_pill_and_ok_opens_the_library() {
        showHome()
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("library:pill").assertIsFocused()

        press(Key.DirectionCenter)
        assertEquals(1, opened)
    }

    @Test
    fun the_pill_under_the_grid_starts_where_the_grids_first_column_does() {
        showHome()
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        compose.onNodeWithTag("library:pill").assertIsFocused()

        // A focused pill draws 8% wider about its centre; its left edge before that is the grid's.
        val pill = compose.onNodeWithTag("library:pill").getBoundsInRoot()
        val firstColumn = compose.onNodeWithTag("tile:pkg.7").getBoundsInRoot()
        assertEquals(firstColumn.left.value, pill.left.value + pill.width.value * (1f - 1f / 1.08f) / 2f, 1f)
    }

    @Test
    fun down_from_the_last_row_focuses_the_pill_and_up_returns_to_the_tile() {
        showHome()
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.8").assertIsFocused()

        press(Key.DirectionDown)
        compose.onNodeWithTag("library:pill").assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        compose.onNodeWithTag("library:pill").assertIsFocused()

        press(Key.DirectionUp)
        compose.onNodeWithTag("tile:pkg.8").assertIsFocused()
    }

    @Test
    fun down_from_a_dock_tile_over_the_short_last_row_goes_to_that_row_not_to_the_pill() {
        showHome()
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.5").assertIsFocused()

        press(Key.DirectionDown)

        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
    }

    @Test
    fun without_the_app_library_there_is_no_pill_and_down_from_the_last_row_stays() {
        showHome(SixColumns)
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        compose.onNodeWithTag("library:pill").assertDoesNotExist()
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
    }

    @Test
    fun a_top_bar_button_with_library_button_opens_the_library() {
        showHome(SixColumns.copy(appLibrary = true, libraryButton = true, showLibraryPill = false), inputs = InputsMenu(available = true))
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("App library").assertIsFocused()

        press(Key.DirectionCenter)

        assertEquals(1, opened)
        compose.onNodeWithTag("library:pill").assertDoesNotExist()
    }

    @Test
    fun ok_on_the_pill_in_reorder_mode_ends_the_mode_and_opens_the_library() {
        showHome(reordering = true)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("library:pill").assertIsFocused()

        press(Key.DirectionCenter)

        assertEquals(listOf("toggle"), toggles)
        assertEquals(1, opened)
    }

    private var libraryOpen by mutableStateOf(false)

    private fun showSingleRowHome() {
        val config = SixColumns.copy(singleRow = true, appLibrary = true)
        compose.setContent {
            TvTheme {
                val viewModel = rememberHomeViewModel(sources(), config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = config.topBarLayout(),
                    actions = viewModel.actions,
                    libraryOpen = { libraryOpen },
                    onOpenLibrary = { libraryOpen = true },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    @Test
    fun in_a_single_row_the_pill_is_the_row_s_last_item_that_right_from_the_last_tile_reaches_and_left_leaves() {
        showSingleRowHome()
        repeat(8) { press(Key.DirectionRight) }
        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()

        press(Key.DirectionRight)

        compose.onNodeWithTag("library:pill").assertIsFocused()
        val pill = compose.onNodeWithTag("library:pill").getBoundsInRoot()
        val tile = compose.onNodeWithTag("tile:pkg.9").getBoundsInRoot()
        assertTrue("pill $pill, tile $tile", pill.top < tile.bottom)

        press(Key.DirectionLeft)

        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
    }

    @Test
    fun in_a_single_row_closing_the_library_scrolls_the_row_to_the_pill_and_focuses_it() {
        showSingleRowHome()
        repeat(9) { press(Key.DirectionRight) }
        compose.onNodeWithTag("library:pill").assertIsFocused()
        press(Key.DirectionCenter)
        assertTrue(libraryOpen)
        compose.onNodeWithTag("dock").onChild().performScrollToIndex(0)
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").requestFocus()
        compose.waitForIdle()
        compose.onNodeWithTag("library:pill").assertIsNotDisplayed()

        libraryOpen = false
        compose.waitForIdle()

        compose.onNodeWithTag("library:pill").assertIsFocused().assertIsDisplayed()
    }
}
