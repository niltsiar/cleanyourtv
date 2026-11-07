package dev.niltsiar.tvhome.feature.home

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
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
            AppEntry(
                key = "pkg.$it",
                packageName = "pkg.$it",
                label = "App $it",
                image = bitmap(16, 9) { _, _ -> grey(245) },
                lightArt = true,
            )
        }
    private val darkApps =
        (1..9).map {
            AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = bitmap(16, 9) { _, _ -> grey(40) })
        }
    private val white = bitmap(40, 40) { _, _ -> grey(255) }
    private val night = bitmap(40, 40) { _, _ -> grey(20) }

    /** A frost of relative luminance 0.07 once baked, and one of 0.11 (mean L* 36): dark pictures under a light glass dock. */
    private val dusk = bitmap(40, 40) { _, _ -> grey(76) }
    private val twilight = bitmap(40, 40) { _, _ -> grey(90) }

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

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
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
    fun the_dock_panel_tints_the_frost_by_016_on_a_dim_wallpaper() {
        showHome(uniform)
        val at = gap()

        assertSame(over(dark, 0.16f, frostAt(uniform, at)), screenAt(at))
    }

    @Test
    fun a_bright_wallpaper_raises_the_tint_to_022() {
        showHome(white)
        val at = gap()

        assertTrue("L* ${glass.dockLightness}", glass.dockLightness >= 75f)
        assertSame(over(dark, 0.22f, frostAt(white, at)), screenAt(at))
    }

    @Test
    fun a_wallpaper_with_no_thumbnail_goes_back_to_the_base_tint_after_a_bright_one() {
        val wallpaper = MutableStateFlow(Wallpaper(thumbnail = white))
        showHome(white, wallpaper = wallpaper)
        assertTrue("L* ${glass.dockLightness}", glass.dockLightness >= 75f)

        wallpaper.value = Wallpaper()
        settle()

        assertEquals(0.16f, DockLook().tint(darkSurfaces = true, lightness = glass.dockLightness).alpha, 0.005f)
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

        assertSame(over(light, 0.20f, frostAt(uniform, at, glass = true)), screenAt(at))
    }

    /**
     * The mean relative luminance of the panel from [fromDp] to [toDp] below its top (the default: less 3 dp inside its
     * edges), less 3 dp inside its sides and 10 dp around each tile, which clears their rings.
     */
    private fun trayLuminance(
        fromDp: Float = 3f,
        toDp: Float? = null,
    ): Float {
        val panel = compose.onNodeWithTag("dock").getBoundsInRoot()
        val tiles = (1..6).map { dockTile(it) }
        val density = compose.activity.resources.displayMetrics.density
        val image = compose.onRoot().captureToImage().toPixelMap()
        var sum = 0.0
        var count = 0
        for (x in ((panel.left.value + 3f) * density).toInt() until ((panel.right.value - 3f) * density).toInt()) {
            for (y in ((panel.top.value + fromDp) * density).toInt() until
                ((toDp?.let { panel.top.value + it } ?: (panel.bottom.value - 3f)) * density).toInt()) {
                val near =
                    tiles.any {
                        x >= (it.left.value - 10f) * density && x <= (it.right.value + 10f) * density &&
                            y >= (it.top.value - 10f) * density && y <= (it.bottom.value + 10f) * density
                    }
                if (near) continue
                sum += image[x, y].luminance()
                count++
            }
        }
        return (sum / count).toFloat()
    }

    @Test
    fun focus_in_the_top_bar_darkens_a_light_tray_and_its_tiles_over_a_white_picture() {
        showHome(
            white,
            cinematic.copy(dockGlass = true),
            wallpaper = flowOf(Wallpaper(picture = white, thumbnail = white)),
            shown = lightApps,
        )
        val tray = trayLuminance()
        val tile = tileLuminance("tile:pkg.3")

        press(Key.DirectionUp)

        assertTrue("the tray goes from $tray to ${trayLuminance()}", trayLuminance() <= 0.8f * tray)
        assertTrue("a tile goes from $tile to ${tileLuminance("tile:pkg.3")}", tileLuminance("tile:pkg.3") <= 0.8f * tile)
    }

    private fun tileLuminance(tag: String): Float {
        val tile = compose.onNodeWithTag(tag).getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        return compose
            .onRoot()
            .captureToImage()
            .toPixelMap()[
            ((tile.left.value + tile.right.value) / 2f * density).toInt(), (
                (tile.top.value + tile.bottom.value) / 2f *
                    density
            ).toInt(),
        ].luminance()
    }

    @Test
    fun a_light_glass_tray_over_a_white_picture_keeps_a_mean_relative_luminance_under_057() {
        showHome(white, cinematic.copy(dockGlass = true))

        assertTrue("luminance ${trayLuminance()}", trayLuminance() <= 0.57f)
    }

    @Test
    fun on_a_dark_picture_a_light_glass_tray_lifts_the_picture_by_at_most_002_and_keeps_its_rim() {
        showHome(night, cinematic.copy(dockGlass = true))
        val at = gap()

        val lift = screenAt(at).luminance() - frostAt(night, at, glass = true).luminance()
        assertTrue("the tray lifts the picture by $lift", lift <= 0.023f)
        val inside = panelEdgeAt(7)
        assertEquals(0.30f, (panelEdgeAt(1) - inside) / (1f - inside), 0.03f)
    }

    @Test
    fun a_light_glass_tray_over_a_dark_frost_stays_under_012_and_2_5_times_the_frost_between_the_tiles() {
        showHome(dusk, cinematic.copy(dockGlass = true))
        val at = gap()

        val frost = frostAt(dusk, at, glass = true).luminance()
        val tray = screenAt(at).luminance()
        assertTrue("the frost is $frost", frost in 0.06f..0.08f)
        assertTrue("the tray is $tray over a frost of $frost", tray <= minOf(2.5f * frost, 0.12f))
    }

    @Test
    fun a_light_glass_tray_over_a_dark_frost_keeps_its_top_strip_under_020() {
        showHome(twilight, cinematic.copy(dockGlass = true))

        // 5 to 16.5 dp under the panel's top: below the rim, over the sheen.
        val strip = trayLuminance(fromDp = 5f, toDp = 16.5f)
        assertTrue("the strip is $strip", strip <= 0.20f)
    }

    @Test
    fun a_light_glass_tray_over_a_dark_frost_has_a_1_dp_white_030_line_on_its_top_edge() {
        showHome(dusk, cinematic.copy(dockGlass = true))
        val inside = panelEdgeAt(7)

        assertEquals(0.30f, (panelEdgeAt(1) - inside) / (1f - inside), 0.03f)
    }

    @Test
    fun a_light_glass_tray_over_a_white_picture_keeps_the_white_040_rim() {
        showHome(white, cinematic.copy(dockGlass = true))
        val inside = panelEdgeAt(7)

        assertEquals(0.40f, (panelEdgeAt(1) - inside) / (1f - inside), 0.03f)
    }

    /** The red channel of the screen [px] pixels under the panel's top (or over its bottom) in the gap between the third and fourth tiles. */
    private fun panelEdgeAt(
        px: Int,
        fromBottom: Boolean = false,
    ): Float {
        val panel = compose.onNodeWithTag("dock").getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val y = if (fromBottom) (panel.bottom.value * density).toInt() - 1 - px else (panel.top.value * density).toInt() + px
        return compose
            .onRoot()
            .captureToImage()
            .toPixelMap()[(gap().x * density).toInt(), y]
            .red
    }

    @Test
    fun the_dock_panel_has_a_1_5_dp_white_highlight_along_its_top_edge() {
        showHome(uniform)

        // At 2 pixels to the dp, row 2 lies 1 to 1.5 dp down, in the highlight; row 7 lies 3.5 dp down, below it.
        val lift = panelEdgeAt(2) - panelEdgeAt(7)
        assertTrue("the highlight lifts the red by $lift", lift in 0.05f..0.20f)
    }

    @Test
    fun the_dock_panel_has_a_1_dp_dark_edge_along_its_bottom() {
        showHome(uniform)

        val drop = panelEdgeAt(6, fromBottom = true) - panelEdgeAt(0, fromBottom = true)
        assertTrue("the edge darkens the red by $drop", drop in 0.05f..0.25f)
    }

    /** The red channel of the screen [outside] pixels over the panel's top edge in the middle of the panel, over a white wallpaper. */
    private fun redOutsideTop(outside: Int): Float {
        val panel = compose.onNodeWithTag("dock").getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        return compose
            .onRoot()
            .captureToImage()
            .toPixelMap()[((panel.left.value + panel.right.value) / 2f * density).toInt(), (panel.top.value * density).toInt() - outside]
            .red
    }

    @Test
    fun a_light_glass_tray_has_a_1_dp_black_030_hairline_round_its_outside() {
        showHome(white, cinematic.copy(dockGlass = true), wallpaper = flowOf(Wallpaper(picture = white, thumbnail = white)))

        // Row 1 lies in the hairline, 0.5 dp out; row 8 lies 4 dp out, past it.
        val dark = redOutsideTop(8) - redOutsideTop(1)
        assertTrue("the hairline darkens the red by $dark", dark in 0.15f..0.35f)
    }

    @Test
    fun a_dark_tray_has_no_hairline_round_its_outside() {
        showHome(white, wallpaper = flowOf(Wallpaper(picture = white, thumbnail = white)))

        assertEquals(redOutsideTop(8), redOutsideTop(1), 0.06f)
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

    /** The green channel over the left edge of tile [tag] (two pixels, the 1 dp edge) and 3 px inside it, at half its height. */
    private fun edgeOf(tag: String): Pair<List<Float>, Float> {
        val tile = compose.onNodeWithTag(tag).getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val image = compose.onRoot().captureToImage().toPixelMap()
        val y = ((tile.top.value + tile.bottom.value) / 2f * density).toInt()
        val left = (tile.left.value * density).toInt()
        return (left..left + 1).map { image[it, y].green } to image[left + 3, y].green
    }

    private fun assertEdge(
        tag: String,
        level: Float,
    ) {
        val (edge, inside) = edgeOf(tag)
        assertEquals("edge $edge", level * (1f - 0.58f), edge.max(), 0.03f)
        assertEquals("edge $edge", level * (1f - 0.58f), edge.min(), 0.03f)
        assertEquals(level, inside, 0.01f)
    }

    private fun assertNoEdge(
        tag: String,
        level: Float,
    ) {
        val (edge, inside) = edgeOf(tag)
        assertEquals("edge $edge", level, edge.min(), 0.02f)
        assertEquals("edge $edge", level, edge.max(), 0.02f)
        assertEquals(level, inside, 0.02f)
    }

    private val level245 = 245f / 255f

    @Test
    fun dock_glass_draws_a_black_058_edge_1_dp_wide_inside_each_light_dock_tile() {
        showHome(uniform, cinematic.copy(dockGlass = true), shown = lightApps)

        assertEdge("tile:pkg.2", level245)
    }

    private val whiteApps = (1..9).map { lightApps[it - 1].copy(image = bitmap(16, 9) { _, _ -> grey(255) }) }

    /** The relative luminance of the lighter of the two pixels of the 1 dp edge of tile [tag], of its art 3 px inside, and of the tray 4 dp outside it. */
    private fun edgeAndNeighbours(tag: String): Triple<Float, Float, Float> {
        val tile = compose.onNodeWithTag(tag).getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val image = compose.onRoot().captureToImage().toPixelMap()
        val y = ((tile.top.value + tile.bottom.value) / 2f * density).toInt()
        val left = (tile.left.value * density).toInt()
        val edge = maxOf(image[left, y].luminance(), image[left + 1, y].luminance())
        return Triple(edge, image[left + 3, y].luminance(), image[left - (4 * density).toInt(), y].luminance())
    }

    @Test
    fun on_the_lightest_tray_the_edge_of_a_white_tile_is_at_least_008_darker_than_its_art_and_the_tray() {
        showHome(
            white,
            cinematic.copy(dockGlass = true),
            wallpaper = flowOf(Wallpaper(picture = white, thumbnail = white)),
            shown = whiteApps,
        )

        val (edge, art, tray) = edgeAndNeighbours("tile:pkg.3")
        assertTrue("the edge is $edge, its art $art, the tray $tray", edge <= art - 0.08f && edge <= tray - 0.08f)
    }

    private val brightWallpaper get() = flowOf(Wallpaper(picture = white, thumbnail = white))

    @Test
    fun a_light_dock_tile_over_a_bright_wallpaper_gets_the_edge_on_the_dark_tray() {
        showHome(white, wallpaper = brightWallpaper, shown = lightApps)

        assertEdge("tile:pkg.2", level245)
    }

    /** The 1 dp edge of the white tile [tag] holds 3 to 1 against the surface 4 dp outside it. */
    private fun assertEdgeHolds(tag: String) {
        val (edge, _, outside) = edgeAndNeighbours(tag)
        val contrast = (outside + 0.05f) / (edge + 0.05f)
        assertTrue("the edge is $edge, the surface $outside: $contrast", contrast >= 3f)
    }

    @Test
    fun the_edge_of_a_white_dock_tile_holds_3_to_1_against_the_dark_tray_over_a_bright_wallpaper() {
        showHome(white, wallpaper = brightWallpaper, shown = whiteApps)

        assertEdgeHolds("tile:pkg.6")
    }

    @Test
    fun the_edge_of_a_white_dock_tile_holds_3_to_1_against_the_glass_tray_over_a_bright_wallpaper() {
        showHome(white, cinematic.copy(dockGlass = true), wallpaper = brightWallpaper, shown = whiteApps)

        assertEdgeHolds("tile:pkg.6")
    }

    @Test
    fun the_edge_of_a_white_grid_tile_holds_3_to_1_against_a_bright_wallpaper() {
        showHome(white, wallpaper = brightWallpaper, shown = whiteApps)
        press(Key.DirectionDown)

        assertEdgeHolds("tile:pkg.9")
    }

    @Test
    fun the_light_tray_of_a_day_mode_dock_gives_a_light_tile_the_same_edge() {
        showHome(uniform, darkTheme = false, shown = lightApps)

        assertEdge("tile:pkg.2", level245)
    }

    @Test
    fun a_dark_tray_gives_a_light_tile_no_edge() {
        showHome(uniform, shown = lightApps)

        assertNoEdge("tile:pkg.2", level245)
    }

    @Test
    fun a_dark_tile_gets_no_edge_on_the_light_tray() {
        showHome(uniform, cinematic.copy(dockGlass = true), shown = darkApps)

        assertNoEdge("tile:pkg.2", 40f / 255f)
    }

    @Test
    fun a_light_grid_tile_over_a_bright_wallpaper_gets_the_edge() {
        showHome(white, shown = lightApps)
        press(Key.DirectionDown)

        assertEdge("tile:pkg.8", level245)
    }

    @Test
    fun a_light_grid_tile_over_a_dim_wallpaper_gets_no_edge() {
        showHome(uniform, shown = lightApps)
        press(Key.DirectionDown)

        assertNoEdge("tile:pkg.8", level245)
    }

    @Test
    fun a_dark_grid_tile_over_a_bright_wallpaper_gets_no_edge() {
        showHome(white, shown = darkApps)
        press(Key.DirectionDown)

        assertNoEdge("tile:pkg.8", 40f / 255f)
    }

    @Test
    fun the_dock_rim_is_white_040_at_the_top_edge() {
        showHome(uniform)
        val inside = panelEdgeAt(7)

        assertEquals(0.40f, (panelEdgeAt(1) - inside) / (1f - inside), 0.03f)
    }

    @Test
    fun the_dock_rim_fades_to_white_008_at_the_bottom_edge_under_the_black_025_one() {
        showHome(uniform)
        val inside = panelEdgeAt(7, fromBottom = true)
        val rim = panelEdgeAt(0, fromBottom = true) / 0.75f

        assertEquals(0.08f, (rim - inside) / (1f - inside), 0.015f)
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

        assertSame(over(light, 0.20f, frostAt(uniform, at)), screenAt(at))
    }

    @Test
    fun with_dock_band_off_the_tiles_float_on_the_wallpaper() {
        showHome(uniform, cinematic.copy(dockBand = false))

        assertSame(Color.Black, screenAt(gap()), tolerance = 14)
    }

    /** The tint over [gradient]'s frost: a wallpaper this bright puts the guard to work. */
    private fun gradientTint() = dark.copy(alpha = DockLook().tint(darkSurfaces = true, lightness = glass.dockLightness).alpha)

    @Test
    fun the_frost_stays_with_the_wallpaper_under_the_scrim_of_a_dock_tiles_menu_with_the_dock_where_it_was() {
        showHome(gradient)
        val before = compose.onNodeWithTag("dock").getBoundsInRoot().top

        holdOk()

        val after = compose.onNodeWithTag("dock").getBoundsInRoot().top
        assertTrue("the dock moved from $before to $after", abs((before - after).value) < 1f)
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
