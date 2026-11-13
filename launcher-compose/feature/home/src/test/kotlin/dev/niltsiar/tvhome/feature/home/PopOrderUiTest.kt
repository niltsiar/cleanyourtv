package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
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
 * With focus_overshoot, a tile at the peak of its pop reaches past the 24 dp gap into its neighbour; it draws over it. In a
 * 2-column dock the white ring at the peak stands about 34 dp right of the first tile, 10 dp inside the second.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class PopOrderUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val apps = (1..6).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    private fun showHome(motion: Motion) {
        val config = TvConfig(columns = 2)
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
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.2").assertIsFocused()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun dockTile(n: Int): DpRect =
        compose.onNode(hasTestTag("tile:pkg.$n") and hasAnyAncestor(hasTestTag("dock"))).getBoundsInRoot()

    /** The colour, as its least channel, 112 ms after focus goes back Left to the first tile, where its ring is at the peak. */
    private fun ringAtPeakInsideTheSecondTile(): Float {
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.mainClock.advanceTimeBy(112)
        val first = dockTile(1)
        val second = dockTile(2)
        // The middle of the white ring: 3 dp wide, 3 dp out from the grown tile, grown with it.
        val x = first.right.value + (first.right - first.left).value * 0.072f + 4.5f * 1.144f
        assertTrue("$x is not inside the second tile ${second.left}", x > second.left.value + 4f)
        val density = compose.activity.resources.displayMetrics.density
        val pixel =
            compose.onRoot().captureToImage().toPixelMap()[
                (x * density).toInt(), ((first.top + first.bottom) / 2)
                    .value
                    .times(
                        density,
                    ).toInt(),
            ]
        return minOf(pixel.red, pixel.green, pixel.blue)
    }

    @Test
    fun with_focus_overshoot_a_tile_at_the_peak_of_its_pop_draws_its_ring_over_the_next_tile() {
        showHome(Motion(focusOvershoot = true))

        val least = ringAtPeakInsideTheSecondTile()
        assertTrue("the ring's least channel is $least", least >= 0.85f)
    }
}
