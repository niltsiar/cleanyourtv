package dev.niltsiar.tvhome.feature.home

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.unit.width
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.Wallpaper
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs
import kotlin.math.ceil

/**
 * The dock panel read from the rendered pixels. The wallpaper's picture is left out, so the screen is black except for
 * the frost the dock draws from the wallpaper's thumbnail: a pixel in the gap between two dock tiles is the frost under
 * the tint.
 */
val DockGlassUiTest by testSuite {
    robolectricTestSuite<DockGlassUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class DockGlassUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { DockGlassUiTestFixture() } asContextForEach {
            test("with dock glass a dock tile has a 1 5 px black 018 edge inside it") {
                showHome(white, cinematic.copy(dockGlass = true), shown = bannerApps)

                val inside = tileEdgeAt(3, inPx = 4)
                inside shouldBe (245f / 255f plusOrMinus 0.01f)
                tileEdgeAt(3, inPx = 0) shouldBe (0.82f * inside plusOrMinus 0.015f)
            }

            test("without dock glass a dock tile has no edge") {
                showHome(white, cinematic, shown = bannerApps)

                tileEdgeAt(3, inPx = 0) shouldBe (tileEdgeAt(3, inPx = 4) plusOrMinus 0.01f)
            }

            test("the dock panel tints the frost by 016 on a dim wallpaper") {
                showHome(uniform)
                val at = gap()

                assertSame(over(dark, 0.16f, frostAt(uniform, at)), screenAt(at))
            }

            test("a bright wallpaper raises the tint to 022") {
                showHome(white)
                val at = gap()

                withClue("L* ${glass.dockLightness}") { glass.dockLightness shouldBeGreaterThanOrEqualTo 75f }
                assertSame(over(dark, 0.22f, frostAt(white, at)), screenAt(at))
            }

            test("a wallpaper with no thumbnail goes back to the base tint after a bright one") {
                val wallpaper = MutableStateFlow(Wallpaper(thumbnail = white))
                showHome(white, wallpaper = wallpaper)
                withClue("L* ${glass.dockLightness}") { glass.dockLightness shouldBeGreaterThanOrEqualTo 75f }

                wallpaper.value = Wallpaper()
                settle()

                DockLook().tint(darkSurfaces = true, lightness = glass.dockLightness).alpha shouldBe (0.16f plusOrMinus 0.005f)
            }

            test("dock opacity sets the tint to 085 times the percent whatever the wallpaper") {
                showHome(white, cinematic.copy(dockOpacity = 50))
                val at = gap()

                assertSame(over(dark, 0.85f * 0.5f, frostAt(white, at)), screenAt(at))
            }

            test("dock glass draws the light tint in night mode between the dock tiles") {
                showHome(uniform, cinematic.copy(dockGlass = true))
                val at = gap()

                assertSame(over(light, 0.20f, frostAt(uniform, at, glass = true)), screenAt(at))
            }

            test("focus in the top bar darkens a light tray and its tiles over a white picture") {
                showHome(
                    white,
                    cinematic.copy(dockGlass = true),
                    wallpaper = flowOf(Wallpaper(picture = white, thumbnail = white)),
                    shown = lightApps,
                )
                val tray = trayLuminance()
                val tile = tileLuminance("tile:pkg.3")

                press(Key.DirectionUp)

                withClue("the tray goes from $tray to ${trayLuminance()}") { trayLuminance() shouldBeLessThanOrEqualTo 0.8f * tray }
                withClue("a tile goes from $tile to ${tileLuminance("tile:pkg.3")}") {
                    tileLuminance("tile:pkg.3") shouldBeLessThanOrEqualTo
                        0.8f * tile
                }
            }

            test("a light glass tray over a white picture keeps a mean relative luminance under 057") {
                showHome(white, cinematic.copy(dockGlass = true))

                withClue("luminance ${trayLuminance()}") { trayLuminance() shouldBeLessThanOrEqualTo 0.57f }
            }

            test("on a dark picture a light glass tray lifts the picture by at most 002 and keeps its rim") {
                showHome(night, cinematic.copy(dockGlass = true))
                val at = gap()

                val lift = screenAt(at).luminance() - frostAt(night, at, glass = true).luminance()
                withClue("the tray lifts the picture by $lift") { lift shouldBeLessThanOrEqualTo 0.023f }
                val inside = panelEdgeAt(7)
                (panelEdgeAt(1) - inside) / (1f - inside) shouldBe (0.30f plusOrMinus 0.03f)
            }

            test("a light glass tray over a dark frost stays under 012 and 2 5 times the frost between the tiles") {
                showHome(dusk, cinematic.copy(dockGlass = true))
                val at = gap()

                val frost = frostAt(dusk, at, glass = true).luminance()
                val tray = screenAt(at).luminance()
                withClue("the frost is $frost") { frost shouldBeIn 0.06f..0.08f }
                withClue("the tray is $tray over a frost of $frost") { tray shouldBeLessThanOrEqualTo minOf(2.5f * frost, 0.12f) }
            }

            test("a light glass tray over a dark frost keeps its top strip under 020") {
                showHome(twilight, cinematic.copy(dockGlass = true))

                // 5 to 16.5 dp under the panel's top: below the rim, over the sheen.
                val strip = trayLuminance(fromDp = 5f, toDp = 16.5f)
                withClue("the strip is $strip") { strip shouldBeLessThanOrEqualTo 0.20f }
            }

            test("a light glass tray over a dark frost has a 1 dp white 030 line on its top edge") {
                showHome(dusk, cinematic.copy(dockGlass = true))
                val inside = panelEdgeAt(7)

                (panelEdgeAt(1) - inside) / (1f - inside) shouldBe (0.30f plusOrMinus 0.03f)
            }

            test("a light glass tray over a white picture keeps the white 040 rim") {
                showHome(white, cinematic.copy(dockGlass = true))
                val inside = panelEdgeAt(7)

                (panelEdgeAt(1) - inside) / (1f - inside) shouldBe (0.40f plusOrMinus 0.03f)
            }

            test("the dock panel has a 1 5 dp white highlight along its top edge") {
                showHome(uniform)

                // At 2 pixels to the dp, row 2 lies 1 to 1.5 dp down, in the highlight; row 7 lies 3.5 dp down, below it.
                val lift = panelEdgeAt(2) - panelEdgeAt(7)
                withClue("the highlight lifts the red by $lift") { lift shouldBeIn 0.05f..0.20f }
            }

            test("the dock panel has a 1 dp dark edge along its bottom") {
                showHome(uniform)

                val drop = panelEdgeAt(6, fromBottom = true) - panelEdgeAt(0, fromBottom = true)
                withClue("the edge darkens the red by $drop") { drop shouldBeIn 0.05f..0.25f }
            }

            test("a light glass tray has a 1 dp black 030 hairline round its outside") {
                showHome(white, cinematic.copy(dockGlass = true), wallpaper = flowOf(Wallpaper(picture = white, thumbnail = white)))

                // Row 1 lies in the hairline, 0.5 dp out; row 8 lies 4 dp out, past it.
                val dark = redOutsideTop(8) - redOutsideTop(1)
                withClue("the hairline darkens the red by $dark") { dark shouldBeIn 0.15f..0.35f }
            }

            test("a dark tray has no hairline round its outside") {
                showHome(white, wallpaper = flowOf(Wallpaper(picture = white, thumbnail = white)))

                redOutsideTop(1) shouldBe (redOutsideTop(8) plusOrMinus 0.06f)
            }

            test("dock glass holds a saturated wallpaper to 035 saturation so a sky colour does not pool") {
                showHome(cyan, cinematic.copy(dockGlass = true))

                withClue("saturation ${saturation(screenAt(gap()))}") { saturation(screenAt(gap())) shouldBeLessThanOrEqualTo 0.36f }
            }

            test("dock glass draws a black 075 edge 1 dp wide inside each light dock tile") {
                showHome(uniform, cinematic.copy(dockGlass = true), shown = lightApps)

                assertEdge("tile:pkg.2", level245)
            }

            test("on the lightest tray the edge of a white tile is at least 008 darker than its art and the tray") {
                showHome(
                    white,
                    cinematic.copy(dockGlass = true),
                    wallpaper = flowOf(Wallpaper(picture = white, thumbnail = white)),
                    shown = whiteApps,
                )

                val (edge, art, tray) = edgeAndNeighbours("tile:pkg.3")
                withClue("the edge is $edge, its art $art, the tray $tray") { (edge <= art - 0.08f && edge <= tray - 0.08f).shouldBeTrue() }
            }

            test("a light dock tile over a bright wallpaper gets the edge on the dark tray") {
                showHome(white, wallpaper = brightWallpaper, shown = lightApps)

                assertEdge("tile:pkg.2", level245)
            }

            test("the edge of a white dock tile holds 3 to 1 against the dark tray over a bright wallpaper") {
                showHome(white, wallpaper = brightWallpaper, shown = whiteApps)

                assertEdgeHolds("tile:pkg.6")
            }

            test("the edge of a white dock tile holds 3 to 1 against the glass tray over a bright wallpaper") {
                showHome(white, cinematic.copy(dockGlass = true), wallpaper = brightWallpaper, shown = whiteApps)

                assertEdgeHolds("tile:pkg.6")
            }

            test("the edge of a white dock tile holds 3 to 1 against mid tone glass") {
                showHome(midTone, cinematic.copy(dockGlass = true), shown = whiteApps)

                assertEdgeHolds("tile:pkg.6")
            }

            test("the edge of a white grid tile holds 3 to 1 against a bright wallpaper") {
                showHome(white, wallpaper = brightWallpaper, shown = whiteApps)
                press(Key.DirectionDown)

                assertEdgeHolds("tile:pkg.9")
            }

            test("the edge of a white grid tile holds 3 to 1 where the wallpaper is white beside it and dark on average") {
                val wallpaper = halved(whiteLeft = true)
                showHome(wallpaper, wallpaper = flowOf(Wallpaper(picture = wallpaper, thumbnail = wallpaper)), shown = whiteApps)
                press(Key.DirectionDown)

                withClue("the tile lies on the white left half") {
                    compose
                        .onNodeWithTag("tile:pkg.9")
                        .getBoundsInRoot()
                        .right.value shouldBeLessThan screenCentre()
                }
                assertEdgeHolds("tile:pkg.9")
            }

            test("the edge of a white dock tile holds 3 to 1 on the dark tray where the wallpaper is white beside it and dark on average") {
                val wallpaper = halved(whiteLeft = false)
                showHome(wallpaper, wallpaper = flowOf(Wallpaper(picture = wallpaper, thumbnail = wallpaper)), shown = whiteApps)

                withClue("the tile and the 4 dp beside it lie on the white right half") {
                    dockTile(6).left.value - 4f shouldBeGreaterThan
                        screenCentre()
                }
                assertEdgeHolds("tile:pkg.6")
            }

            test("the edge of a white dock tile holds 3 to 1 with no tray where the wallpaper is white beside it and dark on average") {
                val wallpaper = halved(whiteLeft = false)
                showHome(
                    wallpaper,
                    cinematic.copy(dockBand = false),
                    wallpaper = flowOf(Wallpaper(picture = wallpaper, thumbnail = wallpaper)),
                    shown = whiteApps,
                )

                withClue("the tile and the 4 dp beside it lie on the white right half") {
                    dockTile(6).left.value - 4f shouldBeGreaterThan
                        screenCentre()
                }
                assertEdgeHolds("tile:pkg.6")
            }

            test("the light tray of a day mode dock gives a light tile the same edge") {
                showHome(uniform, darkTheme = false, shown = lightApps)

                assertEdge("tile:pkg.2", level245)
            }

            test("a dark tray gives a light tile the edge whatever the wallpaper") {
                showHome(uniform, shown = lightApps)

                assertEdge("tile:pkg.2", level245)
            }

            test("a dark tile gets the glass edge and not the light tile edge on the light tray") {
                showHome(uniform, cinematic.copy(dockGlass = true), shown = darkApps)

                val (edge, inside) = edgeOf("tile:pkg.2")
                inside shouldBe (40f / 255f plusOrMinus 0.01f)
                withClue("edge $edge") { edge.min() shouldBe (0.82f * 40f / 255f plusOrMinus 0.01f) }
            }

            test("a light grid tile over a bright wallpaper gets the edge") {
                showHome(white, shown = lightApps)
                press(Key.DirectionDown)

                assertEdge("tile:pkg.8", level245)
            }

            test("a light grid tile over a dim wallpaper gets the edge") {
                showHome(uniform, shown = lightApps)
                press(Key.DirectionDown)

                assertEdge("tile:pkg.8", level245)
            }

            test("a dark grid tile over a bright wallpaper gets no edge") {
                showHome(white, shown = darkApps)
                press(Key.DirectionDown)

                assertNoEdge("tile:pkg.8", 40f / 255f)
            }

            test("the dock rim is white 040 at the top edge") {
                showHome(uniform)
                val inside = panelEdgeAt(7)

                (panelEdgeAt(1) - inside) / (1f - inside) shouldBe (0.40f plusOrMinus 0.03f)
            }

            test("the dock rim fades to white 008 at the bottom edge under the black 025 one") {
                showHome(uniform)
                val inside = panelEdgeAt(7, fromBottom = true)
                val rim = panelEdgeAt(0, fromBottom = true) / 0.75f

                (rim - inside) / (1f - inside) shouldBe (0.08f plusOrMinus 0.015f)
            }

            test("a dock with four tiles in a row of six is a panel for four centred with 28 dp at the sides") {
                showHome(uniform, cinematic.copy(fillDock = false), docked = 4)
                val panel = compose.onNodeWithTag("dock").getBoundsInRoot()
                val screen = compose.onRoot().getBoundsInRoot()
                val first = dockTile(1)
                val second = dockTile(2)
                val column = (880f - 5 * 24f) / 6f

                panel.width.value shouldBe (4 * column + 3 * 24f + 2 * 28f plusOrMinus 1f)
                (panel.left.value + panel.right.value) / 2f shouldBe (screen.width.value / 2f plusOrMinus 1f)
                first.left.value - panel.left.value shouldBe (28f plusOrMinus 1f)
                second.left.value - first.right.value shouldBe (24f plusOrMinus 1f)
                first.width.value shouldBe (column plusOrMinus 1f)
            }

            test("a dock with a full row keeps the panel one gap wider than the grid on each side") {
                showHome(uniform)
                val panel = compose.onNodeWithTag("dock").getBoundsInRoot()

                panel.width.value shouldBe (880f + 2 * 24f plusOrMinus 1f)
                dockTile(1).left.value - panel.left.value shouldBe (26.8f plusOrMinus 0.6f)
            }

            test("without night mode the tint is the light one") {
                showHome(uniform, darkTheme = false)
                val at = gap()

                assertSame(over(light, 0.20f, frostAt(uniform, at)), screenAt(at))
            }

            test("with dock band off the tiles float on the wallpaper") {
                showHome(uniform, cinematic.copy(dockBand = false))

                assertSame(Color.Black, screenAt(gap()), tolerance = 14)
            }

            test("the frost stays with the wallpaper under the scrim of a dock tiles menu with the dock where it was") {
                showHome(gradient)
                val before = compose.onNodeWithTag("dock").getBoundsInRoot().top

                holdOk()

                val after = compose.onNodeWithTag("dock").getBoundsInRoot().top
                withClue("the dock moved from $before to $after") { abs((before - after).value) shouldBeLessThan 1f }
                val at = gap()
                // The menu's scrim darkens everything but the tile it acts on by 0.5 of #07080C.
                val scrim = Color(0xFF07080C)
                val tint = gradientTint()
                assertSame(over(scrim, 0.5f, over(tint, tint.alpha, frostAt(gradient, at))), screenAt(at), tolerance = 8)
            }

            test("the frost stays with the wallpaper when the dock goes to the grid and back") {
                showHome(gradient)
                val at = gap()
                val tint = gradientTint()
                assertSame(over(tint, tint.alpha, frostAt(gradient, at)), screenAt(at), tolerance = 8)

                compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                settle()
                compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
                settle()

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                assertSame(over(tint, tint.alpha, frostAt(gradient, at)), screenAt(at), tolerance = 8)
            }

            test("the first dock tiles ring keeps 12 dp from the dock wall") {
                showHome(night)

                val gap = ringGapToWall(slot = 1, side = -1)

                withClue("the ring stood $gap dp from the left wall") { gap shouldBeGreaterThanOrEqualTo 12f }
            }

            test("the last dock tiles ring keeps 12 dp from the dock wall") {
                showHome(night)
                repeat(5) { press(Key.DirectionRight) }
                compose.onNodeWithTag("tile:pkg.6").assertIsFocused()

                val gap = ringGapToWall(slot = 6, side = +1)

                withClue("the ring stood $gap dp from the right wall") { gap shouldBeGreaterThanOrEqualTo 12f }
            }

            test("with names the first dock tiles ring keeps 12 dp from the dock wall") {
                showHome(night, config = cinematic.copy(alwaysShowNames = true))

                val gap = ringGapToWall(slot = 1, side = -1)

                withClue("the ring stood $gap dp from the left wall") { gap shouldBeGreaterThanOrEqualTo 12f }
            }

            test("a focused end dock tile leaves its neighbour the room a focused middle tile leaves in a four column dock") {
                assertEndTilesLeaveTheirNeighbourTheRoomAMiddleTileLeaves(columns = 4)
            }

            test("a focused end dock tile leaves its neighbour the room a focused middle tile leaves in a six column dock") {
                assertEndTilesLeaveTheirNeighbourTheRoomAMiddleTileLeaves(columns = 6)
            }
        }
    })

private class DockGlassUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    lateinit var glass: GlassState

    val apps = (1..9).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    val cinematic = SixColumns.copy(cinematicHome = true, appLibrary = true)

    fun bitmap(
        width: Int,
        height: Int,
        pixel: (x: Int, y: Int) -> Int,
    ): ImageBitmap =
        Bitmap
            .createBitmap(
                IntArray(width * height) { pixel(it % width, it / width) },
                width,
                height,
                Bitmap.Config.ARGB_8888,
            ).asImageBitmap()

    fun grey(level: Int) = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    val uniform = bitmap(40, 40) { _, _ -> (0xFF shl 24) or (180 shl 16) or (110 shl 8) or 60 }

    val cyan = bitmap(40, 40) { _, _ -> (0xFF shl 24) or (0 shl 16) or (200 shl 8) or 255 }

    val lightApps =
        (1..9).map {
            AppEntry(
                key = "pkg.$it",
                packageName = "pkg.$it",
                label = "App $it",
                image = bitmap(16, 9) { _, _ -> grey(245) },
                lightArt = true,
            )
        }

    val darkApps =
        (1..9).map {
            AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = bitmap(16, 9) { _, _ -> grey(40) })
        }

    val white = bitmap(40, 40) { _, _ -> grey(255) }

    val night = bitmap(40, 40) { _, _ -> grey(20) }

    /** A frost of relative luminance 0.07 once baked, and one of 0.11 (mean L* 36): dark pictures under a light glass dock. */
    val dusk = bitmap(40, 40) { _, _ -> grey(76) }

    val twilight = bitmap(40, 40) { _, _ -> grey(90) }

    /** Black at the top to white at the bottom, so a frost drawn a few dp off its place reads as another grey. */
    val gradient = bitmap(40, 40) { _, y -> grey(y * 255 / 39) }

    fun showHome(
        thumbnail: ImageBitmap,
        config: TvConfig = cinematic,
        darkTheme: Boolean = true,
        wallpaper: Flow<Wallpaper> = flowOf(Wallpaper(thumbnail = thumbnail)),
        shown: List<AppEntry> = apps,
        docked: Int = 0,
    ) {
        val sources = sources(apps = flowOf(AppList(shown, docked)), wallpaper = wallpaper)
        compose.setContent {
            TvTheme(darkTheme = darkTheme) {
                glass = rememberGlassState()
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
                    glass = glass,
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

    fun holdOk() {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
    }

    fun dockTile(n: Int): DpRect = compose.onNode(hasTestTag("tile:pkg.$n") and hasAnyAncestor(hasTestTag("dock"))).getBoundsInRoot()

    /** The middle of the gap between the third and fourth dock tiles, half way down the panel: no tile, ring or sheen is there. */
    fun gap(): Offset {
        val panel = compose.onNodeWithTag("dock").getBoundsInRoot()
        return Offset((dockTile(3).right.value + dockTile(4).left.value) / 2f, (panel.top.value + panel.bottom.value) / 2f)
    }

    fun screenAt(point: Offset): Color {
        val density = compose.activity.resources.displayMetrics.density
        return compose.onRoot().captureToImage().toPixelMap()[(point.x * density).toInt(), (point.y * density).toInt()]
    }

    /** The frost [thumbnail] bakes to, as it lies behind [point] on the screen; with [glass] as the dock's glass holds it. */
    fun frostAt(
        thumbnail: ImageBitmap,
        point: Offset,
        glass: Boolean = false,
    ): Color {
        val baked = bakeFrost(thumbnail, null, 0f)
        if (glass) {
            val pixels = IntArray(baked.width * baked.height).also { baked.readPixels(it) }
            clampSaturation(pixels, 0.35f)
            baked.asAndroidBitmap().setPixels(pixels, 0, baked.width, 0, 0, baked.width, baked.height)
        }
        val frost = baked.toPixelMap()
        val screen = compose.onRoot().getBoundsInRoot()
        val x = (point.x / screen.right.value * frost.width - 0.5f).coerceIn(0f, frost.width - 1f)
        val y = (point.y / screen.bottom.value * frost.height - 0.5f).coerceIn(0f, frost.height - 1f)
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = minOf(x0 + 1, frost.width - 1)
        val y1 = minOf(y0 + 1, frost.height - 1)

        fun row(
            ry: Int,
            channel: (Color) -> Float,
        ) = channel(frost[x0, ry]) * (1 - (x - x0)) + channel(frost[x1, ry]) * (x - x0)

        fun mix(channel: (Color) -> Float) = row(y0, channel) * (1 - (y - y0)) + row(y1, channel) * (y - y0)
        return Color(mix { it.red }, mix { it.green }, mix { it.blue })
    }

    fun over(
        tint: Color,
        alpha: Float,
        under: Color,
    ) = Color(
        under.red * (1 - alpha) + tint.red * alpha,
        under.green * (1 - alpha) + tint.green * alpha,
        under.blue * (1 - alpha) + tint.blue * alpha,
    )

    fun assertSame(
        expected: Color,
        actual: Color,
        tolerance: Int = 6,
    ) {
        val gap = listOf(expected.red - actual.red, expected.green - actual.green, expected.blue - actual.blue).maxOf { abs(it) * 255 }
        withClue("expected $expected, got $actual") { gap shouldBeLessThanOrEqualTo tolerance.toFloat() }
    }

    val dark = Color(0xFF0B0D12)

    val light = Color(0xFFF4F6FA)

    /** The red channel of the screen [inPx] pixels inside dock tile [n]'s left edge, half way down it. */
    fun tileEdgeAt(
        n: Int,
        inPx: Int,
    ): Float {
        val tile = dockTile(n)
        val density = compose.activity.resources.displayMetrics.density
        val x = ceil(tile.left.value * density).toInt() + inPx
        return compose
            .onRoot()
            .captureToImage()
            .toPixelMap()[x, ((tile.top.value + tile.bottom.value) / 2f * density).toInt()]
            .red
    }

    val bannerApps =
        (1..9).map {
            AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = bitmap(16, 9) { _, _ -> grey(245) })
        }

    /**
     * The mean relative luminance of the panel from [fromDp] to [toDp] below its top (the default: less 3 dp inside its
     * edges), less 3 dp inside its sides and 10 dp around each tile, which clears their rings.
     */
    fun trayLuminance(
        fromDp: Float = 3f,
        toDp: Float? = null,
    ): Float {
        val panel = compose.onNodeWithTag("dock").getBoundsInRoot()
        val tiles = (1..6).map { dockTile(it) }
        val density = compose.activity.resources.displayMetrics.density
        val image = compose.onRoot().captureToImage().toPixelMap()
        var sum = 0.0
        var count = 0
        for (x in ((panel.left.value + 3f) * density).toInt() until ((panel.right.value - 3f) * density).toInt()) {
            for (y in ((panel.top.value + fromDp) * density).toInt() until
                ((toDp?.let { panel.top.value + it } ?: (panel.bottom.value - 3f)) * density).toInt()) {
                val near =
                    tiles.any {
                        x >= (it.left.value - 10f) * density && x <= (it.right.value + 10f) * density &&
                            y >= (it.top.value - 10f) * density && y <= (it.bottom.value + 10f) * density
                    }
                if (near) continue
                sum += image[x, y].luminance()
                count++
            }
        }
        return (sum / count).toFloat()
    }

    fun tileLuminance(tag: String): Float {
        val tile = compose.onNodeWithTag(tag).getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        return compose
            .onRoot()
            .captureToImage()
            .toPixelMap()[
            ((tile.left.value + tile.right.value) / 2f * density).toInt(), (
                (tile.top.value + tile.bottom.value) / 2f *
                    density
            ).toInt(),
        ].luminance()
    }

    /** The red channel of the screen [px] pixels under the panel's top (or over its bottom) in the gap between the third and fourth tiles. */
    fun panelEdgeAt(
        px: Int,
        fromBottom: Boolean = false,
    ): Float {
        val panel = compose.onNodeWithTag("dock").getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val y = if (fromBottom) (panel.bottom.value * density).toInt() - 1 - px else (panel.top.value * density).toInt() + px
        return compose
            .onRoot()
            .captureToImage()
            .toPixelMap()[(gap().x * density).toInt(), y]
            .red
    }

    /** The red channel of the screen [outside] pixels over the panel's top edge in the middle of the panel, over a white wallpaper. */
    fun redOutsideTop(outside: Int): Float {
        val panel = compose.onNodeWithTag("dock").getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        return compose
            .onRoot()
            .captureToImage()
            .toPixelMap()[((panel.left.value + panel.right.value) / 2f * density).toInt(), (panel.top.value * density).toInt() - outside]
            .red
    }

    fun saturation(colour: Color): Float {
        val high = maxOf(colour.red, colour.green, colour.blue)
        val low = minOf(colour.red, colour.green, colour.blue)
        return (high - low) / (1f - abs(high + low - 1f))
    }

    /** The green channel over the left edge of tile [tag] (two pixels, the 1 dp edge) and 3 px inside it, at half its height. */
    fun edgeOf(tag: String): Pair<List<Float>, Float> {
        val tile = compose.onNodeWithTag(tag).getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val image = compose.onRoot().captureToImage().toPixelMap()
        val y = ((tile.top.value + tile.bottom.value) / 2f * density).toInt()
        val left = (tile.left.value * density).toInt()
        return (left..left + 1).map { image[it, y].green } to image[left + 3, y].green
    }

    fun assertEdge(
        tag: String,
        level: Float,
    ) {
        val (edge, inside) = edgeOf(tag)
        withClue("edge $edge") { edge.max() shouldBe (level * (1f - 0.75f) plusOrMinus 0.03f) }
        withClue("edge $edge") { edge.min() shouldBe (level * (1f - 0.75f) plusOrMinus 0.03f) }
        inside shouldBe (level plusOrMinus 0.01f)
    }

    fun assertNoEdge(
        tag: String,
        level: Float,
    ) {
        val (edge, inside) = edgeOf(tag)
        withClue("edge $edge") { edge.min() shouldBe (level plusOrMinus 0.02f) }
        withClue("edge $edge") { edge.max() shouldBe (level plusOrMinus 0.02f) }
        inside shouldBe (level plusOrMinus 0.02f)
    }

    val level245 = 245f / 255f

    val whiteApps = (1..9).map { lightApps[it - 1].copy(image = bitmap(16, 9) { _, _ -> grey(255) }) }

    /** The relative luminance of the lighter of the two pixels of the 1 dp edge of tile [tag], of its art 3 px inside, and of the tray 4 dp outside it. */
    fun edgeAndNeighbours(tag: String): Triple<Float, Float, Float> {
        val tile = compose.onNodeWithTag(tag).getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val image = compose.onRoot().captureToImage().toPixelMap()
        val y = ((tile.top.value + tile.bottom.value) / 2f * density).toInt()
        val left = (tile.left.value * density).toInt()
        val edge = maxOf(image[left, y].luminance(), image[left + 1, y].luminance())
        return Triple(edge, image[left + 3, y].luminance(), image[left - (4 * density).toInt(), y].luminance())
    }

    val brightWallpaper get() = flowOf(Wallpaper(picture = white, thumbnail = white))

    /** The 1 dp edge of the white tile [tag] holds 3 to 1 against the surface 4 dp outside it. */
    fun assertEdgeHolds(tag: String) {
        val (edge, _, outside) = edgeAndNeighbours(tag)
        val contrast = (outside + 0.05f) / (edge + 0.05f)
        withClue("the edge is $edge, the surface $outside: $contrast") { contrast shouldBeGreaterThanOrEqualTo 3f }
    }

    /** A picture whose light glass tray reads at a relative luminance of about 0.29, where a white tile alone holds under 3 to 1. */
    val midTone = bitmap(40, 40) { _, _ -> grey(127) }

    /** White on one half and black on the other: a mean L* near 50 over the screen, with a bright patch next to a tile on the white half. */
    fun halved(whiteLeft: Boolean): ImageBitmap {
        val wallpaper = bitmap(40, 40) { x, _ -> grey(if ((x < 20) == whiteLeft) 255 else 0) }
        val baked = bakeFrost(wallpaper, null, 0f)
        val pixels = IntArray(baked.width * baked.height).also { baked.readPixels(it) }
        val mean = meanLightness(pixels, baked.width, baked.height, Rect(0f, 0f, 1f, 1f))
        withClue("mean L* $mean") { mean shouldBeLessThan 70f }
        return wallpaper
    }

    fun screenCentre() = compose.onRoot().getBoundsInRoot().let { (it.left.value + it.right.value) / 2f }

    /** The tint over [gradient]'s frost: a wallpaper this bright puts the guard to work. */
    fun gradientTint() = dark.copy(alpha = DockLook().tint(darkSurfaces = true, lightness = glass.dockLightness).alpha)

    /**
     * How far, in dp, the outer edge of the focus ring of dock slot [slot] (from 1) stands from the dock panel's wall on
     * [side] (-1 left, +1 right), read from the rendered pixels: the ring is the white run, with a 1 dp hairline and a 2 dp
     * dark stroke outside it.
     */
    fun ringGapToWall(
        slot: Int,
        side: Int,
    ): Float {
        val panel = compose.onNodeWithTag("dock").getBoundsInRoot()
        val tile = dockTile(slot)
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val row = ((tile.top.value + tile.bottom.value) / 2f * density).toInt()

        fun white(x: Int) = minOf(pixels[x, row].red, pixels[x, row].green, pixels[x, row].blue) > 0.9f
        val strokes = 3f
        return if (side < 0) {
            val wall = (panel.left.value * density).toInt()
            val ring = (wall..(tile.left.value * density).toInt()).first { white(it) }
            ring / density - strokes - panel.left.value
        } else {
            val wall = (panel.right.value * density).toInt() - 1
            val ring = wall.downTo((tile.right.value * density).toInt()).first { white(it) }
            panel.right.value - ((ring + 1) / density + strokes)
        }
    }

    /**
     * How far, in dp, the outer edge of the focus ring of dock slot [slot] (from 1) stands from the edge of its neighbour on
     * [side] (-1 left, +1 right), read from the rendered pixels as [ringGapToWall] does. A ring the neighbour covers reads as
     * the 3 dp of its strokes under zero.
     */
    fun ringGapToNeighbour(
        slot: Int,
        side: Int,
    ): Float {
        val tile = dockTile(slot)
        val neighbour = dockTile(slot + side)
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val row = ((tile.top.value + tile.bottom.value) / 2f * density).toInt()

        fun white(x: Int) = minOf(pixels[x, row].red, pixels[x, row].green, pixels[x, row].blue) > 0.9f
        val strokes = 3f
        return if (side > 0) {
            val ring = ((tile.right.value * density).toInt()..(neighbour.left.value * density).toInt()).first { white(it) }
            val end = generateSequence(ring) { it + 1 }.first { !white(it) }
            neighbour.left.value - (end / density + strokes)
        } else {
            val ring = ((tile.left.value * density).toInt()).downTo((neighbour.right.value * density).toInt()).first { white(it) }
            val end = generateSequence(ring) { it - 1 }.first { !white(it) }
            (end + 1) / density - strokes - neighbour.right.value
        }
    }

    fun assertEndTilesLeaveTheirNeighbourTheRoomAMiddleTileLeaves(columns: Int) {
        showHome(night, cinematic.copy(columns = columns))
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.2").assertIsFocused()
        val middle = ringGapToNeighbour(slot = 2, side = +1)
        press(Key.DirectionLeft)
        val first = ringGapToNeighbour(slot = 1, side = +1)
        repeat(columns - 1) { press(Key.DirectionRight) }
        compose.onNodeWithTag("tile:pkg.$columns").assertIsFocused()
        val last = ringGapToNeighbour(slot = columns, side = -1)

        withClue("a middle tile left $middle dp, the first $first dp, the last $last dp") { middle shouldBeGreaterThanOrEqualTo 4f }
        withClue("the first tile's gap to the second, $columns columns") { first shouldBe (middle plusOrMinus 0.5f) }
        withClue("the last tile's gap to the one before it, $columns columns") { last shouldBe (middle plusOrMinus 0.5f) }
    }
}
