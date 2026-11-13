package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The single row scrolls a focused tile until its whole ring is inside the row, which clips at the panel's walls. The ring stands
 * a tile's growth and 9 dp, both scaled, past each side of the tile.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class SingleRowRingUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val apps = (1..12).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    private fun showHome(motion: Motion) {
        val config = TvConfig(columns = 4, singleRow = true)
        val sources = sources(apps = flowOf(AppList(apps)))
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
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /** Asserts that the ring of the focused tile [n], at [scale], lies inside the row's walls. */
    private fun assertRingInside(
        n: Int,
        scale: Float,
        way: String,
    ) {
        val tile = compose.onNodeWithTag("tile:pkg.$n").assertIsFocused().getBoundsInRoot()
        val row = compose.onNodeWithTag("dock").getBoundsInRoot()
        val reach = (tile.right - tile.left).value * (scale - 1f) / 2f + 9f * scale
        val left = tile.left.value - reach
        val right = tile.right.value + reach
        assertTrue("tile $n $way: ring from $left to $right in a row from ${row.left} to ${row.right}", left >= row.left.value - HALF_PIXEL)
        assertTrue(
            "tile $n $way: ring from $left to $right in a row from ${row.left} to ${row.right}",
            right <= row.right.value + HALF_PIXEL,
        )
    }

    private fun assertRingInsideAcrossTheRowAndBack(
        motion: Motion,
        scale: Float,
    ) {
        showHome(motion)
        assertRingInside(1, scale, "at the start")
        for (n in 2..apps.size) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            settle()
            assertRingInside(n, scale, "moving right")
        }
        for (n in apps.size - 1 downTo 1) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
            settle()
            assertRingInside(n, scale, "moving left")
        }
    }

    @Test
    fun every_focused_tile_of_the_single_row_has_its_whole_ring_inside_the_row() {
        assertRingInsideAcrossTheRowAndBack(Motion(), scale = 1.08f)
    }

    @Test
    fun with_focus_overshoot_every_focused_tile_of_the_single_row_has_its_ring_at_the_peak_inside_the_row() {
        assertRingInsideAcrossTheRowAndBack(Motion(focusOvershoot = true), scale = 1.144f)
    }
}

/** The layout places in whole pixels; at xhdpi rounding moves an edge by up to half of one, in dp. */
private const val HALF_PIXEL = 0.25f
