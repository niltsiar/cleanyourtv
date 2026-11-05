package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import kotlin.math.abs

/** The band over the grid (title, meta line, key chips), the dock under it and the menus that open over both. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HeroBandUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val cinematic = SixColumns.copy(cinematicHome = true, appLibrary = true)

    private fun showHome(
        config: TvConfig = cinematic,
        apps: List<AppEntry> = NineApps,
        libraryCount: Flow<Int> = emptyFlow(),
        powerMenu: Boolean = false,
        lastOpened: Map<String, Long> = emptyMap(),
    ) {
        val sources =
            sources(
                apps = flowOf(AppList(apps)),
                libraryCount = libraryCount,
                powerMenuAvailable = { powerMenu },
                lastOpened = flowOf(lastOpened),
            )
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

    private fun holdOk() {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    private fun dockTile(n: Int): SemanticsNodeInteraction =
        compose.onNode(hasTestTag("tile:pkg.$n") and hasAnyAncestor(hasTestTag("dock")))

    private fun textOf(tag: String): String =
        compose
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString("")
            .orEmpty()

    private fun assertNear(
        expected: Dp,
        actual: Dp,
    ) = assertTrue("expected $expected, got $actual", abs(expected.value - actual.value) < 1f)

    @Test
    fun the_title_baseline_and_the_meta_slot_stay_put_between_dock_and_grid_focus() {
        showHome()
        val dockTitle = bounds("shelf:name")
        val dockMeta = bounds("shelf:meta")

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        compose.mainClock.advanceTimeBy(1_000)

        assertNear(dockTitle.bottom, bounds("shelf:title").bottom)
        assertNear(dockMeta.top, bounds("shelf:title-meta").top)
    }

    @Test
    fun a_focused_grid_tile_says_when_home_last_opened_it_where_the_dock_does() {
        val opened =
            Evening
                .minusHours(2)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        showHome(lastOpened = mapOf("pkg.7" to opened))
        val dockMeta = bounds("shelf:meta")

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        compose.mainClock.advanceTimeBy(1_000)

        assertTrue(textOf("shelf:title-meta"), textOf("shelf:title-meta").startsWith("Last opened "))
        assertNear(dockMeta.top, bounds("shelf:title-meta").top)
    }

    @Test
    fun the_band_text_ends_28_dp_above_the_dock() {
        showHome()
        // The page dots: 16 dp under the meta slot, 6 dp tall.
        val dotsBottom = bounds("shelf:meta").bottom + 22.dp

        assertNear(dotsBottom + 28.dp, bounds("dock").top)
    }

    @Test
    fun focus_on_the_library_pill_names_the_library_and_counts_its_apps() {
        showHome(libraryCount = flowOf(23))
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("library:pill").assertIsFocused()
        compose.mainClock.advanceTimeBy(1_000)

        assertEquals("App library", textOf("shelf:title"))
        assertEquals("23 apps", textOf("shelf:title-meta"))
    }

    @Test
    fun reorder_mode_names_itself_in_the_band_and_shows_the_key_chips_above_the_dock() {
        showHome()
        press(Key.Menu)
        press(Key.DirectionDown)
        compose.onNodeWithTag("menu:Move").assertIsFocused()
        press(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(1_000)

        assertEquals("Reorder apps", textOf("shelf:title"))
        for (chip in listOf("OK", "pick up", "Arrows", "move", "Back", "drop", "Back ×2", "finish")) {
            compose.onNode(hasText(chip) and hasAnyAncestor(hasTestTag("reorder:hint"))).assertExists()
        }
        assertTrue(bounds("reorder:hint").bottom <= bounds("dock").top)
    }

    @Test
    fun the_dock_rests_at_one_height_whether_focus_is_on_it_on_the_top_bar_or_back_from_the_grid() {
        showHome()
        val resting = bounds("dock").top

        press(Key.DirectionUp)
        compose.mainClock.advanceTimeBy(1_000)
        assertNear(resting, bounds("dock").top)

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionUp)
        compose.mainClock.advanceTimeBy(1_000)
        dockTile(1).assertIsFocused()
        assertNear(resting, bounds("dock").top)
    }

    @Test
    fun the_dock_does_not_move_when_its_first_tile_takes_focus_on_start() {
        compose.mainClock.autoAdvance = false
        val sources = sources()
        compose.setContent {
            TvTheme {
                val viewModel = rememberHomeViewModel(sources, cinematic, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = cinematic.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    shelfLayout = cinematic.shelfLayout().copy(scrimAlpha = 0f),
                    actions = viewModel.actions,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val first = bounds("dock").top

        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        assertNear(first, bounds("dock").top)
    }

    @Test
    fun dock_tiles_sit_on_the_grid_columns() {
        showHome(apps = (1..12).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") })
        // On the top bar no tile is focused, so none is drawn grown.
        press(Key.DirectionUp)

        for (column in 1..6) {
            val dock = dockTile(column).getBoundsInRoot()
            val grid = bounds("tile:pkg.${column + 6}")
            assertNear(grid.left, dock.left)
            assertNear(grid.right, dock.right)
        }
    }

    @Test
    fun the_four_row_tile_menu_opens_beside_its_tile_under_the_band_and_60_dp_above_the_bottom() {
        showHome()
        val tile = dockTile(2).getBoundsInRoot()
        press(Key.DirectionRight)
        holdOk()
        for (row in listOf("ReplaceInDock", "Move", "RemoveFromHome", "AppInfo")) compose.onNodeWithTag("menu:$row").assertExists()

        val menu = bounds("menu")
        assertNear(tile.right + 16.dp, menu.left)
        assertTrue("menu top ${menu.top} under the band", menu.top >= bounds("dock").top - 0.5.dp)
        assertTrue(menu.bottom <= compose.onRoot().getBoundsInRoot().bottom - 60.dp + 0.5.dp)
    }

    @Test
    fun the_settings_menu_ends_at_least_8_dp_above_the_dock() {
        showHome(powerMenu = true)
        press(Key.DirectionUp)
        repeat(2) { press(Key.DirectionRight) }
        holdOk()
        compose.onNodeWithTag("menu:PowerMenu").assertExists()

        assertTrue(
            "menu bottom ${bounds("menu").bottom}, dock top ${bounds("dock").top}",
            bounds("menu").bottom + 8.dp <= bounds("dock").top,
        )
    }
}
