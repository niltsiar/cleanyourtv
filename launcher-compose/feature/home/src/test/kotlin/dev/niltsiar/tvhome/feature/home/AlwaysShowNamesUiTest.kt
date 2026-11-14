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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs

/** always_show_names puts the app's name under every tile, dimmed until it has focus, and makes room for it only then. */
val AlwaysShowNamesUiTest by testSuite {
    robolectricTestSuite<AlwaysShowNamesUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
    robolectricTestSuite<AlwaysShowNamesUiH603TestContent>(
        "sdk 31, 960x603 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h603dp-xhdpi"
            },
    )
    robolectricTestSuite<AlwaysShowNamesUiH613TestContent>(
        "sdk 31, 960x613 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h613dp-xhdpi"
            },
    )
    robolectricTestSuite<AlwaysShowNamesUiH1080TestContent>(
        "sdk 31, 960x1080 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h1080dp-xhdpi"
            },
    )
    robolectricTestSuite<AlwaysShowNamesUiNightTestContent>(
        "sdk 31, night, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-night-xhdpi"
            },
    )
}

class AlwaysShowNamesUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { AlwaysShowNamesUiTestFixture() } asContextForEach {
            test("the focused tile s cut name scrolls and a cut name without focus stays still") {
                showHome(TvConfig(columns = 4, alwaysShowNames = true), apps = longNames)
                // Waiting for idle would run a whole pass and back to its start.
                compose.mainClock.autoAdvance = false
                val focused = frame("name:pkg.1")
                val other = frame("name:pkg.2")

                compose.mainClock.advanceTimeBy(2_000)

                withClue("the focused cut name did not move") { frame("name:pkg.1") shouldNotBe focused }
                frame("name:pkg.2") shouldBe other
            }

            test("with reduced motion the focused tile s cut name stays still") {
                showHome(TvConfig(columns = 4, alwaysShowNames = true), apps = longNames, motion = Motion(reducedMotion = true))
                // Waiting for idle would run a whole pass and back to its start.
                compose.mainClock.autoAdvance = false
                val focused = frame("name:pkg.1")

                compose.mainClock.advanceTimeBy(2_000)

                frame("name:pkg.1") shouldBe focused
            }

            test("a name cut to two lines on a text tile ends in an ellipsis with no space before it") {
                // Words of 1 to 6 letters, so the cut falls at a different place in each name.
                val names = (1..6).map { size -> List(30) { "x".repeat(size) }.joinToString(" ") }
                showHome(SixColumns, apps = names.mapIndexed { i, name -> AppEntry("pkg.${i + 1}", "pkg.${i + 1}", name) })

                val shown =
                    (1..6).map { i ->
                        compose
                            .onNode(hasText("x", substring = true) and hasAnyAncestor(hasTestTag("tile:pkg.$i")), useUnmergedTree = true)
                            .fetchSemanticsNode()
                            .config[SemanticsProperties.Text]
                            .joinToString("") { it.text }
                    }

                withClue("$shown") { (shown.all { it.endsWith(Char(0x2026).toString()) }).shouldBeTrue() }
                withClue("$shown") { (shown.none { it.endsWith(" " + Char(0x2026)) }).shouldBeTrue() }
            }

            test("with the key off no tile has a name under it") {
                showHome(SixColumns)

                compose.onAllNodesWithTag("name:pkg.1").fetchSemanticsNodes().size shouldBe 0
                compose.onAllNodesWithTag("name:pkg.7").fetchSemanticsNodes().size shouldBe 0
            }

            test("a name on its pill is white ink with the surfaces light too") {
                showHome(SixColumns.copy(alwaysShowNames = true))

                ink("name:pkg.2") shouldBe (0.85f plusOrMinus 0.05f)
            }

            test("the name under an unfocused dock tile shows at 85 percent") {
                showHome(SixColumns.copy(alwaysShowNames = true))

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                ink("name:pkg.2") shouldBe (0.85f plusOrMinus 0.05f)
            }

            test("the name under an unfocused grid tile shows at 85 percent") {
                showHome(SixColumns.copy(alwaysShowNames = true))

                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                ink("name:pkg.8") shouldBe (0.85f plusOrMinus 0.05f)
            }

            test("a grid name sits on a black 055 pill 6 dp wider than its text on each side") {
                showHome(SixColumns.copy(alwaysShowNames = true), wallpaper = Wallpaper(picture = white))
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                val (bareLeft, bareRight) = besideName("name:pkg.9", out = 9f)
                val (padLeft, padRight) = besideName("name:pkg.9", out = 3f)

                // The text's own shadow darkens its padding a little too, so the pill is "under half of the bare picture".
                withClue("left $padLeft of $bareLeft") { padLeft.toFloat() shouldBeLessThan bareLeft * 0.5f }
                withClue("right $padRight of $bareRight") { padRight.toFloat() shouldBeLessThan bareRight * 0.5f }
            }

            test("a dock name sits on a black 055 pill 6 dp wider than its text on each side") {
                showHome(SixColumns.copy(alwaysShowNames = true), wallpaper = Wallpaper(picture = white))

                val (bareLeft, bareRight) = besideName("name:pkg.4", out = 9f)
                val (padLeft, padRight) = besideName("name:pkg.4", out = 3f)

                withClue("left $padLeft of $bareLeft") { padLeft.toFloat() shouldBeLessThan bareLeft * 0.5f }
                withClue("right $padRight of $bareRight") { padRight.toFloat() shouldBeLessThan bareRight * 0.5f }
            }

            test("with the key off the dock panel is a tile and two gaps tall") {
                showHome(SixColumns)
                val tile = height("tile:pkg.1")
                assertNear(tile + TileGap * 2, height("dock"))
            }

            test("the dock panel grows by the room of a name with the key on") {
                showHome(SixColumns.copy(alwaysShowNames = true))
                val tile = height("tile:pkg.1")
                assertNear(tile + TileGap * 2 + TileNameRoom, height("dock"))
            }

            test("a grid row is a tile and a gap tall with the key off") {
                showHome(SixColumns)
                assertNear(height("tile:pkg.1") + TileGap, rowPitch())
            }

            test("a grid row grows by the room of a name with the key on") {
                showHome(SixColumns.copy(alwaysShowNames = true))
                assertNear(height("tile:pkg.1") + TileGap + TileNameRoom, rowPitch())
            }

            test("with names no grid row shows a strip of under 48 dp of its tile at any scroll step") {
                assertNoStripAtAnyScrollStep(named4)
            }

            test("with names on a 1080p screen with focus on the first grid row the second draws with at least 48 dp of its tile") {
                showHome(named4, apps = whiteApps)
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.5").assertIsFocused()

                withClue("the second row draws ${drawnDp("tile:pkg.9")} dp of its tile") {
                    drawnDp("tile:pkg.9") shouldBeGreaterThanOrEqualTo
                        48f
                }
            }

            test("with names a grid row whose tiles end inside the screen draws its tile whole though the names hang over the edge") {
                showHome(named4, apps = whiteApps)
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.5").assertIsFocused()

                val screen =
                    compose
                        .onRoot()
                        .getBoundsInRoot()
                        .bottom.value
                val tileBottom = bounds("tile:pkg.9").bottom.value
                withClue("the tiles end at $tileBottom of $screen") { tileBottom shouldBeLessThanOrEqualTo screen }
                withClue("the pills end at ${tileBottom + 14f + 24f} of $screen") { tileBottom + 14f + 24f shouldBeGreaterThan screen }
                val low = bounds("tile:pkg.9")
                val red = redAt((low.left.value + low.right.value) / 2f, low.bottom.value - 3f)
                withClue("the tile's bottom edge shows red $red of 255") { red shouldBeGreaterThanOrEqualTo 250 }
            }

            test("without names on a 1080p screen with focus on the first grid row the second draws whole") {
                showHome(SixColumns.copy(columns = 4), apps = whiteApps)
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.5").assertIsFocused()

                withClue("the second row draws ${drawnDp("tile:pkg.9")} dp of its tile") {
                    drawnDp("tile:pkg.9") shouldBeGreaterThanOrEqualTo
                        height("tile:pkg.9").value - 1f
                }
            }

            test("without names no grid row shows a strip of under 48 dp of its tile at any scroll step") {
                assertNoStripAtAnyScrollStep(SixColumns.copy(columns = 4))
            }

            test("in a single row every tile has its name and the panel grows to hold them") {
                showHome(SixColumns.copy(singleRow = true, alwaysShowNames = true))

                compose.onAllNodesWithTag("name:pkg.5").fetchSemanticsNodes().size shouldBe 1
                assertNear(height("tile:pkg.1") + TileGap * 2 + TileNameRoom, height("dock"))
            }

            test("a name too long for its tile stays on one line in a pill no wider than the tile") {
                showHome(named4, apps = withLongName("pkg.1"))

                assertNear(height("name:pkg.2"), height("name:pkg.1"))
                // The pill is the name's box and 6 dp of padding on each side.
                val pill = bounds("name:pkg.1").width.value + 12f
                val tile = bounds("tile:pkg.1").width.value
                withClue(
                    "the pill is $pill dp wide under a tile of $tile dp",
                ) { (pill <= tile + 0.5f && pill >= tile - 16f).shouldBeTrue() }
            }

            test("a name too long for its tile does not make its row taller") {
                showHome(SixColumns.copy(alwaysShowNames = true), apps = withLongName("pkg.7"))

                assertNear(height("tile:pkg.1") + TileGap + TileNameRoom, rowPitch())
            }

            test("the name pills of the dock share one top with a focused tile in it at every point of the focus growth") {
                showHome(named4)
                compose.mainClock.autoAdvance = false
                compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
                for (elapsed in listOf(0L, 40L, 80L, 400L)) {
                    compose.mainClock.advanceTimeBy(elapsed)
                    val tops = pillTops("pkg.1", "pkg.2", "pkg.3", "pkg.4")
                    withClue("the dock pills are at $tops after $elapsed ms") { (tops.all { abs(it - tops[0]) < 0.01f }).shouldBeTrue() }
                }
            }

            test("the name pills of a grid row share one top with a focused tile in it at every point of the focus growth") {
                showHome(named4)
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.5").assertIsFocused()
                compose.mainClock.autoAdvance = false
                compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
                for (elapsed in listOf(0L, 40L, 80L, 400L)) {
                    compose.mainClock.advanceTimeBy(elapsed)
                    val tops = pillTops("pkg.5", "pkg.6", "pkg.7", "pkg.8")
                    withClue("the grid pills are at $tops after $elapsed ms") { (tops.all { abs(it - tops[0]) < 0.01f }).shouldBeTrue() }
                }
            }

            test("an unfocused grid tiles name pill is 14 dp under its tile") {
                showHome(named4)
                press(Key.DirectionDown)

                tileToPill("pkg.2") shouldBe (14f plusOrMinus 0.01f)
                tileToPill("pkg.6") shouldBe (14f plusOrMinus 0.01f)
            }

            test("an unfocused dock tiles name pill is 14 dp under its tile") {
                showHome(named4)

                tileToPill("pkg.2") shouldBe (14f plusOrMinus 0.01f)
            }

            test("the space under an unfocused grid name pill is at least one and a half times the space over it") {
                showHome(named4)
                press(Key.DirectionDown)

                val under = bounds("tile:pkg.10").top.value - (bounds("name:pkg.6").bottom.value + 2f)
                withClue("$under dp under the pill, ${tileToPill("pkg.6")} over it") {
                    under shouldBeGreaterThanOrEqualTo
                        1.5f * tileToPill("pkg.6")
                }
            }

            test("the space under an unfocused dock name pill is at least one and a half times the space over it") {
                showHome(named4)

                val under = bounds("dock").bottom.value - (bounds("name:pkg.2").bottom.value + 2f)
                withClue("$under dp under the pill, ${tileToPill("pkg.2")} over it") {
                    under shouldBeGreaterThanOrEqualTo
                        1.5f * tileToPill("pkg.2")
                }
            }

            test("a name pill is a capsule of the height of one line also when the name is cut") {
                showHome(named4, wallpaper = Wallpaper(picture = white), apps = withLongName("pkg.2"))
                val bare = inPill("name:pkg.3", -6f, 12f)

                // One line: 24 dp tall, so its ends are half circles of 12 dp.
                for (key in listOf("pkg.3", "pkg.2")) {
                    withClue("$key, corner ${inPill("name:$key", 2.5f, 2.5f)} of $bare") {
                        inPill("name:$key", 2.5f, 2.5f).toFloat() shouldBeGreaterThan
                            bare * 0.8f
                    }
                    withClue("$key, end ${inPill("name:$key", 1.2f, 12f)} of $bare") {
                        inPill("name:$key", 1.2f, 12f).toFloat() shouldBeLessThan
                            bare * 0.6f
                    }
                    assertNear(24.dp, (bounds("name:$key").height.value + 4f).dp)
                }
            }

            test("with names the tiles and the dock keep their names off width and x") {
                showHome(named4)
                val gridWidth =
                    compose
                        .onRoot()
                        .getBoundsInRoot()
                        .width.value - ScreenMargin.value * 2
                val tile = ((gridWidth - TileGap.value * 3) / 4).dp

                // The dock tiles stand inside the panel's padding, which leaves their ring room at the wall.
                val dockTile = bounds("tile:pkg.1")
                withClue("the dock tile is $dockTile dp") { dockTile.width.value shouldBeIn tile.value - 6f..tile.value }
                withClue("the dock tile starts at ${dockTile.left}") {
                    dockTile.left.value shouldBeIn
                        ScreenMargin.value..ScreenMargin.value + 8f
                }
                assertNear(ScreenMargin - TileGap, left("dock"))
                assertNear((gridWidth + TileGap.value * 2).dp, bounds("dock").width)
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.5").assertIsFocused()
                assertNear(tile, bounds("tile:pkg.5").width)
                assertNear(ScreenMargin, left("tile:pkg.5"))
            }

            test("with names a shrunk grid stays centred between the margins as it does without") {
                showHome(SixColumns.copy(columns = 4, rowsBelowDock = 3, alwaysShowNames = true))
                press(Key.DirectionDown)

                withClue(
                    "the grid starts at ${left("tile:pkg.5")}",
                ) { left("tile:pkg.5").value shouldBeGreaterThan ScreenMargin.value + 4f }
            }

            test("without names a shrunk grid stays centred between the margins") {
                showHome(SixColumns.copy(columns = 4, rowsBelowDock = 3))
                press(Key.DirectionDown)

                withClue(
                    "the grid starts at ${left("tile:pkg.5")}",
                ) { left("tile:pkg.5").value shouldBeGreaterThan ScreenMargin.value + 4f }
            }
        }
    })

class AlwaysShowNamesUiH603TestContent :
    RobolectricTestSuiteContent({
        testFixture { AlwaysShowNamesUiTestFixture() } asContextForEach {
            test("with names a grid row with under 48 dp visible draws nothing and leaves no band") {
                showHome(named4, apps = whiteApps)

                withClue("the row shows ${visibleDp("tile:pkg.5")} dp") { visibleDp("tile:pkg.5") shouldBeIn 30f..48f }
                redUnderTop("tile:pkg.5", 2f) shouldBe 0
                redUnderTop("tile:pkg.5", 10f) shouldBe 0
            }
        }
    })

class AlwaysShowNamesUiH613TestContent :
    RobolectricTestSuiteContent({
        testFixture { AlwaysShowNamesUiTestFixture() } asContextForEach {
            test("with names a grid row with 48 dp above the edge but less left after the bottom fade draws nothing") {
                showHome(named4, apps = whiteApps)

                withClue("the row shows ${visibleDp("tile:pkg.5")} dp") { visibleDp("tile:pkg.5") shouldBeIn 48f..60f }
                redUnderTop("tile:pkg.5", 2f) shouldBe 0
            }
        }
    })

class AlwaysShowNamesUiH1080TestContent :
    RobolectricTestSuiteContent({
        testFixture { AlwaysShowNamesUiTestFixture() } asContextForEach {
            test("with 2 columns the ring keeps 4 dp from the pills and the rim and a pill has 1 5 times its gap before the next element") {
                assertRingsKeepClear(2)
            }

            test("with 3 columns the ring keeps 4 dp from the pills and the rim and a pill has 1 5 times its gap before the next element") {
                assertRingsKeepClear(3)
            }

            test("with 4 columns the ring keeps 4 dp from the pills and the rim and a pill has 1 5 times its gap before the next element") {
                assertRingsKeepClear(4)
            }

            test("with 6 columns the ring keeps 4 dp from the pills and the rim and a pill has 1 5 times its gap before the next element") {
                assertRingsKeepClear(6)
            }

            test("with focus overshoot and 2 columns the ring at the peak keeps 4 dp from the pills of the row above") {
                assertRoomOverARowFitsThePeak(2)
            }

            test("with focus overshoot and 4 columns the ring at the peak keeps 4 dp from the pills of the row above") {
                assertRoomOverARowFitsThePeak(4)
            }

            test("with focus overshoot and 7 columns the ring at the peak keeps 4 dp from the pills of the row above") {
                assertRoomOverARowFitsThePeak(7)
            }
        }
    })

class AlwaysShowNamesUiNightTestContent :
    RobolectricTestSuiteContent({
        testFixture { AlwaysShowNamesUiTestFixture() } asContextForEach {
            test("the focused tiles name keeps the dark pill and light ink of the others") {
                showHome(named4, wallpaper = Wallpaper(picture = white))

                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
                withClue("focused pill ${inPill("name:pkg.1", 3f, 12f)}") { inPill("name:pkg.1", 3f, 12f) shouldBeLessThan 140 }
                withClue("other pill ${inPill("name:pkg.2", 3f, 12f)}") { inPill("name:pkg.2", 3f, 12f) shouldBeLessThan 140 }
                ink("name:pkg.1") shouldBe (0.85f plusOrMinus 0.05f)
            }
        }
    })

private class AlwaysShowNamesUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }

    val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    fun showHome(
        config: TvConfig,
        wallpaper: Wallpaper = Wallpaper(),
        apps: List<AppEntry> = this.apps,
        motion: Motion = Motion(),
    ) {
        val sources = sources(apps = flowOf(AppList(apps)), wallpaper = flowOf(wallpaper))
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
                    dockLook = config.dockLook(),
                    actions = viewModel.actions,
                )
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

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    fun top(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().top

    fun height(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().height

    /** How far the brightest and the darkest red of [tag] are apart: what shows of the name, in 0 to 1. */
    fun contrast(tag: String): Float {
        val image: ImageBitmap = compose.onNodeWithTag(tag).captureToImage()
        val pixels = image.toPixelMap()
        val reds = (0 until image.height).flatMap { y -> (0 until image.width).map { x -> pixels[x, y].red } }
        return reds.max() - reds.min()
    }

    /** From the top of the first grid row to the top of the second, which only both being on screen can tell. */
    fun rowPitch(): Dp {
        repeat(2) { press(Key.DirectionDown) }
        compose.onNodeWithTag("tile:pkg.13").assertIsFocused()
        return top("tile:pkg.13") - top("tile:pkg.7")
    }

    fun assertNear(
        expected: Dp,
        actual: Dp,
    ) = withClue("expected $expected, got $actual") { abs(expected.value - actual.value) shouldBeLessThanOrEqualTo 1f }

    /** The pixels of [tag] as the screen draws them now. */
    fun frame(tag: String): List<Color> {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return (0 until pixels.height).flatMap { y -> (0 until pixels.width).map { x -> pixels[x, y] } }
    }

    val longNames =
        apps.mapIndexed { i, app ->
            if (i < 2) app.copy(label = "Control de TV con altavoces inteligentes ${i + 1}") else app
        }

    /** The brightest red, 0 to 1, of [tag]: the ink of a name over its pill. */
    fun ink(tag: String): Float {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return (0 until pixels.height).maxOf { y -> (0 until pixels.width).maxOf { x -> pixels[x, y].red } }
    }

    fun redAt(
        x: Float,
        y: Float,
    ): Int {
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(
            compose
                .onRoot()
                .captureToImage()
                .toPixelMap()[(x * density).toInt(), (y * density).toInt()]
                .red * 255,
        )
    }

    /** The red channel [out] dp left of the name's text, and [out] dp right of it, over the middle of its line. */
    fun besideName(
        tag: String,
        out: Float,
    ): Pair<Int, Int> {
        val name = compose.onNodeWithTag(tag).getBoundsInRoot()
        val middle = (name.top.value + name.bottom.value) / 2f
        return redAt(name.left.value - out, middle) to redAt(name.right.value + out, middle)
    }

    val whiteApps = apps.map { it.copy(image = white) }

    fun redUnderTop(
        tag: String,
        below: Float,
    ): Int {
        val tile = bounds(tag)
        return redAt((tile.left.value + tile.right.value) / 2f, tile.top.value + below)
    }

    /** How many dp of tile [tag] show above the screen's bottom edge. */
    fun visibleDp(tag: String): Float =
        compose
            .onRoot()
            .getBoundsInRoot()
            .bottom.value - bounds(tag).top.value

    /** How many dp of tile [tag] show: the rows of its middle column, on the screen, whose red is above 10 percent. */
    fun drawnDp(tag: String): Float {
        val density = compose.activity.resources.displayMetrics.density
        val tile = bounds(tag)
        val screen =
            compose
                .onRoot()
                .getBoundsInRoot()
                .bottom.value
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val x = ((tile.left.value + tile.right.value) / 2 * density).toInt()
        val rows =
            (tile.top.value * density).toInt().coerceAtLeast(0).until((minOf(tile.bottom.value, screen) * density).toInt()).count {
                pixels[x, it].red > 0.1f
            }
        return rows / density
    }

    /** At each of six scroll steps, no tile in the lower half of the screen shows more than nothing and less than 48 dp of itself. */
    fun assertNoStripAtAnyScrollStep(config: TvConfig) {
        showHome(config, apps = whiteApps)
        repeat(6) { step ->
            press(Key.DirectionDown)
            val lower =
                (1..18).map { "tile:pkg.$it" }.filter {
                    compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() && bounds(it).top.value > 270f
                }
            val strips = lower.associateWith(::drawnDp).filterValues { it in 0.5f..47f }
            withClue("a row shows a strip of its tile at step ${step + 1}: $strips") { strips.isEmpty().shouldBeTrue() }
        }
    }

    val longName = List(30) { "Largo" }.joinToString(" ")

    fun withLongName(vararg keys: String) = apps.map { if (it.key in keys) it.copy(label = longName) else it }

    val named4 = SixColumns.copy(columns = 4, alwaysShowNames = true)

    /**
     * In the column at [x], going from [from] dp (the top or bottom of a focused tile of white art on a white wallpaper) in
     * direction [step] (-1 up, +1 down): where, in dp, the dark outer stroke of the ring ends, past the art, the gap and the white ring.
     */
    fun ringEnd(
        x: Float,
        from: Float,
        step: Int,
    ): Float {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val column = (x * density).toInt()
        var row = (from * density).toInt() + if (step < 0) -1 else 0
        while (pixels[column, row].red >= 0.94f) row += step
        // The ground in the gap sets what counts as dark: a dimmed row sits on a darker ground.
        val ground = pixels[column, row + step].red
        while (pixels[column, row].red < 0.94f) row += step
        while (pixels[column, row].red >= 0.94f) row += step
        // The hairline is no darker than a dimmed ground; the outer stroke after it is.
        val hairline = row + step * 6
        while (row != hairline && pixels[column, row].red >= ground * 0.6f) row += step
        while (pixels[column, row].red < ground * 0.9f) row += step
        return (if (step < 0) row + 1 else row) / density
    }

    fun assertRingsKeepClear(columns: Int) {
        showHome(
            SixColumns.copy(columns = columns, alwaysShowNames = true),
            wallpaper = Wallpaper(picture = white),
            apps = whiteApps,
        )
        val dock = bounds("tile:pkg.1")
        val dockX = (dock.left.value + dock.right.value) / 2f
        val dockOwn = bounds("name:pkg.1").top.value - 2f - ringEnd(dockX, dock.bottom.value, +1)
        val dockRim = ringEnd(dockX, dock.top.value, -1) - bounds("dock").top.value
        val dockGap = bounds("name:pkg.1").top.value - 2f - dock.bottom.value
        val dockBelow = bounds("dock").bottom.value - (bounds("name:pkg.1").bottom.value + 2f)

        repeat(2) { press(Key.DirectionDown) }
        val key = "pkg.${2 * columns + 1}"
        compose.onNodeWithTag("tile:$key").assertIsFocused()
        val grid = bounds("tile:$key")
        val gridX = (grid.left.value + grid.right.value) / 2f
        val gridOwn = bounds("name:$key").top.value - 2f - ringEnd(gridX, grid.bottom.value, +1)
        val gridAbove = ringEnd(gridX, grid.top.value, -1) - (bounds("name:pkg.${columns + 1}").bottom.value + 2f)

        val gridGap = bounds("name:$key").top.value - 2f - grid.bottom.value
        val gridBelow = grid.top.value - (bounds("name:pkg.${columns + 1}").bottom.value + 2f)

        val clearances = "own pill $dockOwn and $gridOwn, row above $gridAbove, dock rim $dockRim"
        withClue("the ring of a ${grid.height} dp tile is not 4 dp clear: $clearances") {
            (
                listOf(dockOwn, gridOwn, gridAbove, dockRim).all {
                    it >=
                        4f
                }
            ).shouldBeTrue()
        }
        withClue("under the dock pill $dockBelow dp, over it $dockGap") { dockBelow shouldBeGreaterThanOrEqualTo 1.5f * dockGap - 0.01f }
        withClue("under the grid pill $gridBelow dp, over it $gridGap") { gridBelow shouldBeGreaterThanOrEqualTo 1.5f * gridGap - 0.01f }
    }

    /**
     * With focus_overshoot, the room between the pills of the first grid row and the tiles of the second: a ring at the 1.144
     * peak of a pop, grown about the tile's bottom edge or 128.5 dp down, and 4 dp.
     */
    fun assertRoomOverARowFitsThePeak(columns: Int) {
        showHome(SixColumns.copy(columns = columns, alwaysShowNames = true), motion = Motion(focusOvershoot = true))
        repeat(2) { press(Key.DirectionDown) }
        val tile = bounds("tile:pkg.${2 * columns + 1}")
        val pivot = minOf(tile.height.value, 128.5f)
        val ring = pivot * 0.144f + 9f * 1.144f

        val room = tile.top.value - (bounds("name:pkg.${columns + 1}").bottom.value + 2f)
        // The layout places in whole pixels: at xhdpi an edge moves by up to 0.25 dp.
        withClue("$room dp over a ${tile.height} tile, whose ring reaches $ring") { room shouldBeGreaterThanOrEqualTo ring + 4f - 0.25f }
    }

    fun pillTops(vararg keys: String): List<Float> = keys.map { bounds("name:$it").top.value }

    /** From the bottom of tile [key] to the top of its name's pill, in dp. */
    fun tileToPill(key: String): Float = bounds("name:$key").top.value - 2f - bounds("tile:$key").bottom.value

    fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    /** The red channel at ([x], [y]) in dp, relative to the left and top of the pill that holds name [tag]: the text's box and 6 dp across, 2 dp down. */
    fun inPill(
        tag: String,
        x: Float,
        y: Float,
    ): Int {
        val name = bounds(tag)
        return redAt(name.left.value - 6f + x, name.top.value - 2f + y)
    }

    fun left(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().left
}
