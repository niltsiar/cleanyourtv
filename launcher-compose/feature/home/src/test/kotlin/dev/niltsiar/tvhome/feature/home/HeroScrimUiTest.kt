package dev.niltsiar.tvhome.feature.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.After
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
 * The hero block's local scrim over a bright picture: its extent follows the title's cross-fade, and its edges have no
 * step a viewer can see, read from the rendered pixels.
 */
@OptIn(InternalComposeTracingApi::class, ExperimentalComposeRuntimeApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HeroScrimUiTest {
    private companion object {
        /** The biggest darkening change, in 255ths per 10 dp, the scrim may make along the title and down its end. */
        const val LIMIT_ALONG = 13f
        const val LIMIT_DOWN = 11f

        /** The biggest darkening change, in 255ths between two neighbouring pixels, the smooth falloff makes across the scrim's end. */
        const val LIMIT_EDGE = 3
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val bodies = mutableMapOf<String, Int>()

    /** Counts, by composable, the bodies that run; the compiler calls it for each composable that does not skip. */
    private val tracer =
        object : CompositionTracer {
            override fun isTraceInProgress() = true

            override fun traceEventStart(
                key: Int,
                dirty1: Int,
                dirty2: Int,
                info: String,
            ) {
                val name = info.substringBefore(' ').substringAfterLast('.')
                bodies[name] = (bodies[name] ?: 0) + 1
            }

            override fun traceEventEnd() = Unit
        }

    @After
    fun stopCounting() {
        Composer.setTracer(NotTracing)
    }

    private fun solid(level: Float) =
        ImageBitmap(16, 9).also { Canvas(it).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color(level, level, level) }) }

    private val shortName = AppEntry(key = "pkg.1", packageName = "pkg.1", label = "Hi")
    private val longName = AppEntry(key = "pkg.2", packageName = "pkg.2", label = "A long app name here")
    private val apps = listOf(shortName, longName) + NineApps.drop(2)

    private fun showHome(
        cinematic: Boolean,
        wallpaper: Wallpaper = solid(1f).let { Wallpaper(picture = it, thumbnail = it) },
        shown: List<AppEntry> = apps,
    ) {
        val config = SixColumns.copy(cinematicHome = cinematic)
        val sources = sources(apps = flowOf(AppList(shown)), wallpaper = flowOf(wallpaper))
        compose.setContent {
            CompositionLocalProvider(LocalBakeDispatcher provides Dispatchers.Unconfined) {
                TvTheme(darkTheme = true) {
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
                    )
                }
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

    private fun titleTag(cinematic: Boolean) = if (cinematic) "shelf:name" else "shelf:title"

    private fun redAt(point: Offset): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(pixels[(point.x * density).toInt(), (point.y * density).toInt()].red * 255)
    }

    private fun titleBounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    /** A point of the row under the title that the long name's scrim holds and the short name's, which fades out 240 dp past it, has left. */
    private fun probe(cinematic: Boolean): Offset {
        press(Key.DirectionRight)
        val long = titleBounds(titleTag(cinematic))
        press(Key.DirectionLeft)
        val short = titleBounds(titleTag(cinematic))
        val at = Offset(short.right.value + 270f, long.bottom.value + 3f)
        assertTrue("the long name ends at ${long.right}, the short one at ${short.right}", at.x < long.right.value - 20f)
        return at
    }

    /** The red at [at] before a key press and on each of the 24 frames after it, then once the move has settled. */
    private fun redsWhileMoving(
        key: Key,
        at: Offset,
    ): Triple<Int, List<Int>, Int> {
        val start = redAt(at)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(key) }
        val frames =
            (1..24).map {
                compose.mainClock.advanceTimeByFrame()
                redAt(at)
            }
        compose.mainClock.autoAdvance = true
        settle()
        return Triple(start, frames, redAt(at))
    }

    private fun assertMovesSmoothly(moved: Triple<Int, List<Int>, Int>) {
        val (start, frames, end) = moved
        val total = abs(end - start)
        assertTrue("the scrim at the probe went from $start to $end", total > 40)
        val steps = (listOf(start) + frames).zipWithNext { a, b -> abs(b - a) }
        assertTrue("the biggest step in a frame is ${steps.max()} of $total: $frames", steps.max() <= 0.6f * total)
        val between = frames.count { abs(it - start) in (0.1f * total).toInt()..(0.9f * total).toInt() }
        assertTrue("$between frames between the two ends: $frames", between >= 2)
    }

    private fun growing(cinematic: Boolean) {
        showHome(cinematic)
        val at = probe(cinematic)
        assertMovesSmoothly(redsWhileMoving(Key.DirectionRight, at))
    }

    private fun shrinking(cinematic: Boolean) {
        showHome(cinematic)
        val at = probe(cinematic)
        press(Key.DirectionRight)
        assertMovesSmoothly(redsWhileMoving(Key.DirectionLeft, at))
    }

    private val shortTitled = AppEntry(key = "pkg.1", packageName = "pkg.1", label = "Plezy")
    private val longTitled = AppEntry(key = "pkg.2", packageName = "pkg.2", label = "SmartTube")

    /** The biggest change in red between two neighbouring pixels of pixel row [row], from pixel [from] on, on the screen now. */
    private fun sharpestStepAlong(
        row: Int,
        from: Int,
    ): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return (from until pixels.width - 1).maxOf {
            abs(
                Math.round(pixels[it + 1, row].red * 255) - Math.round(pixels[it, row].red * 255),
            )
        }
    }

    /**
     * Moves the focus [key] between a short and a long name and reads, on each frame of the swap, the row through the title
     * past the end of the longer text block, where only the scrim is drawn.
     */
    private fun assertNoHardEdgeWhileMoving(
        cinematic: Boolean,
        key: Key,
    ) {
        val density = compose.activity.resources.displayMetrics.density
        showHome(cinematic, shown = listOf(shortTitled, longTitled) + NineApps.drop(2))
        press(Key.DirectionRight)
        val tags = if (cinematic) listOf("shelf:name", "shelf:meta") else listOf("shelf:title", "shelf:title-meta")
        val long = tags.map(::titleBounds)
        val row = ((long[0].top.value + long[0].bottom.value) / 2 * density).toInt()
        val from = ((long.maxOf { it.right.value } + 10f) * density).toInt()
        if (key == Key.DirectionRight) press(Key.DirectionLeft)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(key) }
        val steps =
            (1..30).map {
                compose.mainClock.advanceTimeByFrame()
                sharpestStepAlong(row, from)
            }
        compose.mainClock.autoAdvance = true
        settle()
        assertTrue("the sharpest step in a frame, in 255ths between two pixels: $steps", steps.all { it <= LIMIT_EDGE })
        val under = ((long[1].bottom.value + 5f) * density).toInt()
        assertTrue(
            "the whole row under the settled block has a step of ${sharpestStepAlong(under, 0)}",
            sharpestStepAlong(under, 0) <= LIMIT_EDGE,
        )
    }

    @Test
    fun a_longer_band_title_fading_in_leaves_no_hard_edge_in_the_scrim_on_any_frame() =
        assertNoHardEdgeWhileMoving(cinematic = false, key = Key.DirectionRight)

    @Test
    fun a_shorter_band_title_fading_in_leaves_no_hard_edge_in_the_scrim_on_any_frame() =
        assertNoHardEdgeWhileMoving(cinematic = false, key = Key.DirectionLeft)

    @Test
    fun a_longer_shelf_name_fading_in_leaves_no_hard_edge_in_the_scrim_on_any_frame() =
        assertNoHardEdgeWhileMoving(cinematic = true, key = Key.DirectionRight)

    @Test
    fun a_shorter_shelf_name_fading_in_leaves_no_hard_edge_in_the_scrim_on_any_frame() =
        assertNoHardEdgeWhileMoving(cinematic = true, key = Key.DirectionLeft)

    @Test
    fun the_local_scrim_grows_with_a_longer_band_title_over_its_cross_fade_and_does_not_jump() = growing(cinematic = false)

    @Test
    fun the_local_scrim_shrinks_with_a_shorter_band_title_over_its_cross_fade_and_does_not_jump() = shrinking(cinematic = false)

    @Test
    fun the_local_scrim_grows_with_a_longer_shelf_name_over_its_cross_fade_and_does_not_jump() = growing(cinematic = true)

    @Test
    fun the_local_scrim_shrinks_with_a_shorter_shelf_name_over_its_cross_fade_and_does_not_jump() = shrinking(cinematic = true)

    private val titled = AppEntry(key = "pkg.1", packageName = "pkg.1", label = "Prime Video")

    /** The council's wallpaper [name] at half size (960 by 540), which is one pixel to 2 of the screen's. */
    private fun council(name: String): Bitmap =
        checkNotNull(javaClass.getResourceAsStream("/wallpaper/$name.jpg")).use { BitmapFactory.decodeStream(it) }

    /**
     * How much black the screen puts over [picture]: 1 less the render's channels over the picture's, summed over [rows] of
     * pixels at each column, and over [columns] of pixels at each row.
     */
    private fun darkening(
        picture: Bitmap,
        rows: IntRange,
        columns: IntRange,
    ): Pair<FloatArray, FloatArray> {
        val screen = compose.onRoot().captureToImage().toPixelMap()

        fun ratio(pixels: List<Pair<Int, Int>>): Float {
            var seen = 0f
            var real = 0f
            for ((x, y) in pixels) {
                val shown = screen[x, y]
                val pixel = picture.getPixel(x, y)
                seen += shown.red + shown.green + shown.blue
                real +=
                    (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 255f
            }
            return 1f - seen / real
        }

        val along = FloatArray(screen.width) { x -> ratio(rows.map { x to it }) }
        val down = FloatArray(screen.height) { y -> ratio(columns.map { it to y }) }
        return along to down
    }

    /** The biggest change, in 255ths, of [profile] from pixel [from] to [to], 20 pixels (10 dp) apart. */
    private fun biggestStep(
        profile: FloatArray,
        from: Int,
        to: Int,
    ): Float = (from until minOf(to, profile.size) - 20).maxOf { abs(profile[it + 20] - profile[it]) } * 255f

    /** The biggest step per 10 dp along the row under the subtitle and down the column just past the end of the title, over [name]. */
    private fun stepsOver(name: String): Pair<Float, Float> {
        val picture = council(name)
        val thumbnail = Bitmap.createScaledBitmap(picture, 120, 120 * picture.height / picture.width, true)
        showHome(
            cinematic = false,
            Wallpaper(picture = picture.asImageBitmap(), thumbnail = thumbnail.asImageBitmap()),
            listOf(titled) + NineApps.drop(1),
        )
        val density = compose.activity.resources.displayMetrics.density
        val screenWidth =
            (
                compose
                    .onRoot()
                    .getBoundsInRoot()
                    .width.value * density
            ).toInt()
        val screenHeight =
            (
                compose
                    .onRoot()
                    .getBoundsInRoot()
                    .height.value * density
            ).toInt()
        val title = titleBounds("shelf:title")
        val subtitle = titleBounds("shelf:title-meta")
        val dock = titleBounds("dock")
        val rows = ((subtitle.bottom.value + 4f) * density).toInt()..((subtitle.bottom.value + 10f) * density).toInt()
        val columns = ((title.right.value + 12f) * density).toInt()..((title.right.value + 52f) * density).toInt()
        val (along, down) = darkening(Bitmap.createScaledBitmap(picture, screenWidth, screenHeight, true), rows, columns)
        return biggestStep(along, 0, ((title.right.value + 260f) * density).toInt()) to
            biggestStep(down, (80f * density).toInt(), ((dock.top.value - 5f) * density).toInt())
    }

    @Test
    fun over_the_bright_sky_the_scrims_darkening_steps_by_no_more_than_the_limit_per_10_dp() {
        val (along, down) = stepsOver("bright")

        assertTrue("along the title $along/255, down the title's end $down/255", along <= LIMIT_ALONG && down <= LIMIT_DOWN)
    }

    @Test
    fun over_the_busy_light_forest_the_scrims_darkening_steps_by_no_more_than_the_limit_per_10_dp() {
        val (along, down) = stepsOver("busy")

        assertTrue("along the title $along/255, down the title's end $down/255", along <= LIMIT_ALONG && down <= LIMIT_DOWN)
    }

    @Test
    fun a_title_change_recomposes_nothing_on_the_first_frames_of_its_cross_fade_and_the_scrim_never() {
        showHome(cinematic = false)
        Composer.setTracer(tracer)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val first = bodies.toMap()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        val later = bodies.toMap()
        compose.mainClock.autoAdvance = true
        settle()

        assertEquals("the cross-fade recomposed", first, later)
        assertTrue("the header recomposed ${bodies["HeroTitle"]} times", (bodies["HeroTitle"] ?: 0) <= 2)
        assertTrue("the title slots recomposed ${bodies["TitleSlots"]} times", (bodies["TitleSlots"] ?: 0) <= 2)
        assertEquals("the scrim recomposed", 0, bodies["HeroScrim"] ?: 0)
    }
}

@OptIn(InternalComposeTracingApi::class)
private object NotTracing : CompositionTracer {
    override fun isTraceInProgress() = false

    override fun traceEventStart(
        key: Int,
        dirty1: Int,
        dirty2: Int,
        info: String,
    ) = Unit

    override fun traceEventEnd() = Unit
}
