package dev.niltsiar.tvhome.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import app.cash.molecule.RecompositionMode
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.ExpressiveTile
import dev.niltsiar.tvhome.core.config.GlassStyle
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.OverlayHost
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.focusStyle
import dev.niltsiar.tvhome.core.designsystem.golden.assertMatchesGolden
import dev.niltsiar.tvhome.core.designsystem.golden.bands
import dev.niltsiar.tvhome.core.designsystem.motion
import dev.niltsiar.tvhome.core.model.LibraryApp
import dev.niltsiar.tvhome.core.model.LibraryArt
import dev.niltsiar.tvhome.core.model.OpenedRecord
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

/** Golden images of the App library in each look on the real presenter and screen (docs/testing.md, "Golden tests"). */
val LibraryGoldenTest by testSuite {
    robolectricTestSuite<LibraryGoldenTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class LibraryGoldenTestContent :
    RobolectricTestSuiteContent({
        testFixture { LibraryGoldenFixture() } asContextForEach {
            for (look in Look.entries) {
                val x = TvConfig(appLibrary = true, look = look)
                val prefix = if (look == Look.Expressive) "expressive-" else ""
                val named = if (look == Look.Expressive) ", in Expressive" else ""

                test("the library grid with a focused card$named") {
                    showLibrary(x)
                    press(Key.DirectionRight)
                    compose.onNodeWithTag("card:${key("App 02")}").assertIsFocused()
                    golden("${prefix}grid-focus")
                }

                test("the chooser$named") {
                    showLibrary(x.copy(appLibrarySelection = true))
                    golden("${prefix}chooser")
                }

                test("the card menu panel$named") {
                    showLibrary(x)
                    compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                    compose.mainClock.advanceTimeBy(600)
                    compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
                    settle()
                    compose.onNodeWithTag("menu").assertExists()
                    golden("${prefix}card-menu")
                }

                test("the least used order$named") {
                    showLibrary(x.copy(libraryLeastUsed = true), record = usage)
                    press(Key.Menu)
                    golden("${prefix}least-used")
                }

                test("the least used order with the order chip focused$named") {
                    showLibrary(x.copy(libraryLeastUsed = true), record = usage)
                    press(Key.Menu)
                    compose.onNodeWithTag("library:order").requestFocus()
                    settle()
                    compose.onNodeWithTag("library:order").assertIsFocused()
                    golden("${prefix}least-used-chip")
                }

                test("the least used order with the order chip focused with night mode off$named") {
                    showLibrary(x.copy(libraryLeastUsed = true), dark = false, record = usage)
                    press(Key.Menu)
                    compose.onNodeWithTag("library:order").requestFocus()
                    settle()
                    compose.onNodeWithTag("library:order").assertIsFocused()
                    golden("${prefix}least-used-chip-light")
                }

                test("a focused card of an app on Home$named") {
                    showLibrary(x)
                    compose.onNodeWithTag("card:${key("App 03")}").requestFocus()
                    settle()
                    compose.onNodeWithTag("card:${key("App 03")}").assertIsFocused()
                    golden("${prefix}home-card-focus")
                }

                test("a recent install and a focused card in the rim focus style$named") {
                    showLibrary(x.copy(glassStyle = GlassStyle.Rim), apps = withRecent)
                    press(Key.DirectionRight)
                    golden("${prefix}recent-rim")
                }

                test("a long grid scrolled down a row with night mode off$named") {
                    showLibrary(x, apps = thirty, dark = false)
                    repeat(3) { press(Key.DirectionDown) }
                    golden("${prefix}scrolled-light")
                }

                test("a card whose art is loading and one with no art, which shows its letter$named") {
                    showLibrary(x, apps = apps + libraryApp("Yak") + libraryApp("Zebra"))
                    compose.onNodeWithTag("card:art:${key("Zebra")}", useUnmergedTree = true).assertExists()
                    golden("${prefix}art-states")
                }

                test("an empty library$named") {
                    showLibrary(x, apps = emptyList())
                    compose.onNodeWithTag("library:empty").assertExists()
                    golden("${prefix}empty")
                }
            }

            // The default capsule is in the scenes above.
            test("the library grid with a focused card as a cinch, in Expressive") {
                showLibrary(TvConfig(appLibrary = true, look = Look.Expressive, expressiveTile = ExpressiveTile.Cinch))
                press(Key.DirectionRight)
                compose.onNodeWithTag("card:${key("App 02")}").assertIsFocused()
                golden("expressive-grid-focus-cinch")
            }

            test("a card's check 70 ms after ok puts its app on Home in the chooser, in Expressive") {
                showLibrary(TvConfig(appLibrary = true, appLibrarySelection = true, look = Look.Expressive))
                compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
                compose.mainClock.advanceTimeBy(70)
                golden("expressive-chooser-check-70ms")
            }
        }
    })

/** The house seed of the Expressive scenes, the warm one the design-system goldens use. */
private val Ember = Color(0xFFFF6A3D)

private fun hue(n: Int) = Color.hsv((n * 47f) % 360f, 0.55f, 0.75f)

private class LibraryGoldenFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    /** App 01 to App 14, every third one on Home, each with a banner of its own hues. */
    val apps = (1..14).map { libraryApp("App %02d".format(it), onHome = it % 3 == 0) }

    /** App 01 to App 30, every third one on Home. */
    val thirty = (1..30).map { libraryApp("App %02d".format(it), onHome = it % 3 == 0) }

    /** The 14 apps with App 02 installed a day ago, so it counts as recent. */
    val withRecent = apps.map { if (it.app.label == "App 02") it.copy(installedAt = NOW - DAY) else it }

    /** App 01 opened an hour ago and App 04 three days ago; the rest never. */
    val usage = OpenedRecord(times = mapOf(key("App 01") to NOW - DAY / 24, key("App 04") to NOW - 3 * DAY), since = NOW - 90 * DAY)

    val arts =
        thirty.indices.associate { i -> thirty[i].app.key to LibraryArt(bands(32, 18, listOf(hue(i), hue(i + 3))), hue(i)) } +
            // Yak has no art to show, so its card shows its letter; Zebra's art never arrives.
            (key("Yak") to LibraryArt(null, Color.Unspecified))

    fun showLibrary(
        config: TvConfig,
        apps: List<LibraryApp> = this.apps,
        dark: Boolean = true,
        record: OpenedRecord = OpenedRecord(),
    ) {
        val library = FakeLibrary(apps)
        library.record = record
        compose.mainClock.autoAdvance = false
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        lateinit var viewModel: MoleculeLibraryViewModel
        compose.setContent {
            TvTheme(
                look = config.look,
                seed = { Ember },
                motion = config.motion(),
                focusStyle = config.focusStyle(),
                expressiveTile = config.expressiveTile,
                darkTheme = dark,
            ) {
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
                val state = viewModel.uiState.collectAsState()
                Box(Modifier.fillMaxSize().background(Color(0xFF1C2A3E))) {
                    OverlayHost(open = { state.value.open }) {
                        LibraryOverlay(
                            state = { state.value },
                            layout = config.libraryLayout(),
                            actions = viewModel.actions,
                            art = { arts[it] },
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        viewModel.onOpen()
        settle()
    }

    /** Moves the test clock past every focus spring, fade and card entrance. */
    fun settle() {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    fun golden(scene: String) =
        compose
            .onRoot()
            .captureToImage()
            .assertMatchesGolden("LibraryGoldenTest", scene)
}
