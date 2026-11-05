package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** The tile menu over a Home of white tiles, read from the rendered pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeOverlayLookUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }
    private val apps = (1..9).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = white) }

    private fun showHome() {
        val sources = sources(apps = flowOf(AppList(apps)), inputs = flowOf(FourPorts))
        compose.setContent {
            TvTheme(glassKind = GlassKind.Flat) {
                val viewModel = rememberHomeViewModel(sources, SixColumns, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = SixColumns.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = SixColumns.topBarLayout(),
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

    private fun openTileMenu() {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
        compose.onNodeWithTag("menu").assertExists()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    /** The red channel, 0 to 255, of the screen at [point] (in dp). */
    private fun redAt(point: Offset): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(pixels[(point.x * density).toInt(), (point.y * density).toInt()].red * 255)
    }

    private fun assertNear(
        expected: Int,
        actual: Int,
        tolerance: Int,
    ) = assertTrue("expected $expected, got $actual", abs(actual - expected) <= tolerance)

    @Test
    @Config(qualifiers = "+night")
    fun the_menu_panel_covers_the_white_tile_under_it() {
        showHome()
        openTileMenu()

        val menu = bounds("menu")
        val firstRow = bounds("menu:ReplaceInDock")
        val tile =
            apps
                .drop(1)
                .map { bounds("tile:${it.key}") }
                .first { it.left < menu.right && it.right > menu.left && it.bottom > firstRow.bottom && it.top < menu.bottom }
        val under =
            Offset(
                minOf(menu.right, tile.right).value - 12f,
                (maxOf(firstRow.bottom, tile.top).value + minOf(menu.bottom, tile.bottom).value) / 2,
            )
        assertNear(0x14 + 9, redAt(under), 9)
    }

    @Test
    @Config(qualifiers = "+night")
    fun an_open_menu_dims_home_behind_it_by_half_and_back_lifts_the_dim() {
        showHome()
        val farTile = bounds("tile:pkg.6")
        val far = Offset((farTile.left.value + farTile.right.value) / 2, (farTile.top.value + farTile.bottom.value) / 2)
        assertNear(255, redAt(far), 2)

        openTileMenu()
        assertNear((255 + 0x07) / 2, redAt(far), 4)

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        compose.onNodeWithTag("menu").assertDoesNotExist()
        assertNear(255, redAt(far), 2)
    }

    /** The red channel at the empty right end of the focused first row of the open tile menu. */
    private fun focusedRowRed(): Int {
        showHome()
        openTileMenu()
        compose.onNodeWithTag("menu:ReplaceInDock").assertIsFocused()
        val row = bounds("menu:ReplaceInDock")
        return redAt(Offset(row.right.value - 12f, (row.top.value + row.bottom.value) / 2))
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_focused_menu_row_is_a_light_pill_in_night_mode() {
        assertNear(0xF2, focusedRowRed(), 3)
    }

    @Test
    fun a_focused_menu_row_is_a_dark_pill_with_night_mode_off() {
        assertNear(0x11, focusedRowRed(), 3)
    }

    private fun openInputs() {
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
    private fun assertCapsuleFill(
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

    @Test
    @Config(qualifiers = "+night")
    fun an_unfocused_connected_row_shows_the_faint_green_capsule_in_night_mode() {
        openInputs()
        assertCapsuleFill(row = 1, fillAlpha = 0x29)
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_focused_connected_row_shows_the_stronger_green_capsule_on_its_light_pill_in_night_mode() {
        openInputs()
        assertCapsuleFill(row = 0, fillAlpha = 0x2E)
    }

    @Test
    fun an_unfocused_connected_row_shows_the_stronger_green_capsule_on_the_light_surface() {
        openInputs()
        assertCapsuleFill(row = 1, fillAlpha = 0x2E)
    }
}
