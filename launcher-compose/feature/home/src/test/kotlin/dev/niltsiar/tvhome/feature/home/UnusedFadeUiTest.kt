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
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
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
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DENSITY = 2
private val Orange = Color(0xFFE87A1A)

/** unused_fade_days on the real presenter and screen: the pixels of a faded grid tile against a fresh one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class UnusedFadeUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.systemDefault()
    private val now = LocalDateTime.of(2026, 11, 6, 20, 0)

    private fun millis(time: LocalDateTime) = time.atZone(zone).toInstant().toEpochMilli()

    private val art: ImageBitmap = ImageBitmap(16, 9).also { Canvas(it).drawRect(0f, 0f, 16f, 9f, Paint().apply { color = Orange }) }
    private val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = art, accent = Orange) }

    /** pkg.7 was opened 61 days ago and pkg.8 yesterday; every other app never, in a record 200 days old. */
    private val opened = mapOf("pkg.7" to millis(now.minusDays(61)), "pkg.8" to millis(now.minusDays(1)))

    private fun showHome(
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

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
    }

    private fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** The tile's art colour, left of its centre: a focused tile shows its name and a ring around the same art. */
    private fun PixelMap.tile(key: String): Color {
        val bounds = compose.onNodeWithTag("tile:$key").getBoundsInRoot()
        return this[(bounds.left.value * DENSITY + 12).roundToInt(), (bounds.centerY() * DENSITY).roundToInt()]
    }

    /** The screen between tile 7 and tile 8, where nothing but the background is drawn. */
    private fun PixelMap.gap(): Color {
        val left = compose.onNodeWithTag("tile:pkg.7").getBoundsInRoot()
        val right = compose.onNodeWithTag("tile:pkg.8").getBoundsInRoot()
        return this[((left.right.value + right.left.value) / 2f * DENSITY).roundToInt(), (left.centerY() * DENSITY).roundToInt()]
    }

    private fun androidx.compose.ui.unit.DpRect.centerY() = (top.value + bottom.value) / 2f

    /** [fresh] after the faded look: its saturation cut to 55% and drawn at 75% over the [background]. */
    private fun faded(
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

    private fun assertLooks(
        expected: Color,
        actual: Color,
        what: String,
    ) = assertTrue(
        "$what: expected $expected, got $actual",
        abs(actual.red - expected.red) < 0.03f && abs(actual.green - expected.green) < 0.03f && abs(actual.blue - expected.blue) < 0.03f,
    )

    private fun assertFaded(
        screen: PixelMap,
        key: String,
        fresh: Color,
    ) = assertLooks(faded(fresh, screen.gap()), screen.tile(key), "$key faded")

    @Test
    fun a_faded_grid_tile_draws_at_55_percent_saturation_and_75_percent_opacity_against_a_fresh_one() {
        showHome()
        val screen = screen()

        val fresh = screen.tile("pkg.8")

        assertTrue("the art shows in full colour: $fresh", fresh.red > 0.8f && fresh.blue < 0.3f)
        assertFaded(screen, "pkg.7", fresh)
    }

    @Test
    fun an_app_never_opened_in_an_old_record_is_faded_too() {
        showHome()
        val screen = screen()

        assertFaded(screen, "pkg.9", screen.tile("pkg.8"))
    }

    @Test
    fun a_faded_tile_returns_to_full_colour_while_it_has_focus_and_fades_again_when_focus_leaves() {
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

    /** The green of tile [tile] each 16 ms from the key press and, last, once everything has settled. */
    private fun greenAfterPress(
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
    private fun shortfalls(): Pair<List<Float>, List<Float>> {
        val (fadedSeries, fadedEnd) = greenAfterPress(Key.DirectionDown, "pkg.7")
        val (freshSeries, freshEnd) = greenAfterPress(Key.DirectionRight, "pkg.8")
        return fadedSeries.map { fadedEnd - it } to freshSeries.map { freshEnd - it }
    }

    @Test
    fun the_return_to_full_colour_is_a_short_fade_on_top_of_the_focus_look_the_same_tile_has_without_a_fade() {
        showHome()

        val (faded, fresh) = shortfalls()

        val extra = faded.zip(fresh).map { (a, b) -> a - b }
        assertTrue("still fading 32 ms after the key: $extra", extra[0] > 0.02f)
        assertTrue("fading less each frame: $extra", extra.zipWithNext().all { (a, b) -> b <= a + 0.01f })
        assertTrue("done by 200 ms: $extra", extra.last() < 0.02f)
    }

    @Test
    fun with_reduced_motion_the_return_to_full_colour_is_over_in_the_first_frame() {
        showHome(motion = Motion(reducedMotion = true))

        val (faded, fresh) = shortfalls()

        val extra = faded.zip(fresh).map { (a, b) -> a - b }
        assertTrue("no fade left after two frames: $extra", extra.drop(1).all { abs(it) < 0.02f })
    }

    @Test
    fun a_dock_tile_is_never_faded_whatever_its_app_was_last_opened() {
        showHome()
        val screen = screen()

        assertLooks(screen.tile("pkg.8"), screen.tile("pkg.2"), "dock tile of an app never opened")
    }

    @Test
    fun with_zero_days_no_tile_is_faded() {
        showHome(days = 0)
        val screen = screen()

        assertLooks(screen.tile("pkg.8"), screen.tile("pkg.7"), "pkg.7")
        assertLooks(screen.tile("pkg.8"), screen.tile("pkg.9"), "pkg.9")
    }
}
