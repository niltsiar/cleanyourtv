package dev.niltsiar.tvhome.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
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
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.OpenedRecord
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

/** The library on the real presenter: keys go in at the panel, the choices come out of the fake host. */
val LibraryOverlayUiTest by testSuite {
    robolectricTestSuite<LibraryOverlayUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class LibraryOverlayUiTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())

                lateinit var viewModel: MoleculeLibraryViewModel
                val effects = mutableListOf<LibraryEffect>()
                val library =
                    FakeLibrary(
                        listOf(
                            libraryApp("Alpha"),
                            libraryApp("Beta"),
                            libraryApp("Gamma", onHome = true),
                            libraryApp("Delta", installedAt = NOW - DAY),
                        ),
                    )

                fun showLibrary(config: TvConfig = TvConfig(appLibrary = true)) {
                    // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
                    compose.runOnUiThread {
                        compose.activity.window.decorView
                            .requestFocusFromTouch()
                    }
                    compose.setContent {
                        TvTheme {
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
                            LaunchedEffect(viewModel) { viewModel.effects.collect { effects += it } }
                            val state = viewModel.uiState.collectAsState()
                            LibraryOverlay(
                                state = { state.value },
                                layout = config.libraryLayout(),
                                actions = viewModel.actions,
                                art = { null },
                            )
                        }
                    }
                    viewModel.onOpen()
                    compose.waitForIdle()
                    compose.mainClock.advanceTimeBy(1_000)
                    compose.waitForIdle()
                }

                fun press(key: Key) {
                    compose.onRoot().performKeyInput { pressKey(key) }
                    compose.waitForIdle()
                }

                fun back() {
                    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                    compose.waitForIdle()
                }

                val leastUsed = TvConfig(appLibrary = true, libraryLeastUsed = true)

                fun gammaOpenedThreeDaysAgo() {
                    library.record = OpenedRecord(times = mapOf(key("Gamma") to NOW - 3 * DAY), since = NOW - 90 * DAY)
                }

                fun textLeft() =
                    compose
                        .onNode(hasText("Usual order") and hasAnyAncestor(hasTestTag("library:order")), useUnmergedTree = true)
                        .getBoundsInRoot()
                        .left

                fun orderChip() = compose.onNodeWithTag("library:order")
            }
        } asContextForEach {
            test("the panel lists the apps missing from home first and focuses the first card") {
                showLibrary()

                compose.onNodeWithTag("library").assertExists()
                compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()
                press(Key.DirectionRight)
                compose.onNodeWithTag("card:${key("Alpha")}").assertIsFocused()
                compose.onNodeWithTag("card:${key("Gamma")}").assertExists()
            }

            test("keys cannot leave the panel for the home behind it") {
                showLibrary()

                repeat(3) { press(Key.DirectionLeft) }
                press(Key.DirectionUp)
                press(Key.DirectionDown)

                compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()
            }

            test("ok on a missing app adds it and focus moves to the next missing app") {
                showLibrary()
                press(Key.DirectionRight)

                press(Key.DirectionCenter)
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitForIdle()

                library.saved shouldBe listOf("pkg.Alpha" to true)
                compose.onNodeWithTag("card:${key("Beta")}").assertIsFocused()
            }

            test("a held ok opens the menu and back closes the menu then the library") {
                showLibrary()

                compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                compose.mainClock.advanceTimeBy(600)
                compose.waitForIdle()
                compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
                compose.mainClock.advanceTimeBy(200)
                compose.waitForIdle()
                compose.onNodeWithTag("menu").assertExists()
                compose.onNodeWithTag("menu:Open").assertIsFocused()
                effects shouldBe emptyList<LibraryEffect>()

                back()
                compose.onNodeWithTag("menu").assertDoesNotExist()
                compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()

                back()
                compose.onNodeWithTag("library").assertDoesNotExist()
            }

            test("a held ok opens the menu 16 dp beside its card and inside the screen") {
                showLibrary()
                compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()
                val grown = compose.onNodeWithTag("card:${key("Delta")}").getBoundsInRoot()
                // Its bounds include its focus growth of 8%; the menu goes beside its slot in the row.
                val growth = grown.width * (1f - 1f / 1.08f) / 2f
                val card = DpRect(grown.left + growth, grown.top, grown.right - growth, grown.bottom)

                compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                compose.mainClock.advanceTimeBy(600)
                compose.waitForIdle()
                compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitForIdle()

                val menu = compose.onNodeWithTag("menu").getBoundsInRoot()
                val screen = compose.onRoot().getBoundsInRoot()
                withClue("menu $menu, card $card") {
                    (menu.left >= card.right + 16.dp - 1.dp || menu.right <= card.left - 16.dp + 1.dp).shouldBeTrue()
                }
                withClue("menu $menu, screen $screen") {
                    menu.left shouldBeGreaterThanOrEqualTo screen.left
                    menu.right shouldBeLessThanOrEqualTo screen.right
                }
                withClue("menu $menu, screen $screen") {
                    menu.top shouldBeGreaterThanOrEqualTo screen.top
                    menu.bottom shouldBeLessThanOrEqualTo screen.bottom
                }
            }

            test("an empty catalogue shows its text and back closes it") {
                library.apps = emptyList()
                showLibrary()

                compose.onNodeWithTag("library:empty").assertExists()

                back()

                compose.onNodeWithTag("library").assertDoesNotExist()
            }

            test("in selection mode a held ok opens no menu and ok changes the shortcut") {
                showLibrary(TvConfig(appLibrary = true, appLibrarySelection = true))
                compose.onNodeWithTag("card:${key("Alpha")}").assertIsFocused()

                compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                compose.mainClock.advanceTimeBy(700)
                compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
                compose.waitForIdle()

                compose.onNodeWithTag("menu").assertDoesNotExist()
                library.saved shouldBe listOf("pkg.Alpha" to true)
            }

            test("the menu key switches the order the header names it and focus stays on the same app") {
                gammaOpenedThreeDaysAgo()
                showLibrary(leastUsed)
                compose.onNodeWithTag("library:order").assertTextEquals("Usual order")
                compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()

                press(Key.Menu)

                compose.onNodeWithTag("library:order").assertTextEquals("Least used")
                compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()
                press(Key.DirectionRight)
                compose.onNodeWithTag("card:${key("Gamma")}").assertIsFocused()

                press(Key.Menu)

                compose.onNodeWithTag("library:order").assertTextEquals("Usual order")
                compose.onNodeWithTag("card:${key("Gamma")}").assertIsFocused()
            }

            test("with the key off the menu key changes nothing and the header names no order") {
                showLibrary()

                press(Key.Menu)

                compose.onNodeWithTag("library:order").assertDoesNotExist()
                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home", "Hold OK", "Options")
                compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()
                press(Key.DirectionRight)
                compose.onNodeWithTag("card:${key("Alpha")}").assertIsFocused()
                compose.onNodeWithTag("card:opened:${key("Alpha")}", useUnmergedTree = true).assertDoesNotExist()
            }

            test("in the least used order a card keeps its status and adds when it was last opened") {
                gammaOpenedThreeDaysAgo()
                showLibrary(leastUsed)
                compose.onNodeWithTag("card:opened:${key("Gamma")}", useUnmergedTree = true).assertDoesNotExist()

                press(Key.Menu)

                compose
                    .onNodeWithTag(
                        "card:opened:${key("Gamma")}",
                        useUnmergedTree = true,
                    ).assertTextContains("3 days ago, ", substring = true)
                compose.onNodeWithTag("card:opened:${key("Alpha")}", useUnmergedTree = true).assertTextEquals("Never opened")
            }

            test("in browse mode the hint is the chips of ok and a held ok and an app that opens says open") {
                showLibrary(TvConfig(appLibrary = true, libraryOkOpens = true))

                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Open", "Hold OK", "Options")
            }

            test("in the chooser the hint says ok removes an app on home and ok adds one off home") {
                showLibrary(TvConfig(appLibrary = true, appLibrarySelection = true))
                compose.onNodeWithTag("card:${key("Alpha")}").assertIsFocused()
                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home")

                repeat(3) { press(Key.DirectionRight) }

                compose.onNodeWithTag("card:${key("Gamma")}").assertIsFocused()
                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Remove from Home")
            }

            test("the status line names only what the card does not already say") {
                showLibrary()

                compose.textsOnCard("Alpha") shouldBe listOf("Alpha", "Add to Home")
                compose.textsOnCard("Delta") shouldBe listOf("Delta", "New · Add to Home")
                compose.textsOnCard("Gamma") shouldBe listOf("Gamma")
            }

            test("with library ok opens a card off home has no status text either") {
                showLibrary(TvConfig(appLibrary = true, libraryOkOpens = true))

                compose.textsOnCard("Alpha") shouldBe listOf("Alpha")
                compose.textsOnCard("Delta") shouldBe listOf("Delta")
            }

            test("in the least used order a card shows the opened line alone whatever its home state") {
                gammaOpenedThreeDaysAgo()
                showLibrary(leastUsed)

                press(Key.Menu)

                compose.textsOnCard("Alpha") shouldBe listOf("Alpha", "Never opened")
                compose.textsOnCard("Delta") shouldBe listOf("Delta", "Never opened")
                compose.textsOnCard("Gamma").size shouldBe 2
                compose.textsOnCard("Gamma")[1] shouldStartWith "3 days ago, "
            }

            test("with no open time for any app the least used order shows no opened text and a status says so") {
                showLibrary(leastUsed)
                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home", "Hold OK", "Options")

                press(Key.Menu)

                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home", "Hold OK", "Options")
                compose.onNodeWithTag("library:status").assertTextEquals("No usage data yet")
                compose.onNodeWithTag("card:opened:${key("Alpha")}", useUnmergedTree = true).assertDoesNotExist()
                compose.textsOnCard("Alpha") shouldBe listOf("Alpha", "Add to Home")
            }

            test("with an open time the library does not say there is no usage data") {
                gammaOpenedThreeDaysAgo()
                showLibrary(leastUsed)

                press(Key.Menu)

                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home", "Hold OK", "Options")
                compose.onNodeWithTag("library:status").assertDoesNotExist()
            }

            test("in selection mode the panel is titled choose home apps") {
                showLibrary(TvConfig(appLibrary = true, appLibrarySelection = true))
                compose.onNodeWithText("Choose Home apps").assertExists()
                compose.onNodeWithText("App library").assertDoesNotExist()
            }

            test("in browse mode the panel is titled app library") {
                showLibrary()
                compose.onNodeWithText("App library").assertExists()
                compose.onNodeWithText("Choose Home apps").assertDoesNotExist()
            }

            test("while the order chip has focus the hint says what ok does on it") {
                gammaOpenedThreeDaysAgo()
                showLibrary(leastUsed)

                press(Key.DirectionUp)
                orderChip().assertIsFocused()
                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Least used")

                press(Key.DirectionCenter)
                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Usual order")

                press(Key.DirectionDown)
                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home", "Hold OK", "Options")
            }

            test("with the order chip shown the hint row has no menu hint and the chip holds the menu key cap") {
                showLibrary(leastUsed)

                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home", "Hold OK", "Options")
                compose
                    .onNode(hasText("Menu") and hasAnyAncestor(hasTestTag("library:order")), useUnmergedTree = true)
                    .assertExists()
                orderChip().assertTextEquals("Usual order")
            }

            test("the order chip shows a sort glyph 14 dp before its text") {
                showLibrary(leastUsed)

                val chip = orderChip().getBoundsInRoot()
                val glyph = compose.onNodeWithTag("library:order:glyph", useUnmergedTree = true).getBoundsInRoot()
                glyph.width.value shouldBe (14f plusOrMinus 1f)
                glyph.height.value shouldBe (14f plusOrMinus 1f)
                withClue("glyph $glyph inside chip $chip") {
                    glyph.left shouldBeGreaterThanOrEqualTo chip.left
                    glyph.right shouldBeLessThanOrEqualTo chip.right
                }
                withClue("glyph ends ${glyph.right}, text starts ${textLeft()}") { glyph.right shouldBeLessThanOrEqualTo textLeft() }
            }

            test("up from the first row focuses the order chip and down returns to the card that had focus") {
                showLibrary(leastUsed)
                press(Key.DirectionRight)
                compose.onNodeWithTag("card:${key("Alpha")}").assertIsFocused()

                press(Key.DirectionUp)

                orderChip().assertIsFocused()
                listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp).forEach { press(it) }
                orderChip().assertIsFocused()

                press(Key.DirectionDown)

                compose.onNodeWithTag("card:${key("Alpha")}").assertIsFocused()
            }

            test("ok on the order chip switches the order and focus stays on the chip") {
                gammaOpenedThreeDaysAgo()
                showLibrary(leastUsed)
                press(Key.DirectionUp)
                orderChip().assertIsFocused()
                orderChip().assertTextEquals("Usual order")

                press(Key.DirectionCenter)

                orderChip().assertIsFocused()
                orderChip().assertTextEquals("Least used")
                compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Usual order")

                press(Key.DirectionDown)

                compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()
            }
        }
    })
