package dev.niltsiar.tvhome.feature.home

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.unit.width
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * The dock panel read from the rendered pixels. The wallpaper's picture is left out, so the screen is black except for
 * the frost the dock draws from the wallpaper's thumbnail: a pixel in the gap between two dock tiles is the frost under
 * the tint.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class DockGlassUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var glass: GlassState
    private val apps = (1..9).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }
    private val cinematic = SixColumns.copy(cinematicHome = true, appLibrary = true)

    private fun bitmap(
        width: Int,
        height: Int,
        pixel: (x: Int, y: Int) -> Int,
    ): ImageBitmap =
        Bitmap
            .createBitmap(
                IntArray(width * height) { pixel(it % width, it / width) },
                width,
                height,
                Bitmap.Config.ARGB_8888,
            ).asImageBitmap()

    private fun grey(level: Int) = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    private val uniform = bitmap(40, 40) { _, _ -> (0xFF shl 24) or (180 shl 16) or (110 shl 8) or 60 }
    private val cyan = bitmap(40, 40) { _, _ -> (0xFF shl 24) or (0 shl 16) or (200 shl 8) or 255 }
    private val lightApps =
        (1..9).map {
            AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = bitmap(16, 9) { _, _ -> grey(255) })
        }
    private val white = bitmap(40, 40) { _, _ -> grey(255) }

    /** Black at the top to white at the bottom, so a frost drawn a few dp off its place reads as another grey. */
    private val gradient = bitmap(40, 40) { _, y -> grey(y * 255 / 39) }

    private fun showHome(
        thumbnail: ImageBitmap,
        config: TvConfig = cinematic,
        darkTheme: Boolean = true,
        wallpaper: Flow<Wallpaper> = flowOf(Wallpaper(thumbnail = thumbnail)),
        shown: List<AppEntry> = apps,
        docked: Int = 0,
    ) {
        val sources = sources(apps = flowOf(AppList(shown, docked)), wallpaper = wallpaper)
        compose.setContent {
            TvTheme(darkTheme = darkTheme) {
                glass = rememberGlassState()
                val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = config.topBarLayout(),
                    shelfLayout = config.shelfLayout().copy(scrimAlpha = 0f),
                    dockLook = config.dockLook(),
                    actions = viewModel.actions,
                    glass = glass,
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

    private fun holdOk() {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
    }

    private fun dockTile(n: Int): DpRect =
        compose.onNode(hasTestTag("tile:pkg.$n") and hasAnyAncestor(hasTestTag("dock"))).getBoundsInRoot()

    /** The middle of the gap between the third and fourth dock tiles, half way down the panel: no tile, ring or sheen is there. */
    private fun gap(): Offset {
        val panel = compose.onNodeWithTag("dock").getBoundsInRoot()
        return Offset((dockTile(3).right.value + dockTile(4).left.value) / 2f, (panel.top.value + panel.bottom.value) / 2f)
    }

    private fun screenAt(point: Offset): Color {
        val density = compose.activity.resources.displayMetrics.density
        return compose.onRoot().captureToImage().toPixelMap()[(point.x * density).toInt(), (point.y * density).toInt()]
    }

    /** The frost [thumbnail] bakes to, as it lies behind [point] on the screen; with [glass] as the dock's glass holds it. */
    private fun frostAt(
        thumbnail: ImageBitmap,
        point: Offset,
        glass: Boolean = false,
    ): Color {
        val baked = bakeFrost(thumbnail, null, 0f)
        if (glass) {
            val pixels = IntArray(baked.width * baked.height).also { baked.readPixels(it) }
            clampSaturation(pixels, 0.35f)
            baked.asAndroidBitmap().setPixels(pixels, 0, baked.width, 0, 0, baked.width, baked.height)
        }
        val frost = baked.toPixelMap()
        val screen = compose.onRoot().getBoundsInRoot()
        val x = (point.x / screen.right.value * frost.width - 0.5f).coerceIn(0f, frost.width - 1f)
        val y = (point.y / screen.bottom.value * frost.height - 0.5f).coerceIn(0f, frost.height - 1f)
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = minOf(x0 + 1, frost.width - 1)
        val y1 = minOf(y0 + 1, frost.height - 1)

        fun row(
            ry: Int,
            channel: (Color) -> Float,
        ) = channel(frost[x0, ry]) * (1 - (x - x0)) + channel(frost[x1, ry]) * (x - x0)

        fun mix(channel: (Color) -> Float) = row(y0, channel) * (1 - (y - y0)) + row(y1, channel) * (y - y0)
        return Color(mix { it.red }, mix { it.green }, mix { it.blue })
    }

    private fun over(
        tint: Color,
        alpha: Float,
        under: Color,
    ) = Color(
        under.red * (1 - alpha) + tint.red * alpha,
        under.green * (1 - alpha) + tint.green * alpha,
        under.blue * (1 - alpha) + tint.blue * alpha,
    )

    private fun assertSame(
        expected: Color,
        actual: Color,
        tolerance: Int = 6,
    ) {
        val gap = listOf(expected.red - actual.red, expected.green - actual.green, expected.blue - actual.blue).maxOf { abs(it) * 255 }
        assertTrue("expected $expected, got $actual", gap <= tolerance)
    }

    private val dark = Color(0xFF0B0D12)
    private val light = Color(0xFFF4F6FA)

    @Test
    fun the_dock_panel_tints_the_frost_by_032_on_a_dim_wallpaper() {
        showHome(uniform)
        val at = gap()

        assertSame(over(dark, 0.32f, frostAt(uniform, at)), screenAt(at))
    }

    @Test
    fun a_bright_wallpaper_raises_the_tint_to_042() {
        showHome(white)
        val at = gap()

        assertTrue("L* ${glass.dockLightness}", glass.dockLightness >= 75f)
        assertSame(over(dark, 0.42f, frostAt(white, at)), screenAt(at))
    }

    @Test
    fun a_wallpaper_with_no_thumbnail_goes_back_to_the_base_tint_after_a_bright_one() {
        val wallpaper = MutableStateFlow(Wallpaper(thumbnail = white))
        showHome(white, wallpaper = wallpaper)
        assertTrue("L* ${glass.dockLightness}", glass.dockLightness >= 75f)

        wallpaper.value = Wallpaper()
        settle()

        assertEquals(0.32f, DockLook().tint(darkSurfaces = true, lightness = glass.dockLightness).alpha, 0.005f)
    }

    @Test
    fun dock_opacity_sets_the_tint_to_085_times_the_percent_whatever_the_wallpaper() {
        showHome(white, cinematic.copy(dockOpacity = 50))
        val at = gap()

        assertSame(over(dark, 0.85f * 0.5f, frostAt(white, at)), screenAt(at))
    }

    @Test
    fun dock_glass_takes_the_light_tint_in_night_mode() {
        showHome(uniform, cinematic.copy(dockGlass = true))
        val at = gap()

        assertSame(over(light, 0.36f, frostAt(uniform, at, glass = true)), screenAt(at))
    }

    private fun saturation(colour: Color): Float {
        val high = maxOf(colour.red, colour.green, colour.blue)
        val low = minOf(colour.red, colour.green, colour.blue)
        return (high - low) / (1f - abs(high + low - 1f))
    }

    @Test
    fun dock_glass_holds_a_saturated_wallpaper_to_035_saturation_so_a_sky_colour_does_not_pool() {
        showHome(cyan, cinematic.copy(dockGlass = true))

        assertTrue("saturation ${saturation(screenAt(gap()))}", saturation(screenAt(gap())) <= 0.36f)
    }

    @Test
    fun dock_glass_draws_a_black_012_edge_inside_each_dock_tile() {
        showHome(uniform, cinematic.copy(dockGlass = true), shown = lightApps)
        val tile = dockTile(2)
        val density = compose.activity.resources.displayMetrics.density
        val image = compose.onRoot().captureToImage().toPixelMap()
        val y = ((tile.top.value + tile.bottom.value) / 2f * density).toInt()
        val left = (tile.left.value * density).toInt()
        val edge = (left..left + 2).map { image[it, y].green }

        assertEquals("edge $edge", 1f - 0.12f, edge.min(), 0.03f)
        assertEquals(1f, image[left + 20, y].green, 0.01f)
    }

    @Test
    fun a_dock_with_four_tiles_in_a_row_of_six_is_a_panel_for_four_centred_with_28_dp_at_the_sides() {
        showHome(uniform, cinematic.copy(fillDock = false), docked = 4)
        val panel = compose.onNodeWithTag("dock").getBoundsInRoot()
        val screen = compose.onRoot().getBoundsInRoot()
        val first = dockTile(1)
        val second = dockTile(2)
        val column = (880f - 5 * 24f) / 6f

        assertEquals(4 * column + 3 * 24f + 2 * 28f, panel.width.value, 1f)
        assertEquals(screen.width.value / 2f, (panel.left.value + panel.right.value) / 2f, 1f)
        assertEquals(28f, first.left.value - panel.left.value, 1f)
        assertEquals(24f, second.left.value - first.right.value, 1f)
        assertEquals(column, first.width.value, 1f)
    }

    @Test
    fun a_dock_with_a_full_row_keeps_the_panel_one_gap_wider_than_the_grid_on_each_side() {
        showHome(uniform)
        val panel = compose.onNodeWithTag("dock").getBoundsInRoot()

        assertEquals(880f + 2 * 24f, panel.width.value, 1f)
        assertEquals(24f, dockTile(1).left.value - panel.left.value, 1f)
    }

    @Test
    fun without_night_mode_the_tint_is_the_light_one() {
        showHome(uniform, darkTheme = false)
        val at = gap()

        assertSame(over(light, 0.36f, frostAt(uniform, at)), screenAt(at))
    }

    @Test
    fun with_dock_band_off_the_tiles_float_on_the_wallpaper() {
        showHome(uniform, cinematic.copy(dockBand = false))

        assertSame(Color.Black, screenAt(gap()), tolerance = 14)
    }

    /** The tint over [gradient]'s frost: a wallpaper this bright puts the guard to work. */
    private fun gradientTint() = dark.copy(alpha = DockLook().tint(darkSurfaces = true, lightness = glass.dockLightness).alpha)

    @Test
    fun the_frost_stays_with_the_wallpaper_when_a_tile_menu_lifts_the_dock_without_scrolling_the_grid() {
        showHome(gradient)
        val before = compose.onNodeWithTag("dock").getBoundsInRoot().top

        holdOk()

        val after = compose.onNodeWithTag("dock").getBoundsInRoot().top
        assertTrue("the dock rose from $before to $after", (before - after).value > 100f)
        val at = gap()
        // The menu's scrim darkens everything but the tile it acts on by 0.5 of #07080C.
        val scrim = Color(0xFF07080C)
        val tint = gradientTint()
        assertSame(over(scrim, 0.5f, over(tint, tint.alpha, frostAt(gradient, at))), screenAt(at), tolerance = 8)
    }

    @Test
    fun the_frost_stays_with_the_wallpaper_when_the_dock_goes_to_the_grid_and_back() {
        showHome(gradient)
        val at = gap()
        val tint = gradientTint()
        assertSame(over(tint, tint.alpha, frostAt(gradient, at)), screenAt(at), tolerance = 8)

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        settle()

        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        assertSame(over(tint, tint.alpha, frostAt(gradient, at)), screenAt(at), tolerance = 8)
    }
}
