package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.ringColor
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

/** The focus ring, and the light the focused dock tile casts on the dock, in the app's colour: read from the pixels. */
val FocusColourUiTest by testSuite {
    robolectricTestSuite<FocusColourUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class FocusColourUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { FocusColourUiTestFixture() } asContextForEach {
            test("a dock tiles ring takes its apps colour") {
                showHome()

                assertNear(ringColor(red), ringAtTop(1))
            }

            test("in reorder mode the glow is at most 030 strong and half the way to grey") {
                showHome()
                val glow = { at(576f, 108f) }
                val before = glow()
                withClue("the glow shows ${before.red}") { before.red shouldBeGreaterThan 0.35f }

                carryFirstTile()

                val reordering = glow()
                // Alpha 0.30 against 0.65 at the core, and red 0xE5 mixed half the way to 0x44 is 0.65 of itself.
                reordering.red / before.red shouldBe (0.30f / 0.65f * 0.648f plusOrMinus 0.04f)
                withClue("the colour is greyer: ${reordering.blue / reordering.red} against ${before.blue / before.red}") {
                    reordering.blue /
                        reordering.red shouldBeGreaterThan
                        1.4f * before.blue / before.red
                }
            }

            test("a dock ring that blends with its tiles colour gets a white stroke between the gap and the ring") {
                showHome()

                val gap = gapAtTop(1)
                withClue("the gap is $gap") { minOf(gap.red, gap.green, gap.blue) shouldBeGreaterThanOrEqualTo 0.85f }
                assertNear(ringColor(red), ringAtTop(1))
            }

            test("a grid ring that blends with its tiles colour gets a white stroke between the gap and the ring") {
                showHome()
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                val gap = gapAtTop(7)
                withClue("the gap is $gap") { minOf(gap.red, gap.green, gap.blue) shouldBeGreaterThanOrEqualTo 0.85f }
            }

            test("a ring that stands out from a dark saturated tile has no white stroke") {
                val navy = Color(0xFF14143C)
                showHome(appList = flowOf(AppList(apps.map { it.copy(accent = navy) })))

                withClue("the gap is ${gapAtTop(1)}") { gapAtTop(1).red shouldBeLessThan 0.5f }
                assertNear(ringColor(navy), ringAtTop(1))
            }

            test("cinematic white focus keeps the ring white") {
                showHome(cinematic.copy(cinematicWhiteFocus = true))

                assertNear(Color.White, ringAtTop(1))
            }

            test("a grid tiles ring is white without app glow and takes the apps colour with it") {
                showHome(cinematic.copy(appGlow = false))
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                assertNear(Color.White, ringAtTop(7))
            }

            test("with app glow a grid tiles ring takes the apps colour") {
                showHome()
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

                assertNear(ringColor(green), ringAtTop(7))
            }

            test("along the dock the new ring is between the two colours on its way and ends at its own") {
                showHome()
                compose.mainClock.autoAdvance = false

                compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
                compose.mainClock.advanceTimeBy(112)
                val onTheWay = ringAtTop(2)
                compose.mainClock.advanceTimeBy(1_000)
                val late = ringAtTop(2)

                // The first ring is a light red and the second a light blue: on the way the ring still has red in it.
                assertNear(ringColor(blue), late)
                withClue("on the way $onTheWay, at the end $late") { onTheWay.red shouldBeGreaterThan late.red + 0.04f }
            }

            test("the ring round a tile whose menu is open takes the tiles colour") {
                showHome()
                press(Key.DirectionRight)
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.3").assertIsFocused()

                holdOk()

                compose.onNodeWithTag("menu").assertExists()
                val box = tile(3)
                val ring = at(centreX(3), box.top.value - 1f)
                withClue("green leads the ring: $ring") { (ring.green > ring.red + 0.08f && ring.green > ring.blue + 0.03f).shouldBeTrue() }
            }

            test("the focused dock tile lights the rim over it in its colour") {
                showHome()
                press(Key.DirectionRight)
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.3").assertIsFocused()

                val rim = panel().top.value + 0.75f
                val lit = at(centreX(3), rim)
                val bare = at(centreX(6), rim)
                withClue("lit $lit, bare $bare") {
                    (
                        lit.green > lit.red + 0.15f && lit.green > lit.blue + 0.1f &&
                            abs(bare.green - bare.red) < 0.1f
                    ).shouldBeTrue()
                }
            }

            test("the light leaves a tile that loses focus along the dock and comes to the next") {
                showHome()
                withClue("on tile 1") { rimColour(1) shouldBeGreaterThan 0.2f }
                withClue("not on tile 2") { rimColour(2) shouldBeLessThan 0.12f }

                withClue("left tile 1") { rimColourAfter(Key.DirectionRight, 1) shouldBeLessThan 0.12f }
                withClue("came to tile 2") { rimColour(2) shouldBeGreaterThan 0.2f }
            }

            test("the light fades out when focus leaves the dock row and is back when it returns") {
                showHome()
                withClue("in the dock") { rimColour(1) shouldBeGreaterThan 0.2f }

                withClue("top bar focused") { rimColourAfter(Key.DirectionUp, 1) shouldBeLessThan 0.12f }
                withClue("back in the dock") { rimColourAfter(Key.DirectionDown, 1) shouldBeGreaterThan 0.2f }
            }

            test("with dock edge light false the rim stays grey") {
                assertRimStaysGrey(cinematic.copy(dockEdgeLight = false))
            }

            test("with animations off the rim stays grey") {
                assertRimStaysGrey(cinematic.copy(animations = false))
            }

            test("with dock band off there is no rim to light") {
                assertRimStaysGrey(cinematic.copy(dockBand = false))
            }

            test("a home press from the grid gives the first dock tile its own ring colour at once") {
                showHome()
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                compose.mainClock.autoAdvance = false

                homePresses++
                var frames = 0
                while (!focused(1) && frames++ < 60) compose.mainClock.advanceTimeBy(16)
                compose.mainClock.advanceTimeBy(80)

                // The grid tile's ring was green and the dock tile's is red: a ring still on its way from green is not red yet.
                val ring = ringAtTop(1)
                withClue("red ring expected, got $ring") { ring.red shouldBeGreaterThan ring.green + 0.04f }
            }

            test("the light on the rim takes the colour of art that lands after focus") {
                val list = MutableStateFlow(AppList(apps.map { it.copy(accent = Color.Unspecified) }))
                showHome(appList = list)
                withClue("white before the art") { rimColour(1) shouldBeLessThan 0.12f }

                list.value = AppList(apps)
                settle()

                val lit = at(centreX(1), panel().top.value + 0.75f)
                withClue("red expected, got $lit") { (lit.red > lit.green + 0.12f && lit.red > lit.blue + 0.08f).shouldBeTrue() }
            }

            test(
                "with a single row the light follows the focused tile when the row scrolls",
                testConfig = TestConfig.testScope(isEnabled = true, timeout = 10.minutes),
            ) {
                showHome(cinematic.copy(singleRow = true))
                repeat(8) { press(Key.DirectionRight) }
                compose.onNodeWithTag("tile:pkg.9").assertIsFocused()

                withClue(
                    "light at ${lightCentre()}, tile at ${centreX(9)}",
                ) { abs(lightCentre() - centreX(9)) shouldBeLessThanOrEqualTo 6f }
            }

            test(
                "the light follows a tile carried along the dock or is not shown",
                testConfig = TestConfig.testScope(isEnabled = true, timeout = 10.minutes),
            ) {
                showHome()
                holdOk()
                press(Key.DirectionDown)
                press(Key.DirectionCenter)
                compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

                press(Key.DirectionRight)
                press(Key.DirectionRight)

                val centre = lightCentre()
                withClue(
                    "light at $centre, tile at ${centreX(1)}",
                ) { (rimStrength(centre) < 0.12f || abs(centre - centreX(1)) <= 6f).shouldBeTrue() }
            }
        }
    })

private class FocusColourUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val red = Color(0xFFE53935)

    val blue = Color(0xFF1E88E5)

    val green = Color(0xFF1DB954)

    val accents = listOf(red, blue, green, blue, red, blue, green, red, blue)

    val apps = accents.mapIndexed { i, accent -> AppEntry("pkg.${i + 1}", "pkg.${i + 1}", "App ${i + 1}", accent = accent) }

    val cinematic = SixColumns.copy(cinematicHome = true, appLibrary = true)

    var homePresses by mutableIntStateOf(0)

    fun showHome(
        config: TvConfig = cinematic,
        appList: Flow<AppList> = flowOf(AppList(apps)),
    ) {
        val sources = sources(apps = appList)
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
                    dockLook = config.dockLook(),
                    actions = viewModel.actions,
                    homePresses = { homePresses },
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

    fun holdOk() {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
    }

    fun tile(n: Int): DpRect = compose.onNodeWithTag("tile:pkg.$n").getBoundsInRoot()

    fun panel(): DpRect = compose.onNodeWithTag("dock").getBoundsInRoot()

    fun at(
        x: Float,
        y: Float,
    ): Color {
        val density = compose.activity.resources.displayMetrics.density
        return compose.onRoot().captureToImage().toPixelMap()[(x * density).toInt(), (y * density).toInt()]
    }

    fun centreX(n: Int) = (tile(n).left.value + tile(n).right.value) / 2f

    /** The middle of the ring of tile [n]'s top edge: its outline, 3 dp gap and half the 3 dp stroke out, grown 1.08 times about the tile's centre. */
    fun ringAtTop(n: Int): Color {
        val box = tile(n)
        val centre = (box.top.value + box.bottom.value) / 2f
        return at(centreX(n), centre - 1.08f * ((box.bottom.value - box.top.value) / 2f + 4.5f))
    }

    fun assertNear(
        expected: Color,
        actual: Color,
        tolerance: Int = 10,
    ) {
        val gap = listOf(expected.red - actual.red, expected.green - actual.green, expected.blue - actual.blue).maxOf { abs(it) * 255 }
        withClue("expected $expected, got $actual") { gap shouldBeLessThanOrEqualTo tolerance.toFloat() }
    }

    fun carryFirstTile() {
        press(Key.Menu)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
    }

    /** The colour 2 dp out from tile [n]'s edge, the middle of the outer 2 dp of the gap between the tile and its ring. */
    fun gapAtTop(n: Int): Color {
        val box = tile(n)
        val centre = (box.top.value + box.bottom.value) / 2f
        return at(centreX(n), centre - 1.08f * ((box.bottom.value - box.top.value) / 2f + 2f))
    }

    /** How coloured the dock's top rim is at tile [n]'s centre: the white rim is grey, the light over it is not. */
    fun rimColour(n: Int): Float = rimStrength(centreX(n))

    /** The rim's colour at tile [n] after [key] is pressed. */
    fun rimColourAfter(
        key: Key,
        n: Int,
    ): Float {
        press(key)
        return rimColour(n)
    }

    fun assertRimStaysGrey(config: TvConfig) {
        showHome(config)

        withClue("tile 1 focused") { rimColour(1) shouldBeLessThan 0.12f }
        withClue("tile 2 focused") { rimColourAfter(Key.DirectionRight, 2) shouldBeLessThan 0.12f }
    }

    fun focused(n: Int): Boolean =
        compose
            .onNodeWithTag("tile:pkg.$n")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Focused) == true

    fun rimStrength(x: Float): Float {
        val rim = at(x, panel().top.value + 0.75f)
        return maxOf(rim.red, rim.green, rim.blue) - minOf(rim.red, rim.green, rim.blue)
    }

    /** The x, in dp, where the dock's top rim is most coloured, away from the panel's rounded ends. */
    fun lightCentre(): Float {
        val box = panel()
        return (box.left.value.toInt() + 40 until box.right.value.toInt() - 40).maxBy { rimStrength(it.toFloat()) }.toFloat()
    }
}
