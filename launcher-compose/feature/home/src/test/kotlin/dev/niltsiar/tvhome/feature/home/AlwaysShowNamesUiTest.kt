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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.FocusRingReach
import dev.niltsiar.tvhome.core.designsystem.TILE_FOCUSED_SCALE
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
    }

    @Test
    fun the_name_under_an_unfocused_dock_tile_shows_at_85_percent() {
        showHome(SixColumns.copy(alwaysShowNames = true))

        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        assertEquals(0.85f, ink("name:pkg.2"), 0.05f)
    }

    @Test
    fun the_name_under_an_unfocused_grid_tile_shows_at_85_percent() {
        showHome(SixColumns.copy(alwaysShowNames = true))

        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        assertEquals(0.85f, ink("name:pkg.8"), 0.05f)
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

    private val whiteApps = apps.map { it.copy(image = white) }

    private fun redUnderTop(
        tag: String,
        below: Float,
    ): Int {
        val tile = bounds(tag)
        return redAt((tile.left.value + tile.right.value) / 2f, tile.top.value + below)
    }

    /** How many dp of tile [tag] show above the screen's bottom edge. */
    private fun visibleDp(tag: String): Float =
        compose
            .onRoot()
            .getBoundsInRoot()
            .bottom.value - bounds(tag).top.value

    @Test
    @Config(qualifiers = "w960dp-h610dp-xhdpi")
    fun with_names_a_grid_row_with_under_48_dp_visible_draws_nothing_and_leaves_no_band() {
        showHome(named4, apps = whiteApps)

        assertTrue("the row shows ${visibleDp("tile:pkg.5")} dp", visibleDp("tile:pkg.5") in 30f..48f)
        assertEquals(0, redUnderTop("tile:pkg.5", 2f))
        assertEquals(0, redUnderTop("tile:pkg.5", 10f))
    }

    @Test
    @Config(qualifiers = "w960dp-h620dp-xhdpi")
    fun with_names_a_grid_row_with_48_dp_above_the_edge_but_less_left_after_the_bottom_fade_draws_nothing() {
        showHome(named4, apps = whiteApps)

        assertTrue("the row shows ${visibleDp("tile:pkg.5")} dp", visibleDp("tile:pkg.5") in 48f..60f)
        assertEquals(0, redUnderTop("tile:pkg.5", 2f))
    }

    /** How many dp of tile [tag] show: the rows of its middle column, on the screen, whose red is above 10 percent. */
    private fun drawnDp(tag: String): Float {
        val density = compose.activity.resources.displayMetrics.density
        val tile = bounds(tag)
        val screen =
            compose
                .onRoot()
                .getBoundsInRoot()
                .bottom.value
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val x = ((tile.left.value + tile.right.value) / 2 * density).toInt()
        val rows =
            (tile.top.value * density).toInt().coerceAtLeast(0).until((minOf(tile.bottom.value, screen) * density).toInt()).count {
                pixels[x, it].red > 0.1f
            }
        return rows / density
    }

    /** At each of six scroll steps, no tile in the lower half of the screen shows more than nothing and less than 48 dp of itself. */
    private fun assertNoStripAtAnyScrollStep(config: TvConfig) {
        showHome(config, apps = whiteApps)
        repeat(6) { step ->
            press(Key.DirectionDown)
            val lower =
                (1..18).map { "tile:pkg.$it" }.filter {
                    compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() && bounds(it).top.value > 270f
                }
            val strips = lower.associateWith(::drawnDp).filterValues { it in 0.5f..47f }
            assertTrue("a row shows a strip of its tile at step ${step + 1}: $strips", strips.isEmpty())
        }
    }

    @Test
    fun with_names_no_grid_row_shows_a_strip_of_under_48_dp_of_its_tile_at_any_scroll_step() = assertNoStripAtAnyScrollStep(named4)

    @Test
    fun without_names_no_grid_row_shows_a_strip_of_under_48_dp_of_its_tile_at_any_scroll_step() =
        assertNoStripAtAnyScrollStep(SixColumns.copy(columns = 4))

    @Test
    fun in_a_single_row_every_tile_has_its_name_and_the_panel_grows_to_hold_them() {
        showHome(SixColumns.copy(singleRow = true, alwaysShowNames = true))

        assertEquals(1, compose.onAllNodesWithTag("name:pkg.5").fetchSemanticsNodes().size)
        assertNear(height("tile:pkg.1") + TileGap * 2 + TileNameRoom, height("dock"))
    }

    private val longName = List(30) { "Largo" }.joinToString(" ")

    private fun withLongName(vararg keys: String) = apps.map { if (it.key in keys) it.copy(label = longName) else it }

    private val named4 = SixColumns.copy(columns = 4, alwaysShowNames = true)

    @Test
    fun a_name_too_long_for_its_tile_stays_on_one_line_in_a_pill_no_wider_than_the_tile() {
        showHome(named4, apps = withLongName("pkg.1"))

        assertNear(height("name:pkg.2"), height("name:pkg.1"))
        // The pill is the name's box and 6 dp of padding on each side.
        val pill = bounds("name:pkg.1").width.value + 12f
        val tile = bounds("tile:pkg.1").width.value
        assertTrue("the pill is $pill dp wide under a tile of $tile dp", pill <= tile + 0.5f && pill >= tile - 16f)
    }

    @Test
    fun a_name_too_long_for_its_tile_does_not_make_its_row_taller() {
        showHome(SixColumns.copy(alwaysShowNames = true), apps = withLongName("pkg.7"))

        assertNear(height("tile:pkg.1") + TileGap + TileNameRoom, rowPitch())
    }

    /** How far the focused ring's outer stroke ends under tile [key], then the gap to the top of its name's pill, in dp. */
    private fun ringToPill(key: String): Float {
        val tile = bounds("tile:$key")
        val ringBottom =
            tile.bottom.value + tile.height.value * (TILE_FOCUSED_SCALE - 1f) / 2f + FocusRingReach.value * TILE_FOCUSED_SCALE
        return bounds("name:$key").top.value - 2f - ringBottom
    }

    @Test
    fun the_focused_ring_ends_6_dp_or_more_above_the_name_pill_in_four_columns() {
        showHome(named4)

        assertTrue("the ring ends ${ringToPill("pkg.1")} dp above the pill", ringToPill("pkg.1") >= 6f)
    }

    @Test
    fun the_focused_ring_ends_6_dp_or_more_above_the_name_pill_on_a_large_tile() {
        showHome(SixColumns.copy(columns = 2, alwaysShowNames = true))

        assertTrue("the ring ends ${ringToPill("pkg.1")} dp above the pill", ringToPill("pkg.1") >= 6f)
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    /** The red channel at ([x], [y]) in dp, relative to the left and top of the pill that holds name [tag]: the text's box and 6 dp across, 2 dp down. */
    private fun inPill(
        tag: String,
        x: Float,
        y: Float,
    ): Int {
        val name = bounds(tag)
        return redAt(name.left.value - 6f + x, name.top.value - 2f + y)
    }

    @Test
    fun a_name_pill_is_a_capsule_of_the_height_of_one_line_also_when_the_name_is_cut() {
        showHome(named4, wallpaper = Wallpaper(picture = white), apps = withLongName("pkg.2"))
        val bare = inPill("name:pkg.3", -6f, 12f)

        // One line: 24 dp tall, so its ends are half circles of 12 dp.
        for (key in listOf("pkg.3", "pkg.2")) {
            assertTrue("$key, corner ${inPill("name:$key", 2.5f, 2.5f)} of $bare", inPill("name:$key", 2.5f, 2.5f) > bare * 0.8f)
            assertTrue("$key, end ${inPill("name:$key", 1.2f, 12f)} of $bare", inPill("name:$key", 1.2f, 12f) < bare * 0.6f)
            assertNear(24.dp, (bounds("name:$key").height.value + 4f).dp)
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_focused_name_is_dark_text_on_a_white_pill_and_the_others_keep_their_dark_pill() {
        showHome(named4, wallpaper = Wallpaper(picture = white))

        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
        assertTrue("focused pill ${inPill("name:pkg.1", 3f, 12f)}", inPill("name:pkg.1", 3f, 12f) >= 230)
        assertTrue("other pill ${inPill("name:pkg.2", 3f, 12f)}", inPill("name:pkg.2", 3f, 12f) < 140)
        val darkest =
            compose.onNodeWithTag("name:pkg.1").captureToImage().toPixelMap().let { pixels ->
                (0 until pixels.height).minOf { y -> (0 until pixels.width).minOf { x -> pixels[x, y].red } }
            }
        assertTrue("the text is $darkest", darkest < 0.25f)
    }

    private fun left(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().left

    @Test
    fun with_names_the_tiles_and_the_dock_keep_their_names_off_width_and_x() {
        showHome(named4)
        val gridWidth =
            compose
                .onRoot()
                .getBoundsInRoot()
                .width.value - ScreenMargin.value * 2
        val tile = ((gridWidth - TileGap.value * 3) / 4).dp

        assertNear(tile, bounds("tile:pkg.1").width)
        assertNear(ScreenMargin, left("tile:pkg.1"))
        assertNear(ScreenMargin - TileGap, left("dock"))
        assertNear((gridWidth + TileGap.value * 2).dp, bounds("dock").width)
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.5").assertIsFocused()
        assertNear(tile, bounds("tile:pkg.5").width)
        assertNear(ScreenMargin, left("tile:pkg.5"))
    }

    @Test
    fun with_names_a_shrunk_grid_stays_centred_between_the_margins_as_it_does_without() {
        showHome(SixColumns.copy(columns = 4, rowsBelowDock = 3, alwaysShowNames = true))
        press(Key.DirectionDown)

        assertTrue("the grid starts at ${left("tile:pkg.5")}", left("tile:pkg.5").value > ScreenMargin.value + 4f)
    }

    @Test
    fun without_names_a_shrunk_grid_stays_centred_between_the_margins() {
        showHome(SixColumns.copy(columns = 4, rowsBelowDock = 3))
        press(Key.DirectionDown)

        assertTrue("the grid starts at ${left("tile:pkg.5")}", left("tile:pkg.5").value > ScreenMargin.value + 4f)
    }
}
