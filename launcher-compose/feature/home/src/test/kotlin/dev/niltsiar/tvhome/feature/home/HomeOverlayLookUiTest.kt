package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputsMenu
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** The tile menu over a Home of white tiles, read from the rendered pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeOverlayLookUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }

    private fun appsOf(count: Int) = (1..count).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = white) }

    private var apps = appsOf(9)

    private var libraryOpen by mutableStateOf(false)

    private fun showHome(
        appCount: Int = 9,
        inputs: InputsMenu = FourPorts,
        config: TvConfig = SixColumns,
    ) {
        apps = appsOf(appCount)
        val sources = sources(apps = flowOf(AppList(apps)), inputs = flowOf(inputs), powerMenuAvailable = { true })
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
                    actions = viewModel.actions,
                    libraryOpen = { libraryOpen },
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

    private fun openTileMenu() {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
        compose.onNodeWithTag("menu").assertExists()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    private fun centreOf(tag: String): Offset =
        bounds(tag).let {
            Offset(
                (it.left.value + it.right.value) / 2,
                (it.top.value + it.bottom.value) / 2,
            )
        }

    /** The red channel, 0 to 255, of the screen at [point] (in dp). */
    private fun redAt(point: Offset): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(pixels[(point.x * density).toInt(), (point.y * density).toInt()].red * 255)
    }

    private fun assertNear(
        expected: Int,
        actual: Int,
        tolerance: Int,
    ) = assertTrue("expected $expected, got $actual", abs(actual - expected) <= tolerance)

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    /** A point in the panel [menu], in the gap between its rows [above] and [below], that a tile lies under. */
    private fun gapOverATile(
        menu: String,
        above: String,
        below: String,
    ): Offset {
        val panel = bounds(menu)
        val y = (bounds(above).bottom.value + bounds(below).top.value) / 2
        val x =
            apps
                .map { bounds("tile:${it.key}") }
                .filter { it.top.value <= y && y <= it.bottom.value }
                .map { (it.left.value + it.right.value) / 2 }
                .firstOrNull { panel.left.value < it && it < panel.right.value }
        return Offset(checkNotNull(x) { "no tile under the panel $panel at y $y" }, y)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_menu_panel_hides_the_white_tile_under_it() {
        showHome()
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        openTileMenu()

        assertNear(0x14, redAt(gapOverATile("menu", above = "menu:Move", below = "menu:AppInfo")), 1)
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_dock_tiles_menu_of_four_rows_ends_at_least_12_dp_above_the_dock_panel() {
        showHome(config = SixColumns.copy(appLibrary = true))
        openTileMenu()
        compose.onNodeWithTag("menu:RemoveFromHome").assertExists()
        compose.onNodeWithTag("menu:AppInfo").assertExists()

        val clearance = bounds("dock").top.value - bounds("menu").bottom.value
        assertTrue(
            "the menu ends $clearance dp above the panel: ${bounds("dock")} ${bounds("menu")} ${bounds("tile:pkg.1")}",
            clearance >= 11.5f,
        )
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_inputs_list_ends_at_least_24_dp_above_the_dock_panel_and_scrolls_to_the_row_in_focus() {
        val ports = InputsMenu(available = true, rows = (1..6).map { port(it, InputConnection.Disconnected) } + InputRow.LiveTv)
        showHome(inputs = ports)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        val clearance = bounds("dock").top.value - bounds("menu").bottom.value
        assertTrue("the list ends $clearance dp above the panel", clearance >= 23.5f)

        repeat(6) { press(Key.DirectionDown) }
        compose.onNodeWithTag("input:6").assertIsFocused()
        assertTrue("the last row ends at ${bounds("input:6").bottom}", bounds("input:6").bottom.value <= bounds("menu").bottom.value)
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_grid_tiles_menu_leaves_no_sliver_of_the_next_tile_beside_its_right_edge() {
        showHome(appCount = 20)
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        openTileMenu()

        val menu = bounds("menu")
        val middle = centreOf("tile:pkg.7").y
        assertTrue("the menu does not reach the tile row", middle > menu.top.value && middle < menu.bottom.value)
        // Pixels 2 to 22 dp past the menu's edge lie in the 24 dp gap between tile columns when it ends on a tile's edge.
        val ink = (2..22 step 2).map { redAt(Offset(menu.right.value + it, middle)) }
        assertTrue("tile ink beside the menu: $ink", ink.all { it < 40 })
        assertTrue("no tile at the next column", redAt(Offset(menu.right.value + 30f, middle)) > 100)
    }

    @Test
    @Config(qualifiers = "+night")
    fun an_open_menu_dims_home_behind_it_by_half_and_back_lifts_the_dim() {
        showHome()
        assertNear(255, redAt(centreOf("tile:pkg.6")), 2)

        openTileMenu()
        assertNear((255 + 0x07) / 2, redAt(centreOf("tile:pkg.6")), 4)

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        compose.onNodeWithTag("menu").assertDoesNotExist()
        assertNear(255, redAt(centreOf("tile:pkg.6")), 2)
    }

    /** The brightest red channel, 0 to 255, anywhere on the screen. */
    private fun brightestOnScreen(): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var most = 0f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) most = maxOf(most, pixels[x, y].red)
        return Math.round(most * 255)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_app_library_hides_every_layer_of_home_behind_it_and_closing_it_brings_them_back() {
        showHome()
        val far = centreOf("tile:pkg.6")
        assertNear(255, redAt(far), 2)

        libraryOpen = true
        settle()

        // The wallpaper under the library's scrim, and nothing of Home's top bar, band, dock or grid over it.
        val scrimmed = Math.round(0x07 * 0.78f)
        assertNear(scrimmed, brightestOnScreen(), 2)

        libraryOpen = false
        settle()

        assertNear(255, redAt(far), 2)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_tile_a_menu_acts_on_stays_undimmed_inside_a_thin_white_ring() {
        showHome()
        openTileMenu()

        val tile = bounds("tile:pkg.1")
        val middle = (tile.top.value + tile.bottom.value) / 2
        assertNear(255, redAt(Offset(tile.left.value + 8f, middle)), 2)
        // The dimmed dock, in the gap after the tile, and past the ring on its other side.
        val dimmed = redAt(Offset(tile.right.value + 12f, middle))
        assertNear(dimmed, redAt(Offset(tile.left.value - 4f, middle)), 3)
        // 1 dp out from the tile's edge, in the middle of the 2 dp ring: white at 0.70 over the dimmed dock.
        assertNear(Math.round(dimmed + 0.7f * (255 - dimmed)), redAt(Offset(tile.left.value - 1f, middle)), 8)
    }

    @Test
    @Config(qualifiers = "+night")
    fun settings_menu_rows_have_corners_concentric_with_the_panel() {
        showHome()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        settle()
        repeat(2) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            settle()
        }
        compose.onNodeWithContentDescription("Settings").assertIsFocused()
        openTileMenu()
        compose.onNodeWithTag("menu:EditDock").assertIsFocused()

        val row = bounds("menu:EditDock")
        assertNear(0xF2, redAt(Offset(row.right.value - 12f, (row.top.value + row.bottom.value) / 2)), 3)
        // 5 dp in from the corner lies inside a 14 dp corner and outside a 20 dp one.
        assertTrue(redAt(Offset(row.left.value + 5f, row.top.value + 5f)) < 0x80)
    }

    /** The red channel at the empty right end of the focused first row of the open tile menu. */
    private fun focusedRowRed(): Int {
        showHome()
        openTileMenu()
        compose.onNodeWithTag("menu:ReplaceInDock").assertIsFocused()
        val row = bounds("menu:ReplaceInDock")
        return redAt(Offset(row.right.value - 12f, (row.top.value + row.bottom.value) / 2))
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_focused_menu_row_is_a_light_pill_in_night_mode() {
        assertNear(0xF2, focusedRowRed(), 3)
    }

    @Test
    fun a_focused_menu_row_is_a_dark_pill_with_night_mode_off() {
        assertNear(0x11, focusedRowRed(), 3)
    }

    private fun openInputs() {
        showHome()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        settle()
        compose.onNodeWithContentDescription("Inputs").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        settle()
        compose.onNodeWithTag("input:0").assertIsFocused()
    }

    /**
     * The red channel inside the "Connected" capsule of the Inputs row [row], just right of its text, against the one
     * the row shows right of the capsule, blended as a capsule fill of [fillAlpha] would blend #34C759 over it.
     */
    private fun assertCapsuleFill(
        row: Int,
        fillAlpha: Int,
    ) {
        val text =
            compose
                .onNode(hasText("Connected") and hasAnyAncestor(hasTestTag("input:$row")), useUnmergedTree = true)
                .getBoundsInRoot()
        val bounds = bounds("input:$row")
        val middle = (text.top.value + text.bottom.value) / 2
        val behind = redAt(Offset(bounds.right.value - 6f, middle))
        assertNear(behind + Math.round((0x34 - behind) * fillAlpha / 255f), redAt(Offset(text.right.value + 4f, middle)), 3)
    }

    @Test
    @Config(qualifiers = "+night")
    fun an_unfocused_connected_row_shows_the_faint_green_capsule_in_night_mode() {
        openInputs()
        assertCapsuleFill(row = 1, fillAlpha = 0x29)
    }

    /** The brightest red channel, 0 to 255, inside [box] (in dp): a glyph's stem shows its ink at full strength. */
    private fun brightestRedIn(box: DpRect): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return (box.top.value * density).toInt().until((box.bottom.value * density).toInt()).maxOf { y ->
            (box.left.value * density).toInt().until((box.right.value * density).toInt()).maxOf { x -> Math.round(pixels[x, y].red * 255) }
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_focused_connected_row_shows_a_solid_146C2E_capsule_with_white_text() {
        openInputs()
        val text =
            compose
                .onNode(hasText("Connected") and hasAnyAncestor(hasTestTag("input:0")), useUnmergedTree = true)
                .getBoundsInRoot()

        // The row's own fill is the light pill, red F2; the capsule's solid fill is red 14.
        assertNear(0x14, redAt(Offset(text.right.value + 4f, (text.top.value + text.bottom.value) / 2)), 3)
        assertTrue("ink ${brightestRedIn(text)}", brightestRedIn(text) >= 0xE8)
    }

    @Test
    fun an_unfocused_connected_row_shows_the_stronger_green_capsule_on_the_light_surface() {
        openInputs()
        assertCapsuleFill(row = 1, fillAlpha = 0x2E)
    }

    /** The red channel of the top-bar button described as [description], [inset] dp in from its left edge, at its middle. */
    private fun buttonRed(
        description: String,
        inset: Float,
    ): Int =
        compose.onNodeWithContentDescription(description).getBoundsInRoot().let {
            redAt(Offset(it.left.value + inset, (it.top.value + it.bottom.value) / 2))
        }

    /** [opener] shows a 25 percent white fill and a white edge; [idle], a button next to it, shows neither. */
    private fun assertLit(
        opener: String,
        idle: String,
    ) {
        val fill = buttonRed(opener, 4f)
        val rest = buttonRed(idle, 4f)
        assertTrue("fill $fill, idle button $rest", fill >= rest + 20)
        val edge = buttonRed(opener, 0.5f)
        assertTrue("edge $edge, fill $fill", edge >= fill + 40)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_inputs_button_stays_lit_while_its_list_is_open() {
        openInputs()

        assertLit(opener = "Inputs", idle = "Settings")
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_settings_button_stays_lit_while_its_menu_is_open() {
        showHome()
        press(Key.DirectionUp)
        repeat(2) { press(Key.DirectionRight) }
        compose.onNodeWithContentDescription("Settings").assertIsFocused()
        openTileMenu()

        assertLit(opener = "Settings", idle = "Inputs")
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_inputs_button_is_idle_again_once_its_list_has_closed() {
        openInputs()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        press(Key.DirectionDown)
        compose.onNodeWithTag("menu").assertDoesNotExist()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

        assertNear(buttonRed("Settings", 4f), buttonRed("Inputs", 4f), 2)
    }
}
