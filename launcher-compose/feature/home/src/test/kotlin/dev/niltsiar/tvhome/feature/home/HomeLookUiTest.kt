package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.height
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.SkyPalette
import dev.niltsiar.tvhome.core.model.Wallpaper
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs
import kotlin.math.hypot

private const val WHITE = 0xFFFFFFFF.toInt()

/** How Home draws over its wallpaper, read from the rendered pixels: the scrim, the grid's edges and the dimmed tiles. */
val HomeLookUiTest by testSuite {
    robolectricTestSuite<HomeLookUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
    robolectricTestSuite<HomeLookUiH544TestContent>(
        "sdk 31, 960x544 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h544dp-xhdpi"
            },
    )
    robolectricTestSuite<HomeLookUiH580TestContent>(
        "sdk 31, 960x580 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h580dp-xhdpi"
            },
    )
    robolectricTestSuite<HomeLookUiNightTestContent>(
        "sdk 31, night, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-night-xhdpi"
            },
    )
}

class HomeLookUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeLookUiTestFixture() } asContextForEach {
            test("the scrim darkens the top left of a white wallpaper and leaves its bottom right alone") {
                showHome(apps = whiteApps(1), wallpaper = Wallpaper(picture = white))
                val screen = screen()

                // Black 0.55 from the top over black 0.40 from the left: 255 x 0.45 x 0.60.
                assertNear(69, redAt(Offset(1f, 1f)), 4)
                assertNear(255, redAt(Offset(screen.right.value - 2f, screen.bottom.value - 2f)), 3)
            }

            test("at rest the top fade leaves the dock panel whole") {
                showHome(wallpaper = Wallpaper(picture = white))
                val dock = bounds("dock")
                val tile = bounds("tile:pkg.6")
                val x = (tile.left.value + tile.right.value) / 2

                val rim = redAt(Offset(x, dock.top.value + 3f))
                val body = redAt(Offset(x, (dock.top.value + tile.top.value) / 2 + 4f))
                assertNear(body, rim, 10)
            }

            test("rows above the focused row draw at alpha 0") {
                showHome(apps = whiteAppsAndOneClear())
                assertNear(255, redAt(centreOf(dockTile(6))), 2)

                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                assertDrawnAt(0f, centreOf(dockTile(6)), centreOf(dockTile(5)))
            }

            test("the row above the last grid row draws at 85 percent with the library pill focused when it clears the title block") {
                showHome(apps = whiteApps(30), config = SixColumns.copy(appLibrary = true))
                repeat(5) { press(Key.DirectionDown) }
                compose.onNodeWithTag("library:pill").assertIsFocused()

                // The layer multiplies each draw: the white art, and the tile's own fill under it.
                val fill = 0x1B * 0.85f
                assertNear(0, redAt(centreOf(bounds("tile:pkg.13"))), 2)
                assertNear(Math.round(0.85f * 255 + 0.15f * fill), redAt(centreOf(bounds("tile:pkg.19"))), 4)
                assertNear(Math.round(0.85f * 255 + 0.15f * fill), redAt(centreOf(bounds("tile:pkg.25"))), 4)
            }

            test("with focus on the third row the second shows whole below the title block and the first is gone") {
                showHome(apps = whiteApps(24), config = SixColumns.copy(appLibrary = true))
                repeat(3) { press(Key.DirectionDown) }
                compose.onNodeWithTag("tile:pkg.19").assertIsFocused()

                assertNear(255, redUnderTop("tile:pkg.13", 10f), 2)
                assertNear(0, redUnderTop("tile:pkg.7", 10f), 2)
                withClue("the second row starts at ${bounds("tile:pkg.13").top}") {
                    bounds("tile:pkg.13").top.value shouldBeGreaterThanOrEqualTo
                        240f
                }
                withClue("the pill ends at ${bounds("library:pill").bottom}") {
                    bounds("library:pill").bottom.value shouldBeLessThanOrEqualTo
                        screen().bottom.value - 12f
                }
            }

            test("with focus in the grid the title stands 36 dp under the top bar") {
                showHome()
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                withClue("the title is ${titleGap()} dp under the top bar") { titleGap() shouldBeGreaterThanOrEqualTo 36f }
            }

            test("in a single row the title stands 36 dp under the top bar") {
                showHome(config = SixColumns.copy(singleRow = true))

                withClue("the title is ${titleGap()} dp under the top bar") { titleGap() shouldBeGreaterThanOrEqualTo 36f }
            }

            test("a grid row with 70 dp above the edge draws nothing when the bottom fade leaves 20 dp of its tile") {
                showHome(apps = whiteApps(30), config = SixColumns.copy(columns = 5))
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.6").assertIsFocused()

                // The third row starts 24 dp under the last whole row, whose bottom is 8 dp short of the fade's start, and the fade runs
                // 36 dp: the row has 20 dp of its tile before it is gone.
                withClue("the row shows ${visibleDp("tile:pkg.16")} dp") { visibleDp("tile:pkg.16") shouldBeIn 60f..84f }
                assertNear(0, redUnderTop("tile:pkg.16", 2f), 2)
                assertNear(0, redUnderTop("tile:pkg.16", 10f), 2)
            }

            test("a row that scrolls in from the bottom edge draws again once it is whole") {
                showHome(apps = whiteApps(30), config = SixColumns.copy(columns = 4))
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.5").assertIsFocused()
                withClue("row 3 shows ${visibleDp("tile:pkg.13")} dp") { visibleDp("tile:pkg.13") shouldBeLessThan 48f }
                assertNear(0, redUnderTop("tile:pkg.13", 2f), 2)

                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.9").assertIsFocused()

                withClue("the row shows ${visibleDp("tile:pkg.13")} dp") {
                    visibleDp("tile:pkg.13") shouldBeGreaterThanOrEqualTo
                        bounds("tile:pkg.1").height.value
                }
                assertNear(255, redAboveBottom("tile:pkg.13", 1f), 2)
            }

            test("in the grid layout the bottom fade leaves the last fully visible row whole and has the next row gone by y 1060 of 1080") {
                showHome(apps = whiteApps(30))
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                val screen = screen().bottom.value
                withClue(
                    "row 2 ends at ${bounds("tile:pkg.20").bottom}",
                ) { bounds("tile:pkg.20").bottom.value shouldBeLessThan screen - 12f }
                withClue("row 3 starts at ${bounds("tile:pkg.26").top}") { bounds("tile:pkg.26").top.value shouldBeLessThan screen }

                assertNear(255, redAboveBottom("tile:pkg.20", 1f), 2)
                val limit = screen * 1060f / 1080f
                val tile = bounds("tile:pkg.26")
                withClue("under the limit") { redAt(Offset((tile.left.value + tile.right.value) / 2, limit + 1f)) shouldBeLessThan 4 }
            }

            test("at the end of the grid the last row stays whole") {
                showHome(apps = whiteApps(30))
                repeat(4) { press(Key.DirectionDown) }
                compose.onNodeWithTag("tile:pkg.25").assertIsFocused()

                assertNear(255, redAboveBottom("tile:pkg.26", 1f), 2)
            }

            test("no row above the focused one leaves a band under the shelf") {
                showHome(apps = whiteApps(30))
                repeat(2) { press(Key.DirectionDown) }
                compose.onNodeWithTag("tile:pkg.13").assertIsFocused()
                val above = bounds("tile:pkg.8")
                val x = (above.left.value + above.right.value) / 2

                assertNear(0, redAt(Offset(x, above.bottom.value - 1f)), 3)
                assertNear(0, redAt(Offset(x, above.bottom.value - 30f)), 3)
            }

            test("a tile of another launcher says its name at the bottom left of its banner and an ordinary tile says nothing") {
                showHome(apps = whiteApps(9).map { if (it.key == "pkg.2") it.copy(label = "Inicio", homeLauncher = true) else it })

                val tile = bounds("tile:pkg.2")
                val name = compose.onNodeWithTag("name:pkg.2", useUnmergedTree = true).getBoundsInRoot()
                withClue("the name is at $name in $tile") { name.left.value - tile.left.value shouldBeIn 0f..16f }
                withClue("the name is at $name in $tile") { tile.bottom.value - name.bottom.value shouldBeIn 0f..16f }
                compose.onAllNodesWithTag("name:pkg.3", useUnmergedTree = true).assertCountEquals(0)
            }

            test("the scrim darkens the top right corner over the sky too") {
                val whiteSky = SkyPalette(top = WHITE, mid = WHITE, bottom = WHITE, glow = WHITE, sun = 0, sunX = 0.5f)
                showHome(apps = whiteApps(1), wallpaper = Wallpaper(picture = white, sky = whiteSky), skyIntensity = 50)

                // Black 0.55 at the top, where the left scrim has run out: 255 x 0.45.
                assertNear(115, redAt(Offset(screen().right.value - 2f, 1f)), 4)
            }

            test("the frost bakes the scrim over the sky too") {
                val whiteSky = SkyPalette(top = WHITE, mid = WHITE, bottom = WHITE, glow = WHITE, sun = 0, sunX = 0.5f)
                val thumbnail =
                    ImageBitmap(
                        40,
                        40,
                    ).also { Canvas(it).drawRect(Rect(0f, 0f, 40f, 40f), Paint().apply { color = Color.White }) }

                val frost = bakeFrost(thumbnail, whiteSky, skyAlpha = 0.5f).toPixelMap()

                // About half the 0.55 top scrim survives the blur at the top right; the sky over it would lift it past 180.
                Math.round(frost[39, 0].red * 255) shouldBeLessThan 150
            }

            test("in reorder mode the tiles not carried draw at sixty five percent") {
                showHome(apps = whiteAppsAndOneClear())
                val white = centreOf(dockTile(6))
                val clear = centreOf(dockTile(5))

                carryFirstTile()

                assertDrawnAt(0.65f, white, clear)
            }

            test("in reorder mode with nothing carried no tile is dimmed") {
                showHome(apps = whiteAppsAndOneClear())
                val white = centreOf(dockTile(6))
                val clear = centreOf(dockTile(5))

                carryFirstTile()
                back()

                assertDrawnAt(1f, white, clear)
            }

            test("a carried tile lifts 7 dp and is drawn at its rest size not grown like a focused tile") {
                showHome(apps = whiteApps(9))
                val neighbour = dockTile(2)
                val middle = (neighbour.top.value + neighbour.bottom.value) / 2
                val at = bounds("tile:pkg.1")
                val x = (at.left.value + at.right.value) / 2
                val (restTop, restBottom) = artSpan(x, middle)

                carryFirstTile()
                val (top, bottom) = artSpan(x, middle)

                // A focused tile is drawn 1.08 times its size.
                assertNear((restBottom - restTop) / 1.08f, bottom - top, 1f)
                assertNear((restTop + restBottom) / 2 - 7f, (top + bottom) / 2, 1f)
            }

            test("with names a carried tile is drawn where it is without them lifted 7 dp at its rest size") {
                showHome(apps = whiteApps(9), config = SixColumns.copy(alwaysShowNames = true))
                val rest = bounds("tile:pkg.1")
                val x = (rest.left.value + rest.right.value) / 2
                val middle = (rest.top.value + rest.bottom.value) / 2

                carryFirstTile()
                val (top, bottom) = artSpan(x, middle - 7f)

                assertNear(rest.top.value - 7f, top, 0.5f)
                assertNear(rest.bottom.value - 7f, bottom, 0.5f)
            }

            test("an arrow stands on a disc 22 dp across of black at 60 percent") {
                val pixels = arrowOn(Color.White)

                // On a white ground the disc reads 0.4; the row through the centre also crosses the chevron's tip, which is lighter.
                val columns = (0 until 100).filter { pixels[it, 50].red < 0.9f }
                val across = (columns.max() - columns.min() + 1) / 2f
                withClue("the disc's width in dp") { across shouldBe (22f plusOrMinus 0.6f) }
                withClue("the disc's fill, left of the chevron") { pixels[36, 50].red shouldBe (0.4f plusOrMinus 0.03f) }
                withClue("the disc's fill, above the chevron") { pixels[50, 34].red shouldBe (0.4f plusOrMinus 0.03f) }
            }

            test("an arrows chevron is a 12 dp tall 6 dp deep white stroke with round caps and no outline") {
                val pixels = arrowOn(Color.White)

                // At 2 px to a dp the chevron, centred 11 px right of the disc's centre, has its ends at (55, 38) and (55, 62) and its tip
                // at (67, 50); the round caps reach 2.5 px past them. White at 0.95 over the disc's 0.4 reads 0.97.
                for ((stroke, clear) in listOf(36 to 34, 63 to 65)) {
                    withClue("the stroke at row $stroke") { pixels[55, stroke].red shouldBe (0.97f plusOrMinus 0.03f) }
                    withClue("the disc's fill at row $clear") { pixels[55, clear].red shouldBe (0.4f plusOrMinus 0.03f) }
                }
                withClue("the tip") { pixels[67, 50].red shouldBe (0.97f plusOrMinus 0.03f) }
                withClue("past the tip") { pixels[70, 50].red shouldBe (0.4f plusOrMinus 0.03f) }
            }

            test("an arrows chevron points the way it names") {
                val pixels = arrowOn(Color.White, pointsLeft = true)

                withClue("the tip, left of the centre") { pixels[33, 50].red shouldBe (0.97f plusOrMinus 0.03f) }
                withClue("past the tip") { pixels[29, 50].red shouldBe (0.4f plusOrMinus 0.03f) }
                withClue("the open end") { pixels[45, 50].red shouldBe (0.4f plusOrMinus 0.03f) }
            }

            test("an arrows white stroke holds 3 to 1 against its disc on a near black tile and on a white one") {
                fun contrast(
                    first: Color,
                    second: Color,
                ) = (maxOf(first.luminance(), second.luminance()) + 0.05f) / (minOf(first.luminance(), second.luminance()) + 0.05f)

                val nearBlack = Color(0xFF0F1115)
                val white = Color(0xFFF5F5F5)

                val onDark = arrowOn(nearBlack).let { contrast(it[55, 36], it[36, 50]) }
                val onLight = arrowOn(white).let { contrast(it[55, 36], it[36, 50]) }
                withClue("the stroke on near black: $onDark, on white: $onLight") { (onDark >= 3f && onLight >= 3f).shouldBeTrue() }
            }

            test("with nothing carried the legend is ok pick up and back finish and nothing else") {
                showHome(wallpaper = Wallpaper(picture = white))
                carryFirstTile()
                back()

                assertChip("ok", "OK", "pick up")
                assertChip("back", "Back", "finish")
                compose.onNodeWithTag("reorder:chip:arrows").assertDoesNotExist()
            }

            test("with a tile carried the legend is arrows move and one chip for the two keys that drop it and nothing else") {
                showHome(wallpaper = Wallpaper(picture = white))
                carryFirstTile()

                assertChip("arrows", "Arrows", "move")
                assertChip("drop", "OK", "drop")
                compose.onNode(hasText("Back") and hasAnyAncestor(hasTestTag("reorder:chip:drop"))).assertExists()
                compose.onAllNodes(hasText("drop")).assertCountEquals(1)
                compose.onNodeWithTag("reorder:chip:ok").assertDoesNotExist()
                compose.onNodeWithTag("reorder:chip:back").assertDoesNotExist()
                compose.onAllNodes(hasText("pick up")).assertCountEquals(0)
                compose.onAllNodes(hasText("finish")).assertCountEquals(0)
            }
        }
    })

class HomeLookUiH544TestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeLookUiTestFixture() } asContextForEach {
            test("a grid row with under 48 dp visible draws at alpha 0") {
                showHome(apps = whiteApps(30), config = SixColumns.copy(columns = 5))

                withClue("the row shows ${visibleDp("tile:pkg.6")} dp") { visibleDp("tile:pkg.6") shouldBeIn 40f..48f }
                assertNear(0, redUnderTop("tile:pkg.6", 2f), 2)
                assertNear(0, redUnderTop("tile:pkg.6", 10f), 2)
            }
        }
    })

class HomeLookUiH580TestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeLookUiTestFixture() } asContextForEach {
            test("a grid row with 48 dp above the edge but less left after the bottom fade draws nothing") {
                showHome(apps = whiteApps(30), config = SixColumns.copy(columns = 4))

                withClue("the row shows ${visibleDp("tile:pkg.5")} dp") { visibleDp("tile:pkg.5") shouldBeIn 48f..56f }
                assertNear(0, redUnderTop("tile:pkg.5", 2f), 2)
            }
        }
    })

class HomeLookUiNightTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeLookUiTestFixture() } asContextForEach {
            test("the carried tile shows a chevron in the gutter only on the sides it can move to") {
                showHome(apps = clearApps(3))
                val rest = restTiles(3)

                carryFirstTile()
                withClue("first slot, right") { chevronBeside(rest, 1, +1).shouldBeTrue() }
                withClue("first slot, left") { chevronBeside(rest, 1, -1).shouldBeFalse() }

                press(Key.DirectionRight)
                withClue("middle slot, right") { chevronBeside(rest, 2, +1).shouldBeTrue() }
                withClue("middle slot, left") { chevronBeside(rest, 2, -1).shouldBeTrue() }

                press(Key.DirectionRight)
                withClue("last slot, right") { chevronBeside(rest, 3, +1).shouldBeFalse() }
                withClue("last slot, left") { chevronBeside(rest, 3, -1).shouldBeTrue() }
            }

            test("an arrows white stroke covers none of the carried tiles art and none of the tiles beside it") {
                showHome(apps = clearApps(3))
                val rest = restTiles(3)
                carryFirstTile()
                press(Key.DirectionRight)
                withClue("both chevrons show") { (chevronBeside(rest, 2, -1) && chevronBeside(rest, 2, +1)).shouldBeTrue() }

                val carried = rest[1]
                withClue(
                    "the art",
                ) { whiteIn(carried.left.value, carried.top.value, carried.right.value, carried.bottom.value).shouldBeFalse() }
                for (neighbour in listOf(rest[0], rest[2])) {
                    withClue("the tile beside it") {
                        whiteIn(neighbour.left.value, neighbour.top.value, neighbour.right.value, neighbour.bottom.value).shouldBeFalse()
                    }
                }
            }

            test("the carried tiles ring is drawn over its arrow discs") {
                // Without the wiggle the tile stands upright, so the ring and the chevron sit where the tile's bounds put them.
                showHome(apps = clearApps(3), motion = Motion(reducedMotion = true))
                val rest = restTiles(3)
                carryFirstTile()
                press(Key.DirectionRight)
                val at = rest[1]
                val middle = (at.top.value + at.bottom.value) / 2f - 7f
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                val density = compose.activity.resources.displayMetrics.density

                // The orange band runs 3 to 6 dp past the art, inside a disc centred 9 dp past it; the chevron ends 7.25 dp above
                // and below that centre.
                for (x in listOf(at.left.value - 4.5f, at.right.value + 4.5f)) {
                    for (y in listOf(middle - 8.5f, middle + 8.5f)) {
                        val pixel = pixels[(x * density).toInt(), (y * density).toInt()]
                        withClue("the ring at ($x, $y): $pixel") { isRingOrange(pixel).shouldBeTrue() }
                    }
                }
            }

            test("a carried tiles chevrons keep off its ring and inside their discs") {
                // Without the wiggle the tile stands upright, so the ring's outer edge and the discs sit where the tile's bounds put them.
                showHome(apps = clearApps(3), motion = Motion(reducedMotion = true))
                val rest = restTiles(3)
                carryFirstTile()
                press(Key.DirectionRight)
                val at = rest[1]
                val middle = (at.top.value + at.bottom.value) / 2f - 7f
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                val density = compose.activity.resources.displayMetrics.density
                // Along the discs' centre line, through the chevrons' height: the only white there is the chevrons' stroke.
                for ((ringEdge, outward) in listOf(at.right.value + ringReach to 1f, at.left.value - ringReach to -1f)) {
                    val columns = ((ringEdge - arrowDisc) * density).toInt()..((ringEdge + arrowDisc) * density).toInt()
                    val rows = ((middle - arrowDisc / 2f) * density).toInt()..((middle + arrowDisc / 2f) * density).toInt()
                    var white = 0
                    for (x in columns) {
                        for (y in rows) {
                            if (pixels[x, y].green <= 0.8f) continue
                            white++
                            val out = ((x + 0.5f) / density - ringEdge) * outward
                            val fromCentre = hypot(out, (y + 0.5f) / density - middle)
                            withClue("chevron white $out dp out from the ring's outer edge, at row ${y / density}") {
                                out shouldBeGreaterThanOrEqualTo
                                    0f
                            }
                            withClue("chevron white $fromCentre dp from the disc's centre") {
                                fromCentre shouldBeLessThanOrEqualTo
                                    arrowDisc / 2f
                            }
                        }
                    }
                    withClue("a chevron beside the ring at $ringEdge") { white shouldBeGreaterThan 0 }
                }
            }

            test("with four dock tiles a carried tiles arrows clear the next tile by 4 dp and its ring stays 8 dp under the rim") {
                assertCarriedClearances(tiles = 4, slot = 2)
            }

            test("with five dock tiles a carried tiles arrows clear the next tile by 4 dp and its ring stays 8 dp under the rim") {
                assertCarriedClearances(tiles = 5, slot = 2)
            }

            test("with six dock tiles a carried tiles arrows clear the next tile by 4 dp and its ring stays 8 dp under the rim") {
                assertCarriedClearances(tiles = 6, slot = 2)
            }

            test("the arrows keep their clearances with the carried tile in the middle of a full dock") {
                assertCarriedClearances(tiles = 6, slot = 4)
            }

            test("with four columns a carried tiles arrows keep their clearances") {
                assertCarriedClearances(tiles = 4, slot = 2, config = TvConfig(columns = 4))
            }

            test("the right arrow of the last dock tile keeps 4 dp from the dock wall in a six column dock") {
                val gap = lastTileDiscToWall(SixColumns)

                withClue("the disc stood $gap dp from the wall") { gap shouldBeGreaterThanOrEqualTo 4f }
            }

            test("the right arrow of the last dock tile keeps 4 dp from the dock wall in a four column dock") {
                val gap = lastTileDiscToWall(TvConfig(columns = 4))

                withClue("the disc stood $gap dp from the wall") { gap shouldBeGreaterThanOrEqualTo 4f }
            }

            test("with a tile carried no other tile moves so every gap stays at its rest value") {
                showHome(apps = whiteApps(6))
                val rest = restTiles(6)
                val row = rest[1].top.value + 6f
                val artStarts = rest.map { artStart((it.left.value + it.right.value) / 2f, row) }

                carryFirstTile()
                for (slot in 1..6) {
                    if (slot > 1) press(Key.DirectionRight)
                    for (other in (1..6).filter { it != slot }) {
                        val at = rest[other - 1]
                        withClue("the tile in slot $other with the carried tile in slot $slot") {
                            artStart((at.left.value + at.right.value) / 2f, row) shouldBe
                                (artStarts[other - 1] plusOrMinus 0.6f)
                        }
                    }
                }
            }
        }
    })

private class HomeLookUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }

    fun whiteApps(count: Int) = (1..count).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = white) }

    /** Nine white tiles, except pkg.5, whose clear art shows what each tile draws under its art. */
    fun whiteAppsAndOneClear() = whiteApps(9).map { if (it.key == "pkg.5") it.copy(image = ImageBitmap(16, 9)) else it }

    fun showHome(
        apps: List<AppEntry> = whiteApps(9),
        wallpaper: Wallpaper = Wallpaper(),
        config: TvConfig = SixColumns,
        skyIntensity: Int = 0,
        motion: Motion = Motion(),
    ) {
        val sources = sources(apps = flowOf(AppList(apps)), wallpaper = flowOf(wallpaper))
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
                    wallpaperLayout = WallpaperLayout(skyIntensity = skyIntensity),
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

    fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    fun screen(): DpRect = compose.onRoot().getBoundsInRoot()

    /** The red channel, 0 to 255, of the screen at [point] (in dp). */
    fun redAt(point: Offset): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(pixels[(point.x * density).toInt(), (point.y * density).toInt()].red * 255)
    }

    fun centreOf(rect: DpRect) = Offset((rect.left.value + rect.right.value) / 2, (rect.top.value + rect.bottom.value) / 2)

    fun dockTile(n: Int): DpRect = compose.onNode(hasTestTag("tile:pkg.$n") and hasAnyAncestor(hasTestTag("dock"))).getBoundsInRoot()

    /** A layer at [alpha] multiplies each of its draws, so white art reads [alpha] x 255 over what the tile drew under it. */
    fun assertDrawnAt(
        alpha: Float,
        white: Offset,
        clear: Offset,
    ) {
        val under = redAt(clear)
        assertNear(Math.round(alpha * 255 + (1 - alpha) * under), redAt(white), 4)
    }

    fun assertNear(
        expected: Float,
        actual: Float,
        tolerance: Float,
    ) = withClue("expected $expected, got $actual") { abs(actual - expected) shouldBeLessThanOrEqualTo tolerance }

    fun assertNear(
        expected: Int,
        actual: Int,
        tolerance: Int,
    ) = withClue("expected $expected, got $actual") { abs(actual - expected) shouldBeLessThanOrEqualTo tolerance }

    /** How far under the top bar the title's capitals start: its line box top, 36 sp to the baseline, less the capitals' 0.72 of 48 sp. */
    fun titleGap() = bounds("shelf:title").top.value + 36f - 0.72f * 48f - (TopBarTop + TopBarHeight).value

    /** The red channel over the middle of tile [tag], [below] dp under its top edge. */
    fun redUnderTop(
        tag: String,
        below: Float,
    ): Int {
        val tile = bounds(tag)
        return redAt(Offset((tile.left.value + tile.right.value) / 2, tile.top.value + below))
    }

    /** The red channel over the middle of tile [tag], [above] dp over its bottom edge. */
    fun redAboveBottom(
        tag: String,
        above: Float,
    ): Int {
        val tile = bounds(tag)
        return redAt(Offset((tile.left.value + tile.right.value) / 2, tile.bottom.value - above))
    }

    /** How many dp of tile [tag] show above the screen's bottom edge. */
    fun visibleDp(tag: String): Float = screen().bottom.value - bounds(tag).top.value

    fun carryFirstTile() {
        press(Key.Menu)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
    }

    /** The top and bottom, in dp, of the run of white art that crosses [y] in the column at [x]. */
    fun artSpan(
        x: Float,
        y: Float,
    ): Pair<Float, Float> {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density

        fun white(row: Int) = pixels[(x * density).toInt(), row].green > 0.8f
        val centre = (y * density).toInt()
        var top = centre
        while (white(top - 1)) top--
        var bottom = centre
        while (white(bottom + 1)) bottom++
        return top / density to (bottom + 1) / density
    }

    /** The ring reaches 9 dp past the art of a tile at its rest size. */
    val ringReach = 9f

    /** Whether the screen holds a white pixel in the box from ([left], [top]) to ([right], [bottom]), in dp. */
    fun whiteIn(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Boolean {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return (((left * density).toInt()) until (right * density).toInt()).any { x ->
            (((top * density).toInt()) until (bottom * density).toInt()).any { y -> pixels[x, y].green > 0.8f }
        }
    }

    /** Apps whose art is clear, so that only a chevron is white over the dark tile. */
    fun clearApps(count: Int) = whiteApps(count).map { it.copy(image = ImageBitmap(16, 9)) }

    /** Whether a white chevron stands in the gutter on [side] (-1 left, +1 right) of dock slot [slot] (from 1), past the ring, where the dock tiles [rest] stood before the carry. */
    fun chevronBeside(
        rest: List<DpRect>,
        slot: Int,
        side: Int,
    ): Boolean {
        val tile = rest[slot - 1]
        val from =
            if (side <
                0
            ) {
                rest.getOrNull(slot - 2)?.right?.value ?: (tile.left.value - TileGap.value)
            } else {
                tile.right.value + ringReach
            }
        val to = if (side < 0) tile.left.value - ringReach else rest.getOrNull(slot)?.left?.value ?: (tile.right.value + TileGap.value)
        return whiteIn(from, tile.top.value - 10f, to, tile.bottom.value)
    }

    fun restTiles(count: Int) = (1..count).map { dockTile(it) }

    /** The runs of columns, in dp, that hold a pixel with green over [level] (white is 1) at some row from [top] to [bottom] dp, between [from] and [to] dp. */
    fun whiteRuns(
        from: Float,
        to: Float,
        top: Float,
        bottom: Float,
        level: Float = 0.8f,
    ): List<ClosedFloatingPointRange<Float>> {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        val rows = (top * density).toInt() until (bottom * density).toInt()
        val runs = mutableListOf<ClosedFloatingPointRange<Float>>()
        var start = -1
        for (x in (from * density).toInt()..(to * density).toInt()) {
            val white = rows.any { pixels[x, it].green > level }
            if (white && start < 0) start = x
            if (!white && start >= 0) {
                runs += (start / density)..(x / density)
                start = -1
            }
        }
        if (start >= 0) runs += (start / density)..to
        return runs
    }

    fun isRingOrange(pixel: Color) = pixel.red > 0.9f && pixel.green in 0.3f..0.55f && pixel.blue < 0.4f

    /** The first row, in dp, from [from] down to [to] dp whose pixel in the column at [x] dp is the reorder ring's orange. */
    fun ringTop(
        x: Float,
        from: Float,
        to: Float,
    ): Float {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return ((from * density).toInt()..(to * density).toInt()).first { isRingOrange(pixels[(x * density).toInt(), it]) } / density
    }

    /** A reorder arrow stands on a dark disc this many dp across. */
    val arrowDisc = 22f

    /** What [carriedClearances] reads, in dp. */
    data class ArrowClearances(
        /** From the disc's outer edge to the next tile's art, on the right then on the left. */
        val nextRight: Float,
        val nextLeft: Float,
        /** From the ring's outer edge out to the disc's centre, on the right then on the left; zero when it is centred on the edge. */
        val centreRight: Float,
        val centreLeft: Float,
        /** The ring's room under the tray's rim. */
        val underRim: Float,
    )

    /** A chevron is centred this far out from its disc's centre, on the middle of the disc's outer half. */
    val chevronOut = arrowDisc / 4f

    /**
     * Carries the first of [tiles] dock tiles, of clear art among white, to [slot] (from 1) and reads where its arrows stand:
     * each disc, [arrowDisc] across, is centred [chevronOut] in from its chevron, whose white stroke is the only white in the
     * gutter.
     */
    fun carriedClearances(
        tiles: Int,
        slot: Int,
        config: TvConfig,
    ): ArrowClearances {
        showHome(apps = clearApps(1) + whiteApps(tiles).drop(1), config = config)
        val rest = restTiles(tiles)
        // Below the arrows and clear of the tile's rounded corners.
        val artRow = (rest[1].top.value + rest[1].bottom.value) / 2f + 14f
        carryFirstTile()
        repeat(slot - 1) { press(Key.DirectionRight) }
        val at = rest[slot - 1]
        // The ring's dark hairline and stroke reach 3 dp past its orange.
        val halo = 3f
        val middle = (at.top.value + at.bottom.value) / 2f - 7f
        val centre = (at.left.value + at.right.value) / 2f
        // The carried tile is drawn at its rest size, so its ring's outer edge is where the tile's bounds put it.
        val ringRight = at.right.value + ringReach
        val ringLeft = at.left.value - ringReach

        val chevronRight = whiteRuns(ringRight - 8f, at.right.value + 80f, middle - 10f, middle + 10f).single()
        val chevronLeft = whiteRuns(at.left.value - 80f, ringLeft + 8f, middle - 10f, middle + 10f).single()
        val centreRight = (chevronRight.start + chevronRight.endInclusive) / 2f - chevronOut
        val centreLeft = (chevronLeft.start + chevronLeft.endInclusive) / 2f + chevronOut
        // The tiles beside it are dimmed to 0.65, and their art starts and ends at the row below the arrows.
        val neighbourRight = whiteRuns(ringRight, at.right.value + 80f, artRow, artRow + 1f, level = 0.5f).first().start
        val neighbourLeft = whiteRuns(at.left.value - 80f, ringLeft, artRow, artRow + 1f, level = 0.5f).last().endInclusive
        val underRim = ringTop(centre, bounds("dock").top.value, at.top.value + 40f) - halo - bounds("dock").top.value

        return ArrowClearances(
            nextRight = neighbourRight - (centreRight + arrowDisc / 2f),
            nextLeft = (centreLeft - arrowDisc / 2f) - neighbourLeft,
            centreRight = centreRight - ringRight,
            centreLeft = ringLeft - centreLeft,
            underRim = underRim,
        )
    }

    fun assertCarriedClearances(
        tiles: Int,
        slot: Int,
        config: TvConfig = SixColumns,
    ) {
        val clearances = carriedClearances(tiles, slot, config)

        withClue(
            "disc clearance from the next tile: $clearances",
        ) { (clearances.nextRight >= 4f && clearances.nextLeft >= 4f).shouldBeTrue() }
        withClue("disc centre past the ring's outer edge: $clearances") {
            (
                abs(clearances.centreRight) <= 1f &&
                    abs(clearances.centreLeft) <= 1f
            ).shouldBeTrue()
        }
        withClue("the ring is ${clearances.underRim} dp under the rim") { clearances.underRim shouldBeGreaterThanOrEqualTo 7.5f }
    }

    /** A 100 px square of [ground], 2 px to a dp, with an arrow's disc centred in it. */
    fun arrowOn(
        ground: Color,
        pointsLeft: Boolean = false,
    ): PixelMap {
        val image = ImageBitmap(100, 100)
        Canvas(image).drawRect(Rect(0f, 0f, 100f, 100f), Paint().apply { color = ground })
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(image), Size(100f, 100f)) {
            drawArrowDisc(Offset(50f, 50f), alpha = 1f)
            drawChevron(Offset(50f, 50f), pointsLeft = pointsLeft, alpha = 1f)
        }
        return image.toPixelMap()
    }

    /** Carries the first of nine tiles to the last of [config]'s dock and reads how far the right arrow's disc stands from the dock's wall. */
    fun lastTileDiscToWall(config: TvConfig): Float {
        showHome(apps = clearApps(1) + whiteApps(9).drop(1), config = config)
        val rest = restTiles(config.columns)
        carryFirstTile()
        repeat(config.columns - 1) { press(Key.DirectionRight) }
        val at = rest.last()
        val middle = (at.top.value + at.bottom.value) / 2f - 7f
        val wall = bounds("dock").right.value
        val chevron = whiteRuns(at.right.value + ringReach - 8f, wall, middle - 10f, middle + 10f).single()
        return wall - ((chevron.start + chevron.endInclusive) / 2f - chevronOut + arrowDisc / 2f)
    }

    /** The left edge, in dp, of the white art that covers [x] dp at [row] dp. */
    fun artStart(
        x: Float,
        row: Float,
    ): Float = whiteRuns(x - 60f, x + 60f, row, row + 1f, level = 0.5f).first { x in it }.start

    /** The red channel just outside a chip, inside its backing, and on its rim. */
    data class ChipPixels(
        val outside: Int,
        val inside: Int,
        val rim: Int,
    )

    /** Reads the chip tagged [tag] over a white wallpaper. */
    fun chipPixels(tag: String): ChipPixels {
        val chip = bounds("reorder:chip:$tag")
        val middle = (chip.top.value + chip.bottom.value) / 2
        val x = (chip.left.value + chip.right.value) / 2
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density

        fun red(
            px: Float,
            py: Float,
        ) = Math.round(pixels[(px * density).toInt(), (py * density).toInt()].red * 255)
        // The rim is 1 dp, two pixels, along the top edge; the brighter of its two rows.
        val rim = maxOf(red(x, chip.top.value + 0.25f), red(x, chip.top.value + 0.75f))
        return ChipPixels(outside = red(chip.left.value - 6f, middle), inside = red(chip.left.value + 5f, middle), rim = rim)
    }

    /** Backing #07080C at 0.55, then the rim, white at 0.20, both under the chip's [alpha]. */
    fun assertChipAt(
        tag: String,
        alpha: Float,
    ) {
        val (outside, inside, rim) = chipPixels(tag)
        assertNear(Math.round(outside * (1 - 0.55f * alpha) + 7 * 0.55f * alpha), inside, 5)
        assertNear(Math.round(inside + 0.20f * alpha * (255 - inside)), rim, 8)
    }

    /** The chip tagged [tag] says [key] and [action], and is drawn at full alpha. */
    fun assertChip(
        tag: String,
        key: String,
        action: String,
    ) {
        compose.onNode(hasText(key) and hasAnyAncestor(hasTestTag("reorder:chip:$tag"))).assertExists()
        compose.onNode(hasText(action) and hasAnyAncestor(hasTestTag("reorder:chip:$tag"))).assertExists()
        assertChipAt(tag, 1f)
    }
}
