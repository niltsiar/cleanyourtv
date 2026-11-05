package dev.niltsiar.tvhome.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import app.cash.molecule.RecompositionMode
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.OverlayHost
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.LibraryApp
import dev.niltsiar.tvhome.core.model.LibraryArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs

/** The library in Home's overlay host, read from the rendered pixels and the laid-out cards. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class LibraryOverlayLookUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var home by mutableStateOf(Color.Black)

    private val white =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.White })
        }

    /** Nine apps off Home, in this order: App 1 to App 9. */
    private val nineApps = (1..9).map { libraryApp("App $it") }

    private fun showLibrary(
        apps: List<LibraryApp>,
        art: LibraryArt? = null,
    ) {
        val config = TvConfig(appLibrary = true)
        val library = FakeLibrary(apps)
        lateinit var viewModel: MoleculeLibraryViewModel
        compose.setContent {
            TvTheme {
                val scope = rememberCoroutineScope()
                viewModel =
                    remember {
                        MoleculeLibraryViewModel(
                            library.sources,
                            config,
                            Locale.ENGLISH,
                            scope,
                            RecompositionMode.Immediate,
                            EmptyCoroutineContext,
                        )
                    }
                val state = viewModel.uiState.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(home))
                    OverlayHost(open = { state.value.open }) {
                        LibraryOverlay(
                            state = { state.value },
                            layout = config.libraryLayout(),
                            actions = viewModel.actions,
                            art = { art },
                        )
                    }
                }
            }
        }
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        viewModel.onOpen()
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    private fun card(name: String): DpRect = bounds("card:${key(name)}")

    /** The focused card's bounds: its focus growth is drawn, so they are its size before it. */
    private fun focusedCard(name: String): DpRect {
        compose.onNodeWithTag("card:${key(name)}").assertIsFocused()
        return card(name)
    }

    /** Where the focused card draws, grown about its centre. */
    private fun grownCard(name: String): DpRect {
        val card = focusedCard(name)
        val dx = card.width * (FOCUSED_SCALE - 1f) / 2f
        val dy = card.height * (FOCUSED_SCALE - 1f) / 2f
        return DpRect(card.left - dx, card.top - dy, card.right + dx, card.bottom + dy)
    }

    /** The red channel, 0 to 255, of the screen at ([x], [y]). */
    private fun redAt(
        x: Dp,
        y: Dp,
    ): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        return Math.round(pixels[(x.value * density).toInt(), (y.value * density).toInt()].red * 255)
    }

    private fun assertNear(
        expected: Dp,
        actual: Dp,
    ) = assertTrue("expected $expected, got $actual", abs(actual.value - expected.value) <= 1f)

    /** How much of 255 the panel's inside changes when Home behind it goes from black to white. */
    private fun bleedThrough(): Int {
        showLibrary(listOf(libraryApp("Alpha"), libraryApp("Beta")))
        // The gap between the title and the count, in the panel's header row.
        val inside = {
            compose
                .onRoot()
                .captureToImage()
                .toPixelMap()[PANEL_X, PANEL_HEADER_Y]
                .red
        }
        val dark = inside()
        home = Color.White
        compose.waitForIdle()
        return Math.round((inside() - dark) * 255)
    }

    @Test
    fun a_bright_home_does_not_show_through_the_panel() {
        assertTrue(bleedThrough() <= MAX_BLEED)
    }

    @Test
    fun five_cards_show_whole_and_the_sixth_peeks_40_dp() {
        showLibrary(nineApps)

        val row = bounds("library:cards")
        assertTrue("${card("App 5")} in $row", card("App 5").right <= row.right)
        assertNear(row.right - 40.dp, card("App 6").left)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_ring_of_the_first_card_shows_whole_at_its_side_and_top() {
        showLibrary(nineApps)

        val grown = grownCard("App 1")
        // The middle of the white ring: 3 dp wide, 3 dp out from the grown card, grown with it.
        val out = 4.5.dp * FOCUSED_SCALE
        assertTrue(redAt(grown.left - out, (grown.top + grown.bottom) / 2) >= WHITE)
        assertTrue(redAt((grown.left + grown.right) / 2, grown.top - out) >= WHITE)
    }

    @Test
    fun past_the_fifth_card_the_row_scrolls_one_card_and_the_next_one_peeks_40_dp() {
        showLibrary(nineApps)

        press(Key.DirectionRight, times = 5)

        val row = bounds("library:cards")
        assertNear(row.right - 56.dp, focusedCard("App 6").right)
        assertNear(row.right - 40.dp, card("App 7").left)
    }

    @Test
    fun the_last_card_ends_the_row_and_coming_back_the_card_before_peeks_40_dp() {
        showLibrary(nineApps)
        val row = bounds("library:cards")
        val start = focusedCard("App 1").left

        press(Key.DirectionRight, times = 8)
        assertNear(row.right - (start - row.left), focusedCard("App 9").right)

        press(Key.DirectionLeft, times = 7)
        assertNear(row.left + 56.dp, focusedCard("App 2").left)
        assertNear(row.left + 40.dp, card("App 1").right)

        press(Key.DirectionLeft)
        assertNear(start, focusedCard("App 1").left)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_right_edge_fades_out_and_the_left_edge_fades_only_once_the_row_has_scrolled() {
        showLibrary(nineApps, art = LibraryArt(white, Color.White))
        val row = bounds("library:cards")
        val artMiddle = card("App 2").top + 42.dp

        assertTrue(redAt(card("App 5").right - 20.dp, artMiddle) >= WHITE)
        assertTrue(redAt(row.right - 4.dp, artMiddle) < FADED)
        assertTrue(redAt(card("App 1").left + 12.dp, artMiddle) >= WHITE)

        press(Key.DirectionRight, times = 5)

        val nearLeft = card("App 2").left + 12.dp
        assertTrue("$nearLeft in $row", nearLeft < row.left + 48.dp)
        assertTrue(redAt(nearLeft, artMiddle) < WHITE - 40)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_last_page_has_no_right_fade() {
        showLibrary(nineApps, art = LibraryArt(white, Color.White))

        press(Key.DirectionRight, times = 8)

        val last = card("App 9")
        assertTrue(redAt(last.right - 12.dp, last.top + 42.dp) >= WHITE)
    }

    @Test
    fun the_status_line_sits_at_the_bottom_of_every_card_on_one_baseline() {
        val long = "Long enough a name to wrap onto two lines"
        // Aaa takes the focus, and its growth with it; the cards measured here are idle.
        showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta"), libraryApp(long), libraryApp("Gamma", onHome = true)))

        fun text(
            text: String,
            name: String,
        ): DpRect =
            compose
                .onNode(hasText(text) and hasAnyAncestor(hasTestTag("card:${key(name)}")), useUnmergedTree = true)
                .getBoundsInRoot()

        compose.onNodeWithTag("card:${key("Aaa")}").assertIsFocused()
        val bottom = card("Beta").bottom - 12.dp
        assertNear(bottom, text("Add to Home", "Beta").bottom)
        assertNear(bottom, text("Add to Home", long).bottom)
        assertNear(bottom, text("On Home", "Gamma").bottom)
        assertEquals(text("Beta", "Beta").height, text(long, long).height)
    }

    @Test
    fun on_the_light_surface_an_idle_card_is_a_shade_darker_than_the_panel() {
        showLibrary(nineApps)

        val idle = card("App 3")
        val middle = (idle.top + idle.bottom) / 2
        val panel = redAt(idle.right + 8.dp, middle)
        val inside = redAt(idle.left + 4.dp, middle)
        assertTrue("card $inside, panel $panel", inside <= panel - 4)
    }

    /** The colour, 0 to 255 a channel, of the screen at ([x], [y]). */
    private fun rgbAt(
        x: Dp,
        y: Dp,
    ): List<Int> {
        val density = compose.activity.resources.displayMetrics.density
        val pixel = compose.onRoot().captureToImage().toPixelMap()[(x.value * density).toInt(), (y.value * density).toInt()]
        return listOf(pixel.red, pixel.green, pixel.blue).map { Math.round(it * 255) }
    }

    @Test
    @Config(qualifiers = "+night")
    fun an_idle_card_is_opaque_so_a_bright_home_does_not_show_through_it() {
        showLibrary(nineApps)
        home = Color.White
        compose.waitForIdle()

        val idle = card("App 3")
        val inside = rgbAt(idle.left + 4.dp, (idle.top + idle.bottom) / 2)
        val expected = listOf(0x1C, 0x20, 0x30)
        assertTrue("card $inside", inside.zip(expected).all { (actual, wanted) -> abs(actual - wanted) <= 2 })
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_card_on_home_has_no_green_check_badge() {
        showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta", onHome = true)))

        val onHome = card("Beta")
        val (red, green) = rgbAt(onHome.right - 15.dp, onHome.top + 15.dp)
        assertTrue("red $red, green $green", green - red < 20)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_right_fade_starts_at_the_peeking_card_and_leaves_more_of_it_showing() {
        showLibrary(nineApps, art = LibraryArt(white, Color.White))
        val row = bounds("library:cards")
        val peek = card("App 6")
        val panel = redAt(peek.left - 8.dp, peek.top - 4.dp)

        val x = peek.left + 12.dp
        val expected = panel + Math.round((255 - panel) * ((row.right - x) / 40.dp))
        val actual = redAt(x, peek.top + 42.dp)
        assertTrue("expected $expected, got $actual", abs(actual - expected) <= 8)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_name_of_the_peeking_card_is_not_drawn_while_under_half_of_the_card_shows() {
        val long = "App 6 Recorded Title List For The Peek"
        showLibrary(nineApps.take(5) + libraryApp(long) + nineApps.drop(6))
        val row = bounds("library:cards")
        val peek = card(long)
        val name =
            compose
                .onNode(
                    hasText(long) and hasAnyAncestor(hasTestTag("card:${key(long)}")),
                    useUnmergedTree = true,
                ).getBoundsInRoot()

        var brightest = 0
        var x = peek.left + 2.dp
        while (x < row.right - 2.dp) {
            brightest = maxOf(brightest, redAt(x, name.top + 8.dp), redAt(x, name.bottom - 8.dp))
            x += 2.dp
        }
        assertTrue("brightest $brightest over the peeking card's name", brightest <= FADED)
    }

    private companion object {
        const val PANEL_X = 960
        const val PANEL_HEADER_Y = 304
        const val MAX_BLEED = 10
        const val WHITE = 230
        const val FADED = 90
        const val FOCUSED_SCALE = 1.08f
    }
}
