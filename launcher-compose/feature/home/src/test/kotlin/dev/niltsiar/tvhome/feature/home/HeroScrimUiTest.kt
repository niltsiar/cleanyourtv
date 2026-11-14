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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
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
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.HeaderScrim
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.Wallpaper
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs

/** The biggest darkening change, in 255ths per 10 dp, the scrim may make along the title and down its end. */
private const val LIMIT_ALONG = 13f
private const val LIMIT_DOWN = 11f

/** The biggest darkening change, in 255ths between two neighbouring pixels, the smooth falloff makes across the scrim's end. */
private const val LIMIT_EDGE = 3

/**
 * The hero block's local scrim over a bright picture: its extent follows the title's cross-fade, and its edges have no
 * step a viewer can see, read from the rendered pixels.
 */
val HeroScrimUiTest by testSuite {
    robolectricTestSuite<HeroScrimUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

@OptIn(InternalComposeTracingApi::class, ExperimentalComposeRuntimeApi::class)
class HeroScrimUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HeroScrimUiTestFixture() } closeWith { Composer.setTracer(NotTracing) } asContextForEach {
            test("a longer band title fading in leaves no hard edge in the scrim on any frame") {
                assertNoHardEdgeWhileMoving(cinematic = false, key = Key.DirectionRight)
            }

            test("a shorter band title fading in leaves no hard edge in the scrim on any frame") {
                assertNoHardEdgeWhileMoving(cinematic = false, key = Key.DirectionLeft)
            }

            test("a longer shelf name fading in leaves no hard edge in the scrim on any frame") {
                assertNoHardEdgeWhileMoving(cinematic = true, key = Key.DirectionRight)
            }

            test("a shorter shelf name fading in leaves no hard edge in the scrim on any frame") {
                assertNoHardEdgeWhileMoving(cinematic = true, key = Key.DirectionLeft)
            }

            test("the local scrim grows with a longer band title over its cross fade and does not jump") {
                growing(cinematic = false)
            }

            test("the local scrim shrinks with a shorter band title over its cross fade and does not jump") {
                shrinking(cinematic = false)
            }

            test("the local scrim grows with a longer shelf name over its cross fade and does not jump") {
                growing(cinematic = true)
            }

            test("the local scrim shrinks with a shorter shelf name over its cross fade and does not jump") {
                shrinking(cinematic = true)
            }

            test("over the bright sky the scrims darkening steps by no more than the limit per 10 dp") {
                val (along, down) = stepsOver("bright")

                withClue(
                    "along the title $along/255, down the title's end $down/255",
                ) { (along <= LIMIT_ALONG && down <= LIMIT_DOWN).shouldBeTrue() }
            }

            test("over the busy light forest the scrims darkening steps by no more than the limit per 10 dp") {
                val (along, down) = stepsOver("busy")

                withClue(
                    "along the title $along/255, down the title's end $down/255",
                ) { (along <= LIMIT_ALONG && down <= LIMIT_DOWN).shouldBeTrue() }
            }

            test("over the bright sky the background behind the hero title glyphs has a 90th percentile luminance of at most 016") {
                val p90 = heroBackgroundP90("bright")

                withClue("the 90th percentile behind the hero title is $p90") { p90 shouldBeLessThanOrEqualTo 0.16f }
            }

            test("over the busy light forest the background behind the hero title glyphs has a 90th percentile luminance of at most 016") {
                val p90 = heroBackgroundP90("busy")

                withClue("the 90th percentile behind the hero title is $p90") { p90 shouldBeLessThanOrEqualTo 0.16f }
            }

            test("over the bright sky the background behind the hero title is neither near black nor too light for white text") {
                showCouncil("bright")
                val luminance = heroBackground()

                withClue("the background behind the hero title has relative luminance $luminance") { luminance shouldBeIn 0.08f..0.16f }
            }

            test("over the busy light forest the background behind the hero title is neither near black nor too light for white text") {
                showCouncil("busy")
                val luminance = heroBackground()

                withClue("the background behind the hero title has relative luminance $luminance") { luminance shouldBeIn 0.08f..0.16f }
            }

            test("the hero scrim is none up to a luminance of 012 and at most half strong over a white picture") {
                heroScrimDarkening(0f) shouldBe (0f plusOrMinus 0f)
                heroScrimDarkening(0.10f) shouldBe (0f plusOrMinus 0f)
                heroScrimDarkening(0.12f) shouldBe (0f plusOrMinus 0.01f)
                withClue("over a half-bright picture ${heroScrimDarkening(0.5f)}") { heroScrimDarkening(0.5f) shouldBeIn 0.4f..0.5f }
                heroScrimDarkening(1f) shouldBe (0.5f plusOrMinus 0f)
                val steps = (0..100).map { heroScrimDarkening(it / 100f) }
                withClue("the strength falls somewhere: $steps") { (steps.zipWithNext().all { (a, b) -> b >= a }).shouldBeTrue() }
            }

            test("a dark picture gets no hero scrim so the screen is the same with header scrim off") {
                val dark = solid(0.3f).let { Wallpaper(picture = it, thumbnail = it) }
                showHome(cinematic = false, dark)
                val auto = compose.onRoot().captureToImage().toPixelMap()
                scrimMode.value = HeaderScrim.Off
                settle()
                val off = compose.onRoot().captureToImage().toPixelMap()

                val differing = (0 until auto.width).sumOf { x -> (0 until auto.height).count { y -> auto[x, y] != off[x, y] } }
                withClue("pixels that differ with the hero scrim possible") { differing shouldBe 0 }
            }

            test("the band title has a tight shadow while the hero scrim is on") {
                assertTightShadowOnBright(cinematic = false)
            }

            test("the shelf name has a tight shadow while the hero scrim is on") {
                assertTightShadowOnBright(cinematic = true)
            }

            test("the band title keeps its wide shadow without a hero scrim") {
                assertWideShadowOnDark(cinematic = false)
            }

            test("the shelf name keeps its wide shadow without a hero scrim") {
                assertWideShadowOnDark(cinematic = true)
            }

            test("while a tile menu is open the subtitle pill does not show past its edge and it comes back after") {
                showCouncil("bright", cinematic = true)
                withClue("the pill's stroke is ${pillStroke()} brighter") { pillStroke() shouldBeGreaterThan 0.05f }

                pressMenuKey()
                compose.mainClock.advanceTimeBy(160)
                compose.waitForIdle()
                val menu = compose.onNodeWithTag("menu").getBoundsInRoot()
                withClue("the menu starts at ${menu.left}, the pill's end is 10 dp before 44") { menu.left.value shouldBeGreaterThan 36f }
                withClue("the pill's stroke is ${pillStroke()} brighter") { pillStroke() shouldBeLessThan 0.02f }

                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.mainClock.advanceTimeBy(160)
                compose.waitForIdle()
                withClue("the pill's stroke is ${pillStroke()} brighter") { pillStroke() shouldBeGreaterThan 0.05f }
            }

            test("the shelf subtitle pill keeps its round ends while a new name fades in") {
                assertPillKeepsItsRoundEndsInASwap(cinematic = true, tag = "shelf:meta")
            }

            test("the band subtitle pill keeps its round ends while a new title fades in") {
                assertPillKeepsItsRoundEndsInASwap(cinematic = false, tag = "shelf:title-meta")
            }

            test("the shelf name keeps its shadow past its box while a tile menu fades it out") {
                assertShadowKeptWhileTheTitleFades(cinematic = true, tag = "shelf:name")
            }

            test("the band title keeps its shadow past its box while a tile menu fades it out") {
                assertShadowKeptWhileTheTitleFades(cinematic = false, tag = "shelf:title")
            }

            test("a title swap does not draw the hero scrims body again on any frame of its cross fade") {
                showHome(cinematic = false)
                compose.onRoot().captureToImage()
                var draws = 0
                onHeroBodyDraw = { draws++ }
                try {
                    compose.mainClock.autoAdvance = false
                    compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
                    repeat(30) {
                        compose.mainClock.advanceTimeByFrame()
                        // Rendering the frame is what draws the layers.
                        compose.onRoot().captureToImage()
                    }
                    compose.mainClock.autoAdvance = true
                    settle()
                } finally {
                    onHeroBodyDraw = {}
                }

                withClue("the body was drawn again") { draws shouldBe 0 }
            }

            test("a title change recomposes nothing on the first frames of its cross fade and the scrim never") {
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

                withClue("the cross-fade recomposed") { later shouldBe first }
                withClue("the header recomposed ${bodies["HeroTitle"]} times") { (bodies["HeroTitle"] ?: 0) shouldBeLessThanOrEqualTo 2 }
                withClue("the title slots recomposed ${bodies["TitleSlots"]} times") {
                    (bodies["TitleSlots"] ?: 0) shouldBeLessThanOrEqualTo
                        2
                }
                withClue("the scrim recomposed") { (bodies["HeroScrim"] ?: 0) shouldBe 0 }
            }
        }
    })

@OptIn(InternalComposeTracingApi::class, ExperimentalComposeRuntimeApi::class)
private class HeroScrimUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val bodies = mutableMapOf<String, Int>()

    val scrimMode = mutableStateOf(HeaderScrim.Auto)

    val appList = MutableStateFlow(AppList(emptyList()))

    /** Counts, by composable, the bodies that run; the compiler calls it for each composable that does not skip. */
    val tracer =
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

    fun solid(level: Float) =
        ImageBitmap(16, 9).also { Canvas(it).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color(level, level, level) }) }

    val shortName = AppEntry(key = "pkg.1", packageName = "pkg.1", label = "Hi")

    val longName = AppEntry(key = "pkg.2", packageName = "pkg.2", label = "A long app name here")

    val apps = listOf(shortName, longName) + NineApps.drop(2)

    fun showHome(
        cinematic: Boolean,
        wallpaper: Wallpaper = solid(1f).let { Wallpaper(picture = it, thumbnail = it) },
        shown: List<AppEntry> = apps,
        headerScrim: HeaderScrim = HeaderScrim.Auto,
    ) {
        scrimMode.value = headerScrim
        appList.value = AppList(shown)
        val config = SixColumns.copy(cinematicHome = cinematic)
        val sources = sources(apps = appList, wallpaper = flowOf(wallpaper))
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
                        wallpaperLayout = WallpaperLayout(headerScrim = scrimMode.value),
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

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    fun titleTag(cinematic: Boolean) = if (cinematic) "shelf:name" else "shelf:title"

    fun redAt(point: Offset): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(pixels[(point.x * density).toInt(), (point.y * density).toInt()].red * 255)
    }

    fun titleBounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    /** A point of the row under the title that the long name's scrim holds and the short name's, which fades out 240 dp past it, has left. */
    fun probe(cinematic: Boolean): Offset {
        press(Key.DirectionRight)
        val long = titleBounds(titleTag(cinematic))
        press(Key.DirectionLeft)
        val short = titleBounds(titleTag(cinematic))
        val at = Offset(short.right.value + 270f, long.bottom.value + 3f)
        withClue("the long name ends at ${long.right}, the short one at ${short.right}") { at.x shouldBeLessThan long.right.value - 20f }
        return at
    }

    /** The red at [at] before a key press and on each of the 24 frames after it, then once the move has settled. */
    fun redsWhileMoving(
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

    fun assertMovesSmoothly(moved: Triple<Int, List<Int>, Int>) {
        val (start, frames, end) = moved
        val total = abs(end - start)
        withClue("the scrim at the probe went from $start to $end") { total shouldBeGreaterThan 40 }
        val steps = (listOf(start) + frames).zipWithNext { a, b -> abs(b - a) }
        withClue("the biggest step in a frame is ${steps.max()} of $total: $frames") {
            steps.max().toFloat() shouldBeLessThanOrEqualTo
                0.6f * total
        }
        val between = frames.count { abs(it - start) in (0.1f * total).toInt()..(0.9f * total).toInt() }
        withClue("$between frames between the two ends: $frames") { between shouldBeGreaterThanOrEqualTo 2 }
    }

    fun growing(cinematic: Boolean) {
        showHome(cinematic)
        val at = probe(cinematic)
        assertMovesSmoothly(redsWhileMoving(Key.DirectionRight, at))
    }

    fun shrinking(cinematic: Boolean) {
        showHome(cinematic)
        val at = probe(cinematic)
        press(Key.DirectionRight)
        assertMovesSmoothly(redsWhileMoving(Key.DirectionLeft, at))
    }

    val shortTitled = AppEntry(key = "pkg.1", packageName = "pkg.1", label = "Plezy")

    val longTitled = AppEntry(key = "pkg.2", packageName = "pkg.2", label = "SmartTube")

    /** The biggest change in red between two neighbouring pixels of pixel row [row], from pixel [from] on, on the screen now. */
    fun sharpestStepAlong(
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
    fun assertNoHardEdgeWhileMoving(
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
        withClue("the sharpest step in a frame, in 255ths between two pixels: $steps") { (steps.all { it <= LIMIT_EDGE }).shouldBeTrue() }
        val under = ((long[1].bottom.value + 5f) * density).toInt()
        withClue("the whole row under the settled block has a step of ${sharpestStepAlong(under, 0)}") {
            sharpestStepAlong(under, 0) shouldBeLessThanOrEqualTo
                LIMIT_EDGE
        }
    }

    val titled = AppEntry(key = "pkg.1", packageName = "pkg.1", label = "Prime Video")

    /** The council's wallpaper [name] at half size (960 by 540), which is one pixel to 2 of the screen's. */
    fun council(name: String): Bitmap =
        checkNotNull(javaClass.getResourceAsStream("/wallpaper/$name.jpg")).use { BitmapFactory.decodeStream(it) }

    /**
     * How much black the screen puts over [picture]: 1 less the render's channels over the picture's, summed over [rows] of
     * pixels at each column, and over [columns] of pixels at each row.
     */
    fun darkening(
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
    fun biggestStep(
        profile: FloatArray,
        from: Int,
        to: Int,
    ): Float = (from until minOf(to, profile.size) - 20).maxOf { abs(profile[it + 20] - profile[it]) } * 255f

    /** Home over the council's wallpaper [name], with [titled] focused; returns the picture. */
    fun showCouncil(
        name: String,
        cinematic: Boolean = false,
    ): Bitmap {
        val picture = council(name)
        val thumbnail = Bitmap.createScaledBitmap(picture, 120, 120 * picture.height / picture.width, true)
        showHome(
            cinematic,
            Wallpaper(picture = picture.asImageBitmap(), thumbnail = thumbnail.asImageBitmap()),
            listOf(titled) + NineApps.drop(1),
        )
        return picture
    }

    /** The biggest step per 10 dp along the row under the subtitle and down the column just past the end of the title, over [name]. */
    fun stepsOver(name: String): Pair<Float, Float> {
        val picture = showCouncil(name)
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

    fun relativeLuminance(color: Color): Float {
        fun linear(c: Float) = if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * linear(color.red) + 0.7152f * linear(color.green) + 0.0722f * linear(color.blue)
    }

    /**
     * The relative luminance of the background right behind the title: the median over the gap between the title's box and the
     * subtitle's pill, which has no glyph, and which the scrim holds at full strength.
     */
    fun heroBackground(): Float {
        val density = compose.activity.resources.displayMetrics.density
        val title = titleBounds("shelf:title")
        val subtitle = titleBounds("shelf:title-meta")
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val values =
            (title.left.value * density).toInt().until((title.right.value * density).toInt()).flatMap { x ->
                ((title.bottom.value + 1f) * density).toInt().until(((subtitle.top.value - 4f) * density).toInt()).map { y ->
                    relativeLuminance(pixels[x, y])
                }
            }
        return values.sorted()[values.size / 2]
    }

    /**
     * The 90th percentile of the relative luminance behind the hero title's glyphs: over the box of "Prime Video", once the title
     * is blank spaces wider than it, so that the scrim stays and no glyph or shadow is drawn.
     */
    fun heroBackgroundP90(name: String): Float {
        showCouncil(name)
        val density = compose.activity.resources.displayMetrics.density
        val box = titleBounds("shelf:title")
        val spaces = "\u2002".repeat((box.width.value / 22f).toInt() + 2)
        appList.value = AppList(listOf(titled.copy(label = spaces)) + NineApps.drop(1))
        settle()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val values =
            (box.left.value * density).toInt().until((box.right.value * density).toInt()).flatMap { x ->
                (box.top.value * density).toInt().until((box.bottom.value * density).toInt()).map { y ->
                    relativeLuminance(pixels[x, y])
                }
            }
        return values.sorted()[values.size * 9 / 10]
    }

    fun titleShadow(cinematic: Boolean) = compose.onNodeWithTag(titleTag(cinematic)).textStyle().shadow

    fun assertTightShadowOnBright(cinematic: Boolean) {
        val density = compose.activity.resources.displayMetrics.density
        showCouncil("bright", cinematic)
        titleShadow(cinematic) shouldBe Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2 * density), 6 * density)
    }

    fun assertWideShadowOnDark(cinematic: Boolean) {
        val density = compose.activity.resources.displayMetrics.density
        showHome(cinematic, solid(0.3f).let { Wallpaper(picture = it, thumbnail = it) })
        titleShadow(cinematic)?.blurRadius shouldBe 9 * density
    }

    /** How much brighter the pill's 1 dp stroke at the subtitle's left end is than the background just outside it, in 0 to 1. */
    fun pillStroke(): Float {
        val density = compose.activity.resources.displayMetrics.density
        val meta = titleBounds("shelf:meta")
        val y = ((meta.top.value + meta.bottom.value) / 2 * density).toInt()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels[((meta.left.value - 9.5f) * density).toInt(), y].red - pixels[((meta.left.value - 14f) * density).toInt(), y].red
    }

    fun pressMenuKey() = compose.onRoot().performKeyInput { pressKey(Key.Menu) }

    /**
     * How much brighter, in the red channel, the pixel [across] dp right of the subtitle's box's left edge and [down] dp below its
     * top is than the picture 14 dp left of that edge, outside the pill. The pill is black over a scrim that is already near black,
     * so only its 1 dp white stroke shows.
     */
    fun pillStroke(
        meta: DpRect,
        across: Float,
        down: Float,
    ): Float {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val outside = pixels[((meta.left.value - 14f) * density).toInt(), ((meta.top.value + meta.bottom.value) / 2 * density).toInt()].red
        return pixels[((meta.left.value + across) * density).toInt(), ((meta.top.value + down) * density).toInt()].red - outside
    }

    fun assertPillRound(
        meta: DpRect,
        moment: String,
    ) {
        // The pill's left end at its middle height, 6 dp left of the slot's bounds.
        val end = pillStroke(meta, across = -9.5f, down = (meta.bottom.value - meta.top.value) / 2)
        // The corner of the pill's own box, which a round end leaves clear.
        val corner = pillStroke(meta, across = -9.5f, down = -2.5f)
        withClue("$moment: the pill's left end is $end brighter") { end shouldBeGreaterThan 0.05f }
        withClue("$moment: the pill's top left corner is $corner brighter") { corner shouldBeLessThan 0.02f }
    }

    fun assertPillKeepsItsRoundEndsInASwap(
        cinematic: Boolean,
        tag: String,
    ) {
        showCouncil("bright", cinematic)
        val meta = titleBounds(tag)
        assertPillRound(meta, "at rest")

        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        try {
            for (frame in 1..8) {
                compose.mainClock.advanceTimeByFrame()
                if (frame == 1 || frame == 8) assertPillRound(meta, "frame $frame of the swap")
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    /**
     * How much darker, in the red channel, the title's shadow makes the picture one pixel left of the title's box, at mid height,
     * than 12 dp left of it, where the shadow no longer reaches. The glyphs start 6 dp inside the box and the shadow blurs 9 dp.
     */
    fun shadowLeftOfTheTitle(tag: String): Float {
        val density = compose.activity.resources.displayMetrics.density
        val title = titleBounds(tag)
        val y = ((title.top.value + title.bottom.value) / 2 * density).toInt()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels[((title.left.value - 12f) * density).toInt(), y].red - pixels[(title.left.value * density).toInt() - 1, y].red
    }

    fun assertShadowKeptWhileTheTitleFades(
        cinematic: Boolean,
        tag: String,
    ) {
        showHome(cinematic, solid(1f).let { Wallpaper(picture = it, thumbnail = it) }, headerScrim = HeaderScrim.Off)
        val atRest = shadowLeftOfTheTitle(tag)
        withClue("at rest the shadow darkens the picture by $atRest") { atRest shouldBeGreaterThan 0.03f }

        compose.mainClock.autoAdvance = false
        pressMenuKey()
        try {
            compose.mainClock.advanceTimeBy(48)
            val midFade = shadowLeftOfTheTitle(tag)
            withClue(
                "mid-fade the shadow darkens the picture by $midFade, at rest by $atRest",
            ) { midFade shouldBeGreaterThan 0.25f * atRest }
        } finally {
            compose.mainClock.autoAdvance = true
        }
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
