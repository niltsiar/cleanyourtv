package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
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
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The padding between the dock panel's walls and its tiles, measured on tiles that do not have focus (a focused one is drawn
 * larger). The grid is 880 dp wide, so a tile of the 6-column dock is 126.67 dp wide and one of the 4-column dock 202 dp.
 */
val DockPaddingUiTest by testSuite {
    robolectricTestSuite<DockPaddingUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
    robolectricTestSuite<DockPaddingUiTallTestContent>(
        "sdk 31, 960x1080 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = TALL
            },
    )
}

class DockPaddingUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { DockPaddingUiTestFixture() } asContextForEach {
            test("a dock pads its sides by the rings room and its top and bottom by a gap unless the theme asks for even padding") {
                showHome(SixColumns)

                val room = ringRoom(126.67f)
                assertSides(Sides(room, 24f, room, 24f), padding(last = 6))
            }

            test("with focus overshoot a dock pads its sides for the ring at the peak of the pop") {
                showHome(SixColumns, motion = Motion(focusOvershoot = true))

                val room = 126.67f * 0.072f + 9f * 1.144f + 12f
                assertSides(Sides(room, 24f, room, 24f), padding(last = 6))
            }

            test("with focus overshoot and names the ring at the peak keeps 4 dp from the rim and the pill with 2 to 7 columns") {
                showHome(TvConfig(columns = 2, alwaysShowNames = true), motion = Motion(focusOvershoot = true))
                for (columns in 2..7) {
                    switchTo(TvConfig(columns = columns, alwaysShowNames = true))
                    val (ring, over, toPill) = namedRoom()

                    withClue("$columns columns: $over dp over a ring of ${ring.over}") {
                        over shouldBeGreaterThanOrEqualTo
                            ring.over + 4f - HALF_PIXEL
                    }
                    withClue("$columns columns: $toPill dp to the pill under a ring of ${ring.under}") {
                        toPill shouldBeGreaterThanOrEqualTo
                            ring.under + 4f - HALF_PIXEL
                    }
                }
            }

            test("without focus overshoot a named dock keeps a gap over its tiles and 14 dp and their growth to the pill") {
                showHome(TvConfig(columns = 2, alwaysShowNames = true))
                for (columns in 2..7) {
                    switchTo(TvConfig(columns = columns, alwaysShowNames = true))
                    val (ring, over, toPill) = namedRoom()

                    withClue("$columns columns") { over shouldBe (24f plusOrMinus 0.6f) }
                    withClue("$columns columns") {
                        toPill shouldBe
                            (14f + 0.08f * (ring.height - minOf(ring.height, 128.5f)) plusOrMinus 0.6f)
                    }
                }
            }

            test("with focus overshoot a 2 column dock without names pads its top and bottom for the ring at the peak") {
                showHome(TvConfig(columns = 2), motion = Motion(focusOvershoot = true))

                val sides = padding(last = 2)
                val tile = dockTile(1)
                val ring = peakRing((tile.bottom - tile.top).value, names = false)
                withClue("top ${sides.top} over a ring of ${ring.over}") {
                    sides.top shouldBeGreaterThanOrEqualTo
                        ring.over + 4f - HALF_PIXEL
                }
                withClue("bottom ${sides.bottom} under a ring of ${ring.under}") {
                    sides.bottom shouldBeGreaterThanOrEqualTo
                        ring.under + 4f - HALF_PIXEL
                }
            }

            test("without focus overshoot a 2 column dock without names pads its top and bottom by a gap") {
                showHome(TvConfig(columns = 2))

                val sides = padding(last = 2)
                sides.top shouldBe (24f plusOrMinus 0.6f)
                sides.bottom shouldBe (24f plusOrMinus 0.6f)
            }

            test("even padding gives the top and bottom the sides padding when that is more than a gap") {
                showHome(SixColumns.copy(dockEvenPadding = true))

                val room = ringRoom(126.67f)
                assertSides(Sides(room, room, room, room), padding(last = 6))
            }

            test("even padding keeps the sides at the rings room in a four column dock") {
                showHome(TvConfig(columns = 4, dockEvenPadding = true))

                val room = ringRoom(202f)
                assertSides(Sides(room, room, room, room), padding(last = 4))
            }

            test("even padding of a dock with fewer tiles than the row holds is the shrunk docks side all round") {
                showHome(SixColumns.copy(dockEvenPadding = true, fillDock = false), docked = 3)

                assertSides(Sides(28f, 28f, 28f, 28f), padding(last = 3))
            }

            test("even padding does not move the panels top edge when focus moves") {
                showHome(SixColumns)
                val rest = dock().top

                look = look.copy(evenPadding = true)
                settle()
                press(Key.DirectionRight)
                press(Key.DirectionLeft)

                dock().top.value shouldBe (rest.value plusOrMinus 0.6f)
                (dockTile(2).top - dock().top).value shouldBe (ringRoom(126.67f) plusOrMinus 0.6f)
            }

            test("a dock tiles menu ends the same distance above the panel with even padding") {
                showHome(SixColumns)
                val plain = menuClearance()

                look = look.copy(evenPadding = true)
                settle()

                menuClearance() shouldBe (plain plusOrMinus 0.6f)
            }
        }
    })

class DockPaddingUiTallTestContent :
    RobolectricTestSuiteContent({
        testFixture { DockPaddingUiTestFixture() } asContextForEach {
            test("with focus overshoot a named docks room under its pills is a gap or one and a half times the pills gap") {
                assertGapOrOneAndAHalfUnderPills(Motion(focusOvershoot = true))
            }

            test("without focus overshoot a named docks room under its pills is a gap or one and a half times the pills gap") {
                assertGapOrOneAndAHalfUnderPills(Motion())
            }

            test("with focus overshoot a first row tiles ring at the peak keeps 4 dp under the dock panel") {
                showHome(rowOneConfigs.first(), motion = Motion(focusOvershoot = true))
                for (config in rowOneConfigs) {
                    switchTo(config)
                    val tile = rowOneTile(config.columns)
                    val ring = peakRing((tile.bottom - tile.top).value, config.alwaysShowNames)
                    val room = (tile.top - dock().bottom).value

                    withClue("$config: $room dp under the panel over a ring of ${ring.over}") {
                        room shouldBeGreaterThanOrEqualTo
                            ring.over + 4f - HALF_PIXEL
                    }
                }
            }

            test("without focus overshoot the first row is a gap under the dock panel") {
                showHome(rowOneConfigs.first())
                for (config in rowOneConfigs) {
                    switchTo(config)

                    withClue("$config") { (rowOneTile(config.columns).top - dock().bottom).value shouldBe (24f plusOrMinus 0.6f) }
                }
            }

            test("with focus overshoot a lone grid tiles ring at the peak keeps 4 dp under the dock panel") {
                val first = rowOneConfigs.first()
                showHome(first, motion = Motion(focusOvershoot = true), list = apps.take(first.columns + 1))
                for (config in rowOneConfigs) {
                    appList.value = AppList(apps.take(config.columns + 1))
                    switchTo(config)
                    val tile = rowOneTile(config.columns)
                    val ring = peakRing((tile.bottom - tile.top).value, config.alwaysShowNames)
                    val room = (tile.top - dock().bottom).value

                    withClue("$config: $room dp under the panel over a ring of ${ring.over}") {
                        room shouldBeGreaterThanOrEqualTo
                            ring.over + 4f - HALF_PIXEL
                    }
                }
            }

            test("with focus overshoot and an empty grid the library pill is as far under the dock panel as without") {
                showHome(pillConfigs.first(), list = emptyGrid)
                for (config in pillConfigs) {
                    switchTo(config)
                    compose.onNode(hasTestTag("tile:pkg.2") and hasAnyAncestor(hasTestTag("dock"))).assertExists()
                    val plain = pillUnderPanel()

                    motion = Motion(focusOvershoot = true)
                    settle()

                    withClue("$config") { pillUnderPanel() shouldBe (plain plusOrMinus 0.6f) }
                    motion = Motion()
                    settle()
                }
            }
        }
    })

private data class Sides(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** A dock tile's height, and how far its ring reaches over and under it at the peak of a pop, in dp. */
private data class PeakRing(
    val height: Float,
    val over: Float,
    val under: Float,
)

private class DockPaddingUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    // Enough apps below the dock for the grid to scroll, which is what a pivot that is off would do.
    val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    var look by mutableStateOf(DockLook())

    var shown by mutableStateOf(SixColumns)

    var motion by mutableStateOf(Motion())

    val appList = MutableStateFlow(AppList())

    fun showHome(
        config: TvConfig = SixColumns,
        docked: Int = 0,
        motion: Motion = Motion(),
        list: List<AppEntry> = apps,
    ) {
        appList.value = AppList(list, docked)
        val sources = sources(apps = appList)
        look = config.dockLook()
        shown = config
        this.motion = motion
        compose.setContent {
            TvTheme(motion = this.motion) {
                key(shown) {
                    val viewModel = rememberHomeViewModel(sources, shown, mutableListOf())
                    val state = viewModel.uiState.collectAsState()
                    HomeScreen(
                        state = { state.value },
                        layout = shown.homeLayout(),
                        onFocus = viewModel::onFocus,
                        onClick = {},
                        topBar = shown.topBarLayout(),
                        dockLook = look,
                        actions = viewModel.actions,
                    )
                }
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

    /** Shows Home again with [config], in the theme [showHome] set. */
    fun switchTo(config: TvConfig) {
        look = config.dockLook()
        shown = config
        settle()
        compose.onNodeWithTag("tile:pkg.1").performSemanticsAction(SemanticsActions.RequestFocus)
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

    fun dock(): DpRect = compose.onNodeWithTag("dock").getBoundsInRoot()

    fun dockTile(n: Int): DpRect = compose.onNode(hasTestTag("tile:pkg.$n") and hasAnyAncestor(hasTestTag("dock"))).getBoundsInRoot()

    /** The room between the panel's walls and the tiles, with focus on the second tile so the first and the last are at rest. */
    fun padding(last: Int): Sides {
        press(Key.DirectionRight)
        val panel = dock()
        val first = dockTile(1)
        val end = dockTile(last)
        return Sides(
            left = (first.left - panel.left).value,
            top = (first.top - panel.top).value,
            right = (panel.right - end.right).value,
            bottom = (panel.bottom - first.bottom).value,
        )
    }

    fun assertSides(
        expected: Sides,
        actual: Sides,
    ) {
        withClue("left") { actual.left shouldBe (expected.left plusOrMinus 0.6f) }
        withClue("top") { actual.top shouldBe (expected.top plusOrMinus 0.6f) }
        withClue("right") { actual.right shouldBe (expected.right plusOrMinus 0.6f) }
        withClue("bottom") { actual.bottom shouldBe (expected.bottom plusOrMinus 0.6f) }
    }

    /** A focused end tile's ring, with its 12 dp of clearance: the tile's growth, the ring's 9 dp scaled, and the clearance. */
    fun ringRoom(tileWidth: Float) = tileWidth * 0.04f + 9f * 1.08f + 12f

    /** The ring of a dock tile [height] dp high at the 1.144 peak of a pop: with names it grows about its bottom edge, or 128.5 dp down. */
    fun peakRing(
        height: Float,
        names: Boolean,
    ): PeakRing {
        val pivot = if (names) minOf(height, 128.5f) else height / 2f
        return PeakRing(height, pivot * 0.144f + 9f * 1.144f, (height - pivot) * 0.144f + 9f * 1.144f)
    }

    /** With focus on the second tile, the first tile's ring at the peak and its room over it and under it to its name's pill. */
    fun namedRoom(): Triple<PeakRing, Float, Float> {
        press(Key.DirectionRight)
        val tile = dockTile(1)
        val pill = compose.onNodeWithTag("name:pkg.1").getBoundsInRoot()
        val ring = peakRing((tile.bottom - tile.top).value, names = true)
        return Triple(ring, (tile.top - dock().top).value, (pill.top - tile.bottom).value - 2f)
    }

    /** The room from the bottom of the first dock tile's name pill to the panel's rim, and the gap from the tile to the pill. */
    fun underPills(): Pair<Float, Float> {
        val tile = dockTile(1)
        val name = compose.onNode(hasTestTag("name:pkg.1") and hasAnyAncestor(hasTestTag("dock"))).getBoundsInRoot()
        return (dock().bottom - name.bottom).value - 2f to (name.top - tile.bottom).value - 2f
    }

    fun assertGapOrOneAndAHalfUnderPills(motion: Motion) {
        showHome(TvConfig(columns = 2, alwaysShowNames = true), motion = motion)
        for (columns in 2..7) {
            switchTo(TvConfig(columns = columns, alwaysShowNames = true))
            val (under, gap) = underPills()

            withClue("$columns columns") { under shouldBe (maxOf(24f, 1.5f * gap) plusOrMinus 0.6f) }
        }
    }

    /** The first grid row's tile in a dock of [columns] tiles. */
    fun rowOneTile(columns: Int): DpRect = compose.onNodeWithTag("tile:pkg.${columns + 1}").getBoundsInRoot()

    val rowOneConfigs = (2..4).map { TvConfig(columns = it, alwaysShowNames = true) } + TvConfig(columns = 2)

    /** Configs with the App library pill after the grid, which [emptyGrid] leaves with no tile: the dock takes every app. */
    val pillConfigs = rowOneConfigs.map { it.copy(appLibrary = true) }

    val emptyGrid = apps.take(2)

    fun pillUnderPanel(): Float = (compose.onNodeWithTag("library:pill").getBoundsInRoot().top - dock().bottom).value

    fun menuClearance(): Float {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
        val clearance = (dock().top - compose.onNodeWithTag("menu").getBoundsInRoot().bottom).value
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        return clearance
    }
}

/** The layout places in whole pixels; at xhdpi rounding moves an edge by up to half of one, in dp. */
private const val HALF_PIXEL = 0.25f

/** A window tall enough for a dock of two named tiles and the first grid row under it. */
private const val TALL = "w960dp-h1080dp-xhdpi"
