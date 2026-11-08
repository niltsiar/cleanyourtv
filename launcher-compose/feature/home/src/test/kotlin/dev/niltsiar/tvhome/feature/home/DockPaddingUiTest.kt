package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The padding between the dock panel's walls and its tiles, measured on tiles that do not have focus (a focused one is drawn
 * larger). The grid is 880 dp wide, so a tile of the 6-column dock is 126.67 dp wide and one of the 4-column dock 202 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class DockPaddingUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // Enough apps below the dock for the grid to scroll, which is what a pivot that is off would do.
    private val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }
    private var look by mutableStateOf(DockLook())

    private fun showHome(
        config: TvConfig = SixColumns,
        docked: Int = 0,
    ) {
        val sources = sources(apps = flowOf(AppList(apps, docked)))
        look = config.dockLook()
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
                    dockLook = look,
                    actions = viewModel.actions,
                )
            }
        }
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        settle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun dock(): DpRect = compose.onNodeWithTag("dock").getBoundsInRoot()

    private fun dockTile(n: Int): DpRect =
        compose.onNode(hasTestTag("tile:pkg.$n") and hasAnyAncestor(hasTestTag("dock"))).getBoundsInRoot()

    private data class Sides(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    /** The room between the panel's walls and the tiles, with focus on the second tile so the first and the last are at rest. */
    private fun padding(last: Int): Sides {
        press(Key.DirectionRight)
        val panel = dock()
        val first = dockTile(1)
        val end = dockTile(last)
        return Sides(
            left = (first.left - panel.left).value,
            top = (first.top - panel.top).value,
            right = (panel.right - end.right).value,
            bottom = (panel.bottom - first.bottom).value,
        )
    }

    private fun assertSides(
        expected: Sides,
        actual: Sides,
    ) {
        assertEquals("left", expected.left, actual.left, 0.6f)
        assertEquals("top", expected.top, actual.top, 0.6f)
        assertEquals("right", expected.right, actual.right, 0.6f)
        assertEquals("bottom", expected.bottom, actual.bottom, 0.6f)
    }

    /** A focused end tile's ring, with its 12 dp of clearance: the tile's growth, the ring's 9 dp scaled, and the clearance. */
    private fun ringRoom(tileWidth: Float) = tileWidth * 0.04f + 9f * 1.08f + 12f

    @Test
    fun a_dock_pads_its_sides_by_the_rings_room_and_its_top_and_bottom_by_a_gap_unless_the_theme_asks_for_even_padding() {
        showHome(SixColumns)

        val room = ringRoom(126.67f)
        assertSides(Sides(room, 24f, room, 24f), padding(last = 6))
    }

    @Test
    fun even_padding_gives_the_top_and_bottom_the_sides_padding_when_that_is_more_than_a_gap() {
        showHome(SixColumns.copy(dockEvenPadding = true))

        val room = ringRoom(126.67f)
        assertSides(Sides(room, room, room, room), padding(last = 6))
    }

    @Test
    fun even_padding_keeps_the_sides_at_the_rings_room_in_a_four_column_dock() {
        showHome(TvConfig(columns = 4, dockEvenPadding = true))

        val room = ringRoom(202f)
        assertSides(Sides(room, room, room, room), padding(last = 4))
    }

    @Test
    fun even_padding_of_a_dock_with_fewer_tiles_than_the_row_holds_is_the_shrunk_docks_side_all_round() {
        showHome(SixColumns.copy(dockEvenPadding = true, fillDock = false), docked = 3)

        assertSides(Sides(28f, 28f, 28f, 28f), padding(last = 3))
    }

    @Test
    fun even_padding_does_not_move_the_panels_top_edge_when_focus_moves() {
        showHome(SixColumns)
        val rest = dock().top

        look = look.copy(evenPadding = true)
        settle()
        press(Key.DirectionRight)
        press(Key.DirectionLeft)

        assertEquals(rest.value, dock().top.value, 0.6f)
        assertEquals(ringRoom(126.67f), (dockTile(2).top - dock().top).value, 0.6f)
    }

    @Test
    fun a_dock_tiles_menu_ends_the_same_distance_above_the_panel_with_even_padding() {
        showHome(SixColumns)
        val plain = menuClearance()

        look = look.copy(evenPadding = true)
        settle()

        assertEquals(plain, menuClearance(), 0.6f)
    }

    private fun menuClearance(): Float {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
        val clearance = (dock().top - compose.onNodeWithTag("menu").getBoundsInRoot().bottom).value
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        return clearance
    }
}
