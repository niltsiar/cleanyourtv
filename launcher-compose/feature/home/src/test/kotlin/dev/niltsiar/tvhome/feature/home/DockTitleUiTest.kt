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
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** dock_title false: the focused tile gets no line and no scrim under it; the lines that are not the tile's keep both. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class DockTitleUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val white: ImageBitmap =
        ImageBitmap(16, 9).also { Canvas(it).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White }) }

    private val dockTitleOn = mutableStateOf(true)

    private var probe = Offset.Zero

    private fun showHome(dockTitle: Boolean) {
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

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun titleText(): String =
        compose
            .onNodeWithTag("shelf:title")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString("")
            .orEmpty()

    /** The red of the pixel just under the title, at the end of the first tile's name, where the hero scrim lies over the wallpaper. */
    private fun redUnderTitle(): Int {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return Math.round(pixels[(probe.x * density).toInt(), (probe.y * density).toInt()].red * 255)
    }

    @Test
    fun the_focused_tile_is_named_over_the_dock_by_default() {
        showHome(dockTitle = true)

        assertEquals("App 1", titleText())
    }

    @Test
    fun the_focused_tile_is_not_named_over_the_dock_when_dock_title_is_false() {
        showHome(dockTitle = false)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.2").assertIsFocused()

        assertEquals("", titleText())
    }

    @Test
    fun reorder_mode_still_names_the_moved_app_and_shows_its_keys_when_dock_title_is_false() {
        showHome(dockTitle = false)
        press(Key.Menu)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertEquals("App 1", titleText())
        compose.onNode(hasText("drop") and hasAnyAncestor(hasTestTag("reorder:hint"))).assertExists()
    }

    @Test
    fun the_library_pill_still_names_the_library_when_dock_title_is_false() {
        showHome(dockTitle = false)
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        compose.onNodeWithTag("library:pill").assertIsFocused()
        assertEquals("App library", titleText())
    }

    @Test
    fun dock_title_false_takes_the_scrim_from_under_a_focused_tile() {
        showHome(dockTitle = true)
        val withTitle = redUnderTitle()

        dockTitleOn.value = false
        settle()

        assertTrue(
            "red under the title: ${redUnderTitle()} with dock_title false, $withTitle with it true",
            redUnderTitle() - withTitle > 15,
        )
    }

    @Test
    fun dock_title_false_keeps_the_scrim_under_a_top_bar_control_s_line() {
        showHome(dockTitle = false)
        press(Key.DirectionUp)
        val bare = redUnderTitle()

        dockTitleOn.value = true
        settle()

        assertTrue("red under the title: $bare with dock_title false, ${redUnderTitle()} with it true", abs(bare - redUnderTitle()) <= 3)
    }
}
