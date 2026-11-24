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
import app.cash.molecule.RecompositionMode
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
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
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

/** Golden images of the App library in the Glass look on the real presenter and screen (docs/testing.md, "Golden tests"). */
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
            test("the library grid with a focused card") {
                showLibrary(TvConfig(appLibrary = true))
                press(Key.DirectionRight)
                compose.onNodeWithTag("card:${key("App 02")}").assertIsFocused()
                golden("grid-focus")
            }

            test("the chooser") {
                showLibrary(TvConfig(appLibrary = true, appLibrarySelection = true))
                golden("chooser")
            }

            test("the card menu panel") {
                showLibrary(TvConfig(appLibrary = true))
                compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
                compose.mainClock.advanceTimeBy(600)
                compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
                settle()
                compose.onNodeWithTag("menu").assertExists()
                golden("card-menu")
            }
        }
    })

private fun hue(n: Int) = Color.hsv((n * 47f) % 360f, 0.55f, 0.75f)

private class LibraryGoldenFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    /** App 01 to App 14, every third one on Home, each with a banner of its own hues. */
    val apps = (1..14).map { libraryApp("App %02d".format(it), onHome = it % 3 == 0) }

    val arts = apps.indices.associate { i -> apps[i].app.key to LibraryArt(bands(32, 18, listOf(hue(i), hue(i + 3))), hue(i)) }

    fun showLibrary(config: TvConfig) {
        val library = FakeLibrary(apps)
        compose.mainClock.autoAdvance = false
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        lateinit var viewModel: MoleculeLibraryViewModel
        compose.setContent {
            TvTheme(look = Look.Glass, motion = config.motion(), focusStyle = config.focusStyle(), darkTheme = true) {
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
