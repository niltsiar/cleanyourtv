package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputArrival
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

private const val DENSITY = 2
private val Console = InputArrival("PlayStation 5 · HDMI 2", InputTarget.TvInput("tv/.Cec/HDMI2"), token = 1)

/** app_shelf on the real presenter and screen: the cards' paths, their line, their look and what they leave alone. */
val AppShelfUiTest by testSuite {
    robolectricTestSuite<AppShelfUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class AppShelfUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { AppShelfUiTestFixture() } asContextForEach {
            test("up from a dock tile with a shelf goes to the first card, and left and right move along the cards and stop at the ends") {
                showHome(Cinematic.copy(appShelfSources = listOf("pkg.4")))
                repeat(3) { press(Key.DirectionRight) }
                hold()

                press(Key.DirectionUp)
                compose.onNodeWithTag("shelf:card:0").assertIsFocused()
                press(Key.DirectionRight)
                press(Key.DirectionRight)
                compose.onNodeWithTag("shelf:card:2").assertIsFocused()
                press(Key.DirectionRight)
                compose.onNodeWithTag("shelf:card:2").assertIsFocused()
                repeat(3) { press(Key.DirectionLeft) }
                compose.onNodeWithTag("shelf:card:0").assertIsFocused()
            }

            test("down from a card returns focus to its dock tile") {
                showHome()
                hold()
                press(Key.DirectionUp)
                press(Key.DirectionRight)

                press(Key.DirectionDown)

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
            }

            test("back from a card returns focus to its dock tile") {
                showHome()
                hold()
                press(Key.DirectionUp)
                press(Key.DirectionRight)

                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
            }

            test("down from the last card scrolls the row back to its first card as it moves along the cards") {
                items += (1..7).map { items[1].copy(title = "Show $it") }
                showHome()
                hold()
                press(Key.DirectionUp)
                val start = firstCardLeft()
                repeat(9) { press(Key.DirectionRight) }
                compose.onNodeWithTag("shelf:card:9").assertIsFocused()
                withClue("scrolled to the last card") { firstCardLeft() shouldBeLessThan start }

                compose.mainClock.autoAdvance = false
                press(Key.DirectionDown, settle = false)
                step(16)
                withClue("one frame after down") { firstCardLeft() shouldBeLessThan start }
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                firstCardLeft() shouldBe start
            }

            test("with reduced_motion back from the last card puts the row at its first card at once") {
                items += (1..7).map { items[1].copy(title = "Show $it") }
                showHome(motion = Motion(reducedMotion = true))
                hold()
                press(Key.DirectionUp)
                val start = firstCardLeft()
                repeat(9) { press(Key.DirectionRight) }
                withClue("scrolled to the last card") { firstCardLeft() shouldBeLessThan start }

                compose.mainClock.autoAdvance = false
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                step(16)

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                firstCardLeft() shouldBe start
            }

            test("up from a card goes to the top bar, as up from the dock does") {
                showHome()
                hold()
                press(Key.DirectionUp)
                compose.onNodeWithTag("shelf:card:0").assertIsFocused()

                press(Key.DirectionUp)

                compose.onNodeWithContentDescription("Inputs").assertIsFocused()
            }

            test("with input_arrival, up from a card goes to the inputs button while it offers a device") {
                items += listOf("Silo", "Pluribus").map { items[1].copy(title = it) }
                showHome(Cinematic.copy(inputArrival = true))
                hold()
                press(Key.DirectionUp)
                repeat(4) { press(Key.DirectionRight) }
                compose.onNodeWithTag("shelf:card:4").assertIsFocused()

                inputs.value = FourPorts.copy(arrival = Console)
                compose.mainClock.advanceTimeBy(2_100)
                press(Key.DirectionUp)

                compose.onNode(hasContentDescription("Inputs", substring = true)).assertIsFocused()
            }

            test("up from the next dock tile while the cards fade out does not land on a card") {
                showHome()
                hold()

                compose.mainClock.autoAdvance = false
                press(Key.DirectionRight, settle = false)
                step(16)
                press(Key.DirectionUp, settle = false)
                step(16)
                compose.onNodeWithTag("shelf:card:0").assertExists()

                compose.onNodeWithTag("tile:pkg.2").assertIsNotFocused()
                compose
                    .onAllNodes((hasTestTag("shelf:card:0") or hasTestTag("shelf:card:1") or hasTestTag("shelf:card:2")) and isFocused())
                    .assertCountEquals(0)
            }

            test("ok on a card opens its item with the app of its dock tile") {
                showHome()
                hold()
                press(Key.DirectionUp)
                press(Key.DirectionRight)

                press(Key.DirectionCenter)

                effects shouldBe listOf(HomeEffect.OpenShelfItem(apps[0], items[1]))
            }

            test("a card with focus whose shelf a new read finds empty hands focus to its dock tile") {
                showHome()
                hold()
                press(Key.DirectionUp)
                press(Key.DirectionRight)
                compose.onNodeWithTag("shelf:card:1").assertIsFocused()

                items.clear()
                compose.runOnUiThread { viewModel.onStarted() }
                compose.waitForIdle()

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                compose.onNodeWithTag("shelf:card:0").assertDoesNotExist()
            }

            test("a card with focus that a new read leaves out hands focus to its dock tile, and the shorter shelf shows") {
                showHome()
                hold()
                press(Key.DirectionUp)
                press(Key.DirectionRight)
                press(Key.DirectionRight)
                compose.onNodeWithTag("shelf:card:2").assertIsFocused()

                items.subList(1, items.size).clear()
                compose.runOnUiThread { viewModel.onStarted() }
                compose.waitForIdle()

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                compose.onNodeWithTag("shelf:card:0").assertExists()
                compose.onNodeWithTag("shelf:card:1").assertDoesNotExist()
            }

            test("with press_feedback ok on a card dips it to 96 percent of its focused size and back") {
                showHome(Cinematic.copy(pressFeedback = true))
                hold()
                press(Key.DirectionUp)
                val rest = redWidth()

                compose.mainClock.autoAdvance = false
                compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
                val widths =
                    List(16) {
                        compose.mainClock.advanceTimeBy(16)
                        redWidth() / rest
                    }

                withClue("widths $widths") { widths.min() shouldBeIn 0.955f..0.967f }
                withClue("back at rest: $widths") { widths.last() shouldBeIn 0.997f..1.003f }
            }

            test("with the cinematic shelf a focused card's line gives its title, season and episode and the minutes left") {
                showHome()
                hold()

                press(Key.DirectionUp)

                textOf("shelf:name") shouldBe "App 1"
                textOf("shelf:meta") shouldBe "Severance · S2 E4 · 32 min left"
                press(Key.DirectionRight)
                textOf("shelf:meta") shouldBe "Andor"
            }

            test("without the cinematic shelf a focused card's line gives its title, season and episode and the minutes left") {
                showHome(Plain)
                hold()

                press(Key.DirectionUp)

                textOf("shelf:title") shouldBe "App 1"
                textOf("shelf:title-meta") shouldBe "Severance · S2 E4 · 32 min left"
            }

            test("while focus stays on the dock tile the line under the title does not change as the cards show") {
                showHome()
                val before = textOf("shelf:meta")

                hold()

                compose.onNodeWithTag("shelf:card:0").assertExists()
                textOf("shelf:meta") shouldBe before
            }

            test("with app_shelf off a held dock tile shows no cards and up goes to the top bar") {
                showHome(Cinematic.copy(appShelf = false))
                hold()

                compose.onNodeWithTag("shelf:card:0").assertDoesNotExist()
                press(Key.DirectionUp)
                compose.onNodeWithContentDescription("Inputs").assertIsFocused()
                reads.shouldBeEmpty()
            }

            test("a dock tile whose app has no ticked sources shows no cards and up goes to the top bar") {
                showHome(Cinematic.copy(appShelfSources = listOf("pkg.2")))
                hold()

                compose.onNodeWithTag("shelf:card:0").assertDoesNotExist()
                press(Key.DirectionUp)
                compose.onNodeWithContentDescription("Inputs").assertIsFocused()
            }

            test("the dock and the title's place in the grid layout do not move as the cards show and go") {
                showHome()
                val dock = compose.onNodeWithTag("tile:pkg.1").getBoundsInRoot()
                val second = compose.onNodeWithTag("tile:pkg.2").getBoundsInRoot()

                hold()
                compose.onNodeWithTag("tile:pkg.1").getBoundsInRoot() shouldBe dock
                press(Key.DirectionUp)
                compose.onNodeWithTag("tile:pkg.1").getBoundsInRoot() shouldBe dock
                press(Key.DirectionDown)
                press(Key.DirectionRight)
                hold()

                compose.onNodeWithTag("shelf:card:0").assertDoesNotExist()
                compose.onNodeWithTag("tile:pkg.2").getBoundsInRoot() shouldBe second
            }

            test("the cards stand between the meta line and the dock, clear of both") {
                showHome()
                hold()

                val meta = compose.onNodeWithTag("shelf:meta").getBoundsInRoot()
                val card = compose.onNodeWithTag("shelf:card:0").getBoundsInRoot()
                val dock = compose.onNodeWithTag("dock").getBoundsInRoot()

                // A focused card grows 4% on each side and its ring reaches 9.72 dp past that.
                val ring = (card.bottom - card.top).value * 0.04f + 9.72f
                withClue("meta $meta, card $card") { (card.top - meta.bottom).value shouldBeIn ring..40f }
                withClue("card $card, dock $dock") { (dock.top - card.bottom).value shouldBeIn ring..40f }
                (card.bottom - card.top).value shouldBeIn ShelfCardMinHeight.value..ShelfCardHeight.value
            }

            test("the cards fade in over 200 ms and out as fast") {
                showHome()
                appear()

                step(100)
                withClue("half way in") { redness() shouldBeIn 0.2f..0.9f }
                step(150)
                withClue("in") { redness() shouldBeIn 0.97f..1f }
                press(Key.DirectionRight, settle = false)
                step(16)
                withClue("going out") { redness() shouldBeIn 0.1f..0.9f }
            }

            test("with reduced_motion the cards show and go at once") {
                showHome(motion = Motion(reducedMotion = true))
                appear()

                step(16)
                redness() shouldBeIn 0.97f..1f
                press(Key.DirectionRight, settle = false)
                step(16)
                compose.onNodeWithTag("shelf:card:0").assertDoesNotExist()
            }

            test("a card whose poster does not decode, such as an https one, shows the app's art under the item's title") {
                showHome()
                hold()

                textOf("shelf:card-title", unmerged = true) shouldBe "Andor"
            }

            test("with rows_below_dock 2 the layout has no room for the cards: nothing is read and up goes to the top bar") {
                showHome(Cinematic.copy(rowsBelowDock = 2))
                hold()

                compose.onNodeWithTag("shelf:card:0").assertDoesNotExist()
                reads.shouldBeEmpty()
                press(Key.DirectionUp)
                compose.onNodeWithContentDescription("Inputs").assertIsFocused()
            }
        }
    })

private val Cinematic = SixColumns.copy(cinematicHome = true, appShelf = true, appShelfSources = listOf("pkg.1"))
private val Plain = SixColumns.copy(appShelf = true, appShelfSources = listOf("pkg.1"))

private class AppShelfUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val effects = mutableListOf<HomeEffect>()

    val inputs = MutableStateFlow(FourPorts)

    val reads = mutableListOf<String>()

    lateinit var viewModel: MoleculeHomeViewModel

    val red =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.Red })
        }

    val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    val items =
        mutableListOf(
            ShelfItem(
                packageName = "pkg.1",
                title = "Severance",
                kind = ShelfKind.Continue,
                seasonNumber = "2",
                episodeNumber = "4",
                positionMs = 600_000,
                durationMs = 600_000 + 31 * 60_000 + 1,
                posterUri = "content://pkg.1/poster/severance",
            ),
            ShelfItem(packageName = "pkg.1", title = "Andor", kind = ShelfKind.Next, posterUri = "https://example.com/andor.jpg"),
            ShelfItem(packageName = "pkg.1", title = "Silo", kind = ShelfKind.Next, posterUri = "content://pkg.1/poster/silo"),
        )

    fun showHome(
        config: TvConfig = Cinematic,
        motion: Motion = Motion(),
    ) {
        val sources =
            sources(
                apps = flowOf(AppList(apps)),
                inputs = inputs,
                shelfItems = { pkg ->
                    reads += pkg
                    items.toList()
                },
                shelfPoster = { uri, _, _ -> if (uri.startsWith("content://")) red else null },
            )
        compose.setContent {
            TvTheme(motion = motion) {
                val viewModel = rememberHomeViewModel(sources, config, effects).also { viewModel = it }
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = viewModel::onClick,
                    topBar = config.topBarLayout(),
                    shelfLayout = config.shelfLayout().copy(scrimAlpha = 0f),
                    dockLook = config.dockLook(),
                    actions = viewModel.actions,
                    onOpenShelfItem = viewModel::onOpenShelfItem,
                    shelfPoster = sources.shelfPoster,
                    onShelfRoom = viewModel::onShelfRoom,
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

    /** Keeps focus where it is past the hold and the cards' fade. */
    fun hold() {
        compose.mainClock.advanceTimeBy(SHELF_HOLD_MS + 400)
        compose.waitForIdle()
    }

    /** Stops the clock and moves it on a frame at a time to the first frame that has the cards. */
    fun appear() {
        compose.mainClock.autoAdvance = false
        repeat(60) {
            step(16)
            if (compose.onAllNodesWithTag("shelf:card:0").fetchSemanticsNodes().isNotEmpty()) return
        }
        error("no cards in 960 ms")
    }

    fun step(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    fun press(
        key: Key,
        settle: Boolean = true,
    ) {
        compose.onRoot().performKeyInput { pressKey(key) }
        if (settle) compose.waitForIdle()
    }

    /** How much red, over green, the middle of the first card's red poster shows: its opacity over the backdrop. */
    fun redness(): Float {
        val image = compose.onRoot().captureToImage().toPixelMap()
        val bounds = compose.onNodeWithTag("shelf:card:0").getBoundsInRoot()
        val pixel =
            image[
                ((bounds.left.value + bounds.right.value) / 2f * DENSITY).roundToInt(),
                ((bounds.top.value + bounds.bottom.value) / 2f * DENSITY).roundToInt(),
            ]
        return pixel.red - pixel.green
    }

    fun textOf(
        tag: String,
        unmerged: Boolean = false,
    ): String =
        compose
            .onNodeWithTag(tag, useUnmergedTree = unmerged)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString("")
            .orEmpty()

    /** How many pixels of the first card's red poster its middle row covers. */
    fun redWidth(): Float {
        val image = compose.onRoot().captureToImage().toPixelMap()
        val bounds = compose.onNodeWithTag("shelf:card:0").getBoundsInRoot()
        val row = ((bounds.top.value + bounds.bottom.value) / 2f * DENSITY).roundToInt()
        val from = ((bounds.left.value - 14f) * DENSITY).roundToInt().coerceAtLeast(0)
        val to = ((bounds.right.value + 14f) * DENSITY).roundToInt().coerceAtMost(image.width - 1)
        var width = 0
        for (x in from..to) {
            val pixel = image[x, row]
            if (pixel.red > 0.8f && pixel.green < 0.25f && pixel.blue < 0.25f) width++
        }
        return width.toFloat()
    }

    fun firstCardLeft(): Float =
        compose
            .onNodeWithTag("shelf:card:0")
            .getBoundsInRoot()
            .left.value
}
