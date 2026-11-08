package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.height
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.SkyPalette
import dev.niltsiar.tvhome.core.model.Wallpaper
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

private const val WHITE = 0xFFFFFFFF.toInt()

/** How Home draws over its wallpaper, read from the rendered pixels: the scrim, the grid's edges and the dimmed tiles. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeLookUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }

    private fun whiteApps(count: Int) =
        (1..count).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = white) }

    /** Nine white tiles, except pkg.5, whose clear art shows what each tile draws under its art. */
    private fun whiteAppsAndOneClear() = whiteApps(9).map { if (it.key == "pkg.5") it.copy(image = ImageBitmap(16, 9)) else it }

    private fun showHome(
        apps: List<AppEntry> = whiteApps(9),
        wallpaper: Wallpaper = Wallpaper(),
        config: TvConfig = SixColumns,
        skyIntensity: Int = 0,
    ) {
        val sources = sources(apps = flowOf(AppList(apps)), wallpaper = flowOf(wallpaper))
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
                    wallpaperLayout = WallpaperLayout(skyIntensity = skyIntensity),
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

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    private fun screen(): DpRect = compose.onRoot().getBoundsInRoot()

    /** The red channel, 0 to 255, of the screen at [point] (in dp). */
    private fun redAt(point: Offset): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(pixels[(point.x * density).toInt(), (point.y * density).toInt()].red * 255)
    }

    private fun centreOf(rect: DpRect) = Offset((rect.left.value + rect.right.value) / 2, (rect.top.value + rect.bottom.value) / 2)

    private fun dockTile(n: Int): DpRect =
        compose.onNode(hasTestTag("tile:pkg.$n") and hasAnyAncestor(hasTestTag("dock"))).getBoundsInRoot()

    /** A layer at [alpha] multiplies each of its draws, so white art reads [alpha] x 255 over what the tile drew under it. */
    private fun assertDrawnAt(
        alpha: Float,
        white: Offset,
        clear: Offset,
    ) {
        val under = redAt(clear)
        assertNear(Math.round(alpha * 255 + (1 - alpha) * under), redAt(white), 4)
    }

    private fun assertNear(
        expected: Float,
        actual: Float,
        tolerance: Float,
    ) = assertTrue("expected $expected, got $actual", abs(actual - expected) <= tolerance)

    private fun assertNear(
        expected: Int,
        actual: Int,
        tolerance: Int,
    ) = assertTrue("expected $expected, got $actual", abs(actual - expected) <= tolerance)

    @Test
    fun the_scrim_darkens_the_top_left_of_a_white_wallpaper_and_leaves_its_bottom_right_alone() {
        showHome(apps = whiteApps(1), wallpaper = Wallpaper(picture = white))
        val screen = screen()

        // Black 0.55 from the top over black 0.40 from the left: 255 x 0.45 x 0.60.
        assertNear(69, redAt(Offset(1f, 1f)), 4)
        assertNear(255, redAt(Offset(screen.right.value - 2f, screen.bottom.value - 2f)), 3)
    }

    @Test
    fun at_rest_the_top_fade_leaves_the_dock_panel_whole() {
        showHome(wallpaper = Wallpaper(picture = white))
        val dock = bounds("dock")
        val tile = bounds("tile:pkg.6")
        val x = (tile.left.value + tile.right.value) / 2

        val rim = redAt(Offset(x, dock.top.value + 3f))
        val body = redAt(Offset(x, (dock.top.value + tile.top.value) / 2 + 4f))
        assertNear(body, rim, 10)
    }

    @Test
    fun rows_above_the_focused_row_draw_at_alpha_0() {
        showHome(apps = whiteAppsAndOneClear())
        assertNear(255, redAt(centreOf(dockTile(6))), 2)

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        assertDrawnAt(0f, centreOf(dockTile(6)), centreOf(dockTile(5)))
    }

    @Test
    fun the_row_above_the_last_grid_row_draws_at_85_percent_with_the_library_pill_focused_when_it_clears_the_title_block() {
        showHome(apps = whiteApps(30), config = SixColumns.copy(appLibrary = true))
        repeat(5) { press(Key.DirectionDown) }
        compose.onNodeWithTag("library:pill").assertIsFocused()

        // The layer multiplies each draw: the white art, and the tile's own fill under it.
        val fill = 0x1B * 0.85f
        assertNear(0, redAt(centreOf(bounds("tile:pkg.13"))), 2)
        assertNear(Math.round(0.85f * 255 + 0.15f * fill), redAt(centreOf(bounds("tile:pkg.19"))), 4)
        assertNear(Math.round(0.85f * 255 + 0.15f * fill), redAt(centreOf(bounds("tile:pkg.25"))), 4)
    }

    @Test
    fun with_focus_on_the_third_row_the_second_shows_whole_below_the_title_block_and_the_first_is_gone() {
        showHome(apps = whiteApps(24), config = SixColumns.copy(appLibrary = true))
        repeat(3) { press(Key.DirectionDown) }
        compose.onNodeWithTag("tile:pkg.19").assertIsFocused()

        assertNear(255, redUnderTop("tile:pkg.13", 10f), 2)
        assertNear(0, redUnderTop("tile:pkg.7", 10f), 2)
        assertTrue("the second row starts at ${bounds("tile:pkg.13").top}", bounds("tile:pkg.13").top.value >= 240f)
        assertTrue("the pill ends at ${bounds("library:pill").bottom}", bounds("library:pill").bottom.value <= screen().bottom.value - 12f)
    }

    /** How far under the top bar the title's capitals start: its line box top, 36 sp to the baseline, less the capitals' 0.72 of 48 sp. */
    private fun titleGap() = bounds("shelf:title").top.value + 36f - 0.72f * 48f - (TopBarTop + TopBarHeight).value

    @Test
    fun with_focus_in_the_grid_the_title_stands_36_dp_under_the_top_bar() {
        showHome()
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        assertTrue("the title is ${titleGap()} dp under the top bar", titleGap() >= 36f)
    }

    @Test
    fun in_a_single_row_the_title_stands_36_dp_under_the_top_bar() {
        showHome(config = SixColumns.copy(singleRow = true))

        assertTrue("the title is ${titleGap()} dp under the top bar", titleGap() >= 36f)
    }

    /** The red channel over the middle of tile [tag], [below] dp under its top edge. */
    private fun redUnderTop(
        tag: String,
        below: Float,
    ): Int {
        val tile = bounds(tag)
        return redAt(Offset((tile.left.value + tile.right.value) / 2, tile.top.value + below))
    }

    /** The red channel over the middle of tile [tag], [above] dp over its bottom edge. */
    private fun redAboveBottom(
        tag: String,
        above: Float,
    ): Int {
        val tile = bounds(tag)
        return redAt(Offset((tile.left.value + tile.right.value) / 2, tile.bottom.value - above))
    }

    /** How many dp of tile [tag] show above the screen's bottom edge. */
    private fun visibleDp(tag: String): Float = screen().bottom.value - bounds(tag).top.value

    @Test
    @Config(qualifiers = "w960dp-h548dp-xhdpi")
    fun a_grid_row_with_under_48_dp_visible_draws_at_alpha_0() {
        showHome(apps = whiteApps(30), config = SixColumns.copy(columns = 5))

        assertTrue("the row shows ${visibleDp("tile:pkg.6")} dp", visibleDp("tile:pkg.6") in 40f..48f)
        assertNear(0, redUnderTop("tile:pkg.6", 2f), 2)
        assertNear(0, redUnderTop("tile:pkg.6", 10f), 2)
    }

    @Test
    @Config(qualifiers = "w960dp-h580dp-xhdpi")
    fun a_grid_row_with_48_dp_above_the_edge_but_less_left_after_the_bottom_fade_draws_nothing() {
        showHome(apps = whiteApps(30), config = SixColumns.copy(columns = 4))

        assertTrue("the row shows ${visibleDp("tile:pkg.5")} dp", visibleDp("tile:pkg.5") in 48f..56f)
        assertNear(0, redUnderTop("tile:pkg.5", 2f), 2)
    }

    @Test
    fun a_grid_row_with_70_dp_above_the_edge_draws_nothing_when_the_bottom_fade_leaves_20_dp_of_its_tile() {
        showHome(apps = whiteApps(30), config = SixColumns.copy(columns = 5))
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.6").assertIsFocused()

        // The third row starts 24 dp under the last whole row, whose bottom is 8 dp short of the fade's start, and the fade runs
        // 36 dp: the row has 20 dp of its tile before it is gone.
        assertTrue("the row shows ${visibleDp("tile:pkg.16")} dp", visibleDp("tile:pkg.16") in 60f..84f)
        assertNear(0, redUnderTop("tile:pkg.16", 2f), 2)
        assertNear(0, redUnderTop("tile:pkg.16", 10f), 2)
    }

    @Test
    fun a_row_that_scrolls_in_from_the_bottom_edge_draws_again_once_it_is_whole() {
        showHome(apps = whiteApps(30), config = SixColumns.copy(columns = 4))
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.5").assertIsFocused()
        assertTrue("row 3 shows ${visibleDp("tile:pkg.13")} dp", visibleDp("tile:pkg.13") < 48f)
        assertNear(0, redUnderTop("tile:pkg.13", 2f), 2)

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()

        assertTrue("the row shows ${visibleDp("tile:pkg.13")} dp", visibleDp("tile:pkg.13") >= bounds("tile:pkg.1").height.value)
        assertNear(255, redAboveBottom("tile:pkg.13", 1f), 2)
    }

    @Test
    fun in_the_grid_layout_the_bottom_fade_leaves_the_last_fully_visible_row_whole_and_has_the_next_row_gone_by_y_1060_of_1080() {
        showHome(apps = whiteApps(30))
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        val screen = screen().bottom.value
        assertTrue("row 2 ends at ${bounds("tile:pkg.20").bottom}", bounds("tile:pkg.20").bottom.value < screen - 12f)
        assertTrue("row 3 starts at ${bounds("tile:pkg.26").top}", bounds("tile:pkg.26").top.value < screen)

        assertNear(255, redAboveBottom("tile:pkg.20", 1f), 2)
        val limit = screen * 1060f / 1080f
        val tile = bounds("tile:pkg.26")
        assertTrue("under the limit", redAt(Offset((tile.left.value + tile.right.value) / 2, limit + 1f)) < 4)
    }

    @Test
    fun at_the_end_of_the_grid_the_last_row_stays_whole() {
        showHome(apps = whiteApps(30))
        repeat(4) { press(Key.DirectionDown) }
        compose.onNodeWithTag("tile:pkg.25").assertIsFocused()

        assertNear(255, redAboveBottom("tile:pkg.26", 1f), 2)
    }

    @Test
    fun no_row_above_the_focused_one_leaves_a_band_under_the_shelf() {
        showHome(apps = whiteApps(30))
        repeat(2) { press(Key.DirectionDown) }
        compose.onNodeWithTag("tile:pkg.13").assertIsFocused()
        val above = bounds("tile:pkg.8")
        val x = (above.left.value + above.right.value) / 2

        assertNear(0, redAt(Offset(x, above.bottom.value - 1f)), 3)
        assertNear(0, redAt(Offset(x, above.bottom.value - 30f)), 3)
    }

    @Test
    fun a_tile_of_another_launcher_says_its_name_at_the_bottom_left_of_its_banner_and_an_ordinary_tile_says_nothing() {
        showHome(apps = whiteApps(9).map { if (it.key == "pkg.2") it.copy(label = "Inicio", homeLauncher = true) else it })

        val tile = bounds("tile:pkg.2")
        val name = compose.onNodeWithTag("name:pkg.2", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the name is at $name in $tile", name.left.value - tile.left.value in 0f..16f)
        assertTrue("the name is at $name in $tile", tile.bottom.value - name.bottom.value in 0f..16f)
        compose.onAllNodesWithTag("name:pkg.3", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun the_scrim_darkens_the_top_right_corner_over_the_sky_too() {
        val whiteSky = SkyPalette(top = WHITE, mid = WHITE, bottom = WHITE, glow = WHITE, sun = 0, sunX = 0.5f)
        showHome(apps = whiteApps(1), wallpaper = Wallpaper(picture = white, sky = whiteSky), skyIntensity = 50)

        // Black 0.55 at the top, where the left scrim has run out: 255 x 0.45.
        assertNear(115, redAt(Offset(screen().right.value - 2f, 1f)), 4)
    }

    @Test
    fun the_frost_bakes_the_scrim_over_the_sky_too() {
        val whiteSky = SkyPalette(top = WHITE, mid = WHITE, bottom = WHITE, glow = WHITE, sun = 0, sunX = 0.5f)
        val thumbnail = ImageBitmap(40, 40).also { Canvas(it).drawRect(Rect(0f, 0f, 40f, 40f), Paint().apply { color = Color.White }) }

        val frost = bakeFrost(thumbnail, whiteSky, skyAlpha = 0.5f).toPixelMap()

        // About half the 0.55 top scrim survives the blur at the top right; the sky over it would lift it past 180.
        assertTrue(Math.round(frost[39, 0].red * 255) < 150)
    }

    private fun carryFirstTile() {
        press(Key.Menu)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
    }

    @Test
    fun in_reorder_mode_the_tiles_not_carried_draw_at_sixty_five_percent() {
        showHome(apps = whiteAppsAndOneClear())
        val white = centreOf(dockTile(6))
        val clear = centreOf(dockTile(5))

        carryFirstTile()

        assertDrawnAt(0.65f, white, clear)
    }

    @Test
    fun in_reorder_mode_with_nothing_carried_no_tile_is_dimmed() {
        showHome(apps = whiteAppsAndOneClear())
        val white = centreOf(dockTile(6))
        val clear = centreOf(dockTile(5))

        carryFirstTile()
        back()

        assertDrawnAt(1f, white, clear)
    }

    /** The top and bottom, in dp, of the run of white art that crosses [y] in the column at [x]. */
    private fun artSpan(
        x: Float,
        y: Float,
    ): Pair<Float, Float> {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density

        fun white(row: Int) = pixels[(x * density).toInt(), row].green > 0.8f
        val centre = (y * density).toInt()
        var top = centre
        while (white(top - 1)) top--
        var bottom = centre
        while (white(bottom + 1)) bottom++
        return top / density to (bottom + 1) / density
    }

    /** The ring reaches 9 dp past the art of a tile at its rest size. */
    private val ringReach = 9f

    @Test
    fun a_carried_tile_lifts_7_dp_and_is_drawn_at_its_rest_size_not_grown_like_a_focused_tile() {
        showHome(apps = whiteApps(9))
        val neighbour = dockTile(2)
        val middle = (neighbour.top.value + neighbour.bottom.value) / 2
        val at = bounds("tile:pkg.1")
        val x = (at.left.value + at.right.value) / 2
        val (restTop, restBottom) = artSpan(x, middle)

        carryFirstTile()
        val (top, bottom) = artSpan(x, middle)

        // A focused tile is drawn 1.08 times its size.
        assertNear((restBottom - restTop) / 1.08f, bottom - top, 1f)
        assertNear((restTop + restBottom) / 2 - 7f, (top + bottom) / 2, 1f)
    }

    @Test
    fun with_names_a_carried_tile_is_drawn_where_it_is_without_them_lifted_7_dp_at_its_rest_size() {
        showHome(apps = whiteApps(9), config = SixColumns.copy(alwaysShowNames = true))
        val rest = bounds("tile:pkg.1")
        val x = (rest.left.value + rest.right.value) / 2
        val middle = (rest.top.value + rest.bottom.value) / 2

        carryFirstTile()
        val (top, bottom) = artSpan(x, middle - 7f)

        assertNear(rest.top.value - 7f, top, 0.5f)
        assertNear(rest.bottom.value - 7f, bottom, 0.5f)
    }

    /** Whether the screen holds a white pixel in the box from ([left], [top]) to ([right], [bottom]), in dp. */
    private fun whiteIn(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Boolean {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return (((left * density).toInt()) until (right * density).toInt()).any { x ->
            (((top * density).toInt()) until (bottom * density).toInt()).any { y -> pixels[x, y].green > 0.8f }
        }
    }

    /** Apps whose art is clear, so that only a chevron is white over the dark tile. */
    private fun clearApps(count: Int) = whiteApps(count).map { it.copy(image = ImageBitmap(16, 9)) }

    /** Whether a white chevron stands in the gutter on [side] (-1 left, +1 right) of dock slot [slot] (from 1), past the ring, where the dock tiles [rest] stood before the carry. */
    private fun chevronBeside(
        rest: List<DpRect>,
        slot: Int,
        side: Int,
    ): Boolean {
        val tile = rest[slot - 1]
        val from =
            if (side <
                0
            ) {
                rest.getOrNull(slot - 2)?.right?.value ?: (tile.left.value - TileGap.value)
            } else {
                tile.right.value + ringReach
            }
        val to = if (side < 0) tile.left.value - ringReach else rest.getOrNull(slot)?.left?.value ?: (tile.right.value + TileGap.value)
        return whiteIn(from, tile.top.value - 10f, to, tile.bottom.value)
    }

    private fun restTiles(count: Int) = (1..count).map { dockTile(it) }

    @Test
    @Config(qualifiers = "+night")
    fun the_carried_tile_shows_a_chevron_in_the_gutter_only_on_the_sides_it_can_move_to() {
        showHome(apps = clearApps(3))
        val rest = restTiles(3)

        carryFirstTile()
        assertTrue("first slot, right", chevronBeside(rest, 1, +1))
        assertTrue("first slot, left", !chevronBeside(rest, 1, -1))

        press(Key.DirectionRight)
        assertTrue("middle slot, right", chevronBeside(rest, 2, +1))
        assertTrue("middle slot, left", chevronBeside(rest, 2, -1))

        press(Key.DirectionRight)
        assertTrue("last slot, right", !chevronBeside(rest, 3, +1))
        assertTrue("last slot, left", chevronBeside(rest, 3, -1))
    }

    @Test
    @Config(qualifiers = "+night")
    fun an_arrows_white_stroke_covers_none_of_the_carried_tiles_art_and_none_of_the_tiles_beside_it() {
        showHome(apps = clearApps(3))
        val rest = restTiles(3)
        carryFirstTile()
        press(Key.DirectionRight)
        assertTrue("both chevrons show", chevronBeside(rest, 2, -1) && chevronBeside(rest, 2, +1))

        val carried = rest[1]
        assertTrue("the art", !whiteIn(carried.left.value, carried.top.value, carried.right.value, carried.bottom.value))
        for (neighbour in listOf(rest[0], rest[2])) {
            assertTrue(
                "the tile beside it",
                !whiteIn(neighbour.left.value, neighbour.top.value, neighbour.right.value, neighbour.bottom.value),
            )
        }
    }

    /** The runs of columns, in dp, that hold a pixel with green over [level] (white is 1) at some row from [top] to [bottom] dp, between [from] and [to] dp. */
    private fun whiteRuns(
        from: Float,
        to: Float,
        top: Float,
        bottom: Float,
        level: Float = 0.8f,
    ): List<ClosedFloatingPointRange<Float>> {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        val rows = (top * density).toInt() until (bottom * density).toInt()
        val runs = mutableListOf<ClosedFloatingPointRange<Float>>()
        var start = -1
        for (x in (from * density).toInt()..(to * density).toInt()) {
            val white = rows.any { pixels[x, it].green > level }
            if (white && start < 0) start = x
            if (!white && start >= 0) {
                runs += (start / density)..(x / density)
                start = -1
            }
        }
        if (start >= 0) runs += (start / density)..to
        return runs
    }

    private fun isRingOrange(pixel: Color) = pixel.red > 0.9f && pixel.green in 0.3f..0.55f && pixel.blue < 0.4f

    /** The first row, in dp, from [from] down to [to] dp whose pixel in the column at [x] dp is the reorder ring's orange. */
    private fun ringTop(
        x: Float,
        from: Float,
        to: Float,
    ): Float {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return ((from * density).toInt()..(to * density).toInt()).first { isRingOrange(pixels[(x * density).toInt(), it]) } / density
    }

    /** A reorder arrow stands on a dark disc this many dp across. */
    private val arrowDisc = 22f

    /** What [carriedClearances] reads, in dp. */
    private data class ArrowClearances(
        /** From the disc's outer edge to the next tile's art, on the right then on the left. */
        val nextRight: Float,
        val nextLeft: Float,
        /** From the ring's outer edge out to the disc's centre, on the right then on the left; zero when it is centred on the edge. */
        val centreRight: Float,
        val centreLeft: Float,
        /** The ring's room under the tray's rim. */
        val underRim: Float,
    )

    /**
     * Carries the first of [tiles] dock tiles, of clear art among white, to [slot] (from 1) and reads where its arrows stand:
     * each disc is centred on its chevron, whose white stroke is the only white in the gutter, and is [arrowDisc] across.
     */
    private fun carriedClearances(
        tiles: Int,
        slot: Int,
        config: TvConfig,
    ): ArrowClearances {
        showHome(apps = clearApps(1) + whiteApps(tiles).drop(1), config = config)
        val rest = restTiles(tiles)
        // Below the arrows and clear of the tile's rounded corners.
        val artRow = (rest[1].top.value + rest[1].bottom.value) / 2f + 14f
        carryFirstTile()
        repeat(slot - 1) { press(Key.DirectionRight) }
        val at = rest[slot - 1]
        // The ring's dark hairline and stroke reach 3 dp past its orange.
        val halo = 3f
        val middle = (at.top.value + at.bottom.value) / 2f - 7f
        val centre = (at.left.value + at.right.value) / 2f
        // The carried tile is drawn at its rest size, so its ring's outer edge is where the tile's bounds put it.
        val ringRight = at.right.value + ringReach
        val ringLeft = at.left.value - ringReach

        // The disc covers the ring there, so the chevron's whole width is searched for from well inside the ring.
        val chevronRight = whiteRuns(ringRight - 8f, at.right.value + 80f, middle - 10f, middle + 10f).single()
        val chevronLeft = whiteRuns(at.left.value - 80f, ringLeft + 8f, middle - 10f, middle + 10f).single()
        val centreRight = (chevronRight.start + chevronRight.endInclusive) / 2f
        val centreLeft = (chevronLeft.start + chevronLeft.endInclusive) / 2f
        // The tiles beside it are dimmed to 0.65, and their art starts and ends at the row below the arrows.
        val neighbourRight = whiteRuns(ringRight, at.right.value + 80f, artRow, artRow + 1f, level = 0.5f).first().start
        val neighbourLeft = whiteRuns(at.left.value - 80f, ringLeft, artRow, artRow + 1f, level = 0.5f).last().endInclusive
        val underRim = ringTop(centre, bounds("dock").top.value, at.top.value + 40f) - halo - bounds("dock").top.value

        return ArrowClearances(
            nextRight = neighbourRight - (centreRight + arrowDisc / 2f),
            nextLeft = (centreLeft - arrowDisc / 2f) - neighbourLeft,
            centreRight = centreRight - ringRight,
            centreLeft = ringLeft - centreLeft,
            underRim = underRim,
        )
    }

    private fun assertCarriedClearances(
        tiles: Int,
        slot: Int,
        config: TvConfig = SixColumns,
    ) {
        val clearances = carriedClearances(tiles, slot, config)

        assertTrue(
            "disc clearance from the next tile: $clearances",
            clearances.nextRight >= 4f && clearances.nextLeft >= 4f,
        )
        assertTrue(
            "disc centre past the ring's outer edge: $clearances",
            abs(clearances.centreRight) <= 1f && abs(clearances.centreLeft) <= 1f,
        )
        assertTrue("the ring is ${clearances.underRim} dp under the rim", clearances.underRim >= 7.5f)
    }

    /** A 100 px square of [ground], 2 px to a dp, with an arrow centred in it. */
    private fun arrowOn(
        ground: Color,
        pointsLeft: Boolean = false,
    ): PixelMap {
        val image = ImageBitmap(100, 100)
        Canvas(image).drawRect(Rect(0f, 0f, 100f, 100f), Paint().apply { color = ground })
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(image), Size(100f, 100f)) {
            drawChevron(Offset(50f, 50f), pointsLeft = pointsLeft, alpha = 1f)
        }
        return image.toPixelMap()
    }

    @Test
    fun an_arrow_stands_on_a_disc_22_dp_across_of_black_at_60_percent() {
        val pixels = arrowOn(Color.White)

        // On a white ground the disc reads 0.4; the row through the centre also crosses the chevron's tip, which is lighter.
        val columns = (0 until 100).filter { pixels[it, 50].red < 0.9f }
        val across = (columns.max() - columns.min() + 1) / 2f
        assertEquals("the disc's width in dp", 22f, across, 0.6f)
        assertEquals("the disc's fill, left of the chevron", 0.4f, pixels[36, 50].red, 0.03f)
        assertEquals("the disc's fill, above the chevron", 0.4f, pixels[50, 34].red, 0.03f)
    }

    @Test
    fun an_arrows_chevron_is_a_12_dp_tall_6_dp_deep_white_stroke_with_round_caps_and_no_outline() {
        val pixels = arrowOn(Color.White)

        // At 2 px to a dp the chevron's ends are at (44, 38) and (44, 62) and its tip at (56, 50); the round caps reach 2.5 px past
        // them. White at 0.95 over the disc's 0.4 reads 0.97.
        for ((stroke, clear) in listOf(36 to 34, 63 to 65)) {
            assertEquals("the stroke at row $stroke", 0.97f, pixels[44, stroke].red, 0.03f)
            assertEquals("the disc's fill at row $clear", 0.4f, pixels[44, clear].red, 0.03f)
        }
        assertEquals("the tip", 0.97f, pixels[56, 50].red, 0.03f)
        assertEquals("past the tip", 0.4f, pixels[60, 50].red, 0.03f)
    }

    @Test
    fun an_arrows_chevron_points_the_way_it_names() {
        val pixels = arrowOn(Color.White, pointsLeft = true)

        assertEquals("the tip, left of the centre", 0.97f, pixels[44, 50].red, 0.03f)
        assertEquals("past the tip", 0.4f, pixels[40, 50].red, 0.03f)
        assertEquals("the open end", 0.4f, pixels[56, 50].red, 0.03f)
    }

    @Test
    fun an_arrows_white_stroke_holds_3_to_1_against_its_disc_on_a_near_black_tile_and_on_a_white_one() {
        fun contrast(
            first: Color,
            second: Color,
        ) = (maxOf(first.luminance(), second.luminance()) + 0.05f) / (minOf(first.luminance(), second.luminance()) + 0.05f)

        val nearBlack = Color(0xFF0F1115)
        val white = Color(0xFFF5F5F5)

        val onDark = arrowOn(nearBlack).let { contrast(it[44, 36], it[36, 50]) }
        val onLight = arrowOn(white).let { contrast(it[44, 36], it[36, 50]) }
        assertTrue("the stroke on near black: $onDark, on white: $onLight", onDark >= 3f && onLight >= 3f)
    }

    @Test
    @Config(qualifiers = "+night")
    fun with_four_dock_tiles_a_carried_tiles_arrows_clear_the_next_tile_by_4_dp_and_its_ring_stays_8_dp_under_the_rim() {
        assertCarriedClearances(tiles = 4, slot = 2)
    }

    @Test
    @Config(qualifiers = "+night")
    fun with_five_dock_tiles_a_carried_tiles_arrows_clear_the_next_tile_by_4_dp_and_its_ring_stays_8_dp_under_the_rim() {
        assertCarriedClearances(tiles = 5, slot = 2)
    }

    @Test
    @Config(qualifiers = "+night")
    fun with_six_dock_tiles_a_carried_tiles_arrows_clear_the_next_tile_by_4_dp_and_its_ring_stays_8_dp_under_the_rim() {
        assertCarriedClearances(tiles = 6, slot = 2)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_arrows_keep_their_clearances_with_the_carried_tile_in_the_middle_of_a_full_dock() {
        assertCarriedClearances(tiles = 6, slot = 4)
    }

    @Test
    @Config(qualifiers = "+night")
    fun with_four_columns_a_carried_tiles_arrows_keep_their_clearances() {
        assertCarriedClearances(tiles = 4, slot = 2, config = TvConfig(columns = 4))
    }

    /** The left edge, in dp, of the white art that covers [x] dp at [row] dp. */
    private fun artStart(
        x: Float,
        row: Float,
    ): Float = whiteRuns(x - 60f, x + 60f, row, row + 1f, level = 0.5f).first { x in it }.start

    @Test
    @Config(qualifiers = "+night")
    fun with_a_tile_carried_no_other_tile_moves_so_every_gap_stays_at_its_rest_value() {
        showHome(apps = whiteApps(6))
        val rest = restTiles(6)
        val row = rest[1].top.value + 6f
        val artStarts = rest.map { artStart((it.left.value + it.right.value) / 2f, row) }

        carryFirstTile()
        for (slot in 1..6) {
            if (slot > 1) press(Key.DirectionRight)
            for (other in (1..6).filter { it != slot }) {
                val at = rest[other - 1]
                assertEquals(
                    "the tile in slot $other with the carried tile in slot $slot",
                    artStarts[other - 1],
                    artStart((at.left.value + at.right.value) / 2f, row),
                    0.6f,
                )
            }
        }
    }

    /** The red channel just outside a chip, inside its backing, and on its rim. */
    private data class ChipPixels(
        val outside: Int,
        val inside: Int,
        val rim: Int,
    )

    /** Reads the chip tagged [tag] over a white wallpaper. */
    private fun chipPixels(tag: String): ChipPixels {
        val chip = bounds("reorder:chip:$tag")
        val middle = (chip.top.value + chip.bottom.value) / 2
        val x = (chip.left.value + chip.right.value) / 2
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density

        fun red(
            px: Float,
            py: Float,
        ) = Math.round(pixels[(px * density).toInt(), (py * density).toInt()].red * 255)
        // The rim is 1 dp, two pixels, along the top edge; the brighter of its two rows.
        val rim = maxOf(red(x, chip.top.value + 0.25f), red(x, chip.top.value + 0.75f))
        return ChipPixels(outside = red(chip.left.value - 6f, middle), inside = red(chip.left.value + 5f, middle), rim = rim)
    }

    /** Backing #07080C at 0.55, then the rim, white at 0.20, both under the chip's [alpha]. */
    private fun assertChipAt(
        tag: String,
        alpha: Float,
    ) {
        val (outside, inside, rim) = chipPixels(tag)
        assertNear(Math.round(outside * (1 - 0.55f * alpha) + 7 * 0.55f * alpha), inside, 5)
        assertNear(Math.round(inside + 0.20f * alpha * (255 - inside)), rim, 8)
    }

    /** The chip tagged [tag] says [key] and [action], and is drawn at full alpha. */
    private fun assertChip(
        tag: String,
        key: String,
        action: String,
    ) {
        compose.onNode(hasText(key) and hasAnyAncestor(hasTestTag("reorder:chip:$tag"))).assertExists()
        compose.onNode(hasText(action) and hasAnyAncestor(hasTestTag("reorder:chip:$tag"))).assertExists()
        assertChipAt(tag, 1f)
    }

    @Test
    fun with_nothing_carried_the_legend_is_ok_pick_up_and_back_finish_and_nothing_else() {
        showHome(wallpaper = Wallpaper(picture = white))
        carryFirstTile()
        back()

        assertChip("ok", "OK", "pick up")
        assertChip("back", "Back", "finish")
        compose.onNodeWithTag("reorder:chip:arrows").assertDoesNotExist()
    }

    @Test
    fun with_a_tile_carried_the_legend_is_arrows_move_and_one_chip_for_the_two_keys_that_drop_it_and_nothing_else() {
        showHome(wallpaper = Wallpaper(picture = white))
        carryFirstTile()

        assertChip("arrows", "Arrows", "move")
        assertChip("drop", "OK", "drop")
        compose.onNode(hasText("Back") and hasAnyAncestor(hasTestTag("reorder:chip:drop"))).assertExists()
        compose.onAllNodes(hasText("drop")).assertCountEquals(1)
        compose.onNodeWithTag("reorder:chip:ok").assertDoesNotExist()
        compose.onNodeWithTag("reorder:chip:back").assertDoesNotExist()
        compose.onAllNodes(hasText("pick up")).assertCountEquals(0)
        compose.onAllNodes(hasText("finish")).assertCountEquals(0)
    }
}
