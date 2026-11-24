package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputConnection
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DENSITY = 2

/** A pinned input's tile on the real presenter and screen: its face, its dot, its name and the line under the title. */
val InputTileUiTest by testSuite {
    robolectricTestSuite<InputTileUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

/** A layout the tile is checked in: the [config], where the tile sits and the key that takes focus to it, and the line's tag. */
private class TileLayout(
    val name: String,
    val config: TvConfig,
    val docked: Boolean,
    val line: String = "shelf:title-meta",
)

class InputTileUiTestContent :
    RobolectricTestSuiteContent({
        val layouts =
            listOf(
                TileLayout("in the grid", SixColumns, docked = false),
                TileLayout("in the dock", SixColumns, docked = true),
                TileLayout("in a single row", SixColumns.copy(singleRow = true), docked = true),
                TileLayout("in the cinematic dock", SixColumns.copy(cinematicHome = true), docked = true, line = "shelf:meta"),
            )
        testFixture { InputTileUiTestFixture() } asContextForEach {
            for (layout in layouts) {
                test("${layout.name} an input tile shows its glyph and name and a green dot while connected, and its line says Connected") {
                    showHome(layout.config, docked = layout.docked)
                    press(if (layout.docked) Key.DirectionRight else Key.DirectionDown)
                    compose.onNodeWithTag("tile:${PlayStationTile.key}").assertIsFocused()

                    faceText("PlayStation").assertExists()
                    val screen = screen()
                    val tile = tileBounds()
                    withClue("glyph") { glyphShown(screen, tile, named = true).shouldBeTrue() }
                    withClue("green dot") { dotAt(screen, tile).any(::isGreen).shouldBeTrue() }
                    compose.onNodeWithTag(layout.line).assertTextEquals("Connected")
                }
            }

            test("with always_show_names the name shows once, under the tile") {
                showHome(SixColumns.copy(alwaysShowNames = true), docked = true)

                nodesTagged("name:${PlayStationTile.key}") shouldBe 1
                faceText("PlayStation").assertDoesNotExist()
                withClue("glyph") { glyphShown(screen(), tileBounds(), named = false).shouldBeTrue() }
            }

            test("a listed input's tile shows the name its row reads now, and the stored name when it is not listed") {
                inputs.value = playStationInputs(label = "PS5 · HDMI 2")
                showHome(SixColumns, docked = true)
                faceText("PS5 · HDMI 2").assertExists()

                inputs.value = playStationInputs(label = null)
                settle()
                faceText("HDMI 2").assertExists()
                faceText("PS5").assertDoesNotExist()

                inputs.value = playStationInputs(listed = false)
                settle()
                faceText("PlayStation").assertExists()
            }

            test("an input the TV does not list keeps its tile with a grey dot and its line says No device") {
                inputs.value = playStationInputs(listed = false)
                showHome(SixColumns, docked = true)
                press(Key.DirectionRight)

                val dot = dotAt(screen(), tileBounds())
                withClue("grey dot") { dot.any(::isGrey).shouldBeTrue() }
                dot.any(::isGreen).shouldBeFalse()
                compose.onNodeWithTag("shelf:title-meta").assertTextEquals("No device")
            }

            test("an input with no state draws no dot") {
                inputs.value = playStationInputs(connection = InputConnection.Unknown)
                showHome(SixColumns, docked = true)

                val dot = dotAt(screen(), tileBounds())
                withClue("a dot") { dot.any { pixel -> isGreen(pixel) || isGrey(pixel) }.shouldBeFalse() }
            }

            test("a state change recomposes no tile") {
                showHome(SixColumns, docked = true)
                val counter = Recompositions()
                startCounting(counter)
                try {
                    inputs.value = playStationInputs(connection = InputConnection.Disconnected)
                    settle()

                    withClue("grey dot") { dotAt(screen(), tileBounds()).any(::isGrey).shouldBeTrue() }
                    counter.tiles shouldBe 0
                    counter.faces shouldBe 0
                } finally {
                    stopCounting()
                }
            }
        }
    })

private class InputTileUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val inputs = MutableStateFlow(playStationInputs())

    fun showHome(
        config: TvConfig,
        docked: Boolean,
    ) {
        val apps: List<AppEntry> =
            if (docked) {
                listOf(NineApps[0], PlayStationTile) + NineApps.drop(1)
            } else {
                NineApps.take(6) +
                    PlayStationTile
            }
        val sources = sources(apps = flowOf(AppList(apps)), inputs = inputs)
        compose.setContent {
            TvTheme(look = Look.Glass) {
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
        settle()
    }

    fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    fun tileBounds(): Rect =
        compose.onNodeWithTag("tile:${PlayStationTile.key}").getBoundsInRoot().let {
            Rect(it.left.value * DENSITY, it.top.value * DENSITY, it.right.value * DENSITY, it.bottom.value * DENSITY)
        }

    /** The text on the tile's face that starts with [label]: a long name is cut to one line. */
    fun faceText(label: String) =
        compose.onNode(
            hasText(label, substring = true) and hasAnyAncestor(hasTestTag("input-tile:${PlayStationTile.key}")),
            useUnmergedTree = true,
        )

    fun nodesTagged(tag: String): Int = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    /** The pixels around the dot's centre, 15 dp in from the tile's top and end. */
    fun dotAt(
        screen: PixelMap,
        tile: Rect,
    ): List<Color> {
        val x = (tile.right - 15 * DENSITY).roundToInt()
        val y = (tile.top + 15 * DENSITY).roundToInt()
        return (-4..4).flatMap { dx -> (-4..4).map { dy -> screen[x + dx, y + dy] } }
    }

    /**
     * Whether the glyph's light ink shows in its square: over the middle of the tile when the name is under it, else centred.
     * The square holds no text either way.
     */
    fun glyphShown(
        screen: PixelMap,
        tile: Rect,
        named: Boolean,
    ): Boolean {
        val centreX = tile.center.x.roundToInt()
        val centreY = tile.center.y.roundToInt()
        val rows = if (named) (centreY - 26 * DENSITY)..(centreY - 2 * DENSITY) else (centreY - 12 * DENSITY)..(centreY + 12 * DENSITY)
        return rows.any { y ->
            (-14 * DENSITY..14 * DENSITY).any { dx ->
                screen[centreX + dx, y].let {
                    minOf(it.red, it.green, it.blue) >
                        0.6f
                }
            }
        }
    }

    fun isGreen(pixel: Color) = pixel.green > pixel.red + 0.25f && pixel.green > pixel.blue + 0.2f

    fun isGrey(pixel: Color) =
        abs(pixel.red - 0x8E / 255f) < 0.06f && abs(pixel.green - 0x8E / 255f) < 0.06f && abs(pixel.blue - 0x93 / 255f) < 0.06f

    @OptIn(InternalComposeTracingApi::class)
    fun startCounting(counter: Recompositions) {
        Composer.setTracer(counter)
    }

    @OptIn(InternalComposeTracingApi::class)
    fun stopCounting() {
        Composer.setTracer(NoTracing)
    }
}

/** Counts the AppTile and InputTileFace bodies that run; the compiler calls it for each composable that does not skip. */
@OptIn(InternalComposeTracingApi::class)
private class Recompositions : CompositionTracer {
    var tiles = 0
    var faces = 0

    override fun isTraceInProgress() = true

    override fun traceEventStart(
        key: Int,
        dirty1: Int,
        dirty2: Int,
        info: String,
    ) {
        if (info.startsWith("dev.niltsiar.tvhome.feature.home.AppTile ")) tiles++
        if (info.startsWith("dev.niltsiar.tvhome.feature.home.InputTileFace ")) faces++
    }

    override fun traceEventEnd() = Unit
}

@OptIn(InternalComposeTracingApi::class)
private object NoTracing : CompositionTracer {
    override fun isTraceInProgress() = false

    override fun traceEventStart(
        key: Int,
        dirty1: Int,
        dirty2: Int,
        info: String,
    ) = Unit

    override fun traceEventEnd() = Unit
}
