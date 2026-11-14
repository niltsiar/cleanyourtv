package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.Wallpaper
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs

/** dock_title false: the focused tile gets no line and no scrim under it; the lines that are not the tile's keep both. */
val DockTitleUiTest by testSuite {
    robolectricTestSuite<DockTitleUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class DockTitleUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { DockTitleUiTestFixture() } asContextForEach {
            test("the focused tile is named over the dock by default") {
                showHome(dockTitle = true)

                titleText() shouldBe "App 1"
            }

            test("the focused tile is not named over the dock when dock title is false") {
                showHome(dockTitle = false)
                press(Key.DirectionRight)
                compose.onNodeWithTag("tile:pkg.2").assertIsFocused()

                titleText() shouldBe ""
            }

            test("reorder mode still names the moved app and shows its keys when dock title is false") {
                showHome(dockTitle = false)
                press(Key.Menu)
                press(Key.DirectionDown)
                press(Key.DirectionCenter)

                titleText() shouldBe "App 1"
                compose.onNode(hasText("drop") and hasAnyAncestor(hasTestTag("reorder:hint"))).assertExists()
            }

            test("the library pill still names the library when dock title is false") {
                showHome(dockTitle = false)
                press(Key.DirectionDown)
                press(Key.DirectionDown)

                compose.onNodeWithTag("library:pill").assertIsFocused()
                titleText() shouldBe "App library"
            }

            test("dock title false takes the scrim from under a focused tile") {
                showHome(dockTitle = true)
                val withTitle = redUnderTitle()

                dockTitleOn.value = false
                settle()

                withClue("red under the title: ${redUnderTitle()} with dock_title false, $withTitle with it true") {
                    redUnderTitle() -
                        withTitle shouldBeGreaterThan
                        15
                }
            }

            test("dock title false keeps the scrim under a top bar control s line") {
                showHome(dockTitle = false)
                press(Key.DirectionUp)
                val bare = redUnderTitle()

                dockTitleOn.value = true
                settle()

                withClue("red under the title: $bare with dock_title false, ${redUnderTitle()} with it true") {
                    abs(bare - redUnderTitle()) shouldBeLessThanOrEqualTo
                        3
                }
            }
        }
    })

private class DockTitleUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    val white: ImageBitmap =
        ImageBitmap(16, 9).also { Canvas(it).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White }) }

    val dockTitleOn = mutableStateOf(true)

    var probe = Offset.Zero

    fun showHome(dockTitle: Boolean) {
        dockTitleOn.value = true
        val config = SixColumns.copy(appLibrary = true)
        val sources = sources(apps = flowOf(AppList(NineApps)), wallpaper = flowOf(Wallpaper(picture = white, thumbnail = white)))
        compose.setContent {
            CompositionLocalProvider(LocalBakeDispatcher provides Dispatchers.Unconfined) {
                TvTheme(darkTheme = true) {
                    val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                    val state = viewModel.uiState.collectAsState()
                    HomeScreen(
                        state = { state.value },
                        layout = config.copy(dockTitle = dockTitleOn.value).homeLayout(),
                        onFocus = viewModel::onFocus,
                        onClick = {},
                        topBar = config.topBarLayout(),
                        shelfLayout = config.shelfLayout().copy(scrimAlpha = 0f),
                        actions = viewModel.actions,
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
        val title = compose.onNodeWithTag("shelf:title").getBoundsInRoot()
        probe = Offset(title.right.value, title.bottom.value + 3f)
        dockTitleOn.value = dockTitle
        settle()
    }

    fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    fun titleText(): String =
        compose
            .onNodeWithTag("shelf:title")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString("")
            .orEmpty()

    /** The red of the pixel just under the title, at the end of the first tile's name, where the hero scrim lies over the wallpaper. */
    fun redUnderTitle(): Int {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return Math.round(pixels[(probe.x * density).toInt(), (probe.y * density).toInt()].red * 255)
    }
}
