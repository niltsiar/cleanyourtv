package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputsMenu
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
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** In dock focus the band and the dock sit 120 dp lower; the grid layout is where Down takes them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class DockLowerUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }
    private val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = white) }
    private val cinematic = SixColumns.copy(cinematicHome = true, appLibrary = true)
    private var inputsRequests by mutableIntStateOf(0)
    private var libraryOpen by mutableStateOf(false)

    private fun showHome(
        motion: Motion = Motion(),
        config: TvConfig = cinematic,
        inputs: Flow<InputsMenu> = emptyFlow(),
    ) {
        val sources = sources(apps = flowOf(AppList(apps)), inputs = inputs)
        compose.setContent {
            TvTheme(motion = motion) {
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
                    libraryOpen = { libraryOpen },
                    onOpenLibrary = { libraryOpen = true },
                    overlays = { if (libraryOpen) LibraryStandIn() },
                )
            }
        }
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    /** An overlay that takes focus when it opens, as the App library does. */
    @Composable
    private fun LibraryStandIn() {
        val focus = remember { FocusRequester() }
        Box(Modifier.testTag("library").focusRequester(focus).focusable())
        LaunchedEffect(Unit) { focus.requestFocus() }
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

    private fun top(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().top

    private fun bottom(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().bottom

    private fun assertNear(
        expected: Dp,
        actual: Dp,
        tolerance: Float = 1f,
    ) = assertTrue("expected $expected, got $actual", abs(expected.value - actual.value) <= tolerance)

    @Test
    fun in_dock_focus_the_band_and_the_dock_sit_120_dp_lower_than_in_the_grid_layout() {
        showHome()
        val dock = top("dock")
        val title = bottom("shelf:name")

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        assertNear(title - 120.dp, bottom("shelf:title"))
        press(Key.DirectionUp)
        assertNear(dock, top("dock"))
        assertNear(title, bottom("shelf:name"))
    }

    @Test
    fun down_from_the_dock_puts_the_first_grid_row_where_the_dock_rested_before_it_moved_lower() {
        showHome()
        // Before the dock moved lower the focused row rested 24 dp inside the dock panel's old top edge.
        val rest = top("dock") - 120.dp + 24.dp

        press(Key.DirectionDown)

        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        assertNear(rest, top("tile:pkg.7"))
    }

    @Test
    fun up_into_the_top_bar_keeps_the_low_layout_and_down_returns_to_the_dock_without_a_move() {
        showHome()
        val dock = top("dock")

        press(Key.DirectionUp)
        assertNear(dock, top("dock"))
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        assertNear(dock, top("dock"))
    }

    @Test
    fun moving_along_the_dock_does_not_scroll_the_grid() {
        showHome()
        val dock = top("dock")
        val row = top("tile:pkg.7")

        press(Key.DirectionRight)
        press(Key.DirectionRight)

        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
        assertNear(dock, top("dock"))
        assertNear(row, top("tile:pkg.7"))
    }

    @Test
    fun a_tile_menu_on_a_dock_tile_returns_the_band_and_the_dock_to_the_grid_layout() {
        showHome()
        val dock = top("dock")

        holdOk()

        compose.onNodeWithTag("menu").assertExists()
        assertNear(dock - 120.dp, top("dock"))
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        assertNear(dock, top("dock"))
    }

    @Test
    fun the_tile_a_menu_acts_on_stays_undimmed_while_the_group_rises_to_the_grid_layout() {
        showHome()
        val tile = compose.onNodeWithTag("tile:pkg.1")
        val resting = tile.getBoundsInRoot().top
        compose.mainClock.autoAdvance = false

        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(140)

        val bounds = tile.getBoundsInRoot()
        assertTrue("risen ${(resting - bounds.top).value} dp", (resting - bounds.top).value in 20f..110f)
        val density = compose.activity.resources.displayMetrics.density
        val centre =
            compose.onRoot().captureToImage().toPixelMap()[
                (bounds.left.value * density).toInt() + 60, (
                    (bounds.bottom - 4.dp).value *
                        density
                ).toInt(),
            ]
        assertTrue("tile bottom ${centre.red}", centre.red >= 0.95f)
    }

    @Test
    fun the_group_takes_320_ms_to_rise_for_the_grid() {
        showHome()
        val low = bottom("shelf:name")
        compose.mainClock.autoAdvance = false

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(100)
        val early = (low - bottom("shelf:name")).value
        assertTrue("risen $early dp after 100 ms", early in 1f..110f)

        compose.mainClock.advanceTimeBy(500)
        assertNear(low - 120.dp, bottom("shelf:title"))
    }

    @Test
    fun a_reversal_inside_the_move_goes_on_from_where_the_group_is() {
        showHome()
        val low = bottom("shelf:name")
        compose.mainClock.autoAdvance = false

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(160)
        val middle = bottom("shelf:name")
        assertTrue("risen ${low - middle} dp", (low - middle).value in 5f..115f)

        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.mainClock.advanceTimeBy(16)
        assertNear(middle, bottom("shelf:name"), tolerance = 12f)

        compose.mainClock.advanceTimeBy(1_000)
        assertNear(low, bottom("shelf:name"))
    }

    @Test
    fun with_animations_off_the_group_moves_at_once() {
        showHome(motion = Motion(animations = false))
        val low = bottom("shelf:name")
        compose.mainClock.autoAdvance = false

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(64)

        assertNear(low - 120.dp, bottom("shelf:title"))
    }

    @Test
    fun with_reduced_motion_the_group_moves_at_once() {
        showHome(motion = Motion(reducedMotion = true))
        val low = bottom("shelf:name")
        compose.mainClock.autoAdvance = false

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(64)

        assertNear(low - 120.dp, bottom("shelf:title"))
    }

    @Test
    fun a_tile_menu_hangs_from_where_the_tile_rests_once_the_group_has_risen() {
        val input =
            HomeInput(
                mutableStateOf(HomeMenuActions()),
                groupShift = { 240f },
                carried = mutableStateOf(null),
                dimmed = mutableStateOf(emptySet()),
                reordering = mutableStateOf(false),
            )

        input.anchorTile(Rect(80f, 776f, 334f, 919f))

        assertEquals(Rect(80f, 536f, 334f, 679f), input.anchor.value)
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(16)
    }

    /**
     * Runs [action] with the clock stopped, then steps 960 ms in frames; how far [tag] strays from the place it ends up at
     * (so that a scroll that settles somewhere else does not count), in dp. A frame in which [tag] is not there counts as 0.
     */
    private fun strayOf(
        tag: String,
        action: () -> Unit,
    ): Float {
        compose.mainClock.autoAdvance = false
        action()
        val tops =
            List(60) {
                compose.mainClock.advanceTimeBy(16)
                if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) null else top(tag).value
            }
        val end = tops.last() ?: return 0f
        return tops.filterNotNull().maxOf { abs(it - end) }
    }

    private fun focusGridTile() {
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
    }

    @Test
    fun the_spotlight_stays_on_the_tile_while_the_group_drops_back_after_its_menu_closes() {
        showHome()
        val tile = compose.onNodeWithTag("tile:pkg.1")
        holdOk()
        val risen = tile.getBoundsInRoot().top
        compose.mainClock.autoAdvance = false

        back()
        var moved = 0f
        repeat(40) {
            if (moved < 3f) {
                compose.mainClock.advanceTimeBy(16)
                moved = (tile.getBoundsInRoot().top - risen).value
            }
        }

        assertTrue("the tile dropped $moved dp", moved >= 3f)
        val bounds = tile.getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val inside =
            compose.onRoot().captureToImage().toPixelMap()[
                (bounds.left.value * density).toInt() + 60, ((bounds.bottom - 1.5.dp).value * density).toInt(),
            ]
        assertTrue("tile bottom ${inside.red}", inside.red >= 0.95f)
    }

    @Test
    fun the_inputs_list_a_remote_key_opens_over_a_grid_row_leaves_the_group_up() {
        showHome(inputs = flowOf(FourPorts))
        focusGridTile()

        val stray = strayOf("tile:pkg.7") { inputsRequests++ }

        compose.onNodeWithTag("menu").assertExists()
        assertTrue("the group strayed $stray dp", stray <= 1f)
    }

    @Test
    fun closing_the_menu_of_a_grid_tile_leaves_the_group_up() {
        showHome()
        focusGridTile()
        holdOk()
        compose.onNodeWithTag("menu").assertExists()

        val stray = strayOf("shelf:name") { back() }

        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        assertTrue("the group strayed $stray dp", stray <= 1f)
    }

    @Test
    fun with_single_row_the_dock_does_not_lower_and_a_tile_menu_moves_nothing() {
        showHome(config = cinematic.copy(singleRow = true))
        val dock = top("dock")

        holdOk()

        compose.onNodeWithTag("menu").assertExists()
        assertNear(dock, top("dock"))
    }

    @Test
    fun closing_the_menu_of_a_grid_tile_leaves_the_grid_where_it_was() {
        showHome()
        focusGridTile()
        val before = top("tile:pkg.7")
        holdOk()
        compose.onNodeWithTag("menu").assertExists()

        strayOf("tile:pkg.7") { back() }

        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        assertNear(before, top("tile:pkg.7"))
    }

    @Test
    fun closing_the_library_back_to_the_pill_leaves_the_grid_where_it_was() {
        showHome()
        repeat(3) { press(Key.DirectionDown) }
        compose.onNodeWithTag("library:pill").assertIsFocused()
        val before = top("tile:pkg.7")
        press(Key.DirectionCenter)
        compose.onNodeWithTag("library").assertExists()
        compose.mainClock.advanceTimeBy(1_000)

        val stray = strayOf("tile:pkg.7") { libraryOpen = false }

        compose.onNodeWithTag("library:pill").assertIsFocused()
        assertNear(before, top("tile:pkg.7"))
        assertTrue("the grid strayed $stray dp", stray <= 1f)
    }
}
