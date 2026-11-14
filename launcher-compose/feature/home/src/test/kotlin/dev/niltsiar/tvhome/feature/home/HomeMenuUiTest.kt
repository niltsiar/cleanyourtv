package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputsMenu
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs
import kotlin.math.roundToInt

/** Home on the real presenter: keys go in at the screen, the menus and modes come out of the state. */
val HomeMenuUiTest by testSuite {
    robolectricTestSuite<HomeMenuUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
    robolectricTestSuite<HomeMenuUiNightTestContent>(
        "sdk 31, night, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-night-xhdpi"
            },
    )
    robolectricTestSuite<HomeMenuUiH360TestContent>(
        "sdk 31, 960x360 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h360dp-xhdpi"
            },
    )
}

class HomeMenuUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeMenuUiTestFixture() } asContextForEach {
            test("holding ok on a tile opens its menu and back returns focus to the tile") {
                showHome()

                holdOk()
                compose.onNodeWithTag("menu").assertExists()
                compose.onNodeWithTag("menu:ReplaceInDock").assertIsFocused()

                releaseOk()
                compose.onNodeWithTag("menu").assertExists()
                clicked shouldBe (emptyList<String>())

                back()
                compose.onNodeWithTag("menu").assertDoesNotExist()
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                clicked shouldBe (emptyList<String>())
            }

            test("the repeats and the release of a hold do not press the row the menu opened under them") {
                showHome()

                compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                compose.onRoot().performKeyInput { advanceEventTime(560) }
                compose.waitForIdle()
                compose.onNodeWithTag("menu:ReplaceInDock").assertIsFocused()
                compose.onRoot().performKeyInput { advanceEventTime(200) }
                compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
                compose.waitForIdle()

                compose.onNodeWithTag("menu").assertExists()
                compose.onNodeWithTag("reorder:hint").assertDoesNotExist()
                clicked shouldBe (emptyList<String>())
            }

            test("a short press opens the app and no menu") {
                showHome()

                holdOk(millis = 100)
                releaseOk()

                compose.onNodeWithTag("menu").assertDoesNotExist()
                clicked shouldBe listOf("pkg.1")
            }

            test("the menu key opens the menu and a row fires on its own press") {
                showHome()

                press(Key.Menu)
                compose.onNodeWithTag("menu").assertExists()

                press(Key.DirectionDown)
                compose.onNodeWithTag("menu:Move").assertIsFocused()
                press(Key.DirectionCenter)

                compose.onNodeWithTag("menu").assertDoesNotExist()
                compose.onNodeWithTag("reorder:hint").assertExists()
            }

            test("without tile menu the menu key does nothing and a held ok asks for app info") {
                showHome(SixColumns.copy(tileMenu = false))

                press(Key.Menu)
                compose.onNodeWithTag("menu").assertDoesNotExist()

                holdOk()
                releaseOk()

                compose.onNodeWithTag("menu").assertDoesNotExist()
                effects shouldBe (listOf<HomeEffect>(HomeEffect.OpenAppInfo("pkg.1")))
                clicked shouldBe (emptyList<String>())
            }

            test("a grid tile carried into the dock keeps focus and the order is saved when the mode ends") {
                showHome()
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                holdOk()
                releaseOk()
                press(Key.DirectionDown)
                compose.onNodeWithTag("menu:Move").assertIsFocused()
                press(Key.DirectionCenter)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                press(Key.DirectionUp)

                compose.onNode(hasTestTag("tile:pkg.7") and hasAnyAncestor(hasTestTag("dock"))).assertIsFocused()
                saved shouldBe emptyList<Pair<List<String>, Int>>()

                press(Key.DirectionCenter)
                back()

                compose.onNodeWithTag("reorder:hint").assertDoesNotExist()
                saved shouldBe listOf((listOf(7, 1, 2, 3, 4, 5, 6, 8, 9).map { "pkg.$it" }) to -1)
            }

            test("the settings button opens its menu on a held ok") {
                showHome()
                press(Key.DirectionUp)
                repeat(2) { press(Key.DirectionRight) }

                holdOk()

                compose.onNodeWithTag("menu").assertExists()
                compose.onNodeWithTag("menu:EditDock").assertExists()
                compose.onNode(hasTestTag("menu:Reorder") and hasAnyAncestor(hasTestTag("menu"))).assertExists()
            }

            test("in reorder mode the hero title is the name of the app that moves") {
                showHome()
                press(Key.DirectionRight)
                holdOk()
                releaseOk()
                press(Key.DirectionDown)
                press(Key.DirectionCenter)
                compose.onNodeWithTag("reorder:hint").assertExists()
                compose.onNodeWithTag("shelf:title").assertTextEquals("App 2")

                press(Key.DirectionRight)

                compose.onNodeWithTag("shelf:title").assertTextEquals("App 2")
            }

            test("in edit dock mode the hero title stays choose dock apps") {
                showHome()
                press(Key.DirectionUp)
                repeat(2) { press(Key.DirectionRight) }
                holdOk()
                releaseOk()
                press(Key.DirectionDown)
                press(Key.DirectionCenter)

                compose.onNodeWithTag("reorder:hint").assertExists()
                compose.onNodeWithTag("shelf:title").assertTextEquals("Choose dock apps")
            }

            test("once a dock place is chosen the hint names it of the dock places and the app in it now beside the ok and back chips") {
                showHome()
                press(Key.DirectionUp)
                repeat(2) { press(Key.DirectionRight) }
                holdOk()
                releaseOk()
                press(Key.DirectionDown)
                press(Key.DirectionCenter)
                repeat(2) { press(Key.DirectionRight) }

                press(Key.DirectionCenter)

                compose.onNodeWithTag("reorder:slot").assertTextEquals("Place 3 of 6 \u00B7 now App 3")
                for ((chip, texts) in listOf(
                    "reorder:chip:put" to listOf("OK", "put here"),
                    "reorder:chip:other" to listOf("Back", "other place"),
                )) {
                    for (text in texts) {
                        compose
                            .onNode(
                                hasText(text) and hasAnyAncestor(hasTestTag(chip) and hasAnyAncestor(hasTestTag("reorder:hint"))),
                            ).assertExists()
                    }
                }
            }

            test("a dock tiles menu opens above its tile without covering the top bar") {
                showHome()
                val tile = boundsOf("tile:pkg.1")

                holdOk()

                val menu = boundsOf("menu")
                withClue("menu $menu, tile $tile") { menu.bottom shouldBeLessThanOrEqualTo tile.top }
                menu.top shouldBeGreaterThanOrEqualTo topBarBottom()
            }

            test("a grid tiles menu opens beside its tile without covering the top bar") {
                showHome()
                press(Key.DirectionDown)
                val tile = boundsOf("tile:pkg.7")

                holdOk()

                val menu = boundsOf("menu")
                withClue("menu $menu, tile $tile") { menu.left shouldBeGreaterThanOrEqualTo tile.right }
                menu.top shouldBeGreaterThanOrEqualTo topBarBottom()
            }

            test("a dock tiles menu names its app in a header row and a grid tiles menu does not") {
                showHome()

                holdOk()
                releaseOk()
                compose.onNodeWithTag("menu:title").assertTextEquals("App 1")
                back()
                press(Key.DirectionDown)
                holdOk()
                releaseOk()

                compose.onNodeWithTag("menu").assertExists()
                compose.onNodeWithTag("menu:title").assertDoesNotExist()
            }

            test("a dock tiles menu header is the text colour at 65 percent on the light surface") {
                showHome()

                holdOk()
                releaseOk()

                val style = compose.onNodeWithTag("menu:title").textStyle()
                style.color.alpha shouldBe (0.65f plusOrMinus 0.005f)
                withClue("${style.color} is not dark") { style.color.luminance() shouldBeLessThan 0.2f }
            }

            test("a dock tiles menu is a whole number of tiles wide and starts at its tiles left edge") {
                showHome()
                val tile = boundsOf("tile:pkg.1")

                holdOk()

                val menu = boundsOf("menu")
                assertWholeTiles(menu, tile)
                menu.left shouldBe (tile.left plusOrMinus 1f)
            }

            test("a grid tiles menu starts where the next tile does") {
                showHome(apps = (1..20).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") })
                press(Key.DirectionDown)
                val tile = boundsOf("tile:pkg.7")

                holdOk()

                val menu = boundsOf("menu")
                assertWholeTiles(menu, tile)
                menu.left shouldBe (boundsOf("tile:pkg.8").left plusOrMinus 1f)
            }

            test("the menu of a tile in the last column ends where the tile before it does") {
                showHome(apps = (1..20).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") })
                press(Key.DirectionDown)
                repeat(5) { press(Key.DirectionRight) }
                compose.onNodeWithTag("tile:pkg.12").assertIsFocused()
                val tile = boundsOf("tile:pkg.12")

                holdOk()

                val menu = boundsOf("menu")
                assertWholeTiles(menu, tile)
                menu.right shouldBe (boundsOf("tile:pkg.11").right plusOrMinus 1f)
            }

            test("the menu of the last tile of the dock ends at its tiles right edge") {
                showHome()
                repeat(5) { press(Key.DirectionRight) }
                compose.onNodeWithTag("tile:pkg.6").assertIsFocused()
                val tile = boundsOf("tile:pkg.6")

                holdOk()

                val menu = boundsOf("menu")
                assertWholeTiles(menu, tile)
                menu.right shouldBe (tile.right plusOrMinus 1f)
            }

            test("the menu of a dock tile with no room for it to the right ends at its tiles right edge") {
                showHome(apps = NineApps.map { if (it.key == "pkg.5") it.copy(label = "A dock app with a much longer name") else it })
                repeat(4) { press(Key.DirectionRight) }
                compose.onNodeWithTag("tile:pkg.5").assertIsFocused()
                val tile = boundsOf("tile:pkg.5")

                holdOk()

                val menu = boundsOf("menu")
                val screenRight =
                    compose
                        .onRoot()
                        .fetchSemanticsNode()
                        .boundsInRoot.right
                val room = screenRight - with(compose.density) { ScreenMargin.toPx() } - tile.left
                withClue("menu $menu, room $room") { menu.width shouldBeGreaterThan room }
                assertWholeTiles(menu, tile)
                menu.right shouldBe (tile.right plusOrMinus 1f)
            }

            test("the reorder hint sits clear of every tile and inside the screen") {
                showHome(apps = (1..40).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") })

                press(Key.Menu)
                press(Key.DirectionDown)
                press(Key.DirectionCenter)

                val hint = boundsOf("reorder:hint")
                val tiles =
                    compose
                        .onAllNodes(
                            SemanticsMatcher("is a tile") {
                                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tile:") ==
                                    true
                            },
                        ).fetchSemanticsNodes()
                        .map { it.boundsInRoot }
                        .filter { it.width > 0f && it.height > 0f }
                tiles.isNotEmpty().shouldBeTrue()
                tiles.filter { it.overlaps(hint) } shouldBe (emptyList<Rect>())
                hint.bottom shouldBeLessThanOrEqualTo (
                    compose
                        .onRoot()
                        .fetchSemanticsNode()
                        .boundsInRoot.bottom - screenMargin()
                )
            }

            test("the settings menu lists launcher settings first and opens the page with the key off") {
                launcherSettingsRowIsFirstAndOpensThePage(SixColumns)
            }

            test("the settings menu lists launcher settings first and opens the page with the key on") {
                launcherSettingsRowIsFirstAndOpensThePage(SixColumns.copy(launcherSettingsButton = true))
            }

            test("focus coming back into home goes to the dock after the home key closed a grid tile menu") {
                showHome()
                press(Key.DirectionDown)
                repeat(2) { press(Key.DirectionRight) }
                compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
                holdOk()
                releaseOk()
                compose.onNodeWithTag("menu").assertExists()
                homePresses++
                compose.waitForIdle()
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

                removeTheFocusedTile()

                assertFocusInTheDock()
            }

            test("focus coming back into home goes to the dock after the home key closed the settings menu") {
                showHome()
                openSettingsMenu()
                homePresses++
                compose.waitForIdle()
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

                removeTheFocusedTile()

                assertFocusInTheDock()
            }

            test("focus coming back into home goes to the dock after the inputs key closed the settings menu for a source panel") {
                showHome()
                openSettingsMenu()
                inputsRequests++
                compose.waitForIdle()
                compose.onNodeWithTag("menu").assertDoesNotExist()
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

                removeTheFocusedTile()

                assertFocusInTheDock()
            }

            test("focus coming back into home goes to the dock after the inputs key closed a grid tile menu for a source panel") {
                showHome()
                press(Key.DirectionDown)
                repeat(2) { press(Key.DirectionRight) }
                compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
                holdOk()
                releaseOk()
                compose.onNodeWithTag("menu").assertExists()
                inputsRequests++
                compose.waitForIdle()
                compose.onNodeWithTag("menu").assertDoesNotExist()
                press(Key.DirectionUp)
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

                removeTheFocusedTile()

                assertFocusInTheDock()
            }
        }
    })

class HomeMenuUiNightTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeMenuUiTestFixture() } asContextForEach {
            test("a dock tiles menu header is 16 sp semi bold white at 65 percent on the dark surface") {
                showHome()

                holdOk()
                releaseOk()

                val style = compose.onNodeWithTag("menu:title").textStyle()
                style.fontSize shouldBe 16.sp
                style.fontWeight shouldBe FontWeight.SemiBold
                style.color shouldBe Color.White.copy(alpha = 0.65f)
            }
        }
    })

class HomeMenuUiH360TestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeMenuUiTestFixture() } asContextForEach {
            test("a tile menu with no room below or above its tile still stays under the top bar") {
                showHome()

                holdOk()

                boundsOf("menu").top shouldBeGreaterThanOrEqualTo topBarBottom()
            }
        }
    })

private class HomeMenuUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val effects = mutableListOf<HomeEffect>()

    val saved = mutableListOf<Pair<List<String>, Int>>()

    val clicked = mutableListOf<String>()

    val installed = MutableStateFlow(AppList(NineApps))

    var homePresses by mutableIntStateOf(0)

    var inputsRequests by mutableIntStateOf(0)

    fun showHome(
        config: TvConfig = SixColumns,
        apps: List<AppEntry> = NineApps,
    ) {
        installed.value = AppList(apps)
        val sources =
            sources(
                apps = installed,
                inputs = flowOf(InputsMenu(available = true, rows = listOf(InputRow.Panel), panelOnly = true)),
                saveOrder = { keys, docked ->
                    saved += keys to docked
                    true
                },
            )
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme {
                val viewModel = rememberHomeViewModel(sources, config, effects)
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = { clicked += it },
                    topBar = config.topBarLayout(),
                    actions = viewModel.actions,
                    homePresses = { homePresses },
                    inputsRequests = { inputsRequests },
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

    fun holdOk(millis: Long = 600) {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    fun releaseOk() {
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    fun boundsOf(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    fun gap() = with(compose.density) { TileGap.toPx() }

    /** How many tiles and gaps wide [menu] is, as a count of tiles ([tile] wide, [gap] apart); whole for a menu that spans tiles. */
    fun tilesAcross(
        menu: Rect,
        tile: Rect,
    ): Float = (menu.width + gap()) / (tile.width + gap())

    fun assertWholeTiles(
        menu: Rect,
        tile: Rect,
    ) {
        val across = tilesAcross(menu, tile)
        withClue("$across tiles across") { (abs(across - across.roundToInt()) < 0.01f && across.roundToInt() >= 1).shouldBeTrue() }
    }

    fun topBarBottom() = with(compose.density) { (TopBarTop + TopBarHeight).toPx() }

    fun screenMargin() = with(compose.density) { 20.dp.toPx() }

    /** The focused tile goes away, so focus comes back into Home from outside, as when the window regains it. */
    fun removeTheFocusedTile() {
        installed.value = AppList(installed.value.apps.filterNot { it.key == "pkg.1" })
        compose.waitForIdle()
    }

    fun assertFocusInTheDock() = compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("dock"))).assertIsFocused()

    fun openSettingsMenu() {
        press(Key.DirectionUp)
        repeat(2) { press(Key.DirectionRight) }
        holdOk()
        releaseOk()
        compose.onNodeWithTag("menu").assertExists()
    }

    fun launcherSettingsRowIsFirstAndOpensThePage(config: TvConfig) {
        showHome(config)
        openSettingsMenu()

        val first = compose.onNodeWithTag("menu:LauncherSettings")
        first.assertTextEquals("Launcher settings")
        first.getBoundsInRoot().top shouldBeLessThan compose.onNodeWithTag("menu:Reorder").getBoundsInRoot().top
        first.assertIsFocused()
        press(Key.DirectionCenter)

        effects shouldBe (listOf<HomeEffect>(HomeEffect.OpenLauncherSettings))
        compose.onNodeWithTag("menu").assertDoesNotExist()
        compose.onNodeWithContentDescription(config.settingsButtonLabel()).assertIsFocused()
    }

    fun TvConfig.settingsButtonLabel() = if (launcherSettingsButton) "Launcher settings" else "Settings"
}
