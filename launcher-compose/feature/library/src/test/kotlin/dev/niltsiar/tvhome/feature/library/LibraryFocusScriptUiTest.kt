package dev.niltsiar.tvhome.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.focus.trace
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The App library's focus paths in Glass and in Expressive: the same focused tags key by key, and every tagged node with the
 * same bounds in both looks at every step.
 */
val LibraryFocusScriptUiTest by testSuite {
    robolectricTestSuite<LibraryFocusScriptUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class LibraryFocusScriptUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { LibraryFocusScriptUiTestFixture() } asContextForEach {
            test("the library walks its cards and the order chip the same way in both looks") {
                val keys =
                    listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionDown, Key.DirectionLeft, Key.DirectionUp, Key.DirectionUp)
                bothLooks(keys.map { key -> { press(key) } }) shouldBe
                    listOf("App01", "App02", "App03", "App09", "App08", "App02", "App02").map { "card:${key(it)}" }
            }

            test("a held down repeats through the cards the same way in both looks") {
                // The repeats take focus four rows down in 1.5 s; one Up then goes back a row.
                bothLooks(listOf({ hold(Key.DirectionDown, 1_500) }, { press(Key.DirectionUp) })) shouldBe
                    listOf("App01", "App25", "App19").map { "card:${key(it)}" }
            }
        }
    })

private class LibraryFocusScriptUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    var look by mutableStateOf(Look.Glass)

    /** A new value builds the library again from nothing. */
    var run by mutableIntStateOf(0)

    val config = TvConfig(appLibrary = true)

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    /** [key] held for [millis], so the window repeats it. */
    fun hold(
        key: Key,
        millis: Long,
    ) {
        compose.onRoot().performKeyInput {
            keyDown(key)
            advanceEventTime(millis)
            keyUp(key)
        }
        settle()
    }

    fun show() {
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            key(run) {
                TvTheme(look = look) {
                    val library = remember { FakeLibrary((1..30).map { libraryApp("App%02d".format(it)) }) }
                    val scope = rememberCoroutineScope()
                    val viewModel =
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
                    LibraryOverlay(state = { state.value }, layout = config.libraryLayout(), actions = viewModel.actions, art = { null })
                    LaunchedEffect(viewModel) { viewModel.onOpen() }
                }
            }
        }
        settle()
    }

    /** The focused tags of [steps] in Glass, after the same steps in Expressive gave the same tags and bounds. */
    fun bothLooks(steps: List<() -> Unit>): List<String> {
        show()
        val glass = compose.trace(steps)
        look = Look.Expressive
        run++
        settle()
        val expressive = compose.trace(steps)
        withClue("tags") { expressive.tags shouldBe glass.tags }
        glass.bounds.indices.forEach { step -> withClue("bounds after step $step") { expressive.bounds[step] shouldBe glass.bounds[step] } }
        return glass.tags
    }
}
