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
    fun rows_above_the_focused_row_draw_at_forty_percent() {
        showHome(apps = whiteAppsAndOneClear())
        assertNear(255, redAt(centreOf(dockTile(6))), 2)

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        assertDrawnAt(0.40f, centreOf(dockTile(6)), centreOf(dockTile(5)))
    }

    @Test
    fun the_first_grid_row_peeking_under_the_dock_fades_out_over_the_bottom_56_dp_of_the_screen() {
        showHome(apps = whiteApps(30))
        val bottom = screen().bottom.value
        val peek = bounds("tile:pkg.8")

        assertTrue("the row starts at ${peek.top}, above the fade", peek.top.value < bottom - 56f)
        assertNear(Math.round(255 * 20f / 56f), redOverColumnTwo(20f), 4)
        assertTrue(redOverColumnTwo(3f) < 40)
    }

    /** The red channel over the second column of tiles, [fromBottom] dp above the screen's bottom. */
    private fun redOverColumnTwo(fromBottom: Float): Int {
        val column = bounds("tile:pkg.8")
        return redAt(Offset((column.left.value + column.right.value) / 2, screen().bottom.value - fromBottom))
    }

    @Test
    fun the_bottom_fade_grows_to_80_dp_while_the_grid_is_scrolled_and_goes_on_below_the_screen() {
        showHome(apps = whiteApps(30))
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        assertNear(Math.round(255 * 70f / 80f), redOverColumnTwo(70f), 4)
        assertNear(Math.round(255 * 40f / 80f), redOverColumnTwo(40f), 4)
    }

    @Test
    fun the_bottom_fade_is_56_dp_at_the_end_of_the_grid() {
        showHome(apps = whiteApps(30))
        repeat(4) { press(Key.DirectionDown) }
        compose.onNodeWithTag("tile:pkg.25").assertIsFocused()

        assertNear(255, redOverColumnTwo(70f), 4)
        assertNear(Math.round(255 * 52f / 56f), redOverColumnTwo(52f), 4)
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

    @Test
    fun in_reorder_mode_the_tiles_not_carried_draw_at_seventy_percent() {
        showHome(apps = whiteAppsAndOneClear())
        val white = centreOf(dockTile(6))
        val clear = centreOf(dockTile(5))

        press(Key.Menu)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertDrawnAt(0.70f, white, clear)
    }
}
