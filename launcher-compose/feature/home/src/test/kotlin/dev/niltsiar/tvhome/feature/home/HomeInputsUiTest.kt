package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputsMenu
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf

/** The Inputs button on the real presenter: keys go in at the screen, the list comes out of the state. */
val HomeInputsUiTest by testSuite {
    robolectricTestSuite<HomeInputsUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HomeInputsUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeInputsUiTestFixture() } asContextForEach {
            test("ok on the inputs button opens the list on its first row and back returns focus to the button") {
                showHome()

                openList()
                compose.onNodeWithTag("menu").assertExists()
                compose.onNodeWithTag("input:0").assertIsFocused()

                back()

                compose.onNodeWithTag("menu").assertDoesNotExist()
                compose.onNodeWithContentDescription("Inputs").assertIsFocused()
            }

            test("the list opens on the first connected input as the tv names no active one") {
                showHome(
                    InputsMenu(
                        available = true,
                        rows =
                            listOf(
                                port(1, InputConnection.Disconnected),
                                port(2, InputConnection.Disconnected),
                                port(3, InputConnection.Connected),
                                port(4, InputConnection.Connected),
                                InputRow.LiveTv,
                            ),
                    ),
                )

                openList()

                compose.onNodeWithTag("input:2").assertIsFocused()
            }

            test("the list opens on its first row when no input is connected") {
                showHome(InputsMenu(available = true, rows = listOf(port(1, InputConnection.Disconnected), port(2), InputRow.LiveTv)))

                openList()

                compose.onNodeWithTag("input:0").assertIsFocused()
            }

            test("a connected input behind more inputs does not stop the list opening on its first row") {
                showHome(
                    InputsMenu(
                        available = true,
                        rows =
                            listOf(
                                port(1, InputConnection.Disconnected),
                                port(2, InputConnection.Connected, folded = true),
                                InputRow.More,
                            ),
                    ),
                )

                openList()

                compose.onNodeWithTag("input:0").assertIsFocused()
            }

            test("the rows show the cec device the connection and live tv last") {
                showHome()

                openList()

                compose.onNodeWithText("HDMI 1").assertExists()
                compose.onNodeWithText("Apple TV · HDMI 2").assertExists()
                compose.onNodeWithText("Live TV").assertExists()
                compose.onAllNodesWithText("Connected").fetchSemanticsNodes().size shouldBe 2
                compose.onAllNodesWithText("No device").fetchSemanticsNodes().size shouldBe 1
                repeat(4) { press(Key.DirectionDown) }
                compose.onNodeWithTag("input:5").assertIsFocused()
            }

            test("ok on a row closes the list hands the input to the host and focus returns to the button") {
                showHome()
                openList()
                press(Key.DirectionDown)

                press(Key.DirectionCenter)

                compose.onNodeWithTag("menu").assertDoesNotExist()
                effects shouldBe (listOf<HomeEffect>(HomeEffect.OpenInput(InputTarget.TvInput("tv/.Cec/HDMI2"))))
                compose.onNodeWithContentDescription("Inputs").assertIsFocused()
            }

            test("more inputs shows the folded rows and moves focus to the first of them") {
                showHome(
                    InputsMenu(
                        available = true,
                        rows =
                            listOf(
                                port(1, InputConnection.Connected),
                                port(2, InputConnection.Disconnected, folded = true),
                                InputRow.Separator(folded = true),
                                InputRow.Port(
                                    "AV 1",
                                    InputIcon.Av,
                                    InputConnection.Unknown,
                                    InputTarget.TvInput("tv/.Av/AV1"),
                                    folded = true,
                                ),
                                InputRow.More,
                            ),
                    ),
                )
                openList()
                compose.onNodeWithText("HDMI 2").assertDoesNotExist()
                compose.onNodeWithText("AV 1").assertDoesNotExist()

                press(Key.DirectionDown)
                compose.onNodeWithTag("input:more").assertIsFocused()
                press(Key.DirectionCenter)

                compose.onNodeWithText("More inputs").assertDoesNotExist()
                compose.onNodeWithText("AV 1").assertExists()
                compose.onNodeWithTag("input:1").assertIsFocused()
            }

            test("the list is at least 150 dp wide") {
                showHome(InputsMenu(available = true, rows = listOf(port(1))))

                openList()

                val width = compose.onNodeWithTag("menu").getBoundsInRoot().width
                withClue("$width") { width shouldBeGreaterThanOrEqualTo 150.dp }
            }

            test("every row is 46 dp tall") {
                showHome()

                openList()

                (0..3).forEach { row -> compose.onNodeWithTag("input:$row").getBoundsInRoot().height shouldBe 46.dp }
            }

            test("a tv with nothing to list has no inputs button") {
                showHome(InputsMenu(available = false))

                compose.onNodeWithContentDescription("Inputs").assertDoesNotExist()
            }

            test("a system request opens the list once the button is there") {
                showHome()

                inputsRequests++
                compose.waitForIdle()

                compose.onNodeWithTag("menu").assertExists()
                compose.onNodeWithTag("input:0").assertIsFocused()
            }

            test("a system request before the tv has said what it lists opens the list under the button once it has") {
                val inputs = MutableSharedFlow<InputsMenu>(replay = 1)
                showHome(inputs)

                inputsRequests++
                compose.waitForIdle()
                compose.onNodeWithTag("menu").assertDoesNotExist()
                inputs.tryEmit(FourPorts)
                compose.waitForIdle()

                compose.onNodeWithTag("menu").assertExists()
                compose.onNodeWithTag("input:0").assertIsFocused()
                val button = compose.onNodeWithContentDescription("Inputs").getBoundsInRoot()
                val list = compose.onNodeWithTag("menu").getBoundsInRoot()
                // With the Wi-Fi button a round icon the Inputs button sits near the right margin, which keeps the list from reaching past it.
                withClue("list $list, button $button") {
                    (
                        list.top >= button.bottom && list.left <= button.right &&
                            list.right >= button.left
                    ).shouldBeTrue()
                }
            }

            test("a list taller than its room shows whole rows only at rest") {
                showHome(longList)

                openList()

                val list = compose.onNodeWithTag("menu").getBoundsInRoot()
                val rows = rowTags().map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
                val shown = rows.filter { it.bottom > list.top && it.top < list.bottom }
                withClue("shown $shown in $list") { (shown.isNotEmpty() && shown.size < rows.size).shouldBeTrue() }
                shown.forEach { withClue("$it is cut by $list") { (it.top >= list.top && it.bottom <= list.bottom).shouldBeTrue() } }
            }

            test("a list taller than its room still ends 24 dp above the docks tray") {
                showHome(longList)

                openList()

                val list = compose.onNodeWithTag("menu").getBoundsInRoot()
                val tray = compose.onNodeWithTag("dock").getBoundsInRoot()
                withClue("list $list, tray $tray") { list.bottom shouldBeLessThanOrEqualTo tray.top - 24.dp }
            }

            test("the focused row stays inside the list as focus walks down it") {
                showHome(longList)
                openList()

                rowTags().forEachIndexed { step, tag ->
                    if (step > 0) press(Key.DirectionDown)
                    compose.onNodeWithTag(tag).assertIsFocused()
                    val list = compose.onNodeWithTag("menu").getBoundsInRoot()
                    val row = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
                    withClue("$tag $row is outside $list") { (row.top >= list.top && row.bottom <= list.bottom).shouldBeTrue() }
                }
            }
        }
    })

private class HomeInputsUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val effects = mutableListOf<HomeEffect>()

    var inputsRequests by mutableIntStateOf(0)

    fun showHome(inputs: InputsMenu = FourPorts) = showHome(flowOf(inputs))

    fun showHome(inputs: Flow<InputsMenu>) {
        val sources = sources(inputs = inputs)
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme {
                val viewModel = rememberHomeViewModel(sources, SixColumns, effects)
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = SixColumns.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = SixColumns.topBarLayout(),
                    actions = viewModel.actions,
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

    fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    fun openList() {
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Inputs").assertIsFocused()
        press(Key.DirectionCenter)
    }

    /** Eight ports, a divider and Live TV: more than the room between the button and the dock's tray. */
    val longList =
        InputsMenu(
            available = true,
            rows = (1..8).map { port(it) } + InputRow.Separator() + InputRow.LiveTv,
        )

    /** The rows of [longList] by position; the divider at 8 has no tag. */
    fun rowTags() = ((0..7) + 9).map { "input:$it" }
}
