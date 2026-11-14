package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
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
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DENSITY = 2

/** habit_hint on the real presenter and screen: the mark under the usual app's tile, and the line under the title. */
val HabitHintUiTest by testSuite {
    robolectricTestSuite<HabitHintUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HabitHintUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { HabitHintUiTestFixture() } asContextForEach {
            test("the mark under an unfocused dock tile is a light bar 40 by 6 px with a dark edge") {
                showHome(SixColumns.copy(habitHint = true))
                val screen = screen()
                val (centre, top) = markAt("tile:pkg.2")

                barShownAt(screen, centre, top).shouldBeTrue()
                (isFill(screen[centre - 15, top + 3]) && isFill(screen[centre + 15, top + 3])).shouldBeTrue()
                (isFill(screen[centre - 23, top + 3]) || isFill(screen[centre + 23, top + 3])).shouldBeFalse()
                withClue("taller than 6 px") { (isFill(screen[centre, top - 1]) || isFill(screen[centre, top + 6])).shouldBeFalse() }
                val edge = screen[centre, top]
                withClue("edge ${edge.red}") { edge.red shouldBeIn 0.5f..0.72f }
                withClue("edge ${screen[centre, top + 5].red}") { screen[centre, top + 5].red shouldBeIn 0.5f..0.72f }
                screen[centre, top + 2].alpha shouldBe (1f plusOrMinus 0f)
            }

            test("no other dock tile has the mark and nothing moves") {
                showHome(SixColumns.copy(habitHint = true))
                val without = compose.onNodeWithTag("tile:pkg.3").getBoundsInRoot()
                val screen = screen()

                (dockTiles - "pkg.2").forEach { tile ->
                    val (centre, top) = markAt("tile:$tile")
                    withClue("$tile has a mark") { barShownAt(screen, centre, top).shouldBeFalse() }
                }
                usual = null
                viewModel.onStopped()
                settle()
                viewModel.onStarted()
                settle()
                compose.onNodeWithTag("tile:pkg.3").getBoundsInRoot() shouldBe without
            }

            test("the mark under an unfocused grid tile is the same bar and no other grid tile has it") {
                usual = "pkg.8"
                showHome(SixColumns.copy(habitHint = true))
                press(Key.DirectionDown)
                compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
                val screen = screen()

                val (centre, top) = markAt("tile:pkg.8")
                barShownAt(screen, centre, top).shouldBeTrue()
                screen[centre, top].red shouldBeIn 0.5f..0.72f
                listOf("pkg.9", "pkg.10", "pkg.11", "pkg.12").forEach { tile ->
                    val (c, t) = markAt("tile:$tile")
                    withClue("$tile has a mark") { barShownAt(screen, c, t).shouldBeFalse() }
                }
            }

            test("with the names on the mark sits under the name line and not under the tile") {
                showHome(SixColumns.copy(habitHint = true, alwaysShowNames = true))
                val screen = screen()

                val (centre, top) = markAt("tile:pkg.2", below = TileNameRoom.value.toInt())
                barShownAt(screen, centre, top).shouldBeTrue()
                val (_, bare) = markAt("tile:pkg.2")
                barShownAt(screen, centre, bare).shouldBeFalse()
                val name = compose.onNodeWithTag("name:pkg.2").getBoundsInRoot()
                top.toFloat() shouldBeGreaterThan name.bottom.value * DENSITY + 18
            }

            test("the mark follows the habit to another app over 400 ms and the old mark goes") {
                showHome(SixColumns.copy(habitHint = true))
                val (oldCentre, oldTop) = markAt("tile:pkg.2")
                val (newCentre, newTop) = markAt("tile:pkg.3")
                usual = "pkg.3"
                viewModel.onStopped()
                settle()
                compose.mainClock.autoAdvance = false

                viewModel.onStarted()
                compose.mainClock.advanceTimeBy(100)
                compose.waitForIdle()
                val fading = mutableListOf<Float>()
                repeat(10) {
                    compose.mainClock.advanceTimeBy(50)
                    fading += screen()[newCentre, newTop + 3].red
                }

                withClue("not yet shown at 150 ms: $fading") { fading.first() shouldBeLessThan 0.9f }
                withClue("fading in: $fading") { (fading.zipWithNext().all { (a, b) -> b >= a - 0.001f }).shouldBeTrue() }
                withClue("full after 400 ms: $fading") { isFill(screen()[newCentre, newTop + 3]).shouldBeTrue() }
                barShownAt(screen(), oldCentre, oldTop).shouldBeFalse()
            }

            test("with reduced motion the mark is there at once and does not fade") {
                showHome(SixColumns.copy(habitHint = true), motion = Motion(reducedMotion = true))
                usual = "pkg.3"
                viewModel.onStopped()
                settle()
                val (centre, top) = markAt("tile:pkg.3")
                compose.mainClock.autoAdvance = false

                viewModel.onStarted()
                compose.mainClock.advanceTimeBy(100)
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(34)

                barShownAt(screen(), centre, top).shouldBeTrue()
            }

            test("with the key off no tile has the mark") {
                showHome(SixColumns)
                val screen = screen()

                dockTiles.forEach { tile ->
                    val (centre, top) = markAt("tile:$tile")
                    withClue("$tile has a mark") { barShownAt(screen, centre, top).shouldBeFalse() }
                }
            }

            test("the line under the title adds the usual phrase only while the usual app has focus") {
                showHome(
                    SixColumns.copy(habitHint = true, cinematicHome = true),
                    lastOpened =
                        mapOf(
                            "pkg.2" to yesterday,
                            "pkg.3" to yesterday,
                        ),
                )

                compose.onNodeWithTag("shelf:meta").assertTextEquals("Never opened")
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.2").assertIsFocused()
                compose.onNodeWithTag("shelf:meta").assertTextContains("Last opened", substring = true)
                compose.onNodeWithTag("shelf:meta").assertTextContains(" · You usually open it now", substring = true)

                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
                textOf("shelf:meta").contains("You usually open it now").shouldBeFalse()
                textOf("shelf:meta").startsWith("Last opened").shouldBeTrue()
            }

            test("the band over the grid adds the usual phrase too and alone for an app never opened") {
                showHome(SixColumns.copy(habitHint = true), lastOpened = mapOf("pkg.2" to yesterday))
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.2").assertIsFocused()
                withClue(textOf("shelf:title-meta")) { textOf("shelf:title-meta").endsWith(" · You usually open it now").shouldBeTrue() }

                usual = "pkg.3"
                viewModel.onStopped()
                settle()
                viewModel.onStarted()
                settle()
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
                compose.onNodeWithTag("shelf:title-meta").assertTextEquals("You usually open it now")
            }
        }
    })

private class HabitHintUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    var usual: String? = "pkg.2"

    lateinit var viewModel: MoleculeHomeViewModel

    fun showHome(
        config: TvConfig,
        motion: Motion = Motion(),
        lastOpened: Map<String, Long> = emptyMap(),
    ) {
        val sources = sources(apps = flowOf(AppList(apps)), lastOpened = flowOf(lastOpened), habit = { usual })
        compose.setContent {
            TvTheme(motion = motion) {
                viewModel = rememberHomeViewModel(sources, config, mutableListOf())
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

    fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    /** The mark's centre column and its first row, in pixels, if it sits [below] dp under the bottom of [tag]. */
    fun markAt(
        tag: String,
        below: Int = 0,
    ): Pair<Int, Int> {
        val bounds = compose.onNodeWithTag(tag).getBoundsInRoot()
        val centre = ((bounds.left.value + bounds.right.value) / 2f * DENSITY).roundToInt()
        // The tile's gap is 24 dp: a 3 dp bar in its middle starts 10.5 dp under the bottom, which is 21 px.
        return centre to (bounds.bottom.value * DENSITY).roundToInt() + below * DENSITY + 21
    }

    fun isFill(pixel: Color) =
        abs(pixel.red - 0xF2 / 255f) < 0.02f && abs(pixel.green - 0xF4 / 255f) < 0.02f && abs(pixel.blue - 0xF8 / 255f) < 0.02f

    fun barShownAt(
        screen: PixelMap,
        centre: Int,
        top: Int,
    ) = isFill(screen[centre, top + 3])

    val dockTiles = listOf("pkg.1", "pkg.2", "pkg.3", "pkg.4", "pkg.5", "pkg.6")

    val yesterday =
        Evening
            .minusDays(1)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    fun textOf(tag: String): String =
        compose
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            .joinToString("") { it.text }
}
