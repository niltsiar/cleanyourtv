package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import java.time.ZoneId
import kotlin.math.abs

/** The band over the grid (title, meta line, key chips), the dock under it and the menus that open over both. */
val HeroBandUiTest by testSuite {
    robolectricTestSuite<HeroBandUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HeroBandUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HeroBandUiTestFixture() } asContextForEach {
            test("the title baseline and the meta slot stand 104 dp above where the dock apps do in grid focus") {
                showHome()
                val dockTitle = bounds("shelf:name")
                val dockMeta = bounds("shelf:meta")

                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                compose.mainClock.advanceTimeBy(1_000)

                // The group rises 120 dp; the title and its line rise 16 dp less, to stand 36 dp under the top bar.
                assertNear(dockTitle.bottom - 104.dp, bounds("shelf:title").bottom)
                assertNear(dockMeta.top - 104.dp, bounds("shelf:title-meta").top)
            }

            test("a focused grid tile says when home last opened it where the dock does less the title drop") {
                val opened =
                    Evening
                        .minusHours(2)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                showHome(lastOpened = mapOf("pkg.7" to opened))
                val dockMeta = bounds("shelf:meta")

                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                compose.mainClock.advanceTimeBy(1_000)

                withClue(textOf("shelf:title-meta")) { textOf("shelf:title-meta").startsWith("Last opened ").shouldBeTrue() }
                assertNear(dockMeta.top - 104.dp, bounds("shelf:title-meta").top)
            }

            test("an app never opened says so under its name in the dock and over the grid") {
                showHome()
                textOf("shelf:meta") shouldBe "Never opened"

                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                compose.mainClock.advanceTimeBy(1_000)

                textOf("shelf:title-meta") shouldBe "Never opened"
            }

            test("an app opened before says when and not never opened") {
                showHome(
                    lastOpened =
                        mapOf(
                            "pkg.1" to
                                Evening
                                    .minusHours(2)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli(),
                        ),
                )

                withClue(textOf("shelf:meta")) { textOf("shelf:meta").startsWith("Last opened ").shouldBeTrue() }
            }

            test("the band text ends 28 dp above the dock") {
                showHome()
                // The page dots: 16 dp under the meta slot, 8 dp tall.
                val dotsBottom = bounds("shelf:meta").bottom + 24.dp

                assertNear(dotsBottom + 28.dp, bounds("dock").top)
            }

            test("focus on the library pill names the library and counts its apps") {
                showHome(libraryCount = flowOf(23))
                press(Key.DirectionDown)
                press(Key.DirectionDown)
                compose.onNodeWithTag("library:pill").assertIsFocused()
                compose.mainClock.advanceTimeBy(1_000)

                textOf("shelf:title") shouldBe "App library"
                textOf("shelf:title-meta") shouldBe "23 apps"
            }

            test("reorder mode names the moved app in the band and shows the key chips above the dock") {
                showHome()
                press(Key.Menu)
                press(Key.DirectionDown)
                compose.onNodeWithTag("menu:Move").assertIsFocused()
                press(Key.DirectionCenter)
                compose.mainClock.advanceTimeBy(1_000)

                textOf("shelf:title") shouldBe "App 1"
                // Move picks the tile up, so the legend is the held step's: one chip says OK and Back both drop it.
                for (chip in listOf("OK", "Arrows", "move", "Back")) {
                    compose.onNode(hasText(chip) and hasAnyAncestor(hasTestTag("reorder:hint"))).assertExists()
                }
                compose.onAllNodes(hasText("drop") and hasAnyAncestor(hasTestTag("reorder:hint"))).assertCountEquals(1)
                bounds("reorder:hint").bottom shouldBeLessThanOrEqualTo bounds("dock").top
            }

            test("the dock rests at one height whether focus is on it on the top bar or back from the grid") {
                showHome()
                val resting = bounds("dock").top

                press(Key.DirectionUp)
                compose.mainClock.advanceTimeBy(1_000)
                assertNear(resting, bounds("dock").top)

                press(Key.DirectionDown)
                press(Key.DirectionDown)
                press(Key.DirectionUp)
                compose.mainClock.advanceTimeBy(1_000)
                dockTile(1).assertIsFocused()
                assertNear(resting, bounds("dock").top)
            }

            test("the dock does not move when its first tile takes focus on start") {
                compose.mainClock.autoAdvance = false
                val sources = sources()
                compose.runOnUiThread {
                    compose.activity.window.decorView
                        .requestFocusFromTouch()
                }
                compose.setContent {
                    TvTheme {
                        val viewModel = rememberHomeViewModel(sources, cinematic, mutableListOf())
                        val state = viewModel.uiState.collectAsState()
                        HomeScreen(
                            state = { state.value },
                            layout = cinematic.homeLayout(),
                            onFocus = viewModel::onFocus,
                            onClick = {},
                            shelfLayout = cinematic.shelfLayout().copy(scrimAlpha = 0f),
                            actions = viewModel.actions,
                        )
                    }
                }
                compose.mainClock.advanceTimeByFrame()
                val first = bounds("dock").top

                compose.mainClock.advanceTimeBy(2_000)
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                assertNear(first, bounds("dock").top)
            }

            test("dock tiles stand within 3 dp of the grid columns and up to 3 dp narrower") {
                showHome(apps = (1..12).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") })
                // On the top bar no tile is focused, so none is drawn grown.
                press(Key.DirectionUp)

                for (column in 1..6) {
                    val dock = dockTile(column).getBoundsInRoot()
                    val grid = bounds("tile:pkg.${column + 6}")
                    val dockWidth = (dock.right - dock.left).value
                    val gridWidth = (grid.right - grid.left).value
                    withClue("column $column: dock tile ${dock.left}..${dock.right}, grid tile ${grid.left}..${grid.right}") {
                        (
                            abs((dock.left.value + dock.right.value) / 2f - (grid.left.value + grid.right.value) / 2f) <=
                                3f &&
                                dockWidth in gridWidth - 3f..gridWidth
                        ).shouldBeTrue()
                    }
                }
            }

            test("the four row grid tile menu opens beside its tile under the band and 60 dp above the bottom") {
                showHome()
                press(Key.DirectionDown)
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.8").assertIsFocused()
                val tile = bounds("tile:pkg.8")
                holdOk()
                for (row in listOf("AddToDock", "Move", "RemoveFromHome", "AppInfo")) compose.onNodeWithTag("menu:$row").assertExists()

                val menu = bounds("menu")
                assertNear(tile.right + 24.dp, menu.left)
                withClue("menu top ${menu.top} under the band") { menu.top shouldBeGreaterThanOrEqualTo bounds("dock").top - 0.5.dp }
                menu.bottom shouldBeLessThanOrEqualTo compose.onRoot().getBoundsInRoot().bottom - 60.dp + 0.5.dp
            }

            test("the four row dock tile menu with its header fits above its tile under the top bar") {
                showHome()
                press(Key.DirectionRight)
                val tile = dockTile(2).getBoundsInRoot()
                holdOk()
                for (row in listOf("ReplaceInDock", "Move", "RemoveFromHome", "AppInfo")) compose.onNodeWithTag("menu:$row").assertExists()

                val menu = bounds("menu")
                assertNear(tile.left, menu.left)
                withClue("menu bottom ${menu.bottom} above the tile ${tile.top}") { menu.bottom shouldBeLessThanOrEqualTo tile.top }
                withClue("menu top ${menu.top} under the top bar") { menu.top shouldBeGreaterThanOrEqualTo TopBarTop + TopBarHeight }
                bounds("menu:title").bottom shouldBeLessThanOrEqualTo bounds("menu:ReplaceInDock").top
            }

            test("the settings menu ends at least 8 dp above the dock") {
                showHome(powerMenu = true)
                press(Key.DirectionUp)
                repeat(2) { press(Key.DirectionRight) }
                holdOk()
                compose.onNodeWithTag("menu:PowerMenu").assertExists()

                withClue("menu bottom ${bounds("menu").bottom}, dock top ${bounds("dock").top}") {
                    bounds("menu").bottom + 8.dp shouldBeLessThanOrEqualTo
                        bounds("dock").top
                }
            }

            test("the settings menu names its rows choose dock apps and restart tv") {
                showHome(powerMenu = true)
                press(Key.DirectionUp)
                repeat(2) { press(Key.DirectionRight) }
                holdOk()

                compose.onNode(hasTestTag("menu:EditDock") and hasText("Choose dock apps")).assertExists()
                compose.onNode(hasTestTag("menu:PowerMenu") and hasText("Restart TV")).assertExists()
            }
        }
    })

private class HeroBandUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val cinematic = SixColumns.copy(cinematicHome = true, appLibrary = true)

    fun showHome(
        config: TvConfig = cinematic,
        apps: List<AppEntry> = NineApps,
        libraryCount: Flow<Int> = emptyFlow(),
        powerMenu: Boolean = false,
        lastOpened: Map<String, Long> = emptyMap(),
    ) {
        val sources =
            sources(
                apps = flowOf(AppList(apps)),
                libraryCount = libraryCount,
                powerMenuAvailable = { powerMenu },
                lastOpened = flowOf(lastOpened),
            )
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
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
                    actions = viewModel.actions,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    fun holdOk() {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    fun dockTile(n: Int): SemanticsNodeInteraction = compose.onNode(hasTestTag("tile:pkg.$n") and hasAnyAncestor(hasTestTag("dock")))

    fun textOf(tag: String): String =
        compose
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString("")
            .orEmpty()

    fun assertNear(
        expected: Dp,
        actual: Dp,
    ) = withClue("expected $expected, got $actual") { abs(expected.value - actual.value) shouldBeLessThan 1f }
}
