package dev.niltsiar.tvhome.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import app.cash.molecule.RecompositionMode
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.FocusRingReach
import dev.niltsiar.tvhome.core.designsystem.FocusStyle
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.OverlayHost
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.LibraryApp
import dev.niltsiar.tvhome.core.model.LibraryArt
import dev.niltsiar.tvhome.core.model.OpenedRecord
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** The library in Home's overlay host, read from the rendered pixels and the laid-out cards. */
val LibraryOverlayLookUiTest by testSuite {
    robolectricTestSuite<LibraryOverlayLookUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
    robolectricTestSuite<LibraryOverlayLookUiNightTestContent>(
        "sdk 31, night, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-night-xhdpi"
            },
    )
}

class LibraryOverlayLookUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { LibraryOverlayLookUiFixture() } asContextForEach {
            test("the focused card s cut name scrolls and a cut name without focus stays still") {
                showLibrary(cutNames.map { libraryApp(it) } + nineApps)
                val (first, second) = cutNames
                compose.onNodeWithTag("card:${key(first)}").assertIsFocused()
                // Waiting for idle would run a whole pass and back to its start.
                compose.mainClock.autoAdvance = false
                val focused = cardFrame(first)
                val other = cardFrame(second)

                compose.mainClock.advanceTimeBy(2_000)

                withClue("the focused cut name did not move") { cardFrame(first) shouldNotBe focused }
                cardFrame(second) shouldBe other
            }

            test("with reduced motion the focused card s cut name stays still") {
                showLibrary(cutNames.map { libraryApp(it) } + nineApps, motion = Motion(reducedMotion = true))
                val first = cutNames.first()
                compose.onNodeWithTag("card:${key(first)}").assertIsFocused()
                // Waiting for idle would run a whole pass and back to its start.
                compose.mainClock.autoAdvance = false
                val focused = cardFrame(first)

                compose.mainClock.advanceTimeBy(2_000)

                cardFrame(first) shouldBe focused
            }

            test("a focused card shows the plate behind it with glass") {
                showLibrary(nineApps, focusStyle = FocusStyle.Glass)

                val inside = insideFocusedCard("App 1")
                withClue("inside $inside") { inside shouldBeLessThan (CARD_FILL_RED - 10) }
            }

            test("a focused card keeps its fill with rim and halo") {
                showLibrary(nineApps, focusStyle = FocusStyle.Rim)

                val inside = insideFocusedCard("App 1")
                withClue("inside $inside") { abs(inside - CARD_FILL_RED) shouldBeLessThanOrEqualTo 2 }
            }

            test("a bright home does not show through the panel") {
                bleedThrough() shouldBeLessThanOrEqualTo MAX_BLEED
            }

            test("three rows of six cards show whole and the fourth row waits below") {
                showLibrary(twentyThree)

                val grid = bounds("library:cards")
                val shown = (1..18).map { card(app(it)) }
                withClue("$shown in $grid") {
                    shown
                        .all {
                            it.left >= grid.left && it.right <= grid.right && it.top >= grid.top &&
                                it.bottom <= grid.bottom
                        }.shouldBeTrue()
                }
                shown.map { it.top.value.roundToInt() }.distinct().size shouldBe 3
                shown.map { it.left.value.roundToInt() }.distinct().size shouldBe 6
                val fourth = compose.onAllNodesWithTag("card:${key(app(19))}").fetchSemanticsNodes()
                (fourth.isEmpty() || card(app(19)).top >= grid.bottom).shouldBeTrue()
            }

            test("the fourth row scrolls in with its ring whole and the first row comes back the same way") {
                showLibrary(twentyThree)
                val grid = bounds("library:cards")
                val reach = 7.dp * FOCUSED_SCALE

                press(Key.DirectionDown, times = 3)
                withClue("${grownCard(app(19))} in $grid") {
                    (grownCard(app(19)).bottom + reach) shouldBeLessThanOrEqualTo
                        (grid.bottom + 1.dp)
                }

                press(Key.DirectionUp, times = 3)
                withClue("${grownCard(app(1))} in $grid") { (grownCard(app(1)).top - reach) shouldBeGreaterThanOrEqualTo (grid.top - 1.dp) }
            }

            test("a chevron under the grid says cards wait below and one at its top says cards wait above") {
                showLibrary(twentyThree)
                val grid = bounds("library:cards")
                val middle = (grid.left + grid.right) / 2
                // The bottom chevron's tip: its pill is centred 9 dp under the grid and the chevron is 9 dp tall.
                val bottomTip = grid.bottom + 13.5.dp
                val topTip = grid.top + 3.5.dp

                withClue("bottom cue at the start") { cueAt(middle, bottomTip).shouldBeTrue() }
                withClue("no top cue at the start") { cueAt(middle, topTip).shouldBeFalse() }

                press(Key.DirectionDown, times = 3)
                withClue("no bottom cue with the last row in view") { cueAt(middle, bottomTip).shouldBeFalse() }
                withClue("top cue with the first row scrolled out") { cueAt(middle, topTip).shouldBeTrue() }
            }

            test("the panel keeps 31 dp under the grid and ends 12 dp over the screens bottom") {
                showLibrary(twentyThree)

                val panel = bounds("library:panel")
                val screen = compose.onRoot().getBoundsInRoot()

                (panel.bottom - bounds("library:cards").bottom).value shouldBe (31f plusOrMinus 0.5f)
                (screen.bottom - panel.bottom).value shouldBe (12.5f plusOrMinus 0.5f)
            }

            test("with focus overshoot the grid leaves room over its top row for the ring at the peak of the pop") {
                showLibrary(nineApps, motion = Motion(focusOvershoot = true))

                val peakReach = 109f * (1.144f - 1f) / 2f + FocusRingReach.value * 1.144f
                (card("App 1").top - bounds("library:cards").top).value shouldBe (peakReach plusOrMinus 0.6f)
            }

            test("a focused top row card has its ring at least 16 dp below the hint row key caps") {
                showLibrary(nineApps)

                withClue("${ringRoomUnderKeyCaps("App 1")}") { ringRoomUnderKeyCaps("App 1") shouldBeGreaterThanOrEqualTo 16.dp }
            }

            test("a focused top row card has its ring at least 16 dp below the key caps with the order chip shown") {
                showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))
                press(Key.DirectionRight)

                withClue("${ringRoomUnderKeyCaps("App 2")}") { ringRoomUnderKeyCaps("App 2") shouldBeGreaterThanOrEqualTo 16.dp }
            }

            test("in the chooser a focused top row card has its ring at least 16 dp below the hint row key caps") {
                showChooser()

                withClue("${ringRoomUnderKeyCaps("Aaa")}") { ringRoomUnderKeyCaps("Aaa") shouldBeGreaterThanOrEqualTo 16.dp }
            }

            test("the 23rd app is seven presses from the first and no key goes past an edge") {
                showLibrary(twentyThree)

                press(Key.DirectionUp)
                press(Key.DirectionLeft)
                focusedApps() shouldBe listOf(app(1))
                press(Key.DirectionDown, times = 3)
                focusedApps() shouldBe listOf(app(19))
                press(Key.DirectionRight, times = 4)
                focusedApps() shouldBe listOf(app(23))
                press(Key.DirectionDown)
                press(Key.DirectionRight)
                focusedApps() shouldBe listOf(app(23))
            }

            test("down from the sixth column lands on the last card of the short last row") {
                showLibrary(twentyThree)

                press(Key.DirectionRight, times = 5)
                press(Key.DirectionDown, times = 3)

                focusedApps() shouldBe listOf(app(23))
            }

            test("down into the fourth row keeps the column") {
                showLibrary(twentyThree)

                press(Key.DirectionRight, times = 2)
                press(Key.DirectionDown, times = 3)

                focusedApps() shouldBe listOf(app(21))
                press(Key.DirectionUp)
                focusedApps() shouldBe listOf(app(15))
            }

            test("the status line sits at the bottom of every card on one baseline") {
                val long = "Long enough a name to wrap onto two lines"
                // Aaa takes the focus, and its growth with it; the cards measured here are idle.
                showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta"), libraryApp(long)))

                compose.onNodeWithTag("card:${key("Aaa")}").assertIsFocused()
                val bottom = card("Beta").bottom - 6.dp
                assertNear(bottom, statusText("Add to Home", "Beta").bottom)
                assertNear(bottom, statusText("Add to Home", long).bottom)
            }

            test("a two line name ends at least 4 dp above the status line and a focused one at least 2 dp") {
                showLibrary(longNames.map { libraryApp(it) })

                assertNameClearsStatus()
            }

            test("in the chooser a two line name with a descender ends at least 6 dp above the check and above the ring") {
                showLibrary(
                    listOf(libraryApp("Aaa"), libraryApp("Bbb $describedName", onHome = true), libraryApp("Ccc $describedName")),
                    art = LibraryArt(red, Color.Red),
                    config = chooser,
                )

                for ((mark, name) in listOf("card:check" to "Bbb $describedName", "card:ring" to "Ccc $describedName")) {
                    val top = compose.onNodeWithTag("$mark:${key(name)}", useUnmergedTree = true).getBoundsInRoot().top
                    val room = top - lastLineBottom(name)
                    withClue("the name ends $room above the $mark") { room shouldBeGreaterThanOrEqualTo (6.dp - 0.05.dp) }
                }
            }

            test("a card keeps its outer size of 109 dp tall and a sixth of the grid wide") {
                showLibrary(listOf(libraryApp("Aaa"), libraryApp("Bbb $describedName")), art = LibraryArt(red, Color.Red), config = chooser)

                for (name in listOf("Aaa", "Bbb $describedName")) {
                    card(name).height.value shouldBe (109f plusOrMinus 0.5f)
                    card(name).width.value shouldBe (108.3f plusOrMinus 0.5f)
                }
            }

            test("a library card with no status mark has the art and the two line name slot centred for a one line and a two line name") {
                showLibrary(
                    listOf(libraryApp("Aaa"), libraryApp("Beta"), libraryApp(longNames[0])),
                    config = TvConfig(appLibrary = true, libraryOkOpens = true),
                )

                // A two-line name fills its slot, so the slot ends where that name does.
                val slotBottom = nameBottom(longNames[0])
                for (name in listOf("Beta", longNames[0])) {
                    val above = art(name).top - card(name).top
                    val below = card(name).bottom - slotBottom
                    withClue("$name has $above over its art and $below under the name slot") {
                        abs(above.value - below.value) shouldBeLessThanOrEqualTo
                            4f
                    }
                }
                assertNear(art("Beta").top - card("Beta").top, art(longNames[0]).top - card(longNames[0]).top)
            }

            test("a one line name keeps its gap under the art when the library has no status mark") {
                showLibrary(
                    listOf(libraryApp("Aaa"), libraryApp("Beta")),
                    config = TvConfig(appLibrary = true, libraryOkOpens = true),
                )

                assertNear(13.dp, nameTop("Beta") - art("Beta").bottom)
            }

            test("a chooser card keeps its art and name above the check") {
                showLibrary(
                    listOf(libraryApp("Aaa"), libraryApp("Bbb", onHome = true), libraryApp("Ccc $describedName")),
                    art = LibraryArt(red, Color.Red),
                    config = chooser,
                )

                val (above, below) = roomAboveAndBelow("Bbb")
                assertNear(4.dp, above)
                assertNear(33.dp, below)
                val (aboveTwo, belowTwo) = roomAboveAndBelow("Ccc $describedName")
                assertNear(4.dp, aboveTwo)
                assertNear(28.dp, belowTwo)
            }

            test("in the least used order a two line name ends at least 4 dp above the opened line and a focused one at least 2 dp") {
                showLibrary(
                    longNames.map { libraryApp(it) },
                    config = TvConfig(appLibrary = true, libraryLeastUsed = true),
                    record = OpenedRecord(times = mapOf(key(longNames[0]) to NOW - 3 * DAY), since = NOW - 90 * DAY),
                )
                press(Key.Menu)

                assertNameClearsStatus()
            }

            test("in the least used order the status line is the opened text alone and the art keeps the usual inset") {
                showLibrary(
                    listOf(libraryApp("Aaa"), libraryApp("Beta", installedAt = NOW - DAY), libraryApp("Gamma", onHome = true)),
                    config = TvConfig(appLibrary = true, libraryLeastUsed = true),
                    record = OpenedRecord(times = mapOf(key("Gamma") to NOW - 3 * DAY), since = NOW - 90 * DAY),
                )
                val inset = { name: String -> art(name).left - card(name).left }
                assertNear(15.8.dp, inset("Beta"))

                press(Key.Menu)

                assertNear(15.8.dp, inset("Beta"))
                assertNear(15.8.dp, inset("Gamma"))
                compose.textsOnCard("Beta") shouldBe listOf("Beta", "Never opened")
                compose.textsOnCard("Gamma").take(1) shouldBe listOf("Gamma")
                compose.textsOnCard("Gamma").size shouldBe 2
                compose.textsOnCard("Gamma")[1] shouldStartWith "3 days ago, "
            }

            test("on the light surface an idle card is a shade darker than the panel") {
                showLibrary(nineApps)

                val idle = card("App 3")
                val middle = (idle.top + idle.bottom) / 2
                val panel = redAt(idle.right + 8.dp, middle)
                val inside = redAt(idle.left + 4.dp, middle)
                withClue("card $inside, panel $panel") { inside shouldBeLessThanOrEqualTo (panel - 4) }
            }

            test("in the chooser the count says how many apps are on home and follows a change") {
                showChooser()
                compose.onNodeWithTag("library:count").assertTextEquals("2 of 4 on Home")

                press(Key.DirectionCenter)

                compose.onNodeWithTag("library:count").assertTextEquals("1 of 4 on Home")
            }

            test("in browse mode the count stays the number of apps") {
                showLibrary(listOf(libraryApp("Aaa", onHome = true), libraryApp("Bbb"), libraryApp("Ccc")))

                compose.onNodeWithTag("library:count").assertTextEquals("3 apps")
            }

            test("a recent install has a green dot in its top end corner unless it is on home") {
                showLibrary(
                    listOf(
                        libraryApp("Aaa"),
                        libraryApp("Beta", installedAt = NOW - DAY),
                        libraryApp("Gamma", onHome = true, installedAt = NOW - DAY),
                    ),
                )

                // How far, 0 to 255, green leads red in the greenest pixel of the card's top end 20 by 20 dp corner.
                fun greenInCorner(name: String): Int {
                    val card = card(name)
                    val density = compose.activity.resources.displayMetrics.density
                    val pixels = compose.onRoot().captureToImage().toPixelMap()
                    val xs = ((card.right - 20.dp).value * density).toInt() until (card.right.value * density).toInt()
                    val ys = (card.top.value * density).toInt() until ((card.top + 20.dp).value * density).toInt()
                    return ys.maxOf { y -> xs.maxOf { x -> Math.round((pixels[x, y].green - pixels[x, y].red) * 255) } }
                }
                withClue("the recent install off home") { greenInCorner("Beta") shouldBeGreaterThan 60 }
                withClue("the recent install on home") { greenInCorner("Gamma") shouldBeLessThan 20 }
            }

            test("the art of a one line card starts as far below the card top as the art of a two line card") {
                showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta"), libraryApp(longNames[0])))

                val one = art("Beta").top - card("Beta").top
                val two = art(longNames[0]).top - card(longNames[0]).top
                withClue("one-line art $one below its card top, two-line art $two") {
                    abs(one.value - two.value) shouldBeLessThanOrEqualTo
                        1f
                }
            }

            test("a one line name is centred between the art and the status line") {
                showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta"), libraryApp(longNames[0])))

                val above = nameTop("Beta") - art("Beta").bottom
                val below = statusTop("Beta") - nameBottom("Beta")
                withClue(
                    "$above under the art, $below above the status line",
                ) { abs(above.value - below.value) shouldBeLessThanOrEqualTo 2f }
            }

            test("a name cut to two lines ends in an ellipsis with no space before it") {
                val labels = (6..14).map { words -> NAME_WORDS.take(words).joinToString(" ") }
                showLibrary(labels.map { libraryApp(it) })

                val shown =
                    labels.map { label ->
                        compose
                            .onNode(
                                hasText("Control", substring = true) and hasAnyAncestor(hasTestTag("card:${key(label)}")),
                                useUnmergedTree = true,
                            ).fetchSemanticsNode()
                            .config[SemanticsProperties.Text]
                            .joinToString("") { it.text }
                    }

                val cut = shown.filter { it.endsWith(ELLIPSIS) }
                withClue("$shown") { cut.size shouldBeGreaterThanOrEqualTo 3 }
                withClue("$cut") { cut.none { it.endsWith(" " + ELLIPSIS) }.shouldBeTrue() }
            }

            test("a name with a long word shrinks until no line ends inside a word") {
                val name = "Temporizadores y reloj"
                showLibrary(listOf(libraryApp(name)))

                val layout = nameLayout(name)
                layout.layoutInput.text.text shouldBe name
                withClue(
                    "the name must break inside a word without autoSize",
                ) { linesEndingInsideAWord(unshrunk(layout)).shouldNotBeEmpty() }
                linesEndingInsideAWord(layout) shouldBe emptyList<Int>()
                withClue("${layout.lineCount} lines") {
                    layout.lineCount shouldBeLessThanOrEqualTo 2
                    layout.isLineEllipsized(layout.lineCount - 1).shouldBeFalse()
                }
            }

            test("a name that fits keeps the full size") {
                showLibrary(listOf(libraryApp("Plex")))

                val layout = nameLayout("Plex")
                layout.lineWidth(0) shouldBe (unshrunk(layout).lineWidth(0) plusOrMinus WIDTH_ROUNDING)
            }

            test("a word too long for the smallest size ends in an ellipsis within two lines at the full size") {
                val name = "Supercalifragilisticexpialidocious Antidisestablishmentarianism Pneumonoultramicroscopic"
                showLibrary(listOf(libraryApp(name)))

                val layout = nameLayout(name)
                withClue("${layout.lineCount} lines") { layout.lineCount shouldBeLessThanOrEqualTo 2 }
                (
                    layout.layoutInput.text.text
                        .endsWith(ELLIPSIS)
                ).shouldBeTrue()
                layout.lineWidth(layout.lineCount - 1) shouldBe
                    (unshrunk(layout).lineWidth(layout.lineCount - 1) plusOrMinus WIDTH_ROUNDING)
            }

            test("on the light menu a focused order chip keeps its key cap 3 to 1 on the chip with its text 4 5 to 1 on the cap") {
                val (capOnChip, textOnCap) = focusedKeyCapContrast()

                withClue("cap on chip $capOnChip") { capOnChip shouldBeGreaterThanOrEqualTo 3f }
                withClue("text on cap $textOnCap") { textOnCap shouldBeGreaterThanOrEqualTo 4.5f }
            }
        }
    })

class LibraryOverlayLookUiNightTestContent :
    RobolectricTestSuiteContent({
        testFixture { LibraryOverlayLookUiFixture() } asContextForEach {
            test("the bottom chevron sits in a 36 dp wide 20 dp tall pill of 15 percent white centred under the grid") {
                showLibrary(twentyThree)
                val grid = bounds("library:cards")
                val centre = grid.bottom + 9.dp

                // 15 % white over the panel's 0x14 is 20 + 0.15 x 235 = 55; 18 is half of what it adds. Only the middle row spans the whole pill.
                val pill = lighterColumns(centre, level = 18)

                (pill.endInclusive - pill.start) shouldBe (36f plusOrMinus 1f)
                ((pill.start + pill.endInclusive) / 2f) shouldBe ((grid.left.value + grid.right.value) / 2f plusOrMinus 1f)
                withClue("the pill ends inside the panel ${bounds("library:panel")}") {
                    (centre + 10.dp) shouldBeLessThanOrEqualTo
                        bounds("library:panel").bottom
                }
            }

            test("the bottom chevron pill has 12 dp clear above it to the last row and 12 dp below it to the panel") {
                showLibrary(twentyThree)
                val grid = bounds("library:cards")
                val lastRow = card(app(18)).bottom
                val panel = bounds("library:panel").bottom
                val middle = (grid.left + grid.right) / 2
                val density = compose.activity.resources.displayMetrics.density
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                val x = (middle.value * density).toInt()
                val ground = pixels[x, ((lastRow.value + 3f) * density).toInt()].red * 255

                // The pill, at the centre column, is the rows lighter than the panel's inside by half of what 15 % white adds; the panel's own rim is not.
                val lit =
                    (((lastRow.value + 1f) * density).toInt() until ((panel.value - 1.5f) * density).toInt()).filter {
                        pixels[x, it].red * 255 -
                            ground >=
                            18
                    }

                val above = lit.min() / density - lastRow.value
                val below = panel.value - (lit.max() + 1) / density
                withClue("$above dp above the pill and $below dp below it") {
                    above shouldBeGreaterThanOrEqualTo 11.5f
                    below shouldBeGreaterThanOrEqualTo 11.5f
                }
            }

            test("the bottom chevron is 18 dp wide") {
                showLibrary(twentyThree)
                val grid = bounds("library:cards")
                val centre = grid.bottom + 9.dp

                // The arms are 18 dp apart at their tops, 4.5 dp over the pill's middle, and 2 dp of stroke widen that to 20 dp.
                val tops = lighterColumns(centre - 4.dp, level = 70)

                (tops.endInclusive - tops.start) shouldBe (20f plusOrMinus 1.5f)
            }

            test("the ring of the first card shows whole at its side and top") {
                showLibrary(nineApps)

                val grown = grownCard("App 1")
                // The middle of the white ring: 3 dp wide, 3 dp out from the grown card, grown with it.
                val out = 4.5.dp * FOCUSED_SCALE
                redAt(grown.left - out, (grown.top + grown.bottom) / 2) shouldBeGreaterThanOrEqualTo WHITE
                redAt((grown.left + grown.right) / 2, grown.top - out) shouldBeGreaterThanOrEqualTo WHITE
            }

            test("the title ink starts at least 20 dp under the panels top edge") {
                showLibrary(nineApps)

                val top = titleInkTop()

                withClue("$top") { top shouldBeGreaterThanOrEqualTo 20.dp }
            }

            test("in the chooser the title ink starts at least 20 dp under the panels top edge") {
                showChooser()

                val top = titleInkTop()

                withClue("$top") { top shouldBeGreaterThanOrEqualTo 20.dp }
            }

            test("never opened draws at 45 percent of the alpha of a last opened line") {
                showLibrary(
                    listOf(libraryApp("Aaa"), libraryApp("Beta"), libraryApp("Gamma")),
                    config = TvConfig(appLibrary = true, libraryLeastUsed = true),
                    record = OpenedRecord(times = mapOf(key("Gamma") to NOW - 3 * DAY), since = NOW - 90 * DAY),
                )
                press(Key.Menu)

                fun lift(name: String): Float {
                    val (peak, ground) =
                        lineBrightness(
                            compose.onNodeWithTag("card:opened:${key(name)}", useUnmergedTree = true).getBoundsInRoot(),
                        )
                    return (peak - ground).toFloat()
                }
                val ratio = lift("Beta") / lift("Gamma")
                withClue("never opened is $ratio of the opened line") { ratio shouldBeIn (0.38f..0.52f) }
            }

            test("an idle card is opaque so a bright home does not show through it") {
                showLibrary(nineApps)
                home = Color.White
                compose.waitForIdle()

                val idle = card("App 3")
                val inside = rgbAt(idle.left + 4.dp, (idle.top + idle.bottom) / 2)
                val expected = listOf(0x1C, 0x20, 0x30)
                withClue("card $inside") { inside.zip(expected).all { (actual, wanted) -> abs(actual - wanted) <= 2 }.shouldBeTrue() }
            }

            test("a card on home has no green check badge") {
                showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta", onHome = true)))

                val onHome = card("Beta")
                val (red, green) = rgbAt(onHome.right - 15.dp, onHome.top + 15.dp)
                withClue("red $red, green $green") { (green - red) shouldBeLessThan 20 }
            }

            test("in browse mode an app on home has no mark and an empty status slot") {
                showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta"), libraryApp("Gamma", onHome = true)))

                compose.onAllNodes(hasContentDescription("On Home"), useUnmergedTree = true).assertCountEquals(0)
                val gamma = card("Gamma")
                val (peak, ground) =
                    lineBrightness(
                        DpRect(gamma.left + 6.dp, gamma.bottom - 20.dp, gamma.right - 6.dp, gamma.bottom - 6.dp),
                    )
                withClue("the status slot of an app on Home draws up to $peak over $ground") { (peak - ground) shouldBeLessThanOrEqualTo 3 }
            }

            test("an app off home has a 12 dp plus 4 dp before its add to home line at the lines alpha") {
                showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta")))

                val text = statusText("Add to Home", "Beta")
                val plusArea = DpRect(text.left - 30.dp, text.top, text.left - 0.5.dp, text.bottom)
                val plus = lightSpan(plusArea)!!
                (plus.endInclusive - plus.start) shouldBe (12f plusOrMinus 1f)
                (text.left.value - plus.endInclusive) shouldBe (4f plusOrMinus 1f)
                val (plusPeak, ground) = lineBrightness(DpRect(text.left - 17.dp, text.top, text.left - 4.dp, text.bottom))
                val (textPeak, _) = lineBrightness(text)
                val ratio = (plusPeak - ground).toFloat() / (textPeak - ground)
                withClue("the plus peaks at $plusPeak, the line at $textPeak, over $ground") { ratio shouldBeIn (0.7f..1.3f) }
            }

            test("in the chooser an app on home has an 18 dp check centred in its status slot and none off home") {
                showChooser()

                val card = card("Bbb")
                val slot = DpRect(card.left + 6.dp, card.bottom - 20.dp, card.right - 6.dp, card.bottom - 6.dp)
                val check = compose.onNodeWithTag("card:check:${key("Bbb")}", useUnmergedTree = true).getBoundsInRoot()
                check.width.value shouldBe (18f plusOrMinus 0.5f)
                check.height.value shouldBe (18f plusOrMinus 0.5f)
                ((check.left.value + check.right.value) / 2f) shouldBe ((card.left.value + card.right.value) / 2f plusOrMinus 1f)
                ((check.top.value + check.bottom.value) / 2f) shouldBe ((slot.top.value + slot.bottom.value) / 2f plusOrMinus 1f)
                compose.onNodeWithTag("card:check:${key("Bbb")}", useUnmergedTree = true).assertContentDescriptionEquals("On Home")
                compose.onNodeWithTag("card:check:${key("Ccc")}", useUnmergedTree = true).assertDoesNotExist()
            }

            test("in the chooser the check of an idle card is a white 085 disc with a dark check") {
                showChooser()

                val idleFill = redAt(card("Bbb").left + 4.dp, (card("Bbb").top + card("Bbb").bottom) / 2)
                val disc = redInMark("card:check", "Bbb", 0.5f, 0.1f)
                val stroke = redInMark("card:check", "Bbb", 0.58f, 0.51f)

                withClue("the disc") { disc.toFloat() shouldBe (idleFill + 0.85f * (255 - idleFill) plusOrMinus 6f) }
                withClue("the check is $stroke on a disc of $disc") { stroke.toFloat() shouldBeLessThanOrEqualTo (0.35f * disc) }
            }

            test("in the chooser the check of the focused card is a white disc with a dark check") {
                showChooser()
                compose.onNodeWithTag("card:${key("Aaa")}").assertIsFocused()

                // The focused card grows about its centre, so the mark is read over its whole box, grown by 3 dp.
                val mark = compose.onNodeWithTag("card:check:${key("Aaa")}", useUnmergedTree = true).getBoundsInRoot()
                val density = compose.activity.resources.displayMetrics.density
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                val reds =
                    ((mark.left.value - 3f) * density).toInt().until(((mark.right.value + 3f) * density).toInt()).flatMap { x ->
                        ((mark.top.value - 3f) * density).toInt().until(((mark.bottom.value + 3f) * density).toInt()).map { y ->
                            Math.round(pixels[x, y].red * 255)
                        }
                    }
                withClue("the disc: ${reds.max()}") { reds.max() shouldBeGreaterThanOrEqualTo 235 }
                withClue("the check: ${reds.min()}") { reds.min() shouldBeLessThanOrEqualTo 40 }
            }

            test(
                "in the chooser an app off home shows an 18 dp ring of 2 dp white 070 filled with white 010 where an app on home has its check",
            ) {
                showChooser()

                val ring = compose.onNodeWithTag("card:ring:${key("Ccc")}", useUnmergedTree = true).getBoundsInRoot()
                val idleFill = redAt(card("Ccc").left + 4.dp, (card("Ccc").top + card("Ccc").bottom) / 2)
                ring.width.value shouldBe (18f plusOrMinus 0.5f)
                ring.height.value shouldBe (18f plusOrMinus 0.5f)
                withClue("the stroke") {
                    redInMark("card:ring", "Ccc", 1f / 18f, 0.5f).toFloat() shouldBe
                        (idleFill + 0.70f * (255 - idleFill) plusOrMinus 6f)
                }
                withClue("the stroke is 2 dp") {
                    redInMark("card:ring", "Ccc", 1.8f / 18f, 0.5f).toFloat() shouldBe
                        (idleFill + 0.70f * (255 - idleFill) plusOrMinus 8f)
                }
                withClue("the inside") {
                    redInMark("card:ring", "Ccc", 0.5f, 0.5f).toFloat() shouldBe
                        (idleFill + 0.10f * (255 - idleFill) plusOrMinus 3f)
                }
                compose.onNodeWithTag("card:ring:${key("Bbb")}", useUnmergedTree = true).assertDoesNotExist()
            }

            test("in the chooser the ring of an app off home is clearly lighter than the check disc of an app on home") {
                showChooser()

                val ring = redInMark("card:ring", "Ccc", 1f / 18f, 0.5f)
                val disc = redInMark("card:check", "Bbb", 0.5f, 0.1f)
                withClue("the ring stroke is $ring against a disc of $disc") { (ring + 20) shouldBeLessThanOrEqualTo disc }
            }

            test("in the chooser no white lies on the art of an app on home and none hangs past the card") {
                showChooser()

                val card = card("Bbb")
                val art = art("Bbb")
                withClue("on the art") { whitePixels(art) shouldBe 0 }
                val corner = DpRect(card.right - 12.dp, card.top - 12.dp, card.right + 12.dp, card.top + 12.dp)
                withClue("on the corner") { whitePixels(corner) shouldBe 0 }
                val below = DpRect(card.left, card.bottom, card.right, card.bottom + 6.dp)
                withClue("under the card") { whitePixels(below) shouldBe 0 }
            }

            test("in the chooser an idle app off home draws its art at 045 and its name at 060 where an app on home draws both whole") {
                showChooser()

                artOverCard("Bbb").first shouldBe 255
                val (off, card) = artOverCard("Ccc")
                withClue("the art over a card of $card") { off.toFloat() shouldBe (card + 0.45f * (255 - card) plusOrMinus 6f) }
                val ratio = nameContrast("Ccc").toFloat() / nameContrast("Bbb")
                withClue("the name off Home is $ratio of the name on Home") { ratio shouldBeIn (0.54f..0.66f) }
            }

            test("in the chooser ok on a focused app off home lifts its art from 075 to whole and its name stays whole") {
                showChooser()
                press(Key.DirectionRight, times = 2)
                compose.onNodeWithTag("card:${key("Ccc")}").assertIsFocused()

                val (before, card) = artOverCard("Ccc", FOCUSED_SCALE)
                val nameBefore = nameContrast("Ccc")
                press(Key.DirectionCenter)
                compose.onNodeWithTag("card:check:${key("Ccc")}", useUnmergedTree = true).assertExists()
                val after = artOverCard("Ccc").first

                withClue("the art over a card of $card") { before.toFloat() shouldBe (card + 0.75f * (255 - card) plusOrMinus 8f) }
                after shouldBe 255
                withClue("the art goes from $before to $after") { (after - before) shouldBeGreaterThanOrEqualTo 40 }
                val name = nameContrast("Ccc").toFloat() / nameBefore
                withClue("the name after OK is $name of the name before") { name shouldBeIn (0.95f..1.05f) }
            }

            test("in browse mode an app off home draws its art and name as an app on home does") {
                showLibrary(
                    listOf(libraryApp("Aaa"), libraryApp("Bbb", onHome = true), libraryApp("Ccc")),
                    art = LibraryArt(red, Color.Red),
                )

                artOverCard("Bbb").first shouldBe 255
                artOverCard("Ccc").first shouldBe 255
                val ratio = nameContrast("Ccc").toFloat() / nameContrast("Bbb")
                withClue("the name off Home is $ratio of the name on Home") { ratio shouldBeIn (0.95f..1.05f) }
            }

            test("in the chooser ok fades the focused card s art to whole over about 150 ms") {
                val (before, reds) = artFramesOfAddingCcc(chooser)

                val frames = reds.indexOfFirst { it == 255 } - reds.indexOfLast { it <= before + 1 }
                withClue("from $before: $reds") { frames shouldBeIn (7..12) }
            }

            test("with motion reduced ok changes the focused card s art in one frame") {
                assertArtJumps(chooser.copy(reducedMotion = true))
            }

            test("with animations off ok changes the focused card s art in one frame") {
                assertArtJumps(chooser.copy(animations = false))
            }

            test("in the chooser an app off home has its ring and no status text focused or not") {
                showChooser()

                compose.textsOnCard("Ccc") shouldBe listOf("Ccc")
                compose.textsOnCard("Bbb") shouldBe listOf("Bbb")
                press(Key.DirectionRight, times = 2)
                compose.onNodeWithTag("card:${key("Ccc")}").assertIsFocused()

                compose.textsOnCard("Ccc") shouldBe listOf("Ccc")
                compose.onNodeWithTag("card:ring:${key("Ccc")}", useUnmergedTree = true).assertExists()
            }

            test("in the chooser the check of a card whose shortcut changed pulses to 112 and no card scales") {
                val (peak, art, settled) = pulseFramesOfAddingCcc(chooser)

                withClue("the check peaks at $peak dp, settled at $settled") { peak shouldBeGreaterThanOrEqualTo (1.08f * settled) }
                withClue("the art spans $art dp") { (art.endInclusive - art.start) shouldBeLessThanOrEqualTo 1.5f }
            }

            test("with motion reduced the check of a card whose shortcut changed does not pulse") {
                val (peak, _, settled) = pulseFramesOfAddingCcc(chooser.copy(reducedMotion = true))

                withClue("the check peaks at $peak dp, settled at $settled") { peak shouldBeLessThanOrEqualTo (1.02f * settled) }
            }

            test("with animations off the check of a card whose shortcut changed does not pulse") {
                val (peak, _, settled) = pulseFramesOfAddingCcc(chooser.copy(animations = false))

                withClue("the check peaks at $peak dp, settled at $settled") { peak shouldBeLessThanOrEqualTo (1.02f * settled) }
            }

            test("the no usage status is one line under the order chip and not in the hint") {
                showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))

                press(Key.Menu)

                val count = bounds("library:count")
                val status = bounds("library:status")
                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home", "Hold OK", "Options")
                compose.onNodeWithTag("library:status").assertTextEquals("No usage data yet")
                assertNear(count.right, status.right)
                withClue("status $status under count $count") { status.top shouldBeGreaterThanOrEqualTo count.bottom }
                withClue("status $status beside the hint ${bounds("library:hint")}") {
                    status.left shouldBeGreaterThanOrEqualTo
                        bounds("library:hint").left
                }
            }

            test("the hint is a row of key chips as tall as reorder mode draws them") {
                showLibrary(nineApps)

                bounds("library:hint").height.value shouldBe (32f plusOrMinus 1f)
            }

            test("a focused order chip is filled white with dark text and a dark glyph") {
                showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))
                val chip = bounds("library:order")
                val middle = (chip.top + chip.bottom) / 2
                val glyph = compose.onNodeWithTag("library:order:glyph", useUnmergedTree = true).getBoundsInRoot()
                withClue("idle fill ${rgbAt(chip.left + 3.dp, middle)}") { rgbAt(chip.left + 3.dp, middle).all { it < 120 }.shouldBeTrue() }

                press(Key.DirectionUp)

                compose.onNodeWithTag("library:order").assertIsFocused()
                withClue(
                    "focused fill ${rgbAt(chip.left + 3.dp, middle)}",
                ) { rgbAt(chip.left + 3.dp, middle).all { it >= 230 }.shouldBeTrue() }
                withClue("glyph is dark") { darkestRed(glyph) shouldBeLessThan 90 }
                withClue("text is dark") {
                    darkestRed(DpRect(glyph.right + 4.dp, chip.top + 6.dp, chip.right - 14.dp, chip.bottom - 6.dp)) shouldBeLessThan
                        90
                }
            }

            test("the status line of the focused card stays on the line of its neighbours") {
                showLibrary(nineApps)
                focusedCard("App 1")

                statusMiddle("App 1", 6.dp, 6.dp) shouldBe (statusMiddle("App 2", 6.dp, 6.dp) plusOrMinus 1f)
            }

            test("the ring of a focused card stays 16 px from the cards beside and below it") {
                showLibrary(nineApps)
                val focused = focusedCard("App 1")
                val beside = card("App 2")
                val below = card("App 7")
                val density = compose.activity.resources.displayMetrics.density
                val pixels = compose.onRoot().captureToImage().toPixelMap()

                fun px(dp: Dp) = dp.value * density
                val midY = px((focused.top + focused.bottom) / 2).toInt()
                val midX = px((focused.left + focused.right) / 2).toInt()
                val across = px(beside.left) - lastWhite(px(focused.right), px(beside.left)) { pixels[it, midY].red }
                val down = px(below.top) - lastWhite(px(focused.bottom), px(below.top)) { pixels[midX, it].red }

                withClue("beside: $across px") { across shouldBeGreaterThanOrEqualTo 16f }
                withClue("below: $down px") { down shouldBeGreaterThanOrEqualTo 16f }
            }

            test(
                "on every frame of a focus move the highlight sits inside the ring of the card that gains focus and the one that loses it",
            ) {
                showLibrary(nineApps)

                assertEveryFrameOfAMoveHasTheHighlightInsideTheRing()
            }

            test("with reduced motion the highlight is inside the ring on every frame too") {
                showLibrary(
                    nineApps,
                    config = TvConfig(appLibrary = true, reducedMotion = true),
                    motion = Motion(reducedMotion = true),
                )

                assertEveryFrameOfAMoveHasTheHighlightInsideTheRing()
            }

            test("the ring of a focused card is as far from it on all four sides") {
                showLibrary(nineApps)
                val grown = grownCard("App 1")
                val density = compose.activity.resources.displayMetrics.density
                val pixels = compose.onRoot().captureToImage().toPixelMap()

                // The walk starts 12 dp outside the card's own edge. Walking down or right, the pixel it stops at lies one further out.
                fun gap(
                    side: Side,
                    grownBy: Dp,
                ): Float {
                    val ring = scanRing(pixels, "App 1", side, strong = RING_CORE)!!
                    val farSide = if (side == Side.Bottom || side == Side.Right) 1 else 0
                    return (scanReach.value - grownBy.value) * density - ring.inner + farSide
                }

                val card = card("App 1")
                val gaps =
                    listOf(
                        Side.Top to gap(Side.Top, card.top - grown.top),
                        Side.Bottom to gap(Side.Bottom, grown.bottom - card.bottom),
                        Side.Left to gap(Side.Left, card.left - grown.left),
                        Side.Right to gap(Side.Right, grown.right - card.right),
                    )
                val wanted = 3f * FOCUSED_SCALE * density
                withClue("gaps in px $gaps, wanted $wanted") { gaps.all { (_, gap) -> abs(gap - wanted) <= 1.5f }.shouldBeTrue() }
                withClue("gaps in px $gaps") { (gaps.maxOf { it.second } - gaps.minOf { it.second }) shouldBeLessThanOrEqualTo 1f }
            }

            test("the ring of a card whose art is coloured takes that colour lifted to a lightness of 065") {
                showLibrary(nineApps, art = LibraryArt(white, Color(0xFFE53935)))

                assertRing(listOf(235, 100, 97), ringAtTop("App 1"))
            }

            test("a ring that blends with its cards colour gets a white stroke between the gap and the ring") {
                showLibrary(nineApps, art = LibraryArt(white, Color(0xFFE53935)))

                val grown = grownCard("App 1")
                val gap = rgbAt((grown.left + grown.right) / 2, grown.top - 2.dp * FOCUSED_SCALE)
                withClue("the gap is $gap") { gap.all { it >= 216 }.shouldBeTrue() }
            }

            test("the ring of a card whose art is near grey is white") {
                showLibrary(nineApps, art = LibraryArt(white, Color(0xFF808080)))

                assertRing(listOf(255, 255, 255), ringAtTop("App 1"))
            }

            test("without app glow the ring is white whatever the art") {
                showLibrary(nineApps, art = LibraryArt(white, Color(0xFFE53935)), config = TvConfig(appLibrary = true, appGlow = false))

                assertRing(listOf(255, 255, 255), ringAtTop("App 1"))
            }

            test("with cinematic white focus the ring is white whatever the art") {
                showLibrary(
                    nineApps,
                    art = LibraryArt(white, Color(0xFFE53935)),
                    config = TvConfig(appLibrary = true, cinematicWhiteFocus = true),
                )

                assertRing(listOf(255, 255, 255), ringAtTop("App 1"))
            }

            test("the hints have no pill round them and each key keeps its fill") {
                showLibrary(nineApps)
                val hint = bounds("library:hint")
                val key = hintPart("OK").getBoundsInRoot()
                val middle = (hint.top + hint.bottom) / 2

                // Where a pill's backing would be: over the key's fill, in the row's top 2 dp.
                val ground = redAt(hint.right - 20.dp, hint.top + 2.dp)
                withClue("over the key: ${redAt((key.left + key.right) / 2, hint.top + 2.dp)}, panel $ground") {
                    abs(redAt((key.left + key.right) / 2, hint.top + 2.dp) - ground) shouldBeLessThanOrEqualTo
                        6
                }
                withClue("key fill ${redAt(key.left - 4.dp, middle)}") {
                    (redAt(key.left - 4.dp, middle) - ground) shouldBeGreaterThanOrEqualTo
                        20
                }
            }

            test("a hints action is plain text at 70 percent of the text colour and 16 dp lies between hints") {
                showLibrary(nineApps)

                val style = hintPart("Add to Home").textStyle()
                val gap = hintPart("Hold OK").getBoundsInRoot().left - 8.dp - hintPart("Add to Home").getBoundsInRoot().right

                style.color.alpha shouldBe (0.70f plusOrMinus 0.005f)
                gap.value shouldBe (16f plusOrMinus 1f)
            }

            test("the order chip has a 1 dp rim of 24 percent white at rest") {
                showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))
                val chip = bounds("library:order")
                val x = (chip.left + chip.right) / 2

                val inside = redAt(x, chip.top + 3.dp)
                val rim = maxOf(redAt(x, chip.top + 0.25.dp), redAt(x, chip.top + 0.75.dp))

                rim.toFloat() shouldBe (Math.round(inside + 0.24f * (255 - inside)).toFloat() plusOrMinus 3f)
            }

            test("the order chip ends with the menu key cap after its label and 4 dp clear of its top and bottom") {
                showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))
                val chip = bounds("library:order")
                val cap = compose.onNodeWithTag("library:order:key", useUnmergedTree = true).getBoundsInRoot()
                val label =
                    compose
                        .onNode(hasText("Usual order") and hasAnyAncestor(hasTestTag("library:order")), useUnmergedTree = true)
                        .getBoundsInRoot()

                withClue("chip ${chip.width}, cap $cap, label $label") {
                    cap.left shouldBeGreaterThanOrEqualTo label.right
                    cap.right shouldBeLessThanOrEqualTo chip.right
                }
                withClue("cap $cap in chip $chip") {
                    (cap.top - chip.top) shouldBeGreaterThanOrEqualTo 4.dp
                    (chip.bottom - cap.bottom) shouldBeGreaterThanOrEqualTo 4.dp
                }
            }

            test("a focused order chip draws its menu key cap text dark") {
                showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))
                val cap = compose.onNodeWithTag("library:order:key", useUnmergedTree = true).getBoundsInRoot()

                press(Key.DirectionUp)

                compose.onNodeWithTag("library:order").assertIsFocused()
                withClue("cap text is dark") { darkestRed(cap) shouldBeLessThan 90 }
            }

            test("a focused order chip keeps its key cap 3 to 1 on the chip with its text 4 5 to 1 on the cap") {
                val (capOnChip, textOnCap) = focusedKeyCapContrast()

                withClue("cap on chip $capOnChip") { capOnChip shouldBeGreaterThanOrEqualTo 3f }
                withClue("text on cap $textOnCap") { textOnCap shouldBeGreaterThanOrEqualTo 4.5f }
            }

            test("the no usage status is set in the hints size at 70 percent of the text colour") {
                showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))
                press(Key.Menu)

                val status = compose.onNodeWithTag("library:status").textStyle()

                status.fontSize shouldBe hintPart("Add to Home").textStyle().fontSize
                status.color.alpha shouldBe (0.70f plusOrMinus 0.005f)
            }
        }
    })

private class LibraryOverlayLookUiFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    var home by mutableStateOf(Color.Black)

    val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }

    /** Nine apps off Home, in this order: App 1 to App 9. */
    val nineApps = (1..9).map { libraryApp("App $it") }

    /** 23 apps off Home, in this order: App 01 to App 23. */
    val twentyThree = (1..23).map { libraryApp(app(it)) }

    fun app(number: Int) = "App %02d".format(number)

    fun showLibrary(
        apps: List<LibraryApp>,
        art: LibraryArt? = null,
        config: TvConfig = TvConfig(appLibrary = true),
        motion: Motion = Motion(),
        focusStyle: FocusStyle = FocusStyle.Glass,
        record: OpenedRecord = OpenedRecord(),
    ) {
        val library = FakeLibrary(apps).apply { this.record = record }
        lateinit var viewModel: MoleculeLibraryViewModel
        compose.setContent {
            TvTheme(motion = motion, focusStyle = focusStyle) {
                val scope = rememberCoroutineScope()
                viewModel =
                    remember {
                        MoleculeLibraryViewModel(
                            library.sources,
                            MutableStateFlow(config),
                            Locale.ENGLISH,
                            scope,
                            RecompositionMode.Immediate,
                            EmptyCoroutineContext,
                        )
                    }
                val state = viewModel.uiState.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(home))
                    OverlayHost(open = { state.value.open }) {
                        LibraryOverlay(
                            state = { state.value },
                            layout = config.libraryLayout(),
                            actions = viewModel.actions,
                            art = { art },
                        )
                    }
                }
            }
        }
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        viewModel.onOpen()
        settle()
    }

    fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    fun card(name: String): DpRect = bounds("card:${key(name)}")

    fun art(name: String): DpRect = compose.onNodeWithTag("card:art:${key(name)}", useUnmergedTree = true).getBoundsInRoot()

    /** The focused card's bounds: its focus growth is drawn, so they are its size before it. */
    fun focusedCard(name: String): DpRect {
        compose.onNodeWithTag("card:${key(name)}").assertIsFocused()
        return card(name)
    }

    /** Where the focused card draws, grown about its centre. */
    fun grownCard(name: String): DpRect {
        val card = focusedCard(name)
        val dx = card.width * (FOCUSED_SCALE - 1f) / 2f
        val dy = card.height * (FOCUSED_SCALE - 1f) / 2f
        return DpRect(card.left - dx, card.top - dy, card.right + dx, card.bottom + dy)
    }

    /** The red channel, 0 to 255, of the screen at ([x], [y]). */
    fun redAt(
        x: Dp,
        y: Dp,
    ): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(pixels[(x.value * density).toInt(), (y.value * density).toInt()].red * 255)
    }

    fun assertNear(
        expected: Dp,
        actual: Dp,
    ) = withClue("expected $expected, got $actual") { abs(actual.value - expected.value) shouldBeLessThanOrEqualTo 1f }

    /** How much of 255 the panel's inside changes when Home behind it goes from black to white. */
    fun bleedThrough(): Int {
        showLibrary(listOf(libraryApp("Alpha"), libraryApp("Beta")))
        // The gap between the title and the count, in the panel's header row.
        val inside = {
            compose
                .onRoot()
                .captureToImage()
                .toPixelMap()[PANEL_X, PANEL_HEADER_Y]
                .red
        }
        val dark = inside()
        home = Color.White
        compose.waitForIdle()
        return Math.round((inside() - dark) * 255)
    }

    /** The red channel, 0 to 255, inside the focused card [name], 5 dp in from its grown left edge, half way down: beside its art and name. */
    fun insideFocusedCard(name: String): Int {
        val card = grownCard(name)
        return redAt(card.left + 5.dp, (card.top + card.bottom) / 2)
    }

    /** Two names too long for a card's two lines; the first sorts first and takes focus. */
    val cutNames =
        listOf("Alta TV con altavoces inteligentes y control por voz", "Altavoces inteligentes con control de la TV por voz")

    /** The pixels of the card [name] as the screen draws them now. */
    fun cardFrame(name: String): List<Color> {
        val pixels = compose.onNodeWithTag("card:${key(name)}").captureToImage().toPixelMap()
        return (0 until pixels.height).flatMap { y -> (0 until pixels.width).map { x -> pixels[x, y] } }
    }

    /** Whether the pixel at ([x], [y]) differs from the panel 40 dp to its left, where a chevron's tip would be. */
    fun cueAt(
        x: Dp,
        y: Dp,
    ): Boolean = abs(redAt(x, y) - redAt(x - 40.dp, y)) > 40

    /** The columns, in dp, of the row at [y] where the screen is lighter than the panel 60 dp to the left of the grid by at least [level]. */
    fun lighterColumns(
        y: Dp,
        level: Int,
    ): ClosedFloatingPointRange<Float> {
        val grid = bounds("library:cards")
        val ground = redAt(grid.left - 8.dp, y)
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val row = (y.value * density).toInt()
        val lit =
            (((grid.left.value + 20f) * density).toInt() until ((grid.right.value - 20f) * density).toInt()).filter {
                pixels[it, row].red * 255 - ground >= level
            }
        return (lit.min() / density)..((lit.max() + 1) / density)
    }

    /** The room between the hint row's key caps and the outer edge of the focused card's ring, as the grid shows it. */
    fun ringRoomUnderKeyCaps(name: String): Dp {
        val ringTop = maxOf(grownCard(name).top - FocusRingReach * FOCUSED_SCALE, bounds("library:cards").top)
        return ringTop - hintPart("OK").getBoundsInRoot().bottom
    }

    /** The dp from the panel's top edge to the title's first row of ink: a pixel of its box that differs from the panel beside it. */
    fun titleInkTop(): Dp {
        val density = compose.activity.resources.displayMetrics.density
        val panelTop = bounds("library:panel").top
        val title = bounds("library:title")
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (title.left.value * density).toInt() until (title.right.value * density).toInt()
        val ground = ((title.left.value - 4f) * density).toInt()
        val ink =
            (((panelTop.value + 3f) * density).toInt() until (title.bottom.value * density).toInt()).first { y ->
                xs.any { x -> abs(pixels[x, y].red - pixels[ground, y].red) * 255 >= TEXT_OVER_GROUND }
            }
        return (ink / density).dp - panelTop
    }

    /** The names of the apps whose cards have focus: one, when the focus is in the grid. */
    fun focusedApps(): List<String> =
        compose.onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config[SemanticsProperties.ContentDescription].single() }

    /** The layout bounds of the text of the card of [name] that holds [text]. */
    fun statusText(
        text: String,
        name: String,
    ): DpRect =
        compose
            .onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("card:${key(name)}")), useUnmergedTree = true)
            .getBoundsInRoot()

    val longNames = listOf("Long enough a name to wrap onto two lines", "Many words in a name that wraps onto two lines")

    /** The layout bottom of the last line of the name on the card of [name], in the root's dp. */
    fun nameBottom(name: String): Dp {
        val node =
            compose.onNode(
                hasText(name.substringBefore(' '), substring = true) and hasAnyAncestor(hasTestTag("card:${key(name)}")),
                useUnmergedTree = true,
            )
        val layout = nameLayout(name)
        return node.getBoundsInRoot().top +
            (layout.getLineBottom(layout.lineCount - 1) / compose.activity.resources.displayMetrics.density).dp
    }

    fun lastLineBottom(name: String): Dp {
        withClue("$name wraps onto two lines") { nameLayout(name).lineCount shouldBe 2 }
        return nameBottom(name)
    }

    /** The top of the highest text under the name on the card of [name]: where its status lines begin. */
    fun statusTop(name: String): Dp {
        val density = compose.activity.resources.displayMetrics.density
        val texts =
            compose
                .onAllNodes(hasAnyAncestor(hasTestTag("card:${key(name)}")) and hasText("", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes()
        return (texts.drop(1).minOf { it.boundsInRoot.top } / density).dp
    }

    fun assertNameClearsStatus() {
        val (focused, idle) = longNames
        compose.onNodeWithTag("card:${key(focused)}").assertIsFocused()
        val statusAt = statusTop(idle)
        withClue("idle: name ends ${lastLineBottom(idle)}, status starts $statusAt") {
            (statusAt - lastLineBottom(idle)) shouldBeGreaterThanOrEqualTo
                (4.dp - 0.05.dp)
        }
        // The focused card's name grows about the card's centre while its status line stays level with its neighbours'.
        val middle = (card(focused).top + card(focused).bottom) / 2
        val grownBottom = middle + (lastLineBottom(focused) - middle) * FOCUSED_SCALE
        withClue("focused: name ends $grownBottom, status starts $statusAt") { (statusAt - grownBottom) shouldBeGreaterThanOrEqualTo 2.dp }
    }

    val describedName = "Lista de títulos grabados con altavoces"

    /** The room over the art and the room under the last line of the name, in the card of [name]. */
    fun roomAboveAndBelow(name: String): Pair<Dp, Dp> = (art(name).top - card(name).top) to (card(name).bottom - nameBottom(name))

    /** The brightest red, 0 to 255, in the text line of [area] and the red of the card beside it. */
    fun lineBrightness(area: DpRect): Pair<Int, Int> {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (area.left.value * density).toInt() until (area.right.value * density).toInt()
        val ys = (area.top.value * density).toInt() until (area.bottom.value * density).toInt()
        val peak = ys.maxOf { y -> xs.maxOf { x -> pixels[x, y].red } }
        return Math.round(peak * 255) to Math.round(pixels[xs.first, ys.first].red * 255)
    }

    /** The colour, 0 to 255 a channel, of the screen at ([x], [y]). */
    fun rgbAt(
        x: Dp,
        y: Dp,
    ): List<Int> {
        val density = compose.activity.resources.displayMetrics.density
        val pixel = compose.onRoot().captureToImage().toPixelMap()[(x.value * density).toInt(), (y.value * density).toInt()]
        return listOf(pixel.red, pixel.green, pixel.blue).map { Math.round(it * 255) }
    }

    /** The first and last column, in dp, where [area] holds a pixel lighter than its top left one by [TEXT_OVER_GROUND]; null for none. */
    fun lightSpan(area: DpRect): ClosedFloatingPointRange<Float>? {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (area.left.value * density).toInt() until (area.right.value * density).toInt()
        val ys = (area.top.value * density).toInt() until (area.bottom.value * density).toInt()
        val ground = pixels[xs.first, ys.first].red
        val lit = xs.filter { x -> ys.any { y -> (pixels[x, y].red - ground) * 255 > TEXT_OVER_GROUND } }
        return if (lit.isEmpty()) null else (lit.first() / density)..((lit.last() + 1) / density)
    }

    val red =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.Red })
        }

    val chooser = TvConfig(appLibrary = true, appLibrarySelection = true)

    /** The chooser lists by label: the focus takes Aaa, on Home, and Bbb stays idle on Home. Ccc is the idle app off Home. */
    fun showChooser() =
        showLibrary(
            listOf(libraryApp("Aaa", onHome = true), libraryApp("Bbb", onHome = true), libraryApp("Ccc"), libraryApp("Ddd")),
            art = LibraryArt(red, Color.Red),
            config = chooser,
        )

    /** The pixels of the screen, dp by dp, in [area] that are white to within 0.12. */
    fun whitePixels(area: DpRect): Int {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (area.left.value * density).toInt() until (area.right.value * density).toInt()
        val ys = (area.top.value * density).toInt() until (area.bottom.value * density).toInt()
        return xs.sumOf { x ->
            ys.count { y ->
                val pixel = pixels[x, y]
                listOf(pixel.red, pixel.green, pixel.blue).all { it >= 0.88f }
            }
        }
    }

    /** The red channel of the pixel [across] and [down] (each 0 to 1) of the box tagged [tag] on the card of [name]. */
    fun redInMark(
        tag: String,
        name: String,
        across: Float,
        down: Float,
    ): Int {
        val mark = compose.onNodeWithTag("$tag:${key(name)}", useUnmergedTree = true).getBoundsInRoot()
        return redAt(mark.left + mark.width * across, mark.top + mark.height * down)
    }

    fun centre(box: DpRect): Pair<Dp, Dp> = (box.left + box.right) / 2 to (box.top + box.bottom) / 2

    /** The red in the middle of the art of [name], and the red of its card 2 dp left of the art drawn [scale] times about the card's centre. */
    fun artOverCard(
        name: String,
        scale: Float = 1f,
    ): Pair<Int, Int> {
        val art = art(name)
        val (x, y) = centre(art)
        val cardX = centre(card(name)).first
        return redAt(x, y) to redAt(cardX - (cardX - art.left) * scale - 2.dp, y)
    }

    /** How much lighter than its ground the name of [name] peaks, in red levels. */
    fun nameContrast(name: String): Int {
        val box =
            compose
                .onNode(hasText(name) and hasAnyAncestor(hasTestTag("card:${key(name)}")), useUnmergedTree = true)
                .getBoundsInRoot()
        val (peak, ground) = lineBrightness(box)
        return peak - ground
    }

    /** The red in the middle of Ccc's focused card's art before OK, then on each of the 16 frames after it, in the chooser of [config]. */
    fun artFramesOfAddingCcc(config: TvConfig): Pair<Int, List<Int>> {
        showLibrary(
            listOf(libraryApp("Aaa", onHome = true), libraryApp("Bbb", onHome = true), libraryApp("Ccc"), libraryApp("Ddd")),
            art = LibraryArt(red, Color.Red),
            config = config,
        )
        press(Key.DirectionRight, times = 2)
        compose.onNodeWithTag("card:${key("Ccc")}").assertIsFocused()
        val (x, y) = centre(art("Ccc"))
        val before = redAt(x, y)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        val reds =
            List(16) {
                compose.mainClock.advanceTimeByFrame()
                redAt(x, y)
            }
        compose.mainClock.autoAdvance = true
        settle()
        return before to reds
    }

    fun assertArtJumps(config: TvConfig) {
        val (before, reds) = artFramesOfAddingCcc(config)

        withClue("from $before: $reds") {
            reds.all { it <= before + 3 || it == 255 }.shouldBeTrue()
            reds.last() shouldBe 255
        }
    }

    /** The widest row, in dp, of pixels with red above 0.45 and green under 0.3 (the red art) or red above 0.45 alone ([white]) in [area]. */
    fun span(
        area: DpRect,
        white: Boolean,
    ): Float {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (area.left.value * density).toInt() until (area.right.value * density).toInt()
        val ys = (area.top.value * density).toInt() until (area.bottom.value * density).toInt()
        val widest =
            ys.maxOf { y ->
                val lit = xs.filter { x -> pixels[x, y].red > 0.45f && (white || pixels[x, y].green < 0.3f) }
                if (lit.isEmpty()) 0 else lit.last() - lit.first() + 1
            }
        return widest / density
    }

    fun around(
        tag: String,
        by: Dp,
    ): DpRect {
        val box = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
        return DpRect(box.left - by, box.top - by, box.right + by, box.bottom + by)
    }

    /** What OK does to Ccc in the chooser, frame by frame: the widest the new check gets, the widest and narrowest its art gets, and the check once settled. */
    fun pulseFramesOfAddingCcc(config: TvConfig): Triple<Float, ClosedFloatingPointRange<Float>, Float> {
        showLibrary(
            listOf(libraryApp("Aaa", onHome = true), libraryApp("Bbb", onHome = true), libraryApp("Ccc"), libraryApp("Ddd")),
            art = LibraryArt(red, Color.Red),
            config = config,
        )
        press(Key.DirectionRight, times = 2)
        compose.onNodeWithTag("card:${key("Ccc")}").assertIsFocused()
        val checks = mutableListOf<Float>()
        val arts = mutableListOf<Float>()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        repeat(14) {
            compose.mainClock.advanceTimeByFrame()
            if (compose.onAllNodesWithTag("card:check:${key("Ccc")}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                checks += span(around("card:check:${key("Ccc")}", 4.dp), white = true)
                arts += span(art("Ccc"), white = false)
            }
        }
        compose.mainClock.autoAdvance = true
        settle()
        return Triple(checks.max(), arts.min()..arts.max(), span(around("card:check:${key("Ccc")}", 4.dp), white = true))
    }

    /** The darkest red, 0 to 255, in [area]. */
    fun darkestRed(area: DpRect): Int {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (area.left.value * density).toInt() until (area.right.value * density).toInt()
        val ys = (area.top.value * density).toInt() until (area.bottom.value * density).toInt()
        return Math.round(ys.minOf { y -> xs.minOf { x -> pixels[x, y].red } } * 255)
    }

    /** The layout top of the first line of the name on the card of [name], in the root's dp. */
    fun nameTop(name: String): Dp =
        compose
            .onNode(
                hasText(name.substringBefore(' '), substring = true) and hasAnyAncestor(hasTestTag("card:${key(name)}")),
                useUnmergedTree = true,
            ).getBoundsInRoot()
            .top + (nameLayout(name).getLineTop(0) / compose.activity.resources.displayMetrics.density).dp

    /** The layout bounds of the status line of the card of [name]. */
    fun status(name: String): DpRect =
        compose
            .onNode(hasText("Add to Home") and hasAnyAncestor(hasTestTag("card:${key(name)}")), useUnmergedTree = true)
            .getBoundsInRoot()

    /** The middle row, in pixels, of what is drawn like text in the card of [name] from [above] to [below] its status line. */
    fun statusMiddle(
        name: String,
        above: Dp,
        below: Dp,
    ): Float {
        val card = card(name)
        val line = status(name)
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val left = ((card.left + 6.dp).value * density).toInt()
        val right = ((card.right - 6.dp).value * density).toInt()
        val top = ((line.top - above).value * density).toInt()
        val bottom = ((line.bottom + below).value * density).toInt()
        val ground = pixels[left, (top + bottom) / 2].red
        val rows = (top until bottom).filter { y -> (left until right).any { x -> (pixels[x, y].red - ground) * 255 > TEXT_OVER_GROUND } }
        withClue("no text in $card between $top and $bottom") { rows.shouldNotBeEmpty() }
        return (rows.first() + rows.last()) / 2f
    }

    /** The last index from [from] up to [to] whose [pixel] is as white as the ring; [from] when none is. */
    fun lastWhite(
        from: Float,
        to: Float,
        pixel: (Int) -> Float,
    ): Float {
        var edge = from
        for (i in from.toInt() until to.toInt()) if (pixel(i) * 255 >= WHITE) edge = i.toFloat()
        return edge
    }

    /** How far the ring of [FOCUSED_SCALE] reaches from the card, in dp, with room to spare. */
    val scanReach = 12.dp

    /**
     * Walks in from [scanReach] outside the middle of [side] of the card [name] towards it, and reads the ring as the run of
     * pixels that differ by at least [strong] from the same walk beside an idle card ("App 5"). Null while there is no such run.
     */
    fun scanRing(
        pixels: PixelMap,
        name: String,
        side: Side,
        strong: Int = RING_STRONG,
    ): RingScan? {
        val density = compose.activity.resources.displayMetrics.density
        val card = card(name)
        val idle = card("App 5")

        fun px(dp: Dp) = (dp.value * density).toInt()
        val dx = px(idle.left) - px(card.left)
        val dy = px(idle.top) - px(card.top)
        val (x0, y0, stepX, stepY) =
            when (side) {
                Side.Top -> listOf(px((card.left + card.right) / 2), px(card.top) - px(scanReach), 0, 1)
                Side.Bottom -> listOf(px((card.left + card.right) / 2), px(card.bottom) + px(scanReach), 0, -1)
                Side.Left -> listOf(px(card.left) - px(scanReach), px((card.top + card.bottom) / 2), 1, 0)
                Side.Right -> listOf(px(card.right) + px(scanReach), px((card.top + card.bottom) / 2), -1, 0)
            }

        fun differs(step: Int): Int {
            val at = pixels[x0 + stepX * step, y0 + stepY * step]
            val ground = pixels[x0 + stepX * step + dx, y0 + stepY * step + dy]
            return Math.round((abs(at.red - ground.red) + abs(at.green - ground.green) + abs(at.blue - ground.blue)) * 255)
        }
        val steps = px(scanReach)
        val outer = (0 until steps).firstOrNull { differs(it) >= strong } ?: return null
        val inner = (outer until steps).firstOrNull { differs(it) < strong } ?: return null
        return RingScan(outer = outer, inner = inner, inside = differs(inner + 3))
    }

    /** Moves focus from "App 1" to "App 2" frame by frame and fails on the first frame where a ring has no highlight inside it. */
    fun assertEveryFrameOfAMoveHasTheHighlightInsideTheRing() {
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        var seen = 0
        repeat(FRAMES) { frame ->
            compose.mainClock.advanceTimeByFrame()
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            for (name in listOf("App 1", "App 2")) {
                for (side in listOf(Side.Top, Side.Bottom)) {
                    val ring = scanRing(pixels, name, side) ?: continue
                    seen++
                    withClue("frame $frame, $name, $side: $ring") { ring.inside shouldBeGreaterThanOrEqualTo HALO_MIN }
                }
            }
        }
        withClue("rings seen on $seen scans") { seen shouldBeGreaterThanOrEqualTo FRAMES }
    }

    fun ringAtTop(name: String): List<Int> {
        val grown = grownCard(name)
        return rgbAt((grown.left + grown.right) / 2, grown.top - 4.5.dp * FOCUSED_SCALE)
    }

    fun assertRing(
        expected: List<Int>,
        actual: List<Int>,
    ) = withClue("expected $expected, got $actual") { expected.zip(actual).all { (want, got) -> abs(want - got) <= 4 }.shouldBeTrue() }

    /** The laid-out name on the card of [name]: the text of its first word is how the card is found. */
    fun nameLayout(name: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose
            .onNode(
                hasText(name.substringBefore(' ').take(8), substring = true) and hasAnyAncestor(hasTestTag("card:${key(name)}")),
                useUnmergedTree = true,
            ).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action!!(results)
        return results.single()
    }

    fun linesEndingInsideAWord(layout: TextLayoutResult): List<Int> {
        val text = layout.layoutInput.text.text
        return (0 until layout.lineCount - 1).filter { line ->
            val end = layout.getLineEnd(line)
            text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
        }
    }

    fun TextLayoutResult.lineWidth(line: Int) = getLineRight(line) - getLineLeft(line)

    /** [layout]'s text laid out at its style's own size in the same box, as without autoSize. */
    fun unshrunk(layout: TextLayoutResult): TextLayoutResult {
        val input = layout.layoutInput
        return TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
            .measure(input.text, input.style, input.overflow, input.softWrap, input.maxLines, constraints = input.constraints)
    }

    fun hintPart(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("library:hint")), useUnmergedTree = true)

    fun SemanticsNodeInteraction.textStyle(): TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single().layoutInput.style
    }

    fun ratio(
        a: Color,
        b: Color,
    ): Float = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    /** The contrast of the focused order chip's key cap against the chip fill, then of the cap's text against the cap. */
    fun focusedKeyCapContrast(): Pair<Float, Float> {
        showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))
        val chip = bounds("library:order")
        val cap = compose.onNodeWithTag("library:order:key", useUnmergedTree = true).getBoundsInRoot()
        press(Key.DirectionUp)
        compose.onNodeWithTag("library:order").assertIsFocused()
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val middle = ((cap.top + cap.bottom) / 2).value * density
        val fill = pixels[((chip.left.value + 3f) * density).toInt(), middle.toInt()]
        val keyCap = pixels[((cap.left.value + 1.5f) * density).toInt(), middle.toInt()]
        val xs = ((cap.left.value + 6f) * density).toInt() until ((cap.right.value - 6f) * density).toInt()
        val ys = ((cap.top.value + 2f) * density).toInt() until ((cap.bottom.value - 2f) * density).toInt()
        val text = ys.flatMap { y -> xs.map { x -> pixels[x, y] } }.maxBy { abs(it.luminance() - keyCap.luminance()) }
        return ratio(keyCap, fill) to ratio(text, keyCap)
    }
}

private const val PANEL_X = 960
private const val PANEL_HEADER_Y = 103
private const val MAX_BLEED = 10

/** The red of an idle card on the light surfaces the tests run in. */
private const val CARD_FILL_RED = 233
private const val WHITE = 230
private const val FRAMES = 24
private const val RING_STRONG = 200
private const val RING_CORE = 300
private const val HALO_MIN = 4
private const val FOCUSED_SCALE = 1.08f
private const val WIDTH_ROUNDING = 1.5f
private const val TEXT_OVER_GROUND = 60
private val ELLIPSIS = Char(0x2026).toString()
private val NAME_WORDS = "Control de TV con altavoces inteligentes para el salon principal de la casa de Maria Luisa".split(" ")

/** Where the ring is along a walk in: [outer] and [inner] steps in, and how much the pixel [inside] it differs from the ground. */
private data class RingScan(
    val outer: Int,
    val inner: Int,
    val inside: Int,
)

private enum class Side { Top, Bottom, Left, Right }
