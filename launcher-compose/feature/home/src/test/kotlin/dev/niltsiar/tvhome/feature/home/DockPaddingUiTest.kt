package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private var shown by mutableStateOf(SixColumns)

    private fun showHome(
        config: TvConfig = SixColumns,
        docked: Int = 0,
        motion: Motion = Motion(),
    ) {
        val sources = sources(apps = flowOf(AppList(apps, docked)))
        look = config.dockLook()
        shown = config
        compose.setContent {
            TvTheme(motion = motion) {
                key(shown) {
                    val viewModel = rememberHomeViewModel(sources, shown, mutableListOf())
                    val state = viewModel.uiState.collectAsState()
                    HomeScreen(
                        state = { state.value },
                        layout = shown.homeLayout(),
                        onFocus = viewModel::onFocus,
                        onClick = {},
                        topBar = shown.topBarLayout(),
                        dockLook = look,
                        actions = viewModel.actions,
                    )
                }
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

    /** Shows Home again with [config], in the theme [showHome] set. */
    private fun switchTo(config: TvConfig) {
        look = config.dockLook()
        shown = config
        settle()
        compose.onNodeWithTag("tile:pkg.1").performSemanticsAction(SemanticsActions.RequestFocus)
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
    fun with_focus_overshoot_a_dock_pads_its_sides_for_the_ring_at_the_peak_of_the_pop() {
        showHome(SixColumns, motion = Motion(focusOvershoot = true))

        val room = 126.67f * 0.072f + 9f * 1.144f + 12f
        assertSides(Sides(room, 24f, room, 24f), padding(last = 6))
    }

    /** A dock tile's height, and how far its ring reaches over and under it at the peak of a pop, in dp. */
    private data class PeakRing(
        val height: Float,
        val over: Float,
        val under: Float,
    )

    /** The ring of a dock tile [height] dp high at the 1.144 peak of a pop: with names it grows about its bottom edge, or 128.5 dp down. */
    private fun peakRing(
        height: Float,
        names: Boolean,
    ): PeakRing {
        val pivot = if (names) minOf(height, 128.5f) else height / 2f
        return PeakRing(height, pivot * 0.144f + 9f * 1.144f, (height - pivot) * 0.144f + 9f * 1.144f)
    }

    /** With focus on the second tile, the first tile's ring at the peak and its room over it and under it to its name's pill. */
    private fun namedRoom(): Triple<PeakRing, Float, Float> {
        press(Key.DirectionRight)
        val tile = dockTile(1)
        val pill = compose.onNodeWithTag("name:pkg.1").getBoundsInRoot()
        val ring = peakRing((tile.bottom - tile.top).value, names = true)
        return Triple(ring, (tile.top - dock().top).value, (pill.top - tile.bottom).value - 2f)
    }

    @Test
    fun with_focus_overshoot_and_names_the_ring_at_the_peak_keeps_4_dp_from_the_rim_and_the_pill_with_2_to_7_columns() {
        showHome(TvConfig(columns = 2, alwaysShowNames = true), motion = Motion(focusOvershoot = true))
        for (columns in 2..7) {
            switchTo(TvConfig(columns = columns, alwaysShowNames = true))
            val (ring, over, toPill) = namedRoom()

            assertTrue("$columns columns: $over dp over a ring of ${ring.over}", over >= ring.over + 4f - HALF_PIXEL)
            assertTrue("$columns columns: $toPill dp to the pill under a ring of ${ring.under}", toPill >= ring.under + 4f - HALF_PIXEL)
        }
    }

    @Test
    fun without_focus_overshoot_a_named_dock_keeps_a_gap_over_its_tiles_and_14_dp_and_their_growth_to_the_pill() {
        showHome(TvConfig(columns = 2, alwaysShowNames = true))
        for (columns in 2..7) {
            switchTo(TvConfig(columns = columns, alwaysShowNames = true))
            val (ring, over, toPill) = namedRoom()

            assertEquals("$columns columns", 24f, over, 0.6f)
            assertEquals("$columns columns", 14f + 0.08f * (ring.height - minOf(ring.height, 128.5f)), toPill, 0.6f)
        }
    }

    @Test
    fun with_focus_overshoot_a_2_column_dock_without_names_pads_its_top_and_bottom_for_the_ring_at_the_peak() {
        showHome(TvConfig(columns = 2), motion = Motion(focusOvershoot = true))

        val sides = padding(last = 2)
        val tile = dockTile(1)
        val ring = peakRing((tile.bottom - tile.top).value, names = false)
        assertTrue("top ${sides.top} over a ring of ${ring.over}", sides.top >= ring.over + 4f - HALF_PIXEL)
        assertTrue("bottom ${sides.bottom} under a ring of ${ring.under}", sides.bottom >= ring.under + 4f - HALF_PIXEL)
    }

    @Test
    fun without_focus_overshoot_a_2_column_dock_without_names_pads_its_top_and_bottom_by_a_gap() {
        showHome(TvConfig(columns = 2))

        val sides = padding(last = 2)
        assertEquals(24f, sides.top, 0.6f)
        assertEquals(24f, sides.bottom, 0.6f)
    }

    /** The room from the bottom of the first dock tile's name pill to the panel's rim, and the gap from the tile to the pill. */
    private fun underPills(): Pair<Float, Float> {
        val tile = dockTile(1)
        val name = compose.onNode(hasTestTag("name:pkg.1") and hasAnyAncestor(hasTestTag("dock"))).getBoundsInRoot()
        return (dock().bottom - name.bottom).value - 2f to (name.top - tile.bottom).value - 2f
    }

    private fun assertGapOrOneAndAHalfUnderPills(motion: Motion) {
        showHome(TvConfig(columns = 2, alwaysShowNames = true), motion = motion)
        for (columns in 2..7) {
            switchTo(TvConfig(columns = columns, alwaysShowNames = true))
            val (under, gap) = underPills()

            assertEquals("$columns columns", maxOf(24f, 1.5f * gap), under, 0.6f)
        }
    }

    @Test
    @Config(qualifiers = TALL)
    fun with_focus_overshoot_a_named_docks_room_under_its_pills_is_a_gap_or_one_and_a_half_times_the_pills_gap() {
        assertGapOrOneAndAHalfUnderPills(Motion(focusOvershoot = true))
    }

    @Test
    @Config(qualifiers = TALL)
    fun without_focus_overshoot_a_named_docks_room_under_its_pills_is_a_gap_or_one_and_a_half_times_the_pills_gap() {
        assertGapOrOneAndAHalfUnderPills(Motion())
    }

    /** The first grid row's tile in a dock of [columns] tiles. */
    private fun rowOneTile(columns: Int): DpRect = compose.onNodeWithTag("tile:pkg.${columns + 1}").getBoundsInRoot()

    private val rowOneConfigs = (2..4).map { TvConfig(columns = it, alwaysShowNames = true) } + TvConfig(columns = 2)

    @Test
    @Config(qualifiers = TALL)
    fun with_focus_overshoot_a_first_row_tiles_ring_at_the_peak_keeps_4_dp_under_the_dock_panel() {
        showHome(rowOneConfigs.first(), motion = Motion(focusOvershoot = true))
        for (config in rowOneConfigs) {
            switchTo(config)
            val tile = rowOneTile(config.columns)
            val ring = peakRing((tile.bottom - tile.top).value, config.alwaysShowNames)
            val room = (tile.top - dock().bottom).value

            assertTrue("$config: $room dp under the panel over a ring of ${ring.over}", room >= ring.over + 4f - HALF_PIXEL)
        }
    }

    @Test
    @Config(qualifiers = TALL)
    fun without_focus_overshoot_the_first_row_is_a_gap_under_the_dock_panel() {
        showHome(rowOneConfigs.first())
        for (config in rowOneConfigs) {
            switchTo(config)

            assertEquals("$config", 24f, (rowOneTile(config.columns).top - dock().bottom).value, 0.6f)
        }
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

/** The layout places in whole pixels; at xhdpi rounding moves an edge by up to half of one, in dp. */
private const val HALF_PIXEL = 0.25f

/** A window tall enough for a dock of two named tiles and the first grid row under it. */
private const val TALL = "w960dp-h1080dp-xhdpi"
