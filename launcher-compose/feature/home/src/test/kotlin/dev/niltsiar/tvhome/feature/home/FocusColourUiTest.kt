package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** The focus ring, and the light the focused dock tile casts on the dock, in the app's colour: read from the pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class FocusColourUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val red = Color(0xFFE53935)
    private val blue = Color(0xFF1E88E5)
    private val green = Color(0xFF1DB954)
    private val accents = listOf(red, blue, green, blue, red, blue, green, red, blue)
    private val apps = accents.mapIndexed { i, accent -> AppEntry("pkg.${i + 1}", "pkg.${i + 1}", "App ${i + 1}", accent = accent) }
    private val cinematic = SixColumns.copy(cinematicHome = true, appLibrary = true)

    private var homePresses by mutableIntStateOf(0)

    private fun showHome(
        config: TvConfig = cinematic,
        appList: Flow<AppList> = flowOf(AppList(apps)),
    ) {
        val sources = sources(apps = appList)
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
                    homePresses = { homePresses },
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

    private fun holdOk() {
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
    }

    private fun tile(n: Int): DpRect = compose.onNodeWithTag("tile:pkg.$n").getBoundsInRoot()

    private fun panel(): DpRect = compose.onNodeWithTag("dock").getBoundsInRoot()

    private fun at(
        x: Float,
        y: Float,
    ): Color {
        val density = compose.activity.resources.displayMetrics.density
        return compose.onRoot().captureToImage().toPixelMap()[(x * density).toInt(), (y * density).toInt()]
    }

    private fun centreX(n: Int) = (tile(n).left.value + tile(n).right.value) / 2f

    /** The middle of the ring of tile [n]'s top edge: its outline, 3 dp gap and half the 3 dp stroke out, grown 1.08 times about the tile's centre. */
    private fun ringAtTop(n: Int): Color {
        val box = tile(n)
        val centre = (box.top.value + box.bottom.value) / 2f
        return at(centreX(n), centre - 1.08f * ((box.bottom.value - box.top.value) / 2f + 4.5f))
    }

    private fun assertNear(
        expected: Color,
        actual: Color,
        tolerance: Int = 10,
    ) {
        val gap = listOf(expected.red - actual.red, expected.green - actual.green, expected.blue - actual.blue).maxOf { abs(it) * 255 }
        assertTrue("expected $expected, got $actual", gap <= tolerance)
    }

    @Test
    fun a_dock_tiles_ring_takes_its_apps_colour() {
        showHome()

        assertNear(ringColor(red), ringAtTop(1))
    }

    @Test
    fun cinematic_white_focus_keeps_the_ring_white() {
        showHome(cinematic.copy(cinematicWhiteFocus = true))

        assertNear(Color.White, ringAtTop(1))
    }

    @Test
    fun a_grid_tiles_ring_is_white_without_app_glow_and_takes_the_apps_colour_with_it() {
        showHome(cinematic.copy(appGlow = false))
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        assertNear(Color.White, ringAtTop(7))
    }

    @Test
    fun with_app_glow_a_grid_tiles_ring_takes_the_apps_colour() {
        showHome()
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        assertNear(ringColor(green), ringAtTop(7))
    }

    @Test
    fun along_the_dock_the_new_ring_is_between_the_two_colours_on_its_way_and_ends_at_its_own() {
        showHome()
        compose.mainClock.autoAdvance = false

        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeBy(112)
        val onTheWay = ringAtTop(2)
        compose.mainClock.advanceTimeBy(1_000)
        val late = ringAtTop(2)

        // The first ring is a light red and the second a light blue: on the way the ring still has red in it.
        assertNear(ringColor(blue), late)
        assertTrue("on the way $onTheWay, at the end $late", onTheWay.red > late.red + 0.04f)
    }

    @Test
    fun the_ring_round_a_tile_whose_menu_is_open_takes_the_tiles_colour() {
        showHome()
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()

        holdOk()

        compose.onNodeWithTag("menu").assertExists()
        val box = tile(3)
        val ring = at(centreX(3), box.top.value - 1f)
        assertTrue("green leads the ring: $ring", ring.green > ring.red + 0.08f && ring.green > ring.blue + 0.03f)
    }

    /** How coloured the dock's top rim is at tile [n]'s centre: the white rim is grey, the light over it is not. */
    private fun rimColour(n: Int): Float = rimStrength(centreX(n))

    /** The rim's colour at tile [n] after [key] is pressed. */
    private fun rimColourAfter(
        key: Key,
        n: Int,
    ): Float {
        press(key)
        return rimColour(n)
    }

    @Test
    fun the_focused_dock_tile_lights_the_rim_over_it_in_its_colour() {
        showHome()
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()

        val rim = panel().top.value + 0.75f
        val lit = at(centreX(3), rim)
        val bare = at(centreX(6), rim)
        assertTrue("lit $lit, bare $bare", lit.green > lit.red + 0.15f && lit.green > lit.blue + 0.1f && abs(bare.green - bare.red) < 0.1f)
    }

    @Test
    fun the_light_leaves_a_tile_that_loses_focus_along_the_dock_and_comes_to_the_next() {
        showHome()
        assertTrue("on tile 1", rimColour(1) > 0.2f)
        assertTrue("not on tile 2", rimColour(2) < 0.12f)

        assertTrue("left tile 1", rimColourAfter(Key.DirectionRight, 1) < 0.12f)
        assertTrue("came to tile 2", rimColour(2) > 0.2f)
    }

    @Test
    fun the_light_fades_out_when_focus_leaves_the_dock_row_and_is_back_when_it_returns() {
        showHome()
        assertTrue("in the dock", rimColour(1) > 0.2f)

        assertTrue("top bar focused", rimColourAfter(Key.DirectionUp, 1) < 0.12f)
        assertTrue("back in the dock", rimColourAfter(Key.DirectionDown, 1) > 0.2f)
    }

    private fun assertRimStaysGrey(config: TvConfig) {
        showHome(config)

        assertTrue("tile 1 focused", rimColour(1) < 0.12f)
        assertTrue("tile 2 focused", rimColourAfter(Key.DirectionRight, 2) < 0.12f)
    }

    @Test
    fun with_dock_edge_light_false_the_rim_stays_grey() = assertRimStaysGrey(cinematic.copy(dockEdgeLight = false))

    @Test
    fun with_animations_off_the_rim_stays_grey() = assertRimStaysGrey(cinematic.copy(animations = false))

    @Test
    fun with_dock_band_off_there_is_no_rim_to_light() = assertRimStaysGrey(cinematic.copy(dockBand = false))

    private fun focused(n: Int): Boolean =
        compose
            .onNodeWithTag("tile:pkg.$n")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Focused) == true

    private fun rimStrength(x: Float): Float {
        val rim = at(x, panel().top.value + 0.75f)
        return maxOf(rim.red, rim.green, rim.blue) - minOf(rim.red, rim.green, rim.blue)
    }

    /** The x, in dp, where the dock's top rim is most coloured, away from the panel's rounded ends. */
    private fun lightCentre(): Float {
        val box = panel()
        return (box.left.value.toInt() + 40 until box.right.value.toInt() - 40).maxBy { rimStrength(it.toFloat()) }.toFloat()
    }

    @Test
    fun a_home_press_from_the_grid_gives_the_first_dock_tile_its_own_ring_colour_at_once() {
        showHome()
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
        compose.mainClock.autoAdvance = false

        homePresses++
        var frames = 0
        while (!focused(1) && frames++ < 60) compose.mainClock.advanceTimeBy(16)
        compose.mainClock.advanceTimeBy(80)

        // The grid tile's ring was green and the dock tile's is red: a ring still on its way from green is not red yet.
        val ring = ringAtTop(1)
        assertTrue("red ring expected, got $ring", ring.red > ring.green + 0.04f)
    }

    @Test
    fun the_light_on_the_rim_takes_the_colour_of_art_that_lands_after_focus() {
        val list = MutableStateFlow(AppList(apps.map { it.copy(accent = Color.Unspecified) }))
        showHome(appList = list)
        assertTrue("white before the art", rimColour(1) < 0.12f)

        list.value = AppList(apps)
        settle()

        val lit = at(centreX(1), panel().top.value + 0.75f)
        assertTrue("red expected, got $lit", lit.red > lit.green + 0.12f && lit.red > lit.blue + 0.08f)
    }

    @Test
    fun with_a_single_row_the_light_follows_the_focused_tile_when_the_row_scrolls() {
        showHome(cinematic.copy(singleRow = true))
        repeat(8) { press(Key.DirectionRight) }
        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()

        assertTrue("light at ${lightCentre()}, tile at ${centreX(9)}", abs(lightCentre() - centreX(9)) <= 6f)
    }

    @Test
    fun the_light_follows_a_tile_carried_along_the_dock_or_is_not_shown() {
        showHome()
        holdOk()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()

        press(Key.DirectionRight)
        press(Key.DirectionRight)

        val centre = lightCentre()
        assertTrue("light at $centre, tile at ${centreX(1)}", rimStrength(centre) < 0.12f || abs(centre - centreX(1)) <= 6f)
    }
}
