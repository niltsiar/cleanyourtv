package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
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
import dev.niltsiar.tvhome.core.model.SkyPalette
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun the_row_under_the_low_dock_shows_at_most_40_px_of_its_top_at_half_alpha() {
        showHome(apps = whiteApps(30))
        val screen = screen().bottom.value

        // 40 px of the 1080 px frame are 20 dp here.
        assertTrue("the row starts at ${bounds("tile:pkg.8").top}", bounds("tile:pkg.8").top.value + 21f < screen)
        assertNear(Math.round(255 * 0.5f * (1 - 2f / 20f)), redUnderTop("tile:pkg.8", 2f), 8)
        assertNear(Math.round(255 * 0.5f * (1 - 10f / 20f)), redUnderTop("tile:pkg.8", 10f), 8)
        assertTrue("past the peek ${redUnderTop("tile:pkg.8", 21f)}", redUnderTop("tile:pkg.8", 21f) < 8)
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

    /** The green channel, 0 to 255, of the screen at [point] (in dp): white art reads 255, the coral ring of a carried tile 107. */
    private fun greenAt(point: Offset): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(pixels[(point.x * density).toInt(), (point.y * density).toInt()].green * 255)
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

    @Test
    fun a_carried_tile_lifts_eight_dp_and_grows_eight_percent_over_the_focused_tile() {
        showHome(apps = whiteApps(9))
        val neighbour = dockTile(2)
        val middle = (neighbour.top.value + neighbour.bottom.value) / 2
        val at = bounds("tile:pkg.1")
        val x = (at.left.value + at.right.value) / 2
        val (restTop, restBottom) = artSpan(x, middle)

        carryFirstTile()
        val (top, bottom) = artSpan(x, middle)

        val height = restBottom - restTop
        assertNear(height * 1.08f, bottom - top, 1f)
        assertNear((restTop + restBottom) / 2 - 8f, (top + bottom) / 2, 1f)
    }

    /** Whether the screen is white just outside the [side] edge of the carried tile's art, where only a chevron badge reaches. */
    private fun badgeOn(
        side: Int,
        key: String,
    ): Boolean {
        val tile = bounds("tile:$key")
        val middle = (tile.left.value + tile.right.value) / 2
        // The badge sits on the layer's edge; the art, 1.08 larger, ends 5 or 6 dp past it, and the badge 12.
        val edge = middle + side * (tile.right.value - tile.left.value) / 2
        return greenAt(Offset(edge + side * 9f, (tile.top.value + tile.bottom.value) / 2)) > 230
    }

    @Test
    fun the_carried_tile_shows_a_chevron_only_on_the_sides_it_can_move_to() {
        showHome(apps = whiteApps(3))

        carryFirstTile()
        assertTrue("first tile, right", badgeOn(+1, "pkg.1"))
        assertTrue("first tile, left", !badgeOn(-1, "pkg.1"))

        press(Key.DirectionRight)
        assertTrue("middle tile, right", badgeOn(+1, "pkg.1"))
        assertTrue("middle tile, left", badgeOn(-1, "pkg.1"))

        press(Key.DirectionRight)
        assertTrue("last tile, right", !badgeOn(+1, "pkg.1"))
        assertTrue("last tile, left", badgeOn(-1, "pkg.1"))
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

    @Test
    fun with_nothing_carried_the_ok_and_finish_chips_are_at_full_alpha_and_the_others_at_sixty_percent() {
        showHome(wallpaper = Wallpaper(picture = white))
        carryFirstTile()
        back()

        assertChipAt("ok", 1f)
        assertChipAt("back-twice", 1f)
        assertChipAt("arrows", 0.60f)
        assertChipAt("back", 0.60f)
    }

    @Test
    fun with_a_tile_carried_the_move_and_drop_chips_are_at_full_alpha_and_the_others_at_sixty_percent() {
        showHome(wallpaper = Wallpaper(picture = white))
        carryFirstTile()

        assertChipAt("arrows", 1f)
        assertChipAt("back", 1f)
        assertChipAt("ok", 0.60f)
        assertChipAt("back-twice", 0.60f)
    }
}
