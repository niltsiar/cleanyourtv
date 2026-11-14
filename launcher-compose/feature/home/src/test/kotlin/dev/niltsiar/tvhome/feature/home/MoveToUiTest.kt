package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
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
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf

/** move_to_menu on the real presenter and screen: where focus goes after a move, and Back from the places list. */
val MoveToUiTest by testSuite {
    robolectricTestSuite<MoveToUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class MoveToUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { MoveToUiTestFixture() } asContextForEach {
            test("a dock app moved to the end of the grid keeps focus in its new place, scrolled into view") {
                showHome()

                openPlaces()
                compose.onNodeWithTag("menu:DockEnd").assertIsFocused()
                press(Key.DirectionDown)
                press(Key.DirectionDown)
                compose.onNodeWithTag("menu:GridEnd").assertIsFocused()
                press(Key.DirectionCenter)

                compose.onNodeWithTag("menu").assertDoesNotExist()
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused().assertIsDisplayed()
                saved shouldBe listOf(apps.drop(1).map { it.key } + "pkg.1" to -1)
            }

            test("a grid app moved to the start of the dock keeps focus there") {
                showHome()
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                openPlaces()
                compose.onNodeWithTag("menu:DockStart").assertIsFocused()
                press(Key.DirectionCenter)

                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                val moved = compose.onNodeWithTag("tile:pkg.7").getBoundsInRoot()
                val dock = compose.onNodeWithTag("tile:pkg.2").getBoundsInRoot()
                moved.top shouldBe dock.top
                (moved.left < dock.left) shouldBe true
            }

            test("back from the places list returns to the tile menu") {
                showHome()

                openPlaces()
                back()

                compose.onNodeWithTag("menu:MoveTo").assertExists()
                compose.onNodeWithTag("menu:DockEnd").assertDoesNotExist()
                compose.onNodeWithTag("menu:ReplaceInDock").assertIsFocused()
                back()
                compose.onNodeWithTag("menu").assertDoesNotExist()
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                saved shouldBe emptyList()
            }
        }
    })

private class MoveToUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val effects = mutableListOf<HomeEffect>()

    val saved = mutableListOf<Pair<List<String>, Int>>()

    val apps = (1..30).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    fun showHome() {
        val config = SixColumns.copy(moveToMenu = true)
        val sources =
            sources(
                apps = flowOf(AppList(apps)),
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
                    onClick = viewModel::onClick,
                    topBar = config.topBarLayout(),
                    actions = viewModel.actions,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    /** The menu key on the focused tile, then "Move to", the third row of the tile menu. */
    fun openPlaces() {
        press(Key.Menu)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("menu:MoveTo").assertIsFocused()
        press(Key.DirectionCenter)
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
