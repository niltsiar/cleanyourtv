package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.width
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.InputsMenu
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import java.util.Locale

/** The App library pill and the top-bar button: how focus reaches them and what OK does. */
val HomeLibraryUiTest by testSuite {
    robolectricTestSuite<HomeLibraryUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HomeLibraryUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeLibraryUiTestFixture() } asContextForEach {
            test("right past the last tile focuses the pill and ok opens the library") {
                showHome()
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                press(Key.DirectionRight)
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
                press(Key.DirectionRight)
                compose.onNodeWithTag("library:pill").assertIsFocused()

                press(Key.DirectionCenter)
                opened shouldBe 1
            }

            test("the pill under the grid starts where the grids first column does") {
                showHome()
                press(Key.DirectionDown)
                press(Key.DirectionRight)
                press(Key.DirectionDown)
                compose.onNodeWithTag("library:pill").assertIsFocused()

                // A focused pill grows 8% from its left edge, so that edge stays on the grid's.
                val pill = compose.onNodeWithTag("library:pill").getBoundsInRoot()
                val firstColumn = compose.onNodeWithTag("tile:pkg.7").getBoundsInRoot()
                pill.left.value shouldBe (firstColumn.left.value plusOrMinus 1f)
            }

            test("down from the last row focuses the pill and up returns to the tile") {
                showHome()
                press(Key.DirectionDown)
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.8").assertIsFocused()

                press(Key.DirectionDown)
                compose.onNodeWithTag("library:pill").assertIsFocused()
                press(Key.DirectionLeft)
                press(Key.DirectionRight)
                press(Key.DirectionDown)
                compose.onNodeWithTag("library:pill").assertIsFocused()

                press(Key.DirectionUp)
                compose.onNodeWithTag("tile:pkg.8").assertIsFocused()
            }

            test("down from a dock tile over the short last row goes to that row not to the pill") {
                showHome()
                press(Key.DirectionRight)
                press(Key.DirectionRight)
                press(Key.DirectionRight)
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.5").assertIsFocused()

                press(Key.DirectionDown)

                compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
            }

            test("without the app library there is no pill and down from the last row stays") {
                showHome(SixColumns)
                press(Key.DirectionDown)
                press(Key.DirectionDown)

                compose.onNodeWithTag("library:pill").assertDoesNotExist()
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
            }

            test("a top bar button with library button opens the library") {
                showHome(
                    SixColumns.copy(appLibrary = true, libraryButton = true, showLibraryPill = false),
                    inputs = InputsMenu(available = true),
                )
                press(Key.DirectionUp)
                press(Key.DirectionRight)
                press(Key.DirectionRight)
                compose.onNodeWithContentDescription("App library").assertIsFocused()

                press(Key.DirectionCenter)

                opened shouldBe 1
                compose.onNodeWithTag("library:pill").assertDoesNotExist()
            }

            test("ok on the pill in reorder mode ends the mode and opens the library") {
                showHome(reordering = true)
                press(Key.DirectionDown)
                press(Key.DirectionDown)
                compose.onNodeWithTag("library:pill").assertIsFocused()

                press(Key.DirectionCenter)

                toggles shouldBe listOf("toggle")
                opened shouldBe 1
            }

            test("in a single row the pill is the row s last item that right from the last tile reaches and left leaves") {
                showSingleRowHome()
                repeat(8) { press(Key.DirectionRight) }
                compose.onNodeWithTag("tile:pkg.9").assertIsFocused()

                press(Key.DirectionRight)

                compose.onNodeWithTag("library:pill").assertIsFocused()
                val pill = compose.onNodeWithTag("library:pill").getBoundsInRoot()
                val tile = compose.onNodeWithTag("tile:pkg.9").getBoundsInRoot()
                withClue("pill $pill, tile $tile") { pill.top shouldBeLessThan tile.bottom }

                press(Key.DirectionLeft)

                compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
            }

            test("in a single row closing the library scrolls the row to the pill and focuses it") {
                showSingleRowHome()
                repeat(9) { press(Key.DirectionRight) }
                compose.onNodeWithTag("library:pill").assertIsFocused()
                press(Key.DirectionCenter)
                libraryOpen.shouldBeTrue()
                compose.onNodeWithTag("dock").onChild().performScrollToIndex(0)
                compose.waitForIdle()
                compose.onNodeWithTag("tile:pkg.1").requestFocus()
                compose.waitForIdle()
                compose.onNodeWithTag("library:pill").assertIsNotDisplayed()

                libraryOpen = false
                compose.waitForIdle()

                compose.onNodeWithTag("library:pill").assertIsFocused().assertIsDisplayed()
            }
        }
    })

private class HomeLibraryUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    var opened = 0

    var focusedKey by mutableStateOf<String?>(null)

    val toggles = mutableListOf<String>()

    fun showHome(
        config: TvConfig = SixColumns.copy(appLibrary = true),
        reordering: Boolean = false,
        inputs: InputsMenu = InputsMenu(),
    ) {
        val apps = homeApps(config = config)
        val reorder = if (reordering) ReorderView(carried = null, dimmed = emptySet(), hint = ReorderHint.Reorder) else null
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme {
                HomeScreen(
                    state = { homeUiState(apps, Evening, focusedKey, Locale.ENGLISH, reorder = reorder, inputs = inputs) },
                    layout = config.homeLayout(),
                    onFocus = { focusedKey = it },
                    onClick = {},
                    topBar = config.topBarLayout(),
                    actions = HomeMenuActions(onToggleReorder = { toggles += "toggle" }),
                    onOpenLibrary = { opened++ },
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

    var libraryOpen by mutableStateOf(false)

    fun showSingleRowHome() {
        val config = SixColumns.copy(singleRow = true, appLibrary = true)
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme {
                val viewModel = rememberHomeViewModel(sources(), config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = config.topBarLayout(),
                    actions = viewModel.actions,
                    libraryOpen = { libraryOpen },
                    onOpenLibrary = { libraryOpen = true },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }
}
