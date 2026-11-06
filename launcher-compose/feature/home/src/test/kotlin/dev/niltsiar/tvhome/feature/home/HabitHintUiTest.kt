package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DENSITY = 2

/** habit_hint on the real presenter and screen: the mark under the usual app's tile, and the line under the title. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HabitHintUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }
    private var usual: String? = "pkg.2"
    private lateinit var viewModel: MoleculeHomeViewModel

    private fun showHome(
        config: TvConfig,
        motion: Motion = Motion(),
        lastOpened: Map<String, Long> = emptyMap(),
    ) {
        val sources = sources(apps = flowOf(AppList(apps)), lastOpened = flowOf(lastOpened), habit = { usual })
        compose.setContent {
            TvTheme(motion = motion) {
                viewModel = rememberHomeViewModel(sources, config, mutableListOf())
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
        settle()
    }

    private fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** The mark's centre column and its first row, in pixels, if it sits [below] dp under the bottom of [tag]. */
    private fun markAt(
        tag: String,
        below: Int = 0,
    ): Pair<Int, Int> {
        val bounds = compose.onNodeWithTag(tag).getBoundsInRoot()
        val centre = ((bounds.left.value + bounds.right.value) / 2f * DENSITY).roundToInt()
        // The tile's gap is 24 dp: a 3 dp bar in its middle starts 10.5 dp under the bottom, which is 21 px.
        return centre to (bounds.bottom.value * DENSITY).roundToInt() + below * DENSITY + 21
    }

    private fun isFill(pixel: Color) =
        abs(pixel.red - 0xF2 / 255f) < 0.02f && abs(pixel.green - 0xF4 / 255f) < 0.02f && abs(pixel.blue - 0xF8 / 255f) < 0.02f

    private fun barShownAt(
        screen: PixelMap,
        centre: Int,
        top: Int,
    ) = isFill(screen[centre, top + 3])

    private val dockTiles = listOf("pkg.1", "pkg.2", "pkg.3", "pkg.4", "pkg.5", "pkg.6")

    @Test
    fun the_mark_under_an_unfocused_dock_tile_is_a_light_bar_40_by_6_px_with_a_dark_edge() {
        showHome(SixColumns.copy(habitHint = true))
        val screen = screen()
        val (centre, top) = markAt("tile:pkg.2")

        assertTrue(barShownAt(screen, centre, top))
        assertTrue(isFill(screen[centre - 15, top + 3]) && isFill(screen[centre + 15, top + 3]))
        assertFalse(isFill(screen[centre - 23, top + 3]) || isFill(screen[centre + 23, top + 3]))
        assertFalse("taller than 6 px", isFill(screen[centre, top - 1]) || isFill(screen[centre, top + 6]))
        val edge = screen[centre, top]
        assertTrue("edge ${edge.red}", edge.red in 0.5f..0.72f)
        assertTrue("edge ${screen[centre, top + 5].red}", screen[centre, top + 5].red in 0.5f..0.72f)
        assertEquals(1f, screen[centre, top + 2].alpha, 0f)
    }

    @Test
    fun no_other_dock_tile_has_the_mark_and_nothing_moves() {
        showHome(SixColumns.copy(habitHint = true))
        val without = compose.onNodeWithTag("tile:pkg.3").getBoundsInRoot()
        val screen = screen()

        (dockTiles - "pkg.2").forEach { tile ->
            val (centre, top) = markAt("tile:$tile")
            assertFalse("$tile has a mark", barShownAt(screen, centre, top))
        }
        usual = null
        viewModel.onStopped()
        settle()
        viewModel.onStarted()
        settle()
        assertEquals(without, compose.onNodeWithTag("tile:pkg.3").getBoundsInRoot())
    }

    @Test
    fun the_mark_under_an_unfocused_grid_tile_is_the_same_bar_and_no_other_grid_tile_has_it() {
        usual = "pkg.8"
        showHome(SixColumns.copy(habitHint = true))
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        val screen = screen()

        val (centre, top) = markAt("tile:pkg.8")
        assertTrue(barShownAt(screen, centre, top))
        assertTrue(screen[centre, top].red in 0.5f..0.72f)
        listOf("pkg.9", "pkg.10", "pkg.11", "pkg.12").forEach { tile ->
            val (c, t) = markAt("tile:$tile")
            assertFalse("$tile has a mark", barShownAt(screen, c, t))
        }
    }

    @Test
    fun with_the_names_on_the_mark_sits_under_the_name_line_and_not_under_the_tile() {
        showHome(SixColumns.copy(habitHint = true, alwaysShowNames = true))
        val screen = screen()

        val (centre, top) = markAt("tile:pkg.2", below = TileNameRoom.value.toInt())
        assertTrue(barShownAt(screen, centre, top))
        val (_, bare) = markAt("tile:pkg.2")
        assertFalse(barShownAt(screen, centre, bare))
        val name = compose.onNodeWithTag("name:pkg.2").getBoundsInRoot()
        assertTrue(top > name.bottom.value * DENSITY + 18)
    }

    @Test
    fun the_mark_follows_the_habit_to_another_app_over_400_ms_and_the_old_mark_goes() {
        showHome(SixColumns.copy(habitHint = true))
        val (oldCentre, oldTop) = markAt("tile:pkg.2")
        val (newCentre, newTop) = markAt("tile:pkg.3")
        usual = "pkg.3"
        viewModel.onStopped()
        settle()
        compose.mainClock.autoAdvance = false

        viewModel.onStarted()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        val fading = mutableListOf<Float>()
        repeat(10) {
            compose.mainClock.advanceTimeBy(50)
            fading += screen()[newCentre, newTop + 3].red
        }

        assertTrue("not yet shown at 150 ms: $fading", fading.first() < 0.9f)
        assertTrue("fading in: $fading", fading.zipWithNext().all { (a, b) -> b >= a - 0.001f })
        assertTrue("full after 400 ms: $fading", isFill(screen()[newCentre, newTop + 3]))
        assertFalse(barShownAt(screen(), oldCentre, oldTop))
    }

    @Test
    fun with_reduced_motion_the_mark_is_there_at_once_and_does_not_fade() {
        showHome(SixColumns.copy(habitHint = true), motion = Motion(reducedMotion = true))
        usual = "pkg.3"
        viewModel.onStopped()
        settle()
        val (centre, top) = markAt("tile:pkg.3")
        compose.mainClock.autoAdvance = false

        viewModel.onStarted()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(34)

        assertTrue(barShownAt(screen(), centre, top))
    }

    @Test
    fun with_the_key_off_no_tile_has_the_mark() {
        showHome(SixColumns)
        val screen = screen()

        dockTiles.forEach { tile ->
            val (centre, top) = markAt("tile:$tile")
            assertFalse("$tile has a mark", barShownAt(screen, centre, top))
        }
    }

    private val yesterday =
        Evening
            .minusDays(1)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    @Test
    fun the_line_under_the_title_adds_the_usual_phrase_only_while_the_usual_app_has_focus() {
        showHome(SixColumns.copy(habitHint = true, cinematicHome = true), lastOpened = mapOf("pkg.2" to yesterday, "pkg.3" to yesterday))

        compose.onNodeWithTag("shelf:meta").assertTextEquals("Never opened")
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.2").assertIsFocused()
        compose.onNodeWithTag("shelf:meta").assertTextContains("Last opened", substring = true)
        compose.onNodeWithTag("shelf:meta").assertTextContains(" · You usually open it now", substring = true)

        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
        assertFalse(textOf("shelf:meta").contains("You usually open it now"))
        assertTrue(textOf("shelf:meta").startsWith("Last opened"))
    }

    @Test
    fun the_band_over_the_grid_adds_the_usual_phrase_too_and_alone_for_an_app_never_opened() {
        showHome(SixColumns.copy(habitHint = true), lastOpened = mapOf("pkg.2" to yesterday))
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.2").assertIsFocused()
        assertTrue(textOf("shelf:title-meta"), textOf("shelf:title-meta").endsWith(" · You usually open it now"))

        usual = "pkg.3"
        viewModel.onStopped()
        settle()
        viewModel.onStarted()
        settle()
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
        compose.onNodeWithTag("shelf:title-meta").assertTextEquals("You usually open it now")
    }

    private fun textOf(tag: String): String =
        compose
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            .joinToString("") { it.text }
}
