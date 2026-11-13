package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.FocusStyle
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where the dock panel clips the halo of its focused first tile: at the panel's edge the halo draws what rim draws there, so
 * the glow ends without a step. Pixels are read at 2 pixels to the dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HaloClipUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }
    private var style by mutableStateOf(FocusStyle.Halo)

    private fun showHome(config: TvConfig) {
        val sources = sources(apps = flowOf(AppList(apps, 0)))
        compose.setContent {
            TvTheme(focusStyle = style) {
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

    private fun dock(): DpRect = compose.onNodeWithTag("dock").getBoundsInRoot()

    private fun firstTile(): DpRect = compose.onNode(hasTestTag("tile:pkg.1") and hasAnyAncestor(hasTestTag("dock"))).getBoundsInRoot()

    private fun pixels(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** The largest difference between [a] and [b] in any colour channel, in levels of 255. */
    private fun levels(
        a: Color,
        b: Color,
    ): Int = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)).times(255).roundToInt()

    /** A pixel, given in dp, as a pixel of the capture. */
    private data class Spot(
        val name: String,
        val x: Int,
        val y: Int,
    )

    /**
     * The panel's last two rows of pixels under the focused first tile, its first two columns beside it and its first two
     * rows above it.
     */
    private fun clipEdge(): List<Spot> {
        val panel = dock()
        val tile = firstTile()
        val midX = ((tile.left + tile.right).value).roundToInt()
        val midY = ((tile.top + tile.bottom).value).roundToInt()
        val left = (panel.left.value * 2).roundToInt()
        val top = (panel.top.value * 2).roundToInt()
        val bottom = (panel.bottom.value * 2).roundToInt()
        return listOf(
            Spot("under", midX, bottom - 1),
            Spot("under", midX, bottom - 2),
            Spot("beside", left, midY),
            Spot("beside", left + 1, midY),
            Spot("above", midX, top),
            Spot("above", midX, top + 1),
        )
    }

    private fun assertHaloEndsAtTheClipLikeRim(config: TvConfig) {
        showHome(config)
        val spots = clipEdge()
        val halo = pixels()
        style = FocusStyle.Rim
        settle()
        val rim = pixels()

        for (spot in spots) {
            val difference = levels(halo[spot.x, spot.y], rim[spot.x, spot.y])
            assertTrue("${spot.name} (${spot.x}, ${spot.y}): halo differs from rim by $difference levels", difference <= 2)
        }
    }

    @Test
    fun the_halo_of_a_six_column_dock_tile_ends_at_the_panel_edge_as_rim_does_and_glows_2_dp_past_the_keyline() {
        assertHaloEndsAtTheClipLikeRim(SixColumns)

        val tile = firstTile()
        val centreY = (tile.top + tile.bottom).value / 2f
        val underKeyline = centreY + 1.08f * ((tile.bottom - tile.top).value / 2f + 9f) + 2f
        val x = ((tile.left + tile.right).value).roundToInt()
        val y = (underKeyline * 2).roundToInt()
        val rim = pixels()[x, y]
        style = FocusStyle.Halo
        settle()
        val glow = levels(pixels()[x, y], rim)
        assertTrue("2 dp past the keyline the halo differs from rim by $glow levels", glow >= 20)
    }

    @Test
    fun the_halo_of_a_four_column_dock_tile_ends_at_the_panel_edge_as_rim_does() {
        assertHaloEndsAtTheClipLikeRim(TvConfig(columns = 4))
    }

    @Test
    fun the_halo_of_a_three_column_dock_tile_with_names_ends_at_the_panel_edge_as_rim_does() {
        assertHaloEndsAtTheClipLikeRim(TvConfig(columns = 3, alwaysShowNames = true))
    }

    @Test
    fun the_halo_of_a_single_row_tile_ends_at_the_panel_edge_as_rim_does() {
        assertHaloEndsAtTheClipLikeRim(TvConfig(columns = 6, singleRow = true))
    }
}
