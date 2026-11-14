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
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.FocusStyle
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where the dock panel clips the halo of its focused first tile: at the panel's edge the halo draws what rim draws there, so
 * the glow ends without a step. Pixels are read at 2 pixels to the dp.
 */
val HaloClipUiTest by testSuite {
    robolectricTestSuite<HaloClipUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HaloClipUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HaloClipUiTestFixture() } asContextForEach {
            test("the halo of a six column dock tile ends at the panel edge as rim does and glows 2 dp past the keyline") {
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
                withClue("2 dp past the keyline the halo differs from rim by $glow levels") { glow shouldBeGreaterThanOrEqualTo 20 }
            }

            test("the halo of a four column dock tile ends at the panel edge as rim does") {
                assertHaloEndsAtTheClipLikeRim(TvConfig(columns = 4))
            }

            test("the halo of a three column dock tile with names ends at the panel edge as rim does") {
                assertHaloEndsAtTheClipLikeRim(TvConfig(columns = 3, alwaysShowNames = true))
            }

            test("the halo of a single row tile ends at the panel edge as rim does") {
                assertHaloEndsAtTheClipLikeRim(TvConfig(columns = 6, singleRow = true))
            }
        }
    })

private class HaloClipUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    var style by mutableStateOf(FocusStyle.Halo)

    fun showHome(config: TvConfig) {
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

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun dock(): DpRect = compose.onNodeWithTag("dock").getBoundsInRoot()

    fun firstTile(): DpRect = compose.onNode(hasTestTag("tile:pkg.1") and hasAnyAncestor(hasTestTag("dock"))).getBoundsInRoot()

    fun pixels(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** The largest difference between [a] and [b] in any colour channel, in levels of 255. */
    fun levels(
        a: Color,
        b: Color,
    ): Int = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)).times(255).roundToInt()

    /** A pixel, given in dp, as a pixel of the capture. */
    data class Spot(
        val name: String,
        val x: Int,
        val y: Int,
    )

    /**
     * The panel's last two rows of pixels under the focused first tile, its first two columns beside it and its first two
     * rows above it.
     */
    fun clipEdge(): List<Spot> {
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

    fun assertHaloEndsAtTheClipLikeRim(config: TvConfig) {
        showHome(config)
        val spots = clipEdge()
        val halo = pixels()
        style = FocusStyle.Rim
        settle()
        val rim = pixels()

        for (spot in spots) {
            val difference = levels(halo[spot.x, spot.y], rim[spot.x, spot.y])
            withClue("${spot.name} (${spot.x}, ${spot.y}): halo differs from rim by $difference levels") {
                difference shouldBeLessThanOrEqualTo
                    2
            }
        }
    }
}
