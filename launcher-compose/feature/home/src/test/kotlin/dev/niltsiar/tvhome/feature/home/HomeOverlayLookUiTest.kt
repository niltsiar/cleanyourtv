package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
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
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.Wallpaper
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs

/** The tile menu over a Home of white tiles, read from the rendered pixels. */
val HomeOverlayLookUiTest by testSuite {
    robolectricTestSuite<HomeOverlayLookUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
    robolectricTestSuite<HomeOverlayLookUiNightTestContent>(
        "sdk 31, night, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-night-xhdpi"
            },
    )
}

class HomeOverlayLookUiNightTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeOverlayLookUiTestFixture() } asContextForEach {
            test("the menu panel hides the white tile under it") {
                showHome()
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                openTileMenu()

                assertNear(0x14, redAt(gapOverATile("menu", above = "menu:Move", below = "menu:AppInfo")), 1)
            }

            test("a dock tiles menu of four rows ends at least 12 dp above the dock panel") {
                showHome(config = SixColumns.copy(appLibrary = true))
                openTileMenu()
                compose.onNodeWithTag("menu:RemoveFromHome").assertExists()
                compose.onNodeWithTag("menu:AppInfo").assertExists()

                val clearance = bounds("dock").top.value - bounds("menu").bottom.value
                withClue("the menu ends $clearance dp above the panel: ${bounds("dock")} ${bounds("menu")} ${bounds("tile:pkg.1")}") {
                    clearance shouldBeGreaterThanOrEqualTo
                        11.5f
                }
            }

            test("a dock tiles menu that fits under the top bar ends 20 dp above the dock panel") {
                showHome(config = SixColumns)
                openTileMenu()
                compose.onNodeWithTag("menu:RemoveFromHome").assertDoesNotExist()

                val clearance = bounds("dock").top.value - bounds("menu").bottom.value
                withClue("the menu ends $clearance dp above the panel") { clearance shouldBe (20f plusOrMinus 0.5f) }
            }

            test("the inputs list ends at least 24 dp above the dock panel and scrolls to the row in focus") {
                val ports = InputsMenu(available = true, rows = (1..6).map { port(it, InputConnection.Disconnected) } + InputRow.LiveTv)
                showHome(inputs = ports)
                press(Key.DirectionUp)
                press(Key.DirectionCenter)

                val clearance = bounds("dock").top.value - bounds("menu").bottom.value
                withClue("the list ends $clearance dp above the panel") { clearance shouldBeGreaterThanOrEqualTo 23.5f }

                repeat(6) { press(Key.DirectionDown) }
                compose.onNodeWithTag("input:6").assertIsFocused()
                withClue("the last row ends at ${bounds("input:6").bottom}") {
                    bounds("input:6").bottom.value shouldBeLessThanOrEqualTo
                        bounds("menu").bottom.value
                }
            }

            test("a grid tiles menu leaves no sliver of the next tile beside its right edge") {
                showHome(appCount = 20)
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                openTileMenu()

                val menu = bounds("menu")
                val middle = centreOf("tile:pkg.7").y
                withClue("the menu does not reach the tile row") { (middle > menu.top.value && middle < menu.bottom.value).shouldBeTrue() }
                // Pixels 2 to 22 dp past the menu's edge lie in the 24 dp gap between tile columns when it ends on a tile's edge.
                val ink = (2..22 step 2).map { redAt(Offset(menu.right.value + it, middle)) }
                withClue("tile ink beside the menu: $ink") { (ink.all { it < 40 }).shouldBeTrue() }
                withClue("no tile at the next column") { redAt(Offset(menu.right.value + 30f, middle)) shouldBeGreaterThan 100 }
            }

            test("an open menu dims home behind it by half and back lifts the dim") {
                showHome()
                assertNear(255, redAt(centreOf("tile:pkg.6")), 2)

                openTileMenu()
                assertNear((255 + 0x07) / 2, redAt(centreOf("tile:pkg.6")), 4)

                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                settle()
                compose.onNodeWithTag("menu").assertDoesNotExist()
                assertNear(255, redAt(centreOf("tile:pkg.6")), 2)
            }

            test(
                "the inputs list draws the dock at 055 under its dim and closing it leaves the dock at 075 with focus back on the top bar",
            ) {
                openInputs()

                assertNear(dimmedDock(0.55f), redAt(centreOf("tile:pkg.2")), 4)

                back()
                compose.onNodeWithTag("input:0").assertDoesNotExist()
                assertNear(Math.round(255 * 0.75f), redAt(centreOf("tile:pkg.2")), 6)
            }

            test(
                "the settings menu draws the dock at 055 under its dim and closing it leaves the dock at 075 with focus back on the top bar",
            ) {
                showHome()
                press(Key.DirectionUp)
                repeat(2) { press(Key.DirectionRight) }
                compose.onNodeWithContentDescription("Settings").assertIsFocused()
                openTileMenu()

                assertNear(dimmedDock(0.55f), redAt(centreOf("tile:pkg.2")), 4)

                back()
                compose.onNodeWithTag("menu").assertDoesNotExist()
                assertNear(Math.round(255 * 0.75f), redAt(centreOf("tile:pkg.2")), 6)
            }

            test("a dock tiles menu leaves the dock at full alpha under its dim") {
                showHome()
                openTileMenu()

                assertNear(dimmedDock(1f), redAt(centreOf("tile:pkg.6")), 4)
            }

            test("the app library hides every layer of home behind it and closing it brings them back") {
                showHome()
                val far = centreOf("tile:pkg.6")
                assertNear(255, redAt(far), 2)

                libraryOpen = true
                settle()

                // The wallpaper under the library's scrim, and nothing of Home's top bar, band, dock or grid over it.
                val scrimmed = Math.round(0x07 * 0.78f)
                assertNear(scrimmed, brightestOnScreen(), 2)

                libraryOpen = false
                settle()

                assertNear(255, redAt(far), 2)
            }

            test("the tile a menu acts on stays undimmed inside a thin white ring") {
                showHome()
                openTileMenu()

                val tile = bounds("tile:pkg.1")
                val middle = (tile.top.value + tile.bottom.value) / 2
                assertNear(255, redAt(Offset(tile.left.value + 8f, middle)), 2)
                // The dimmed dock, in the gap after the tile, and past the ring on its other side.
                val dimmed = redAt(Offset(tile.right.value + 12f, middle))
                assertNear(dimmed, redAt(Offset(tile.left.value - 4f, middle)), 3)
                // 1 dp out from the tile's edge, in the middle of the 2 dp ring: white at 0.70 over the dimmed dock.
                assertNear(Math.round(dimmed + 0.7f * (255 - dimmed)), redAt(Offset(tile.left.value - 1f, middle)), 8)
            }

            test("settings menu rows have corners concentric with the panel") {
                showHome()
                openSettingsMenu()
                compose.onNodeWithTag("menu:LauncherSettings").assertIsFocused()

                val row = bounds("menu:LauncherSettings")
                assertNear(0xF2, redAt(Offset(row.right.value - 12f, (row.top.value + row.bottom.value) / 2)), 3)
                // 5 dp in from the corner lies inside a 14 dp corner and outside a 20 dp one.
                redAt(Offset(row.left.value + 5f, row.top.value + 5f)) shouldBeLessThan 0x80
            }

            test(
                "a dock tiles menu sets its header off with a 1 dp line of 12 percent white 8 dp under it and keeps the rows 8 dp inside",
            ) {
                showHome()
                openTileMenu()

                val title = bounds("menu:title")
                val rule = bounds("menu:title:rule")
                val ground = redAt(Offset(rule.left.value + 4f, rule.top.value - 3f))
                val line = redAt(Offset((rule.left.value + rule.right.value) / 2, (rule.top.value + rule.bottom.value) / 2))

                assertNear(8f, rule.top.value - title.bottom.value, 0.5f)
                assertNear(1f, rule.height.value, 0.1f)
                assertNear(Math.round(ground + 0.12f * (255 - ground)), line, 3)
                assertNear(8f, focusedRowInset(), 0.5f)
            }

            test("the restart row draws its text in coral at rest and not while it has focus") {
                showHome()
                openSettingsMenu()
                val row = bounds("menu:PowerMenu")

                withClue("coral pixels at rest: ${pixelsOf(row, 0xFF8A80)}") { pixelsOf(row, 0xFF8A80) shouldBeGreaterThanOrEqualTo 8 }
                withClue("another row has no coral") { pixelsOf(bounds("menu:Reorder"), 0xFF8A80) shouldBe 0 }
                repeat(3) { press(Key.DirectionDown) }
                compose.onNodeWithTag("menu:PowerMenu").assertIsFocused()
                withClue("coral pixels under focus") { pixelsOf(row, 0xFF8A80) shouldBe 0 }
            }

            test("the line above the restart row is white at 018") {
                showHome()
                openSettingsMenu()

                val rule = compose.onNodeWithTag("menu:divider").getBoundsInRoot()
                val ground = redAt(Offset((rule.left.value + rule.right.value) / 2, rule.top.value - 3f))
                val line = redAt(Offset((rule.left.value + rule.right.value) / 2, (rule.top.value + rule.bottom.value) / 2))

                assertNear(Math.round(ground + 0.18f * (255 - ground)), line, 3)
            }

            test("a focused menu row is a light pill in night mode") {
                assertNear(0xF2, focusedRowRed(), 3)
            }

            test("an unfocused connected row shows the faint green capsule in night mode") {
                openInputs()
                assertCapsuleFill(row = 1, fillAlpha = 0x29)
            }

            test("a focused connected row shows a solid 146C2E capsule with white text") {
                openInputs()
                val text =
                    compose
                        .onNode(hasText("Connected") and hasAnyAncestor(hasTestTag("input:0")), useUnmergedTree = true)
                        .getBoundsInRoot()

                // The row's own fill is the light pill, red F2; the capsule's solid fill is red 14.
                assertNear(0x14, redAt(Offset(text.right.value + 4f, (text.top.value + text.bottom.value) / 2)), 3)
                withClue("ink ${brightestRedIn(text)}") { brightestRedIn(text) shouldBeGreaterThanOrEqualTo 0xE8 }
            }

            test("the no device label is at least 5 to 1 against the dark menu") {
                val ratio = noDeviceContrast(focused = false)
                withClue("contrast $ratio") { ratio shouldBeGreaterThanOrEqualTo 5f }
            }

            test("the no device label is at least 5 to 1 against the focused row of the dark menu") {
                val ratio = noDeviceContrast(focused = true)
                withClue("contrast $ratio") { ratio shouldBeGreaterThanOrEqualTo 5f }
            }

            test("the inputs button keeps its focused look at a fill of 060 while its list is open") {
                showHome()
                press(Key.DirectionUp)
                compose.onNodeWithContentDescription("Inputs").assertIsFocused()
                val focusedFill = buttonRed("Inputs", 4f)
                press(Key.DirectionCenter)
                compose.onNodeWithTag("input:0").assertIsFocused()

                assertFocusedLookAtSixtyPercent(opener = "Inputs", idle = "Settings", focusedFill = focusedFill)
            }

            test("the settings button keeps its focused look at a fill of 060 while its menu is open") {
                showHome()
                press(Key.DirectionUp)
                repeat(2) { press(Key.DirectionRight) }
                compose.onNodeWithContentDescription("Settings").assertIsFocused()
                val focusedFill = buttonRed("Settings", 4f)
                openTileMenu()

                assertFocusedLookAtSixtyPercent(opener = "Settings", idle = "Inputs", focusedFill = focusedFill)
            }

            test("the dock draws at 075 while focus is in the top bar and whole again once focus leaves it") {
                showHome()
                val whole = redAt(centreOf("tile:pkg.2"))
                press(Key.DirectionUp)
                compose.onNodeWithContentDescription("Inputs").assertIsFocused()
                val dimmed = redAt(centreOf("tile:pkg.2"))
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

                assertNear(255, whole, 2)
                // White art at 0.75 over the dark panel: 255 * 0.75 plus a quarter of what is behind it.
                withClue("a white tile reads $dimmed in top-bar focus") { dimmed shouldBeIn 188..206 }
                assertNear(255, redAt(centreOf("tile:pkg.2")), 2)
            }

            test("the inputs button is idle again once its list has closed") {
                openInputs()
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                settle()
                press(Key.DirectionDown)
                compose.onNodeWithTag("menu").assertDoesNotExist()
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

                assertNear(buttonRed("Settings", 4f), buttonRed("Inputs", 4f), 2)
            }

            test("the settings button reads as its menus anchor on a dark wallpaper with its glyph 4 5 to 1 and its quiet outline 3 to 1") {
                assertReadsAsAnchor(anchorContrast("Settings", 0f, ::openSettingsFromTopBar))
            }

            test(
                "the settings button reads as its menus anchor on a bright wallpaper with its glyph 4 5 to 1 and its quiet outline 3 to 1",
            ) {
                assertReadsAsAnchor(anchorContrast("Settings", 1f, ::openSettingsFromTopBar))
            }

            test("the inputs button reads as its lists anchor on a bright wallpaper with its glyph 4 5 to 1 and its quiet outline 3 to 1") {
                assertReadsAsAnchor(anchorContrast("Inputs", 1f, ::openInputsFromTopBar))
            }
        }
    })

class HomeOverlayLookUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeOverlayLookUiTestFixture() } asContextForEach {
            test("the focused row of a grid tiles menu is 8 dp inside the panel") {
                showHome()
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                openTileMenu()

                assertNear(8f, focusedRowInset(), 0.5f)
            }

            test("the focused row of the settings menu is 8 dp inside the panel") {
                showHome()
                openSettingsMenu()

                assertNear(8f, focusedRowInset(), 0.5f)
            }

            test("the focused row of the inputs list is 8 dp inside the panel") {
                openInputs()

                assertNear(8f, focusedRowInset(), 0.5f)
            }

            test("the settings menu sets its restart row apart with the inputs lists line") {
                showHome()
                openSettingsMenu()

                val line = compose.onNodeWithTag("menu:divider").getBoundsInRoot()
                withClue("the line is ${line.height} dp tall") { abs(line.height.value - 5f) shouldBeLessThanOrEqualTo 0.5f }
                withClue("the line ${line.top} is under the Reorder row ${bounds("menu:Reorder")}") {
                    line.top shouldBeGreaterThanOrEqualTo
                        bounds("menu:Reorder").bottom
                }
                withClue("the line ${line.bottom} is over the restart row ${bounds("menu:PowerMenu")}") {
                    line.bottom shouldBeLessThanOrEqualTo
                        bounds("menu:PowerMenu").top
                }
            }

            test("on the light menu the restart row draws its text in dark red at rest and not the pale coral") {
                showHome()
                openSettingsMenu()
                val row = bounds("menu:PowerMenu")

                withClue("dark red pixels at rest: ${pixelsOf(row, 0xC62828)}") { pixelsOf(row, 0xC62828) shouldBeGreaterThanOrEqualTo 8 }
                withClue("pale coral on the light menu") { pixelsOf(row, 0xFF8A80) shouldBe 0 }
                withClue("another row has no red") { pixelsOf(bounds("menu:Reorder"), 0xC62828) shouldBe 0 }
            }

            test("the inputs list does not end on its line when the row after it does not fit") {
                showHome(inputs = InputsMenu(available = true, rows = (1..4).map { port(it) } + InputRow.Separator() + InputRow.LiveTv))
                press(Key.DirectionUp)
                press(Key.DirectionCenter)
                compose.onNodeWithTag("input:0").assertIsFocused()

                val list = bounds("menu")
                val line = compose.onNodeWithTag("menu:divider").getUnclippedBoundsInRoot()
                val live = compose.onNodeWithTag("input:5").getUnclippedBoundsInRoot()
                withClue("the last row of the list does not fit: list $list, live tv $live") { live.bottom shouldBeGreaterThan list.bottom }
                withClue("the line $line shows at the bottom of the list $list") { line.top shouldBeGreaterThanOrEqualTo list.bottom }
            }

            test("the inputs list ends on the right edge of the top bar") {
                val chat = (1..3).map { AppEntry(key = "chat.$it", packageName = "chat.$it", label = "Chat $it") }
                showHome(inputs = InputsMenu(available = true, rows = listOf(port(1))), topBarApps = chat)
                press(Key.DirectionUp)
                compose.onNodeWithContentDescription("Inputs").assertIsFocused()
                press(Key.DirectionCenter)

                val settings = compose.onNodeWithContentDescription("Settings").getBoundsInRoot()
                assertNear(settings.right.value, bounds("menu").right.value, 1f)
            }

            test("a focused menu row is a dark pill with night mode off") {
                assertNear(0x11, focusedRowRed(), 3)
            }

            test("the no device label is at least 5 to 1 against the light menu") {
                val ratio = noDeviceContrast(focused = false)
                withClue("contrast $ratio") { ratio shouldBeGreaterThanOrEqualTo 5f }
            }

            test("the no device label is at least 5 to 1 against the focused row of the light menu") {
                val ratio = noDeviceContrast(focused = true)
                withClue("contrast $ratio") { ratio shouldBeGreaterThanOrEqualTo 5f }
            }

            test("an unfocused connected row shows the stronger green capsule on the light surface") {
                openInputs()
                assertCapsuleFill(row = 1, fillAlpha = 0x2E)
            }

            test("on the light theme the settings button reads as its menus anchor on a bright wallpaper") {
                assertReadsAsAnchor(anchorContrast("Settings", 1f, ::openSettingsFromTopBar))
            }

            test("on the light theme the inputs button reads as its lists anchor on a dark wallpaper") {
                assertReadsAsAnchor(anchorContrast("Inputs", 0f, ::openInputsFromTopBar))
            }

            test("the input list the system opens with no inputs button on screen draws the plain scrim and no ring") {
                showHome(config = SixColumns.copy(showInputsPill = false))
                compose.onNodeWithContentDescription("Inputs").assertDoesNotExist()

                inputsRequests++
                settle()

                compose.onNodeWithTag("input:0").assertExists()
                assertNear(redAt(Offset(40f, 3f)), redAt(Offset(1f, 1f)), 1)
            }
        }
    })

private class HomeOverlayLookUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }

    fun grey(level: Float) =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color(level, level, level) })
        }

    fun appsOf(count: Int) = (1..count).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = white) }

    var apps = appsOf(9)

    var libraryOpen by mutableStateOf(false)

    var inputsRequests by mutableIntStateOf(0)

    fun showHome(
        appCount: Int = 9,
        inputs: InputsMenu = FourPorts,
        config: TvConfig = SixColumns,
        topBarApps: List<AppEntry> = emptyList(),
        wallpaper: Float? = null,
    ) {
        apps = appsOf(appCount)
        val picture = wallpaper?.let { grey(it) }
        val sources =
            sources(
                apps = flowOf(AppList(apps)),
                topBarApps = flowOf(topBarApps),
                inputs = flowOf(inputs),
                wallpaper = flowOf(picture?.let { Wallpaper(picture = it, thumbnail = it) } ?: Wallpaper()),
                powerMenuAvailable = { true },
            )
        compose.setContent {
            CompositionLocalProvider(LocalBakeDispatcher provides Dispatchers.Unconfined) {
                TvTheme {
                    val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                    val state = viewModel.uiState.collectAsState()
                    HomeScreen(
                        state = { state.value },
                        layout = config.homeLayout(),
                        onFocus = viewModel::onFocus,
                        onClick = {},
                        topBar = config.topBarLayout(),
                        actions = viewModel.actions,
                        libraryOpen = { libraryOpen },
                        inputsRequests = { inputsRequests },
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

    fun openTileMenu() {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
        compose.onNodeWithTag("menu").assertExists()
    }

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    fun centreOf(tag: String): Offset =
        bounds(tag).let {
            Offset(
                (it.left.value + it.right.value) / 2,
                (it.top.value + it.bottom.value) / 2,
            )
        }

    /** The red channel, 0 to 255, of the screen at [point] (in dp). */
    fun redAt(point: Offset): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(pixels[(point.x * density).toInt(), (point.y * density).toInt()].red * 255)
    }

    fun assertNear(
        expected: Int,
        actual: Int,
        tolerance: Int,
    ) = withClue("expected $expected, got $actual") { abs(actual - expected) shouldBeLessThanOrEqualTo tolerance }

    fun assertNear(
        expected: Float,
        actual: Float,
        tolerance: Float,
    ) = withClue("expected $expected, got $actual") { abs(actual - expected) shouldBeLessThanOrEqualTo tolerance }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    /** A point in the panel [menu], in the gap between its rows [above] and [below], that a tile lies under. */
    fun gapOverATile(
        menu: String,
        above: String,
        below: String,
    ): Offset {
        val panel = bounds(menu)
        val y = (bounds(above).bottom.value + bounds(below).top.value) / 2
        val x =
            apps
                .map { bounds("tile:${it.key}") }
                .filter { it.top.value <= y && y <= it.bottom.value }
                .map { (it.left.value + it.right.value) / 2 }
                .firstOrNull { panel.left.value < it && it < panel.right.value }
        return Offset(checkNotNull(x) { "no tile under the panel $panel at y $y" }, y)
    }

    fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    /** What a white dock tile shows under the 0.5 dim of an open popup, once the dock is drawn at [alpha] over the black wallpaper. */
    fun dimmedDock(alpha: Float) = Math.round((255 * alpha + 0x07) / 2)

    /** The brightest red channel, 0 to 255, anywhere on the screen. */
    fun brightestOnScreen(): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var most = 0f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) most = maxOf(most, pixels[x, y].red)
        return Math.round(most * 255)
    }

    fun openSettingsMenu() {
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        settle()
        repeat(2) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            settle()
        }
        compose.onNodeWithContentDescription("Settings").assertIsFocused()
        openTileMenu()
    }

    /** How far the focused row of the open menu is from the panel's left edge, in dp. */
    fun focusedRowInset(): Float {
        val row = compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("menu"))).getBoundsInRoot()
        return row.left.value - bounds("menu").left.value
    }

    /** The pixels of the screen in [area] that are [colour] to within 14 in each channel. */
    fun pixelsOf(
        area: DpRect,
        colour: Int,
    ): Int {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (area.left.value * density).toInt() until (area.right.value * density).toInt()
        val ys = (area.top.value * density).toInt() until (area.bottom.value * density).toInt()
        return xs.sumOf { x ->
            ys.count { y ->
                val pixel = pixels[x, y]
                abs(pixel.red * 255 - (colour shr 16 and 0xFF)) <= 14 &&
                    abs(pixel.green * 255 - (colour shr 8 and 0xFF)) <= 14 &&
                    abs(pixel.blue * 255 - (colour and 0xFF)) <= 14
            }
        }
    }

    /** The red channel at the empty right end of the focused first row of the open tile menu. */
    fun focusedRowRed(): Int {
        showHome()
        openTileMenu()
        compose.onNodeWithTag("menu:ReplaceInDock").assertIsFocused()
        val row = bounds("menu:ReplaceInDock")
        return redAt(Offset(row.right.value - 12f, (row.top.value + row.bottom.value) / 2))
    }

    fun openInputs() {
        showHome()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        settle()
        compose.onNodeWithContentDescription("Inputs").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        settle()
        compose.onNodeWithTag("input:0").assertIsFocused()
    }

    /**
     * The red channel inside the "Connected" capsule of the Inputs row [row], just right of its text, against the one
     * the row shows right of the capsule, blended as a capsule fill of [fillAlpha] would blend #34C759 over it.
     */
    fun assertCapsuleFill(
        row: Int,
        fillAlpha: Int,
    ) {
        val text =
            compose
                .onNode(hasText("Connected") and hasAnyAncestor(hasTestTag("input:$row")), useUnmergedTree = true)
                .getBoundsInRoot()
        val bounds = bounds("input:$row")
        val middle = (text.top.value + text.bottom.value) / 2
        val behind = redAt(Offset(bounds.right.value - 6f, middle))
        assertNear(behind + Math.round((0x34 - behind) * fillAlpha / 255f), redAt(Offset(text.right.value + 4f, middle)), 3)
    }

    /** The brightest red channel, 0 to 255, inside [box] (in dp): a glyph's stem shows its ink at full strength. */
    fun brightestRedIn(box: DpRect): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return (box.top.value * density).toInt().until((box.bottom.value * density).toInt()).maxOf { y ->
            (box.left.value * density).toInt().until((box.right.value * density).toInt()).maxOf { x -> Math.round(pixels[x, y].red * 255) }
        }
    }

    /**
     * The contrast ratio of the "No device" ink of the Inputs row 2 against the row beside the label, which has the focus
     * when [focused]. A glyph's stem shows its ink at full strength: it is the pixel farthest in luminance from the row.
     */
    fun noDeviceContrast(focused: Boolean): Float {
        openInputs()
        if (focused) repeat(2) { press(Key.DirectionDown) }
        val text =
            compose
                .onNode(hasText("No device") and hasAnyAncestor(hasTestTag("input:2")), useUnmergedTree = true)
                .getBoundsInRoot()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        val row =
            pixels[((text.right.value + 6f) * density).toInt(), (((text.top.value + text.bottom.value) / 2) * density).toInt()]
                .luminance()
        val ink =
            (text.top.value * density).toInt().until((text.bottom.value * density).toInt()).flatMap { y ->
                (text.left.value * density).toInt().until((text.right.value * density).toInt()).map { x -> pixels[x, y].luminance() }
            }
        val far = ink.maxBy { abs(it - row) }
        return (maxOf(far, row) + 0.05f) / (minOf(far, row) + 0.05f)
    }

    /** The red channel of the top-bar button described as [description], [inset] dp in from its left edge, at its middle. */
    fun buttonRed(
        description: String,
        inset: Float,
    ): Int =
        compose.onNodeWithContentDescription(description).getBoundsInRoot().let {
            redAt(Offset(it.left.value + inset, (it.top.value + it.bottom.value) / 2))
        }

    /** The darkest red channel, 0 to 255, over the button described as [description]: its glyph's ink when the fill is light. */
    fun darkestRedOver(description: String): Int {
        val box = compose.onNodeWithContentDescription(description).getBoundsInRoot()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return (box.top.value * density).toInt().until((box.bottom.value * density).toInt()).minOf { y ->
            (box.left.value * density).toInt().until((box.right.value * density).toInt()).minOf { x -> Math.round(pixels[x, y].red * 255) }
        }
    }

    /** A red channel read through the menu's scrim (half of 07080C over it) as it was under it. */
    fun withoutMenuScrim(red: Int): Float = (red - 7 * 0.5f) / 0.5f

    /**
     * While its panel is open, [opener] stands outside the scrim and is drawn as when focused, with the white fill at 0.60: its
     * fill, 4 dp in from its edge, is 60 percent of the way from the idle [idle] button's to what [opener] read when focused,
     * and its glyph is dark.
     */
    fun assertFocusedLookAtSixtyPercent(
        opener: String,
        idle: String,
        focusedFill: Int,
    ) {
        val fill = buttonRed(opener, 4f).toFloat()
        val rest = withoutMenuScrim(buttonRed(idle, 4f))
        val share = (fill - rest) / (focusedFill - rest)
        withClue("fill $fill of idle $rest and focused $focusedFill: $share") { share shouldBeIn 0.52f..0.68f }
        withClue("the glyph's darkest red ${darkestRedOver(opener)}, fill $fill") {
            darkestRedOver(opener).toFloat() shouldBeLessThan
                fill / 2 - 30
        }
    }

    /** The contrast of two colours. */
    fun ratio(
        a: Color,
        b: Color,
    ): Float = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    /**
     * With the panel of the button described as [description] open over [wallpaper]: the contrast of the button's glyph against
     * its fill, then of its outline (1 dp out from the left edge) against the dimmed surround 5 dp out.
     */
    fun anchorContrast(
        description: String,
        wallpaper: Float,
        open: () -> Unit,
    ): Pair<Float, Float> {
        showHome(wallpaper = wallpaper)
        press(Key.DirectionUp)
        open()
        val box = compose.onNodeWithContentDescription(description).getBoundsInRoot()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        val middle = (box.top.value + box.bottom.value) / 2

        fun at(
            x: Float,
            y: Float = middle,
        ) = pixels[(x * density).toInt(), (y * density).toInt()]
        val fill = at(box.left.value + 4f)
        val centre = (box.left.value + box.right.value) / 2
        val glyph =
            (-6..5).flatMap { dy -> (-6..5).map { dx -> at(centre + dx, middle + dy) } }.maxBy { abs(it.luminance() - fill.luminance()) }
        return ratio(glyph, fill) to ratio(at(box.left.value - 1f), at(box.left.value - 5f))
    }

    fun openSettingsFromTopBar() {
        repeat(2) { press(Key.DirectionRight) }
        compose.onNodeWithContentDescription("Settings").assertIsFocused()
        openTileMenu()
    }

    fun openInputsFromTopBar() {
        press(Key.DirectionCenter)
        compose.onNodeWithTag("input:0").assertIsFocused()
    }

    /** The glyph reads 4.5:1 on the fill, and the outline 3:1 on the surround but no more than 5.5:1: it is quieter than focus. */
    fun assertReadsAsAnchor(contrast: Pair<Float, Float>) {
        withClue("glyph on fill ${contrast.first}, outline on surround ${contrast.second}") {
            (
                contrast.first >= 4.5f &&
                    contrast.second in 3f..5.5f
            ).shouldBeTrue()
        }
    }
}
