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
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs

/**
 * With rows of apps below the screen, Home draws a down chevron in a pill in the band under the last row that shows, in
 * white at 0.95 on black at 0.60 so that it reads on any wallpaper. The wallpaper here is white, so the pill reads as 0.40 grey.
 */
val HomeMoreCueUiTest by testSuite {
    robolectricTestSuite<HomeMoreCueUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HomeMoreCueUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeMoreCueUiTestFixture() } closeWith { stopCounting() } asContextForEach {
            test("the default grid with rows below the screen has the cue centred in the band under its second row") {
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
                withClue("no cue under the second row") { pill.shouldNotBeNull() }
                assertNear(20f * density, (pill!!.last - pill.first + 1).toFloat())
                assertNear((rowBottom + screen) / 2f, (pill.first + pill.last + 1) / 2f)
            }

            test("the pill is black at 60 percent and the chevron white at 95 percent") {
                showHome()
                press(Key.DirectionDown)

                val pill = pill(from = bottomOf("tile:pkg.9"))!!
                val (pixels, x) = column()
                val middle = (pill.first + pill.last) / 2
                // Beside the chevron: 7 dp left of the middle, the pill's own colour.
                pixels[pixels.width / 2 - (7f * density).toInt(), middle].red shouldBe (0.4f plusOrMinus 0.03f)
                // The tip of the chevron, 4 dp under the pill's middle on the centre line, in the chevron's own colour.
                val tip = pixels[pixels.width / 2, middle + (4f * density).toInt()]
                withClue("chevron reads ${tip.red} at $x") { tip.red shouldBeGreaterThan 0.9f }
            }

            test(
                "the cue stays while focus moves down the visible rows and goes when the grid scrolls to its last row with no recomposition",
            ) {
                showHome()
                press(Key.DirectionDown)
                val homeContent = count("HomeContent")
                val before = homeContent()
                withClue("cue with focus on the first row") { pill(from = bottomOf("tile:pkg.9")).shouldNotBeNull() }

                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
                withClue("cue with focus on the second row") { pill(from = bottomOf("tile:pkg.9")).shouldNotBeNull() }

                press(Key.DirectionDown)
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.17").assertIsFocused()
                withClue("cue with the last row on screen") { pill(from = bottomOf("tile:pkg.17")).shouldBeNull() }
                withClue("HomeContent recomposed with the focus moves") { homeContent() shouldBe before }
            }

            test("grid scroll cue false draws no cue where the default draws one") {
                showHome()
                press(Key.DirectionDown)
                withClue("cue with grid_scroll_cue on") { pill(from = bottomOf("tile:pkg.9")).shouldNotBeNull() }

                layout = TvConfig(columns = 4, gridScrollCue = false).homeLayout()
                settle()
                compose.onNodeWithTag("tile:pkg.5").assertIsFocused()
                withClue("cue with grid_scroll_cue false") { pill(from = bottomOf("tile:pkg.9")).shouldBeNull() }
            }

            test("the recomposition count sees home content run when its layout changes") {
                showHome()
                press(Key.DirectionDown)
                val homeContent = count("HomeContent")
                val before = homeContent()

                layout = layout.copy(showNames = true)
                settle()

                withClue("HomeContent runs: before $before, after ${homeContent()}") { homeContent() shouldBeGreaterThan before }
            }

            test("the cue is partly transparent while the row it marks fades in as focus enters the grid") {
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

                withClue("never partly transparent: $seen") { (seen.any { it in 0.5f..0.9f }).shouldBeTrue() }
            }

            test("there is no cue with every row of apps on screen") {
                showHome(count = 12)

                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.5").assertIsFocused()

                pill(from = bottomOf("tile:pkg.9")).shouldBeNull()
            }

            test("there is no cue in dock focus where the grid is not shown") {
                showHome()

                pill(from = 0f).shouldBeNull()
            }

            test("with names and apps below a band too short for the cue has it over the tiles above the names") {
                // Five columns with names: the second row's names end 38 px above the bottom edge, 10 px short of the 48 the cue needs.
                showHome(TvConfig(columns = 5, alwaysShowNames = true), count = 30)
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.6").assertIsFocused()
                val (withCue, x) = column()

                layout = layout.copy(scrollCue = false)
                settle()
                val (without, _) = column()

                val drawn = (0 until withCue.height).filter { y -> withCue[x, y] != without[x, y] }
                withClue("no cue with names on and apps below") { drawn.isNotEmpty().shouldBeTrue() }
                val tileBottom = bottomOf("tile:pkg.13")
                val nameTop =
                    compose
                        .onNodeWithTag("name:pkg.13")
                        .getBoundsInRoot()
                        .top.value * density
                assertNear(20f * density, (drawn.last() - drawn.first() + 1).toFloat())
                assertNear(tileBottom - 2f * density, drawn.last() + 1f)
                withClue("cue ends at ${drawn.last()}, over the name at $nameTop") { drawn.last().toFloat() shouldBeLessThan nameTop }
            }
        }
    })

@OptIn(InternalComposeTracingApi::class)
private class HomeMoreCueUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val density get() = compose.activity.resources.displayMetrics.density

    fun art(colour: Color) = ImageBitmap(16, 9).also { Canvas(it).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = colour }) }

    val inks = listOf(Color(0xFF1B4F9C), Color(0xFFB33A3A), Color(0xFF2E7D5B))

    /** The layout Home is drawn in; a test may change it after [showHome]. */
    var layout by mutableStateOf(TvConfig().homeLayout())

    /** [count] coloured tiles: the first [TvConfig.columns] are the dock, the rest the grid. */
    fun showHome(
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

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    /** The bottom of tile [tag] in pixels. */
    fun bottomOf(tag: String): Float =
        compose
            .onNodeWithTag(tag)
            .getBoundsInRoot()
            .bottom.value * density

    /**
     * The screen's pixels in the column 5.5 dp left of its middle, which the pill covers over its full height and the chevron
     * crosses at a few rows only, and which runs through the gap between the two middle columns of four.
     */
    fun column(): Pair<PixelMap, Int> {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels to (pixels.width / 2 - (5.5f * density).toInt())
    }

    /** The first and last row, in pixels, of the pill in [column] below [from], or null when no pixel there is dark. */
    fun pill(from: Float): IntRange? {
        val (pixels, x) = column()
        val dark = (from.toInt() until pixels.height).filter { y -> pixels[x, y].red < 0.5f && pixels[x, y].green < 0.5f }
        return if (dark.isEmpty()) null else dark.first()..dark.last()
    }

    fun assertNear(
        expected: Float,
        actual: Float,
        by: Float = 2f,
    ) = withClue("expected $expected, got $actual") { abs(expected - actual) shouldBeLessThanOrEqualTo by }

    /** Counts the bodies of the composable [name] that run; the compiler calls the tracer for each one that does not skip. */
    fun count(name: String): () -> Int {
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
