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
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import java.time.ZoneId
import kotlin.math.abs

/** The band over the grid and the grid's edges on the cinematic Home, read from the rendered pixels. */
val HomeBandLookUiTest by testSuite {
    robolectricTestSuite<HomeBandLookUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HomeBandLookUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeBandLookUiTestFixture() } asContextForEach {
            test("the page dots are 8 dp with 8 dp between them the focused one an 18 dp pill and start at the titles left edge") {
                showHome()

                val dots = bounds("shelf:dots")
                dots.height.value shouldBe (8f plusOrMinus 0.1f)
                dots.left.value shouldBe (bounds("shelf:name").left.value plusOrMinus 0.5f)
                val runs = dotRuns()
                runs.map { it.endInclusive - it.start } shouldBe listOf(18f, 8f, 8f, 8f, 8f, 8f)
                runs.zipWithNext { before, after -> after.start - before.endInclusive } shouldBe List(5) { 8f }
                runs.first().start shouldBe (0f plusOrMinus 0.5f)
            }

            test("the inactive page dots read at 4 5 to 1 on a near black wallpaper and stay dimmer than the active one") {
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

                withClue("inactive dot $inactive on $wallpaper: ${contrast(inactive, wallpaper)}") {
                    contrast(inactive, wallpaper) shouldBeGreaterThanOrEqualTo
                        4.5f
                }
                withClue("active dot $active, inactive dot $inactive: ${contrast(active, inactive)}") {
                    contrast(active, inactive) shouldBeGreaterThanOrEqualTo
                        3f
                }
            }

            test("the app library fades out the dock apps name time and dots") {
                showHome()
                brightest(titleArea("shelf:name")) shouldBeGreaterThanOrEqualTo 230
                brightest(detailArea("shelf:meta")) shouldBeGreaterThanOrEqualTo 230

                libraryOpen = true
                settle()

                // Black under the library's scrim.
                val dimmed = Math.round(0x07 * 0.78f)
                assertNear(dimmed, brightest(titleArea("shelf:name")), 2)
                assertNear(dimmed, brightest(detailArea("shelf:meta")), 2)

                libraryOpen = false
                settle()
                brightest(titleArea("shelf:name")) shouldBeGreaterThanOrEqualTo 230
            }

            test("the app library opened from the pill fades out the librarys name and count") {
                showHome()
                press(Key.DirectionDown, times = 3)
                compose.onNodeWithTag("library:pill").assertIsFocused()
                brightest(titleArea("shelf:title")) shouldBeGreaterThanOrEqualTo 230

                libraryOpen = true
                settle()

                val dimmed = Math.round(0x07 * 0.78f)
                assertNear(dimmed, brightest(titleArea("shelf:title")), 2)
                assertNear(dimmed, brightest(detailArea("shelf:title-meta")), 2)
            }

            test("on the top bar the band names the button at full brightness and the page dots are gone") {
                showHome()

                press(Key.DirectionUp)

                brightest(titleArea("shelf:title")) shouldBeGreaterThanOrEqualTo 230
                brightest(detailArea("shelf:title-meta")) shouldBeGreaterThanOrEqualTo 230
                val dots = bounds("shelf:title-meta").let { DpRect(it.left, it.bottom + 16.dp, it.left + 300.dp, it.bottom + 24.dp) }
                assertNear(0, brightest(dots), 2)

                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                brightest(titleArea("shelf:name")) shouldBeGreaterThanOrEqualTo 230
                brightest(detailArea("shelf:meta")) shouldBeGreaterThanOrEqualTo 230
            }

            test("rows above the focused library pill draw at 85 percent") {
                showHome()
                press(Key.DirectionDown, times = 3)
                compose.onNodeWithTag("library:pill").assertIsFocused()

                // The layer multiplies each draw: the white art, and the tile's own fill under it.
                val above = tile(13)
                val fill = 0x1B * 0.85f
                assertNear(Math.round(0.85f * 255 + 0.15f * fill), redAt((above.left + above.right) / 2, (above.top + above.bottom) / 2), 4)
            }

            test("a dock scrolled up for the grid leaves nothing above the grid") {
                val grey = 0x80
                showHome(background = Color(grey, grey, grey))
                val resting = bounds("dock")
                val inside = resting.bottom - 4.dp
                abs(redAt(resting.left + 200.dp, inside) - grey) shouldBeGreaterThan 6

                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                val dock = bounds("dock")
                assertNear(grey, redAt(dock.left + 200.dp, dock.bottom - 4.dp), 1)
                assertNear(grey, redAt(dock.left + 200.dp, dock.bottom + 4.dp), 1)
            }
        }
    })

private class HomeBandLookUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }

    /** A dock of six and two grid rows of six, all white. */
    val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = white) }

    val config = SixColumns.copy(cinematicHome = true, appLibrary = true)

    var libraryOpen by mutableStateOf(false)

    fun showHome(background: Color = Color.Black) {
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

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    val density get() = compose.activity.resources.displayMetrics.density

    /** The red channel, 0 to 255, of the screen at ([x], [y]). */
    fun redAt(
        x: Dp,
        y: Dp,
    ): Int = Math.round(screen()[(x.value * density).toInt(), (y.value * density).toInt()].red * 255)

    /** The brightest red channel in [area], 0 to 255. */
    fun brightest(area: DpRect): Int {
        val pixels = screen()
        var most = 0f
        for (y in (area.top.value * density).toInt() until (area.bottom.value * density).toInt()) {
            for (x in (area.left.value * density).toInt() until (area.right.value * density).toInt()) {
                most = maxOf(most, pixels[x, y].red)
            }
        }
        return Math.round(most * 255)
    }

    fun assertNear(
        expected: Int,
        actual: Int,
        tolerance: Int,
    ) = withClue("expected $expected, got $actual") { abs(actual - expected) shouldBeLessThanOrEqualTo tolerance }

    /** The band's title line, 300 dp of it. */
    fun titleArea(tag: String): DpRect = bounds(tag).let { DpRect(it.left, it.top, it.left + 300.dp, it.bottom) }

    /** The meta line under the title and the page dots under that. */
    fun detailArea(metaTag: String): DpRect = bounds(metaTag).let { DpRect(it.left, it.top, it.left + 300.dp, it.bottom + 24.dp) }

    fun tile(n: Int): DpRect = bounds("tile:pkg.$n")

    /** The runs of lit pixels along the middle of the page dots, as start to end in dp from the dots' left edge. */
    fun dotRuns(): List<ClosedFloatingPointRange<Float>> {
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
}
