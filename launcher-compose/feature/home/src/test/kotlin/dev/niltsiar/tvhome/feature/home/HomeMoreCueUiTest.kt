package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
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
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * With rows of apps below the screen, Home draws a down chevron in a pill in the band under the last row that shows, in
 * white at 0.95 on black at 0.60 so that it reads on any wallpaper. The wallpaper here is white, so the pill reads as 0.40 grey.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeMoreCueUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val density get() = compose.activity.resources.displayMetrics.density

    private fun art(colour: Color) =
        ImageBitmap(16, 9).also { Canvas(it).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = colour }) }

    private val inks = listOf(Color(0xFF1B4F9C), Color(0xFFB33A3A), Color(0xFF2E7D5B))

    /** The layout Home is drawn in; a test may change it after [showHome]. */
    private var layout by mutableStateOf(TvConfig().homeLayout())

    /** [count] coloured tiles: the first [TvConfig.columns] are the dock, the rest the grid. */
    private fun showHome(
        config: TvConfig = TvConfig(columns = 4),
        count: Int = 19,
    ) {
        val apps = (1..count).map { AppEntry("pkg.$it", "pkg.$it", "App $it", image = art(inks[it % inks.size])) }
        val sources = sources(apps = flowOf(AppList(apps)))
        layout = config.homeLayout()
        compose.setContent {
            TvTheme {
                val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = layout,
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = config.topBarLayout(),
                    wallpaperLayout = WallpaperLayout(background = Color.White),
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

    /** The bottom of tile [tag] in pixels. */
    private fun bottomOf(tag: String): Float =
        compose
            .onNodeWithTag(tag)
            .getBoundsInRoot()
            .bottom.value * density

    /**
     * The screen's pixels in the column 5.5 dp left of its middle, which the pill covers over its full height and the chevron
     * crosses at a few rows only, and which runs through the gap between the two middle columns of four.
     */
    private fun column(): Pair<PixelMap, Int> {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels to (pixels.width / 2 - (5.5f * density).toInt())
    }

    /** The first and last row, in pixels, of the pill in [column] below [from], or null when no pixel there is dark. */
    private fun pill(from: Float): IntRange? {
        val (pixels, x) = column()
        val dark = (from.toInt() until pixels.height).filter { y -> pixels[x, y].red < 0.5f && pixels[x, y].green < 0.5f }
        return if (dark.isEmpty()) null else dark.first()..dark.last()
    }

    private fun assertNear(
        expected: Float,
        actual: Float,
        by: Float = 2f,
    ) = assertTrue("expected $expected, got $actual", abs(expected - actual) <= by)

    @Test
    fun the_default_grid_with_rows_below_the_screen_has_the_cue_centred_in_the_band_under_its_second_row() {
        showHome()

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.5").assertIsFocused()

        val rowBottom = bottomOf("tile:pkg.9")
        val screen =
            compose
                .onRoot()
                .getBoundsInRoot()
                .bottom.value * density
        val pill = pill(from = rowBottom)
        assertNotNull("no cue under the second row", pill)
        assertNear(20f * density, (pill!!.last - pill.first + 1).toFloat())
        assertNear((rowBottom + screen) / 2f, (pill.first + pill.last + 1) / 2f)
    }

    @Test
    fun the_pill_is_black_at_60_percent_and_the_chevron_white_at_95_percent() {
        showHome()
        press(Key.DirectionDown)

        val pill = pill(from = bottomOf("tile:pkg.9"))!!
        val (pixels, x) = column()
        val middle = (pill.first + pill.last) / 2
        // Beside the chevron: 7 dp left of the middle, the pill's own colour.
        assertEquals(0.4f, pixels[pixels.width / 2 - (7f * density).toInt(), middle].red, 0.03f)
        // The tip of the chevron, 4 dp under the pill's middle on the centre line, in the chevron's own colour.
        val tip = pixels[pixels.width / 2, middle + (4f * density).toInt()]
        assertTrue("chevron reads ${tip.red} at $x", tip.red > 0.9f)
    }

    @Test
    fun the_cue_stays_while_focus_moves_down_the_visible_rows_and_goes_when_the_grid_scrolls_to_its_last_row_with_no_recomposition() {
        showHome()
        press(Key.DirectionDown)
        val homeContent = count("HomeContent")
        val before = homeContent()
        assertNotNull("cue with focus on the first row", pill(from = bottomOf("tile:pkg.9")))

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
        assertNotNull("cue with focus on the second row", pill(from = bottomOf("tile:pkg.9")))

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.17").assertIsFocused()
        assertNull("cue with the last row on screen", pill(from = bottomOf("tile:pkg.17")))
        assertEquals("HomeContent recomposed with the focus moves", before, homeContent())
    }

    @Test
    fun grid_scroll_cue_false_draws_no_cue_where_the_default_draws_one() {
        showHome()
        press(Key.DirectionDown)
        assertNotNull("cue with grid_scroll_cue on", pill(from = bottomOf("tile:pkg.9")))

        layout = TvConfig(columns = 4, gridScrollCue = false).homeLayout()
        settle()
        compose.onNodeWithTag("tile:pkg.5").assertIsFocused()
        assertNull("cue with grid_scroll_cue false", pill(from = bottomOf("tile:pkg.9")))
    }

    @Test
    fun the_recomposition_count_sees_home_content_run_when_its_layout_changes() {
        showHome()
        press(Key.DirectionDown)
        val homeContent = count("HomeContent")
        val before = homeContent()

        layout = layout.copy(showNames = true)
        settle()

        assertTrue("HomeContent runs: before $before, after ${homeContent()}", homeContent() > before)
    }

    @Test
    fun the_cue_is_partly_transparent_while_the_row_it_marks_fades_in_as_focus_enters_the_grid() {
        showHome()
        val screen = compose.onRoot().captureToImage()
        // Inside the pill, 7 dp left of the middle: white wallpaper until the cue comes, 0.40 grey at its full strength.
        // Row 2 ends at 990 of 1080 and the band's middle is 1035.
        val x = screen.width / 2 - (7f * density).toInt()
        val y = screen.height * 1035 / 1080

        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        val seen = mutableListOf<Float>()
        repeat(60) {
            compose.mainClock.advanceTimeByFrame()
            seen +=
                compose
                    .onRoot()
                    .captureToImage()
                    .toPixelMap()[x, y]
                    .red
        }

        assertTrue("never partly transparent: $seen", seen.any { it in 0.5f..0.9f })
    }

    @Test
    fun there_is_no_cue_with_every_row_of_apps_on_screen() {
        showHome(count = 12)

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.5").assertIsFocused()

        assertNull(pill(from = bottomOf("tile:pkg.9")))
    }

    @Test
    fun there_is_no_cue_in_dock_focus_where_the_grid_is_not_shown() {
        showHome()

        assertNull(pill(from = 0f))
    }

    @Test
    fun there_is_no_cue_when_the_band_under_the_last_row_is_under_the_pill_and_4_dp() {
        // Five columns with names: the second row's names end 38 px above the bottom edge, 10 px short of the 48 the cue needs.
        showHome(TvConfig(columns = 5, alwaysShowNames = true), count = 30)

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.6").assertIsFocused()

        // The middle column's name stands in the column the cue is looked for in, so look from under it.
        val belowNames =
            (
                compose
                    .onNodeWithTag("name:pkg.13")
                    .getBoundsInRoot()
                    .bottom.value + 3f
            ) * density
        assertNull(pill(from = belowNames))
    }

    /** Counts the bodies of the composable [name] that run; the compiler calls the tracer for each one that does not skip. */
    private fun count(name: String): () -> Int {
        var runs = 0
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress() = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    if (info.startsWith("dev.niltsiar.tvhome.feature.home.$name ")) runs++
                }

                override fun traceEventEnd() = Unit
            },
        )
        return { runs }
    }

    @After
    fun stopCounting() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress() = false

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) = Unit

                override fun traceEventEnd() = Unit
            },
        )
    }
}
