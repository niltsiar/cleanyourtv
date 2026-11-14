package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
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
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DENSITY = 2
private val Orange = Color(0xFFE87A1A)

/** unused_fade_days on the real presenter and screen: the pixels of a faded grid tile against a fresh one. */
val UnusedFadeUiTest by testSuite {
    robolectricTestSuite<UnusedFadeUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class UnusedFadeUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { UnusedFadeUiTestFixture() } asContextForEach {
            test("a faded grid tile draws at 55 percent saturation and 75 percent opacity against a fresh one") {
                showHome()
                val screen = screen()

                val fresh = screen.tile("pkg.8")

                withClue("the art shows in full colour: $fresh") { (fresh.red > 0.8f && fresh.blue < 0.3f).shouldBeTrue() }
                assertFaded(screen, "pkg.7", fresh)
            }

            test("an app never opened in an old record is faded too") {
                showHome()
                val screen = screen()

                assertFaded(screen, "pkg.9", screen.tile("pkg.8"))
            }

            test("a faded tile returns to full colour while it has focus and fades again when focus leaves") {
                showHome()
                val before = screen()
                val fresh = before.tile("pkg.8")
                val background = before.gap()
                press(Key.DirectionDown)
                settle()
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                val focusedFaded = screen().tile("pkg.7")

                press(Key.DirectionRight)
                settle()
                compose.onNodeWithTag("tile:pkg.8").assertIsFocused()
                val later = screen()

                assertLooks(later.tile("pkg.8"), focusedFaded, "pkg.7 with focus, like the fresh pkg.8 with focus")
                assertLooks(faded(fresh, background), later.tile("pkg.7"), "pkg.7 after focus left")
            }

            test("the return to full colour is a short fade on top of the focus look the same tile has without a fade") {
                showHome()

                val (faded, fresh) = shortfalls()

                val extra = faded.zip(fresh).map { (a, b) -> a - b }
                withClue("still fading 32 ms after the key: $extra") { extra[0] shouldBeGreaterThan 0.02f }
                withClue("fading less each frame: $extra") { (extra.zipWithNext().all { (a, b) -> b <= a + 0.01f }).shouldBeTrue() }
                withClue("done by 200 ms: $extra") { extra.last() shouldBeLessThan 0.02f }
            }

            test("with reduced motion the return to full colour is over in the first frame") {
                showHome(motion = Motion(reducedMotion = true))

                val (faded, fresh) = shortfalls()

                val extra = faded.zip(fresh).map { (a, b) -> a - b }
                withClue("no fade left after two frames: $extra") { (extra.drop(1).all { abs(it) < 0.02f }).shouldBeTrue() }
            }

            test("a dock tile is never faded whatever its app was last opened") {
                showHome()
                val screen = screen()

                assertLooks(screen.tile("pkg.8"), screen.tile("pkg.2"), "dock tile of an app never opened")
            }

            test("with zero days no tile is faded") {
                showHome(days = 0)
                val screen = screen()

                assertLooks(screen.tile("pkg.8"), screen.tile("pkg.7"), "pkg.7")
                assertLooks(screen.tile("pkg.8"), screen.tile("pkg.9"), "pkg.9")
            }
        }
    })

private class UnusedFadeUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val zone = ZoneId.systemDefault()

    val now = LocalDateTime.of(2026, 11, 6, 20, 0)

    fun millis(time: LocalDateTime) = time.atZone(zone).toInstant().toEpochMilli()

    val art: ImageBitmap = ImageBitmap(16, 9).also { Canvas(it).drawRect(0f, 0f, 16f, 9f, Paint().apply { color = Orange }) }

    val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = art, accent = Orange) }

    /** pkg.7 was opened 61 days ago and pkg.8 yesterday; every other app never, in a record 200 days old. */
    val opened = mapOf("pkg.7" to millis(now.minusDays(61)), "pkg.8" to millis(now.minusDays(1)))

    fun showHome(
        days: Int = 60,
        motion: Motion = Motion(),
    ) {
        val config = SixColumns.copy(rowsBelowDock = 2, unusedFadeDays = days)
        val sources =
            sources(
                apps = flowOf(AppList(apps)),
                lastOpened = flowOf(opened),
                openedSince = flowOf(millis(now.minusDays(200))),
                timeMillis = { millis(now) },
            )
        compose.setContent {
            TvTheme(motion = motion) {
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
    }

    fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** The tile's art colour, left of its centre: a focused tile shows its name and a ring around the same art. */
    fun PixelMap.tile(key: String): Color {
        val bounds = compose.onNodeWithTag("tile:$key").getBoundsInRoot()
        return this[(bounds.left.value * DENSITY + 12).roundToInt(), (bounds.centerY() * DENSITY).roundToInt()]
    }

    /** The screen between tile 7 and tile 8, where nothing but the background is drawn. */
    fun PixelMap.gap(): Color {
        val left = compose.onNodeWithTag("tile:pkg.7").getBoundsInRoot()
        val right = compose.onNodeWithTag("tile:pkg.8").getBoundsInRoot()
        return this[((left.right.value + right.left.value) / 2f * DENSITY).roundToInt(), (left.centerY() * DENSITY).roundToInt()]
    }

    fun androidx.compose.ui.unit.DpRect.centerY() = (top.value + bottom.value) / 2f

    /** [fresh] after the faded look: its saturation cut to 55% and drawn at 75% over the [background]. */
    fun faded(
        fresh: Color,
        background: Color,
    ): Color {
        val grey = 0.213f * fresh.red + 0.715f * fresh.green + 0.072f * fresh.blue

        fun channel(
            own: Float,
            behind: Float,
        ) = 0.75f * (grey + 0.55f * (own - grey)) + 0.25f * behind
        return Color(channel(fresh.red, background.red), channel(fresh.green, background.green), channel(fresh.blue, background.blue))
    }

    fun assertLooks(
        expected: Color,
        actual: Color,
        what: String,
    ) = withClue("$what: expected $expected, got $actual") {
        (
            abs(actual.red - expected.red) < 0.03f &&
                abs(actual.green - expected.green) < 0.03f &&
                abs(actual.blue - expected.blue) < 0.03f
        ).shouldBeTrue()
    }

    fun assertFaded(
        screen: PixelMap,
        key: String,
        fresh: Color,
    ) = assertLooks(faded(fresh, screen.gap()), screen.tile(key), "$key faded")

    /** The green of tile [tile] each 16 ms from the key press and, last, once everything has settled. */
    fun greenAfterPress(
        key: Key,
        tile: String,
    ): Pair<List<Float>, Float> {
        compose.mainClock.autoAdvance = false
        press(key)
        compose.mainClock.advanceTimeBy(16)
        compose.waitForIdle()
        val series =
            List(12) {
                compose.mainClock.advanceTimeBy(16)
                screen().tile(tile).green
            }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        return series to screen().tile(tile).green
    }

    /** How far each sample is short of its end value, for a faded tile and for a fresh one that gets focus the same way. */
    fun shortfalls(): Pair<List<Float>, List<Float>> {
        val (fadedSeries, fadedEnd) = greenAfterPress(Key.DirectionDown, "pkg.7")
        val (freshSeries, freshEnd) = greenAfterPress(Key.DirectionRight, "pkg.8")
        return fadedSeries.map { fadedEnd - it } to freshSeries.map { freshEnd - it }
    }
}
