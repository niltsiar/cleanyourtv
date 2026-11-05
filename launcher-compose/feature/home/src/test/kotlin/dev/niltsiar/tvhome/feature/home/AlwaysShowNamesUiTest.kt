package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import dev.niltsiar.tvhome.core.config.TvConfig
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
import kotlin.math.abs

/** always_show_names puts the app's name under every tile, dimmed until it has focus, and makes room for it only then. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class AlwaysShowNamesUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    private fun showHome(config: TvConfig) {
        val sources = sources(apps = flowOf(AppList(apps)))
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

    private fun top(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().top

    private fun height(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().height

    /** How far the brightest and the darkest red of [tag] are apart: what shows of the name, in 0 to 1. */
    private fun contrast(tag: String): Float {
        val image: ImageBitmap = compose.onNodeWithTag(tag).captureToImage()
        val pixels = image.toPixelMap()
        val reds = (0 until image.height).flatMap { y -> (0 until image.width).map { x -> pixels[x, y].red } }
        return reds.max() - reds.min()
    }

    /** From the top of the first grid row to the top of the second, which only both being on screen can tell. */
    private fun rowPitch(): Dp {
        repeat(2) { press(Key.DirectionDown) }
        compose.onNodeWithTag("tile:pkg.13").assertIsFocused()
        return top("tile:pkg.13") - top("tile:pkg.7")
    }

    private fun assertNear(
        expected: Dp,
        actual: Dp,
    ) = assertTrue("expected $expected, got $actual", abs(expected.value - actual.value) <= 1f)

    @Test
    fun with_the_key_off_no_tile_has_a_name_under_it() {
        showHome(SixColumns)

        assertEquals(0, compose.onAllNodesWithTag("name:pkg.1").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("name:pkg.7").fetchSemanticsNodes().size)
    }

    @Test
    fun the_name_under_an_unfocused_dock_tile_shows_at_three_quarters_and_at_full_when_focused() {
        showHome(SixColumns.copy(alwaysShowNames = true))
        val focused = contrast("name:pkg.1")

        assertEquals(0.75f, contrast("name:pkg.2") / focused, 0.05f)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.2").assertIsFocused()
        assertEquals(focused, contrast("name:pkg.2"), 0.02f)
        assertEquals(0.75f, contrast("name:pkg.1") / focused, 0.05f)
    }

    @Test
    fun the_name_under_an_unfocused_grid_tile_shows_at_three_quarters_and_at_full_when_focused() {
        showHome(SixColumns.copy(alwaysShowNames = true))

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        assertEquals(0.75f, contrast("name:pkg.8") / contrast("name:pkg.7"), 0.05f)
    }

    @Test
    fun with_the_key_off_the_dock_panel_is_a_tile_and_two_gaps_tall() {
        showHome(SixColumns)
        val tile = height("tile:pkg.1")
        assertNear(tile + TileGap * 2, height("dock"))
    }

    @Test
    fun the_dock_panel_grows_by_the_room_of_a_name_with_the_key_on() {
        showHome(SixColumns.copy(alwaysShowNames = true))
        val tile = height("tile:pkg.1")
        assertNear(tile + TileGap * 2 + TileNameRoom, height("dock"))
    }

    @Test
    fun a_grid_row_is_a_tile_and_a_gap_tall_with_the_key_off() {
        showHome(SixColumns)
        assertNear(height("tile:pkg.1") + TileGap, rowPitch())
    }

    @Test
    fun a_grid_row_grows_by_the_room_of_a_name_with_the_key_on() {
        showHome(SixColumns.copy(alwaysShowNames = true))
        assertNear(height("tile:pkg.1") + TileGap + TileNameRoom, rowPitch())
    }

    @Test
    fun in_a_single_row_every_tile_has_its_name_and_the_panel_grows_to_hold_them() {
        showHome(SixColumns.copy(singleRow = true, alwaysShowNames = true))

        assertEquals(1, compose.onAllNodesWithTag("name:pkg.5").fetchSemanticsNodes().size)
        assertNear(height("tile:pkg.1") + TileGap * 2 + TileNameRoom, height("dock"))
    }
}
