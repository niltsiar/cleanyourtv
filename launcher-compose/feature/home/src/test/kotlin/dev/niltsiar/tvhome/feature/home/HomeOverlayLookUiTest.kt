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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputsMenu
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
        topBarApps: List<AppEntry> = emptyList(),
    ) {
        apps = appsOf(appCount)
        val sources =
            sources(
                apps = flowOf(AppList(apps)),
                topBarApps = flowOf(topBarApps),
                inputs = flowOf(inputs),
                powerMenuAvailable = { true },
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

    private fun assertNear(
        expected: Float,
        actual: Float,
        tolerance: Float,
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
    fun a_dock_tiles_menu_that_fits_under_the_top_bar_ends_20_dp_above_the_dock_panel() {
        showHome(config = SixColumns)
        openTileMenu()
        compose.onNodeWithTag("menu:RemoveFromHome").assertDoesNotExist()

        val clearance = bounds("dock").top.value - bounds("menu").bottom.value
        assertEquals("the menu ends $clearance dp above the panel", 20f, clearance, 0.5f)
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

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    /** What a white dock tile shows under the 0.5 dim of an open popup, once the dock is drawn at [alpha] over the black wallpaper. */
    private fun dimmedDock(alpha: Float) = Math.round((255 * alpha + 0x07) / 2)

    @Test
    @Config(qualifiers = "+night")
    fun the_inputs_list_draws_the_dock_at_055_under_its_dim_and_closing_it_leaves_the_dock_at_075_with_focus_back_on_the_top_bar() {
        openInputs()

        assertNear(dimmedDock(0.55f), redAt(centreOf("tile:pkg.2")), 4)

        back()
        compose.onNodeWithTag("input:0").assertDoesNotExist()
        assertNear(Math.round(255 * 0.75f), redAt(centreOf("tile:pkg.2")), 6)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_settings_menu_draws_the_dock_at_055_under_its_dim_and_closing_it_leaves_the_dock_at_075_with_focus_back_on_the_top_bar() {
        showHome()
        press(Key.DirectionUp)
        repeat(2) { press(Key.DirectionRight) }
        compose.onNodeWithContentDescription("Settings").assertIsFocused()
        openTileMenu()

        assertNear(dimmedDock(0.55f), redAt(centreOf("tile:pkg.2")), 4)

        back()
        compose.onNodeWithTag("menu").assertDoesNotExist()
        assertNear(Math.round(255 * 0.75f), redAt(centreOf("tile:pkg.2")), 6)
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_dock_tiles_menu_leaves_the_dock_at_full_alpha_under_its_dim() {
        showHome()
        openTileMenu()

        assertNear(dimmedDock(1f), redAt(centreOf("tile:pkg.6")), 4)
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

    private fun openSettingsMenu() {
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        settle()
        repeat(2) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            settle()
        }
        compose.onNodeWithContentDescription("Settings").assertIsFocused()
        openTileMenu()
    }

    @Test
    @Config(qualifiers = "+night")
    fun settings_menu_rows_have_corners_concentric_with_the_panel() {
        showHome()
        openSettingsMenu()
        compose.onNodeWithTag("menu:EditDock").assertIsFocused()

        val row = bounds("menu:EditDock")
        assertNear(0xF2, redAt(Offset(row.right.value - 12f, (row.top.value + row.bottom.value) / 2)), 3)
        // 5 dp in from the corner lies inside a 14 dp corner and outside a 20 dp one.
        assertTrue(redAt(Offset(row.left.value + 5f, row.top.value + 5f)) < 0x80)
    }

    /** How far the focused row of the open menu is from the panel's left edge, in dp. */
    private fun focusedRowInset(): Float {
        val row = compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("menu"))).getBoundsInRoot()
        return row.left.value - bounds("menu").left.value
    }

    @Test
    fun the_focused_row_of_a_grid_tiles_menu_is_8_dp_inside_the_panel() {
        showHome()
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        openTileMenu()

        assertNear(8f, focusedRowInset(), 0.5f)
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_dock_tiles_menu_sets_its_header_off_with_a_1_dp_line_of_12_percent_white_8_dp_under_it_and_keeps_the_rows_8_dp_inside() {
        showHome()
        openTileMenu()

        val title = bounds("menu:title")
        val rule = bounds("menu:title:rule")
        val ground = redAt(Offset(rule.left.value + 4f, rule.top.value - 3f))
        val line = redAt(Offset((rule.left.value + rule.right.value) / 2, (rule.top.value + rule.bottom.value) / 2))

        assertNear(8f, rule.top.value - title.bottom.value, 0.5f)
        assertNear(1f, rule.height.value, 0.1f)
        assertNear(Math.round(ground + 0.12f * (255 - ground)), line, 3)
        assertNear(8f, focusedRowInset(), 0.5f)
    }

    @Test
    fun the_focused_row_of_the_settings_menu_is_8_dp_inside_the_panel() {
        showHome()
        openSettingsMenu()

        assertNear(8f, focusedRowInset(), 0.5f)
    }

    @Test
    fun the_focused_row_of_the_inputs_list_is_8_dp_inside_the_panel() {
        openInputs()

        assertNear(8f, focusedRowInset(), 0.5f)
    }

    @Test
    fun the_settings_menu_sets_its_restart_row_apart_with_the_inputs_lists_line() {
        showHome()
        openSettingsMenu()

        val line = compose.onNodeWithTag("menu:divider").getBoundsInRoot()
        assertTrue("the line is ${line.height} dp tall", abs(line.height.value - 5f) <= 0.5f)
        assertTrue("the line ${line.top} is under the Reorder row ${bounds("menu:Reorder")}", line.top >= bounds("menu:Reorder").bottom)
        assertTrue(
            "the line ${line.bottom} is over the restart row ${bounds("menu:PowerMenu")}",
            line.bottom <= bounds("menu:PowerMenu").top,
        )
    }

    /** The pixels of the screen in [area] that are [colour] to within 14 in each channel. */
    private fun pixelsOf(
        area: DpRect,
        colour: Int,
    ): Int {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (area.left.value * density).toInt() until (area.right.value * density).toInt()
        val ys = (area.top.value * density).toInt() until (area.bottom.value * density).toInt()
        return xs.sumOf { x ->
            ys.count { y ->
                val pixel = pixels[x, y]
                abs(pixel.red * 255 - (colour shr 16 and 0xFF)) <= 14 &&
                    abs(pixel.green * 255 - (colour shr 8 and 0xFF)) <= 14 &&
                    abs(pixel.blue * 255 - (colour and 0xFF)) <= 14
            }
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_restart_row_draws_its_text_in_coral_at_rest_and_not_while_it_has_focus() {
        showHome()
        openSettingsMenu()
        val row = bounds("menu:PowerMenu")

        assertTrue("coral pixels at rest: ${pixelsOf(row, 0xFF8A80)}", pixelsOf(row, 0xFF8A80) >= 8)
        assertEquals("another row has no coral", 0, pixelsOf(bounds("menu:Reorder"), 0xFF8A80))
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("menu:PowerMenu").assertIsFocused()
        assertEquals("coral pixels under focus", 0, pixelsOf(row, 0xFF8A80))
    }

    @Test
    fun on_the_light_menu_the_restart_row_draws_its_text_in_dark_red_at_rest_and_not_the_pale_coral() {
        showHome()
        openSettingsMenu()
        val row = bounds("menu:PowerMenu")

        assertTrue("dark red pixels at rest: ${pixelsOf(row, 0xC62828)}", pixelsOf(row, 0xC62828) >= 8)
        assertEquals("pale coral on the light menu", 0, pixelsOf(row, 0xFF8A80))
        assertEquals("another row has no red", 0, pixelsOf(bounds("menu:Reorder"), 0xC62828))
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_line_above_the_restart_row_is_white_at_018() {
        showHome()
        openSettingsMenu()

        val rule = compose.onNodeWithTag("menu:divider").getBoundsInRoot()
        val ground = redAt(Offset((rule.left.value + rule.right.value) / 2, rule.top.value - 3f))
        val line = redAt(Offset((rule.left.value + rule.right.value) / 2, (rule.top.value + rule.bottom.value) / 2))

        assertNear(Math.round(ground + 0.18f * (255 - ground)), line, 3)
    }

    @Test
    fun the_inputs_list_does_not_end_on_its_line_when_the_row_after_it_does_not_fit() {
        showHome(inputs = InputsMenu(available = true, rows = (1..4).map { port(it) } + InputRow.Separator() + InputRow.LiveTv))
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        compose.onNodeWithTag("input:0").assertIsFocused()

        val list = bounds("menu")
        val line = compose.onNodeWithTag("menu:divider").getUnclippedBoundsInRoot()
        val live = compose.onNodeWithTag("input:5").getUnclippedBoundsInRoot()
        assertTrue("the last row of the list does not fit: list $list, live tv $live", live.bottom > list.bottom)
        assertTrue("the line $line shows at the bottom of the list $list", line.top >= list.bottom)
    }

    @Test
    fun the_inputs_list_ends_on_the_right_edge_of_the_top_bar() {
        val chat = (1..3).map { AppEntry(key = "chat.$it", packageName = "chat.$it", label = "Chat $it") }
        showHome(inputs = InputsMenu(available = true, rows = listOf(port(1))), topBarApps = chat)
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Inputs").assertIsFocused()
        press(Key.DirectionCenter)

        val settings = compose.onNodeWithContentDescription("Settings").getBoundsInRoot()
        assertNear(settings.right.value, bounds("menu").right.value, 1f)
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

    /**
     * The contrast ratio of the "No device" ink of the Inputs row 2 against the row beside the label, which has the focus
     * when [focused]. A glyph's stem shows its ink at full strength: it is the pixel farthest in luminance from the row.
     */
    private fun noDeviceContrast(focused: Boolean): Float {
        openInputs()
        if (focused) repeat(2) { press(Key.DirectionDown) }
        val text =
            compose
                .onNode(hasText("No device") and hasAnyAncestor(hasTestTag("input:2")), useUnmergedTree = true)
                .getBoundsInRoot()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        val row =
            pixels[((text.right.value + 6f) * density).toInt(), (((text.top.value + text.bottom.value) / 2) * density).toInt()]
                .luminance()
        val ink =
            (text.top.value * density).toInt().until((text.bottom.value * density).toInt()).flatMap { y ->
                (text.left.value * density).toInt().until((text.right.value * density).toInt()).map { x -> pixels[x, y].luminance() }
            }
        val far = ink.maxBy { abs(it - row) }
        return (maxOf(far, row) + 0.05f) / (minOf(far, row) + 0.05f)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_no_device_label_is_at_least_5_to_1_against_the_dark_menu() {
        val ratio = noDeviceContrast(focused = false)
        assertTrue("contrast $ratio", ratio >= 5f)
    }

    @Test
    fun the_no_device_label_is_at_least_5_to_1_against_the_light_menu() {
        val ratio = noDeviceContrast(focused = false)
        assertTrue("contrast $ratio", ratio >= 5f)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_no_device_label_is_at_least_5_to_1_against_the_focused_row_of_the_dark_menu() {
        val ratio = noDeviceContrast(focused = true)
        assertTrue("contrast $ratio", ratio >= 5f)
    }

    @Test
    fun the_no_device_label_is_at_least_5_to_1_against_the_focused_row_of_the_light_menu() {
        val ratio = noDeviceContrast(focused = true)
        assertTrue("contrast $ratio", ratio >= 5f)
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

    /** The darkest red channel, 0 to 255, over the button described as [description]: its glyph's ink when the fill is light. */
    private fun darkestRedOver(description: String): Int {
        val box = compose.onNodeWithContentDescription(description).getBoundsInRoot()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return (box.top.value * density).toInt().until((box.bottom.value * density).toInt()).minOf { y ->
            (box.left.value * density).toInt().until((box.right.value * density).toInt()).minOf { x -> Math.round(pixels[x, y].red * 255) }
        }
    }

    /** A red channel read through the menu's scrim (half of 07080C over it) as it was under it. */
    private fun withoutMenuScrim(red: Int): Float = (red - 7 * 0.5f) / 0.5f

    /**
     * While its panel is open, [opener] is drawn as when focused, with the white fill at 0.60: its fill, 4 dp in from its edge, is
     * 60 percent of the way from the idle [idle] button's to what [opener] read when focused, and its glyph is dark.
     */
    private fun assertFocusedLookAtSixtyPercent(
        opener: String,
        idle: String,
        focusedFill: Int,
    ) {
        val fill = withoutMenuScrim(buttonRed(opener, 4f))
        val rest = withoutMenuScrim(buttonRed(idle, 4f))
        val share = (fill - rest) / (focusedFill - rest)
        assertTrue("fill $fill of idle $rest and focused $focusedFill: $share", share in 0.52f..0.68f)
        assertTrue("the glyph's darkest red ${darkestRedOver(opener)}, fill $fill", darkestRedOver(opener) < fill / 2 - 30)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_inputs_button_keeps_its_focused_look_at_a_fill_of_060_while_its_list_is_open() {
        showHome()
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Inputs").assertIsFocused()
        val focusedFill = buttonRed("Inputs", 4f)
        press(Key.DirectionCenter)
        compose.onNodeWithTag("input:0").assertIsFocused()

        assertFocusedLookAtSixtyPercent(opener = "Inputs", idle = "Settings", focusedFill = focusedFill)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_settings_button_keeps_its_focused_look_at_a_fill_of_060_while_its_menu_is_open() {
        showHome()
        press(Key.DirectionUp)
        repeat(2) { press(Key.DirectionRight) }
        compose.onNodeWithContentDescription("Settings").assertIsFocused()
        val focusedFill = buttonRed("Settings", 4f)
        openTileMenu()

        assertFocusedLookAtSixtyPercent(opener = "Settings", idle = "Inputs", focusedFill = focusedFill)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_dock_draws_at_075_while_focus_is_in_the_top_bar_and_whole_again_once_focus_leaves_it() {
        showHome()
        val whole = redAt(centreOf("tile:pkg.2"))
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Inputs").assertIsFocused()
        val dimmed = redAt(centreOf("tile:pkg.2"))
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

        assertNear(255, whole, 2)
        // White art at 0.75 over the dark panel: 255 * 0.75 plus a quarter of what is behind it.
        assertTrue("a white tile reads $dimmed in top-bar focus", dimmed in 188..206)
        assertNear(255, redAt(centreOf("tile:pkg.2")), 2)
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
