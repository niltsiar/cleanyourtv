package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
import java.time.ZoneId
import kotlin.math.abs

/** The band over the grid and the grid's edges on the cinematic Home, read from the rendered pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeBandLookUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }

    /** A dock of six and two grid rows of six, all white. */
    private val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = white) }
    private val config = SixColumns.copy(cinematicHome = true, appLibrary = true)
    private var libraryOpen by mutableStateOf(false)

    private fun showHome(background: Color = Color.Black) {
        val opened =
            Evening
                .minusHours(2)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val sources = sources(apps = flowOf(AppList(apps)), lastOpened = flowOf(mapOf("pkg.1" to opened)), libraryCount = flowOf(23))
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
                    wallpaperLayout = WallpaperLayout(background = background),
                    shelfLayout = config.shelfLayout().copy(scrimAlpha = 0f, ambientGlow = false),
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

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    private fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    private val density get() = compose.activity.resources.displayMetrics.density

    /** The red channel, 0 to 255, of the screen at ([x], [y]). */
    private fun redAt(
        x: Dp,
        y: Dp,
    ): Int = Math.round(screen()[(x.value * density).toInt(), (y.value * density).toInt()].red * 255)

    /** The brightest red channel in [area], 0 to 255. */
    private fun brightest(area: DpRect): Int {
        val pixels = screen()
        var most = 0f
        for (y in (area.top.value * density).toInt() until (area.bottom.value * density).toInt()) {
            for (x in (area.left.value * density).toInt() until (area.right.value * density).toInt()) {
                most = maxOf(most, pixels[x, y].red)
            }
        }
        return Math.round(most * 255)
    }

    private fun assertNear(
        expected: Int,
        actual: Int,
        tolerance: Int,
    ) = assertTrue("expected $expected, got $actual", abs(actual - expected) <= tolerance)

    /** The band's title line, 300 dp of it. */
    private fun titleArea(tag: String): DpRect = bounds(tag).let { DpRect(it.left, it.top, it.left + 300.dp, it.bottom) }

    /** The meta line under the title and the page dots under that. */
    private fun detailArea(metaTag: String): DpRect = bounds(metaTag).let { DpRect(it.left, it.top, it.left + 300.dp, it.bottom + 24.dp) }

    private fun tile(n: Int): DpRect = bounds("tile:pkg.$n")

    /** The runs of lit pixels along the middle of the page dots, as start to end in dp from the dots' left edge. */
    private fun dotRuns(): List<ClosedFloatingPointRange<Float>> {
        val dots = bounds("shelf:dots")
        val pixels = screen()
        val y = ((dots.top.value + dots.bottom.value) / 2f * density).toInt()
        val lit = (0 until (dots.width.value * density).toInt()).map { pixels[(dots.left.value * density).toInt() + it, y].red > 0.1f }
        val runs = mutableListOf<ClosedFloatingPointRange<Float>>()
        var start = -1
        for ((i, on) in (lit + false).withIndex()) {
            if (on && start < 0) start = i
            if (!on && start >= 0) {
                runs += (start / density)..(i / density)
                start = -1
            }
        }
        return runs
    }

    @Test
    fun the_page_dots_are_8_dp_with_8_dp_between_them_the_focused_one_an_18_dp_pill_and_start_at_the_titles_left_edge() {
        showHome()

        val dots = bounds("shelf:dots")
        assertEquals(8f, dots.height.value, 0.1f)
        assertEquals(bounds("shelf:name").left.value, dots.left.value, 0.5f)
        val runs = dotRuns()
        assertEquals(listOf(18f, 8f, 8f, 8f, 8f, 8f), runs.map { it.endInclusive - it.start })
        assertEquals(List(5) { 8f }, runs.zipWithNext { before, after -> after.start - before.endInclusive })
        assertEquals(0f, runs.first().start, 0.5f)
    }

    @Test
    fun the_inactive_page_dots_read_at_4_5_to_1_on_a_near_black_wallpaper_and_stay_dimmer_than_the_active_one() {
        val wallpaper = Color(0xFF070A13)
        showHome(wallpaper)
        val dots = bounds("shelf:dots")
        val pixels = screen()
        val middle = ((dots.top.value + dots.bottom.value) / 2f * density).toInt()

        // The active dot is 18 dp wide, and the dots after it are 8 dp wide with 8 dp between.
        val active = pixels[((dots.left.value + 9f) * density).toInt(), middle]
        val inactive = pixels[((dots.left.value + 30f) * density).toInt(), middle]

        fun contrast(
            lighter: Color,
            darker: Color,
        ) = (lighter.luminance() + 0.05f) / (darker.luminance() + 0.05f)

        assertTrue("inactive dot $inactive on $wallpaper: ${contrast(inactive, wallpaper)}", contrast(inactive, wallpaper) >= 4.5f)
        assertTrue("active dot $active, inactive dot $inactive: ${contrast(active, inactive)}", contrast(active, inactive) >= 3f)
    }

    @Test
    fun the_app_library_fades_out_the_dock_apps_name_time_and_dots() {
        showHome()
        assertTrue(brightest(titleArea("shelf:name")) >= 230)
        assertTrue(brightest(detailArea("shelf:meta")) >= 230)

        libraryOpen = true
        settle()

        // Black under the library's scrim.
        val dimmed = Math.round(0x07 * 0.78f)
        assertNear(dimmed, brightest(titleArea("shelf:name")), 2)
        assertNear(dimmed, brightest(detailArea("shelf:meta")), 2)

        libraryOpen = false
        settle()
        assertTrue(brightest(titleArea("shelf:name")) >= 230)
    }

    @Test
    fun the_app_library_opened_from_the_pill_fades_out_the_librarys_name_and_count() {
        showHome()
        press(Key.DirectionDown, times = 3)
        compose.onNodeWithTag("library:pill").assertIsFocused()
        assertTrue(brightest(titleArea("shelf:title")) >= 230)

        libraryOpen = true
        settle()

        val dimmed = Math.round(0x07 * 0.78f)
        assertNear(dimmed, brightest(titleArea("shelf:title")), 2)
        assertNear(dimmed, brightest(detailArea("shelf:title-meta")), 2)
    }

    @Test
    fun on_the_top_bar_the_band_names_the_button_at_full_brightness_and_the_page_dots_are_gone() {
        showHome()

        press(Key.DirectionUp)

        assertTrue(brightest(titleArea("shelf:title")) >= 230)
        assertTrue(brightest(detailArea("shelf:title-meta")) >= 230)
        val dots = bounds("shelf:title-meta").let { DpRect(it.left, it.bottom + 16.dp, it.left + 300.dp, it.bottom + 24.dp) }
        assertNear(0, brightest(dots), 2)

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        assertTrue(brightest(titleArea("shelf:name")) >= 230)
        assertTrue(brightest(detailArea("shelf:meta")) >= 230)
    }

    @Test
    fun rows_above_the_focused_library_pill_draw_at_85_percent() {
        showHome()
        press(Key.DirectionDown, times = 3)
        compose.onNodeWithTag("library:pill").assertIsFocused()

        // The layer multiplies each draw: the white art, and the tile's own fill under it.
        val above = tile(13)
        val fill = 0x1B * 0.85f
        assertNear(Math.round(0.85f * 255 + 0.15f * fill), redAt((above.left + above.right) / 2, (above.top + above.bottom) / 2), 4)
    }

    @Test
    fun a_dock_scrolled_up_for_the_grid_leaves_nothing_above_the_grid() {
        val grey = 0x80
        showHome(background = Color(grey, grey, grey))
        val resting = bounds("dock")
        val inside = resting.bottom - 4.dp
        assertTrue(abs(redAt(resting.left + 200.dp, inside) - grey) > 6)

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        val dock = bounds("dock")
        assertNear(grey, redAt(dock.left + 200.dp, dock.bottom - 4.dp), 1)
        assertNear(grey, redAt(dock.left + 200.dp, dock.bottom + 4.dp), 1)
    }
}
