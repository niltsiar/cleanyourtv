package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import dev.niltsiar.tvhome.core.model.InputsMenu
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs

/** In dock focus the band and the dock sit 120 dp lower; the grid layout is where Down takes them. */
val DockLowerUiTest by testSuite {
    robolectricTestSuite<DockLowerUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class DockLowerUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { DockLowerUiTestFixture() } asContextForEach {
            test("in dock focus the dock sits 120 dp and the band 104 dp lower than in the grid layout") {
                showHome()
                val dock = top("dock")
                val title = bottom("shelf:name")

                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                // The title and its line stand 16 dp lower in the grid layout, 36 dp under the top bar.
                assertNear(title - 104.dp, bottom("shelf:title"))
                press(Key.DirectionUp)
                assertNear(dock, top("dock"))
                assertNear(title, bottom("shelf:name"))
            }

            test("down from the dock puts the first grid row where the dock rested before it moved lower") {
                showHome()
                // Before the dock moved lower the focused row rested 24 dp inside the dock panel's old top edge.
                val rest = top("dock") - 120.dp + 24.dp

                press(Key.DirectionDown)

                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                assertNear(rest, top("tile:pkg.7"))
            }

            test("up into the top bar keeps the low layout and down returns to the dock without a move") {
                showHome()
                val dock = top("dock")

                press(Key.DirectionUp)
                assertNear(dock, top("dock"))
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                assertNear(dock, top("dock"))
            }

            test("moving along the dock does not scroll the grid") {
                showHome()
                val dock = top("dock")
                val row = top("tile:pkg.7")

                press(Key.DirectionRight)
                press(Key.DirectionRight)

                compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
                assertNear(dock, top("dock"))
                assertNear(row, top("tile:pkg.7"))
            }

            test("a tile menu on a dock tile leaves the band and the dock low") {
                showHome()
                val dock = top("dock")
                val title = bottom("shelf:name")

                val stray = strayOf("dock") { holdOkClockStopped() }

                compose.onNodeWithTag("menu").assertExists()
                withClue("the dock strayed $stray dp") { stray shouldBeLessThanOrEqualTo 1f }
                assertNear(dock, top("dock"))
                assertNear(title, bottom("shelf:name"))
                back()
                compose.mainClock.advanceTimeBy(1_000)
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                assertNear(dock, top("dock"))
            }

            test("a tile menu on a grid tile keeps the group up") {
                showHome()
                focusGridTile()
                val title = bottom("shelf:name")

                holdOk()

                compose.onNodeWithTag("menu").assertExists()
                assertNear(title, bottom("shelf:name"))
            }

            test("the group takes 320 ms to rise for the grid") {
                showHome()
                val low = bottom("shelf:name")
                compose.mainClock.autoAdvance = false

                compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                compose.mainClock.advanceTimeBy(100)
                val early = (low - bottom("shelf:name")).value
                withClue("risen $early dp after 100 ms") { early shouldBeIn 1f..110f }

                compose.mainClock.advanceTimeBy(500)
                assertNear(low - 104.dp, bottom("shelf:title"))
            }

            test("a reversal inside the move goes on from where the group is") {
                showHome()
                val low = bottom("shelf:name")
                compose.mainClock.autoAdvance = false

                compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                compose.mainClock.advanceTimeBy(160)
                val middle = bottom("shelf:name")
                withClue("risen ${low - middle} dp") { (low - middle).value shouldBeIn 5f..115f }

                compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
                compose.mainClock.advanceTimeBy(16)
                assertNear(middle, bottom("shelf:name"), tolerance = 12f)

                compose.mainClock.advanceTimeBy(1_000)
                assertNear(low, bottom("shelf:name"))
            }

            test("with animations off the group moves at once") {
                showHome(motion = Motion(animations = false))
                val low = bottom("shelf:name")
                compose.mainClock.autoAdvance = false

                compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                compose.mainClock.advanceTimeBy(64)

                assertNear(low - 104.dp, bottom("shelf:title"))
            }

            test("with reduced motion the group moves at once") {
                showHome(motion = Motion(reducedMotion = true))
                val low = bottom("shelf:name")
                compose.mainClock.autoAdvance = false

                compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                compose.mainClock.advanceTimeBy(64)

                assertNear(low - 104.dp, bottom("shelf:title"))
            }

            test("a tile menu hangs from where the tile rests once the group has risen") {
                val input =
                    HomeInput(
                        mutableStateOf(HomeMenuActions()),
                        groupShift = { 240f },
                        carried = mutableStateOf(null),
                        dimmed = mutableStateOf(emptySet()),
                        reordering = mutableStateOf(false),
                    )

                input.anchorTile(Rect(80f, 776f, 334f, 919f))

                input.anchor.value shouldBe Rect(80f, 536f, 334f, 679f)
            }

            test("the inputs list a remote key opens over a grid row leaves the group up") {
                showHome(inputs = flowOf(FourPorts))
                focusGridTile()

                val stray = strayOf("tile:pkg.7") { inputsRequests++ }

                compose.onNodeWithTag("menu").assertExists()
                withClue("the group strayed $stray dp") { stray shouldBeLessThanOrEqualTo 1f }
            }

            test("closing the menu of a grid tile leaves the group up") {
                showHome()
                focusGridTile()
                holdOk()
                compose.onNodeWithTag("menu").assertExists()

                val stray = strayOf("shelf:name") { back() }

                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                withClue("the group strayed $stray dp") { stray shouldBeLessThanOrEqualTo 1f }
            }

            test("focus moving from the grid to the dock starts the group moving on the first frame after the key") {
                showHome()
                focusGridTile()
                val up = bottom("shelf:name")
                compose.mainClock.autoAdvance = false

                compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
                compose.mainClock.advanceTimeBy(16)

                val lowered = (bottom("shelf:name") - up).value
                withClue("lowered $lowered dp one frame after the key") { lowered shouldBeGreaterThanOrEqualTo 4f }
            }

            test("closing the settings menu leaves the group low") {
                showHome()
                press(Key.DirectionUp)
                repeat(2) { press(Key.DirectionRight) }
                holdOk()
                compose.onNodeWithTag("menu").assertExists()

                val stray = strayOf("dock") { back() }

                compose.onNodeWithTag("menu").assertDoesNotExist()
                withClue("the group strayed $stray dp") { stray shouldBeLessThanOrEqualTo 1f }
            }

            test("closing the inputs list leaves the group low") {
                showHome(inputs = flowOf(FourPorts))
                press(Key.DirectionUp)
                press(Key.DirectionCenter)
                compose.onNodeWithTag("menu").assertExists()

                val stray = strayOf("dock") { back() }

                compose.onNodeWithTag("menu").assertDoesNotExist()
                withClue("the group strayed $stray dp") { stray shouldBeLessThanOrEqualTo 1f }
            }

            test("closing the inputs list opened over a grid row lowers the group once for the button focus returns to") {
                showHome(inputs = flowOf(FourPorts))
                focusGridTile()
                inputsRequests++
                compose.waitForIdle()
                compose.onNodeWithTag("menu").assertExists()
                val up = bottom("shelf:name")
                compose.mainClock.autoAdvance = false

                back()
                val bottoms =
                    List(30) {
                        compose.mainClock.advanceTimeBy(16)
                        bottom("shelf:name")
                    }

                compose.onNodeWithContentDescription("Inputs").assertIsFocused()
                assertNear(up + 104.dp, bottoms.last())
                withClue("the group moved back up on the way: $bottoms") {
                    (
                        bottoms.zipWithNext().all { (before, after) ->
                            after.value >=
                                before.value
                        }
                    ).shouldBeTrue()
                }
            }

            test("closing the library leaves the group up") {
                showHome()
                repeat(3) { press(Key.DirectionDown) }
                compose.onNodeWithTag("library:pill").assertIsFocused()
                press(Key.DirectionCenter)
                compose.onNodeWithTag("library").assertExists()
                compose.mainClock.advanceTimeBy(1_000)

                val stray = strayOf("shelf:name") { libraryOpen = false }

                compose.onNodeWithTag("library:pill").assertIsFocused()
                withClue("the group strayed $stray dp") { stray shouldBeLessThanOrEqualTo 1f }
            }

            test("with single row the dock does not lower and a tile menu moves nothing") {
                showHome(config = cinematic.copy(singleRow = true))
                val dock = top("dock")

                holdOk()

                compose.onNodeWithTag("menu").assertExists()
                assertNear(dock, top("dock"))
            }

            test("closing the menu of a grid tile leaves the grid where it was") {
                showHome()
                focusGridTile()
                val before = top("tile:pkg.7")
                holdOk()
                compose.onNodeWithTag("menu").assertExists()

                strayOf("tile:pkg.7") { back() }

                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                assertNear(before, top("tile:pkg.7"))
            }

            test("closing the library back to the pill leaves the grid where it was") {
                showHome()
                repeat(3) { press(Key.DirectionDown) }
                compose.onNodeWithTag("library:pill").assertIsFocused()
                val before = top("tile:pkg.7")
                press(Key.DirectionCenter)
                compose.onNodeWithTag("library").assertExists()
                compose.mainClock.advanceTimeBy(1_000)

                val stray = strayOf("tile:pkg.7") { libraryOpen = false }

                compose.onNodeWithTag("library:pill").assertIsFocused()
                assertNear(before, top("tile:pkg.7"))
                withClue("the grid strayed $stray dp") { stray shouldBeLessThanOrEqualTo 1f }
            }
        }
    })

private class DockLowerUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }

    val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = white) }

    val cinematic = SixColumns.copy(cinematicHome = true, appLibrary = true)

    var inputsRequests by mutableIntStateOf(0)

    var libraryOpen by mutableStateOf(false)

    fun showHome(
        motion: Motion = Motion(),
        config: TvConfig = cinematic,
        inputs: Flow<InputsMenu> = emptyFlow(),
    ) {
        val sources = sources(apps = flowOf(AppList(apps)), inputs = inputs)
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
                    shelfLayout = config.shelfLayout().copy(scrimAlpha = 0f),
                    actions = viewModel.actions,
                    inputsRequests = { inputsRequests },
                    libraryOpen = { libraryOpen },
                    onOpenLibrary = { libraryOpen = true },
                    overlays = { if (libraryOpen) LibraryStandIn() },
                )
            }
        }
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    /** An overlay that takes focus when it opens, as the App library does. */
    @Composable
    fun LibraryStandIn() {
        val focus = remember { FocusRequester() }
        Box(Modifier.testTag("library").focusRequester(focus).focusable())
        LaunchedEffect(Unit) { focus.requestFocus() }
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

    fun holdOkClockStopped() {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
    }

    fun top(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().top

    fun bottom(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().bottom

    fun assertNear(
        expected: Dp,
        actual: Dp,
        tolerance: Float = 1f,
    ) = withClue("expected $expected, got $actual") { abs(expected.value - actual.value) shouldBeLessThanOrEqualTo tolerance }

    fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(16)
    }

    /**
     * Runs [action] with the clock stopped, then steps 960 ms in frames; how far [tag] strays from the place it ends up at
     * (so that a scroll that settles somewhere else does not count), in dp. A frame in which [tag] is not there counts as 0.
     */
    fun strayOf(
        tag: String,
        action: () -> Unit,
    ): Float {
        compose.mainClock.autoAdvance = false
        action()
        val tops =
            List(60) {
                compose.mainClock.advanceTimeBy(16)
                if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) null else top(tag).value
            }
        val end = tops.last() ?: return 0f
        return tops.filterNotNull().maxOf { abs(it - end) }
    }

    fun focusGridTile() {
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
    }
}
