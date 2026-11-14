package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DENSITY = 2

/** What the line says while App 1 opens; the ellipsis is one character. */
private val OpeningApp1 = "Opening App 1" + Char(0x2026)

/** press_feedback on the real presenter and screen: the dip and the ring are read from pixels, so the native graphics mode is needed. */
val PressFeedbackUiTest by testSuite {
    robolectricTestSuite<PressFeedbackUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class PressFeedbackUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { PressFeedbackUiTestFixture() } asContextForEach {
            test("ok on an app tile dips it to 96 percent of its focused size over 80 ms and back") {
                showHome(SixColumns.copy(pressFeedback = true))
                val rest = look()

                val looks = afterOk()
                val widths = looks.map { it.width / rest.width }
                val deepest = widths.indexOf(widths.min())

                withClue("widths $widths") { widths.min() shouldBeIn 0.955f..0.967f }
                withClue("deepest at frame $deepest of 16 ms: $widths") { deepest shouldBeIn 4..6 }
                withClue("back at rest: $widths") { widths.last() shouldBeIn 0.997f..1.003f }
            }

            test("with reduced motion ok does not dip the tile and its ring brightens") {
                showHome(SixColumns.copy(pressFeedback = true), Motion(reducedMotion = true))
                val rest = look()

                val looks = afterOk()

                withClue("widths ${looks.map { it.width }} against ${rest.width}") {
                    looks.all { abs(it.width - rest.width) <= 1 }.shouldBeTrue()
                }
                withClue("ring ${looks.map { it.ring }} against ${rest.ring}") {
                    looks.maxOf { it.ring } shouldBeGreaterThanOrEqualTo rest.ring + 3
                }
                looks.last().ring shouldBe rest.ring
            }

            test("without the key ok neither dips the tile nor brightens its ring") {
                showHome(SixColumns)
                val rest = look()

                val looks = afterOk()

                withClue("looks $looks against $rest") { looks.all { it == rest }.shouldBeTrue() }
            }

            test("the line under the title says the app is opening once home has stayed in front 1 s") {
                showHome(SixColumns.copy(pressFeedback = true))
                compose.onNodeWithTag("shelf:title-meta").assertTextEquals("Never opened")

                ok()
                compose.mainClock.advanceTimeBy(OPENING_SAYS_MS - 100)
                compose.onNodeWithTag("shelf:title-meta").assertTextEquals("Never opened")
                compose.mainClock.advanceTimeBy(200)

                compose.onNodeWithTag("shelf:title-meta").assertTextEquals(OpeningApp1)
                effects.filterIsInstance<HomeEffect.LaunchApp>().map { it.app.key } shouldBe listOf("pkg.1")
            }

            test("with the cinematic shelf its line says the app is opening") {
                showHome(SixColumns.copy(pressFeedback = true, cinematicHome = true))

                ok()
                compose.mainClock.advanceTimeBy(OPENING_SAYS_MS + 100)

                compose.onNodeWithTag("shelf:meta").assertTextEquals(OpeningApp1)
            }
        }
    })

/** The focused tile as drawn across its middle row: how many pixels its red art covers, and how many its white ring. */
private data class PressLook(
    val width: Float,
    val ring: Int,
)

private class PressFeedbackUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val effects = mutableListOf<HomeEffect>()

    val red =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.Red })
        }

    val apps =
        (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = if (it == 1) red else null) }

    fun showHome(
        config: TvConfig,
        motion: Motion = Motion(),
    ) {
        val sources = sources(apps = flowOf(AppList(apps)))
        compose.setContent {
            TvTheme(motion = motion) {
                val viewModel = rememberHomeViewModel(sources, config, effects)
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = viewModel::onClick,
                    topBar = config.topBarLayout(),
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
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    /** A short OK on the focused tile: its UP is the click. */
    fun ok() {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
    }

    /** OK, then the look of the tile every 16 ms for 256 ms, with the clock stopped between them. */
    fun afterOk(): List<PressLook> {
        compose.mainClock.autoAdvance = false
        ok()
        return List(16) {
            compose.mainClock.advanceTimeBy(16)
            look()
        }
    }

    fun look(): PressLook {
        val image = compose.onRoot().captureToImage().toPixelMap()
        val bounds = compose.onNodeWithTag("tile:pkg.1").getBoundsInRoot()
        val row = ((bounds.top.value + bounds.bottom.value) / 2f * DENSITY).roundToInt()
        val from = ((bounds.left.value - 14f) * DENSITY).roundToInt().coerceAtLeast(0)
        val to = ((bounds.right.value + 14f) * DENSITY).roundToInt().coerceAtMost(image.width - 1)
        var width = 0
        var ring = 0
        for (x in from..to) {
            val pixel = image[x, row]
            if (pixel.red > 0.8f && pixel.green < 0.25f && pixel.blue < 0.25f) width++
            if (pixel.red > 0.9f && pixel.green > 0.9f && pixel.blue > 0.9f) ring++
        }
        return PressLook(width.toFloat(), ring)
    }
}
