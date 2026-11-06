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
import androidx.compose.ui.unit.height
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.Wallpaper
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

/** always_show_names puts the app's name under every tile, dimmed until it has focus, and makes room for it only then. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class AlwaysShowNamesUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }

    private val apps = (1..18).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

    private fun showHome(
        config: TvConfig,
        wallpaper: Wallpaper = Wallpaper(),
        apps: List<AppEntry> = this.apps,
    ) {
        val sources = sources(apps = flowOf(AppList(apps)), wallpaper = flowOf(wallpaper))
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

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun top(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().top

    private fun height(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().height

    /** How far the brightest and the darkest red of [tag] are apart: what shows of the name, in 0 to 1. */
    private fun contrast(tag: String): Float {
        val image: ImageBitmap = compose.onNodeWithTag(tag).captureToImage()
        val pixels = image.toPixelMap()
        val reds = (0 until image.height).flatMap { y -> (0 until image.width).map { x -> pixels[x, y].red } }
        return reds.max() - reds.min()
    }

    /** From the top of the first grid row to the top of the second, which only both being on screen can tell. */
    private fun rowPitch(): Dp {
        repeat(2) { press(Key.DirectionDown) }
        compose.onNodeWithTag("tile:pkg.13").assertIsFocused()
        return top("tile:pkg.13") - top("tile:pkg.7")
    }

    private fun assertNear(
        expected: Dp,
        actual: Dp,
    ) = assertTrue("expected $expected, got $actual", abs(expected.value - actual.value) <= 1f)

    @Test
    fun a_name_cut_to_two_lines_on_a_text_tile_ends_in_an_ellipsis_with_no_space_before_it() {
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

        assertTrue("$shown", shown.all { it.endsWith(Char(0x2026).toString()) })
        assertTrue("$shown", shown.none { it.endsWith(" " + Char(0x2026)) })
    }

    @Test
    fun with_the_key_off_no_tile_has_a_name_under_it() {
        showHome(SixColumns)

        assertEquals(0, compose.onAllNodesWithTag("name:pkg.1").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("name:pkg.7").fetchSemanticsNodes().size)
    }

    /** The brightest red, 0 to 1, of [tag]: the ink of a name over its pill. */
    private fun ink(tag: String): Float {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return (0 until pixels.height).maxOf { y -> (0 until pixels.width).maxOf { x -> pixels[x, y].red } }
    }

    @Test
    fun a_name_on_its_pill_is_white_ink_with_the_surfaces_light_too() {
        showHome(SixColumns.copy(alwaysShowNames = true))

        assertEquals(0.85f, ink("name:pkg.2"), 0.05f)
        press(Key.DirectionRight)
        assertEquals(1f, ink("name:pkg.2"), 0.03f)
    }

    @Test
    fun the_name_under_an_unfocused_dock_tile_shows_at_85_percent_and_at_full_when_focused() {
        showHome(SixColumns.copy(alwaysShowNames = true))
        val unfocused = contrast("name:pkg.2")

        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.2").assertIsFocused()
        val focused = contrast("name:pkg.2")

        assertEquals(0.85f, unfocused / focused, 0.05f)
    }

    @Test
    fun the_name_under_an_unfocused_grid_tile_shows_at_85_percent_and_at_full_when_focused() {
        showHome(SixColumns.copy(alwaysShowNames = true))

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        assertEquals(0.85f, contrast("name:pkg.8") / contrast("name:pkg.7"), 0.05f)
    }

    private fun redAt(
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
    private fun besideName(
        tag: String,
        out: Float,
    ): Pair<Int, Int> {
        val name = compose.onNodeWithTag(tag).getBoundsInRoot()
        val middle = (name.top.value + name.bottom.value) / 2f
        return redAt(name.left.value - out, middle) to redAt(name.right.value + out, middle)
    }

    @Test
    fun a_grid_name_sits_on_a_black_055_pill_6_dp_wider_than_its_text_on_each_side() {
        showHome(SixColumns.copy(alwaysShowNames = true), wallpaper = Wallpaper(picture = white))
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        val (bareLeft, bareRight) = besideName("name:pkg.9", out = 9f)
        val (padLeft, padRight) = besideName("name:pkg.9", out = 3f)

        // The text's own shadow darkens its padding a little too, so the pill is "under half of the bare picture".
        assertTrue("left $padLeft of $bareLeft", padLeft < bareLeft * 0.5f)
        assertTrue("right $padRight of $bareRight", padRight < bareRight * 0.5f)
    }

    @Test
    fun a_dock_name_sits_on_a_black_055_pill_6_dp_wider_than_its_text_on_each_side() {
        showHome(SixColumns.copy(alwaysShowNames = true), wallpaper = Wallpaper(picture = white))

        val (bareLeft, bareRight) = besideName("name:pkg.4", out = 9f)
        val (padLeft, padRight) = besideName("name:pkg.4", out = 3f)

        assertTrue("left $padLeft of $bareLeft", padLeft < bareLeft * 0.5f)
        assertTrue("right $padRight of $bareRight", padRight < bareRight * 0.5f)
    }

    @Test
    fun with_the_key_off_the_dock_panel_is_a_tile_and_two_gaps_tall() {
        showHome(SixColumns)
        val tile = height("tile:pkg.1")
        assertNear(tile + TileGap * 2, height("dock"))
    }

    @Test
    fun the_dock_panel_grows_by_the_room_of_a_name_with_the_key_on() {
        showHome(SixColumns.copy(alwaysShowNames = true))
        val tile = height("tile:pkg.1")
        assertNear(tile + TileGap * 2 + TileNameRoom, height("dock"))
    }

    @Test
    fun a_grid_row_is_a_tile_and_a_gap_tall_with_the_key_off() {
        showHome(SixColumns)
        assertNear(height("tile:pkg.1") + TileGap, rowPitch())
    }

    @Test
    fun a_grid_row_grows_by_the_room_of_a_name_with_the_key_on() {
        showHome(SixColumns.copy(alwaysShowNames = true))
        assertNear(height("tile:pkg.1") + TileGap + TileNameRoom, rowPitch())
    }

    @Test
    fun with_four_columns_and_names_the_second_grid_row_shows_whole_with_its_names() {
        showHome(SixColumns.copy(columns = 4, alwaysShowNames = true), wallpaper = Wallpaper(picture = white))
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.5").assertIsFocused()
        val screen =
            compose
                .onRoot()
                .getBoundsInRoot()
                .bottom.value

        val name = compose.onNodeWithTag("name:pkg.10").getBoundsInRoot()
        assertTrue("the second row's name ends at ${name.bottom}", name.bottom.value <= screen - 12f)
        val first = compose.onNodeWithTag("tile:pkg.6").getBoundsInRoot()
        val second = compose.onNodeWithTag("tile:pkg.10").getBoundsInRoot()
        val x = first.left.value + 8f
        val middle = { tile: DpRect -> (tile.top.value + tile.bottom.value) / 2f }
        assertEquals(redAt(x, middle(first)), redAt(x, middle(second)))
    }

    @Test
    fun in_a_single_row_every_tile_has_its_name_and_the_panel_grows_to_hold_them() {
        showHome(SixColumns.copy(singleRow = true, alwaysShowNames = true))

        assertEquals(1, compose.onAllNodesWithTag("name:pkg.5").fetchSemanticsNodes().size)
        assertNear(height("tile:pkg.1") + TileGap * 2 + TileNameRoom, height("dock"))
    }
}
