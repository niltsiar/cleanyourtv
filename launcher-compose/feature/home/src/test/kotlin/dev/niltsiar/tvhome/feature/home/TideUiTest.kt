package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.motion
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs

/**
 * The narrowed tide of Expressive Home: the app's colour reaches the focus band, the dock light and the position capsule's
 * focused segment, and nothing else; read from the pixels.
 */
val TideUiTest by testSuite {
    robolectricTestSuite<TideUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

private val Red = Color(0xFFE0453A)
private val Blue = Color(0xFF2F9FD8)

class TideUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { TideUiTestFixture() } asContextForEach {
            test("a second app colour changes only the band, the dock light and the focused segment, each of them") {
                showHome()
                val red = screen()

                apps.value = AppList(listOf(app(1, Blue)) + apps.value.apps.drop(1))
                settle()
                val blue = screen()

                val allowed =
                    listOf(
                        "band" to bandRing(1),
                        "dock light" to dockLightArea(1),
                        "focused segment" to bounds("shelf:dots").grown(2.dp),
                    )
                val outside = changedOutside(red, blue, allowed.map { it.second })
                withClue("changed pixels outside the band, the dock light and the segment: ${outside.take(5)}") { outside.size shouldBe 0 }
                for ((name, area) in listOf(
                    "band" to bandColumn(1),
                    "dock light" to dockLight(1),
                    "focused segment" to bounds("shelf:dots"),
                )) {
                    withClue("the $name") { samePixels(red, blue, area) shouldBe false }
                }
            }

            test("along the dock the band and the focused segment are still on their way from the old colour on the first frame") {
                showHome()
                val first = bandOnFirstFrameOf(2)
                val segment = mostColoured(bounds("shelf:dots"))
                settle()

                withClue("first frame $first, settled ${band(2)}") { distance(first, band(2)) shouldBeGreaterThan 24f }
                withClue("band $first, segment $segment: both still more red than blue") {
                    (first.red > first.blue && segment.red > segment.blue) shouldBe true
                }
                withClue("settled segment") { mostColoured(bounds("shelf:dots")).let { it.blue > it.red } shouldBe true }
            }

            test("with reduced motion the band has the new colour on the first frame") {
                showHome(SixColumnsExpressive.copy(reducedMotion = true))
                val first = bandOnFirstFrameOf(2)
                settle()

                withClue("first frame $first, settled ${band(2)}") { distance(first, band(2)) shouldBeLessThanOrEqualTo 8f }
            }
        }
    })

private val SixColumnsExpressive = SixColumns.copy(look = Look.Expressive, cinematicHome = true, ambientGlow = false)

private fun app(
    n: Int,
    accent: Color,
) = AppEntry("pkg.$n", "pkg.$n", "App $n", accent = accent)

private class TideUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val apps = MutableStateFlow(AppList(listOf(app(1, Red), app(2, Blue)) + (3..9).map { app(it, Color.Unspecified) }))

    val density: Float get() = compose.activity.resources.displayMetrics.density

    fun showHome(config: TvConfig = SixColumnsExpressive) {
        val sources = sources(apps = apps)
        compose.setContent {
            TvTheme(look = config.look, seed = { Color(0xFFFF6A3D) }, motion = config.motion()) {
                val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = config.topBarLayout(),
                    wallpaperLayout = WallpaperLayout(background = Color.Black),
                    shelfLayout = config.shelfLayout().copy(scrimAlpha = 0f),
                    dockLook = config.dockLook(),
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
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    fun tile(n: Int): DpRect = bounds("tile:pkg.$n")

    /** Tile [n] grown to its focus scale with its band round it, and a margin. */
    fun bandRing(n: Int): DpRect {
        val box = tile(n)
        val grow = (box.right - box.left) * 0.04f + 12.dp
        return DpRect(box.left - grow, box.top - grow, box.right + grow, box.bottom + grow)
    }

    /** The dock's light round tile [n]: the cushion behind it, stretched as far as it goes, and the crest on the dock's lip. */
    fun dockLightArea(n: Int): DpRect {
        val box = tile(n)
        val dock = bounds("dock")
        val centre = (box.left + box.right) / 2
        return DpRect(centre - 120.dp, dock.top - 2.dp, centre + 120.dp, dock.bottom)
    }

    fun DpRect.grown(by: Dp) = DpRect(left - by, top - by, right + by, bottom + by)

    /** The pixels, in px, where [a] and [b] differ outside every one of [allowed]. */
    fun changedOutside(
        a: PixelMap,
        b: PixelMap,
        allowed: List<DpRect>,
    ): List<Pair<Int, Int>> {
        val boxes =
            allowed.map {
                (it.left.value * density).toInt()..(it.right.value * density).toInt() to
                    (it.top.value * density).toInt()..(it.bottom.value * density).toInt()
            }
        val out = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a[x, y] != b[x, y] && boxes.none { (xs, ys) -> x in xs && y in ys }) out += x to y
            }
        }
        return out
    }

    /** A column over tile [n]'s middle, from its top edge out past its focus band. */
    fun bandColumn(n: Int): DpRect {
        val box = tile(n)
        val x = (box.left + box.right) / 2
        return DpRect(x, box.top - 16.dp, x + 1.dp, box.top)
    }

    /** The dock's top rim over tile [n]. */
    fun dockLight(n: Int): DpRect {
        val box = tile(n)
        val top = bounds("dock").top
        return DpRect(box.left, top, box.right, top + 2.dp)
    }

    /** The most coloured pixel of the column over tile [n]: the core of its focus band. */
    fun band(n: Int): Color = mostColoured(bandColumn(n))

    /** The pixel of [area] whose channels are furthest apart. */
    fun mostColoured(area: DpRect): Color {
        val pixels = screen()
        var best = Color.Black
        for (y in (area.top.value * density).toInt() until (area.bottom.value * density).toInt()) {
            for (x in (area.left.value * density).toInt() until (area.right.value * density).toInt()) {
                val it = pixels[x, y]
                if (spread(it) > spread(best)) best = it
            }
        }
        return best
    }

    private fun spread(colour: Color): Float = maxOf(colour.red, colour.green, colour.blue) - minOf(colour.red, colour.green, colour.blue)

    /** Moves focus from tile [n] - 1 to tile [n] and gives the band of tile [n] on the first frame after the move. */
    fun bandOnFirstFrameOf(n: Int): Color {
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tile:pkg.$n").assertIsFocused()
        return band(n)
    }

    fun samePixels(
        a: PixelMap,
        b: PixelMap,
        area: DpRect,
    ): Boolean {
        for (y in (area.top.value * density).toInt() until (area.bottom.value * density).toInt()) {
            for (x in (area.left.value * density).toInt() until (area.right.value * density).toInt()) {
                if (a[x, y] != b[x, y]) return false
            }
        }
        return true
    }

    /** The largest gap between two colours' channels, in 8-bit steps. */
    fun distance(
        a: Color,
        b: Color,
    ): Float = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) * 255f
}
