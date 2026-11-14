package dev.niltsiar.tvhome.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import app.cash.molecule.RecompositionMode
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.LibraryArt
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt

private const val DENSITY = 2

/** press_feedback on the library's real presenter and panel: the dip is read from pixels, so the native graphics mode is needed. */
val LibraryPressFeedbackUiTest by testSuite {
    robolectricTestSuite<LibraryPressFeedbackUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class LibraryPressFeedbackUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { LibraryPressFeedbackUiTestFixture() } asContextForEach {
            test("ok on a card dips it to 96 percent of its focused size over 80 ms and back") {
                showLibrary(TvConfig(appLibrary = true, pressFeedback = true))
                val rest = artWidth()

                val widths = afterOk().map { it / rest }
                val deepest = widths.indexOf(widths.min())
                // The art is about 165 px wide, and its anti-aliased edges cost up to 2 px against the red threshold.
                val edge = 2f / rest

                withClue("widths $widths") { widths.min() shouldBeIn (0.96f - edge)..(0.96f + edge) }
                withClue("deepest at frame $deepest of 16 ms: $widths") { deepest shouldBeIn 4..6 }
                withClue("back at rest: $widths") { widths.last() shouldBe 1f }
                launched() shouldBe listOf("Gamma")
                compose.onNodeWithTag("card:${key("Gamma")}").assertIsFocused()
            }

            test("a second ok while the app opens neither dips the card nor starts anything") {
                showLibrary(TvConfig(appLibrary = true, pressFeedback = true))
                afterOk()
                val rest = artWidth()

                val widths = afterOk()

                withClue("widths $widths against $rest") { widths.all { it == rest }.shouldBeTrue() }
                launched() shouldBe listOf("Gamma")
            }

            test("without the key ok on a card opens its app and closes the library at once") {
                showLibrary(TvConfig(appLibrary = true))

                ok()
                compose.waitForIdle()

                launched() shouldBe listOf("Gamma")
                compose.onNodeWithTag("library").assertDoesNotExist()
            }
        }
    })

private class LibraryPressFeedbackUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val effects = mutableListOf<LibraryEffect>()

    val library = FakeLibrary(listOf(libraryApp("Gamma", onHome = true), libraryApp("Home", onHome = true)))

    val red =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.Red })
        }

    fun launched() = effects.filterIsInstance<LibraryEffect.LaunchApp>().map { it.app.label }

    fun showLibrary(config: TvConfig) {
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            TvTheme {
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
                LaunchedEffect(viewModel) {
                    viewModel.effects.collect { effects += it }
                }
                LaunchedEffect(viewModel) { viewModel.onOpen() }
                val state = viewModel.uiState.collectAsState()
                LibraryOverlay(
                    state = { state.value },
                    layout = config.libraryLayout(),
                    actions = viewModel.actions,
                    art = { if (it == key("Gamma")) LibraryArt(red, Color.Red) else null },
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag("card:${key("Gamma")}").assertIsFocused()
    }

    /** A short OK on the focused card: its UP is the click. */
    fun ok() {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
    }

    /** OK, then the width of Gamma's red art every 16 ms for 256 ms, with the clock stopped between them. */
    fun afterOk(): List<Float> {
        compose.mainClock.autoAdvance = false
        ok()
        return List(16) {
            compose.mainClock.advanceTimeBy(16)
            artWidth()
        }
    }

    /** How many pixels of Gamma's red art the row through its middle covers. */
    fun artWidth(): Float {
        val image = compose.onRoot().captureToImage().toPixelMap()
        val bounds = compose.onNodeWithTag("card:art:${key("Gamma")}", useUnmergedTree = true).getBoundsInRoot()
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
}
