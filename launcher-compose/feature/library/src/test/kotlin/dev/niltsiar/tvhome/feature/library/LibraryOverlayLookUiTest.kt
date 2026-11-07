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
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import app.cash.molecule.RecompositionMode
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.OverlayHost
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.LibraryApp
import dev.niltsiar.tvhome.core.model.LibraryArt
import dev.niltsiar.tvhome.core.model.OpenedRecord
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
import kotlin.math.roundToInt

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

    /** 23 apps off Home, in this order: App 01 to App 23. */
    private val twentyThree = (1..23).map { libraryApp(app(it)) }

    private fun app(number: Int) = "App %02d".format(number)

    private fun showLibrary(
        apps: List<LibraryApp>,
        art: LibraryArt? = null,
        config: TvConfig = TvConfig(appLibrary = true),
        motion: Motion = Motion(),
        record: OpenedRecord = OpenedRecord(),
    ) {
        val library = FakeLibrary(apps).apply { this.record = record }
        lateinit var viewModel: MoleculeLibraryViewModel
        compose.setContent {
            TvTheme(motion = motion) {
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

    private fun art(name: String): DpRect = compose.onNodeWithTag("card:art:${key(name)}", useUnmergedTree = true).getBoundsInRoot()

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
    fun three_rows_of_six_cards_show_whole_and_the_fourth_row_waits_below() {
        showLibrary(twentyThree)

        val grid = bounds("library:cards")
        val shown = (1..18).map { card(app(it)) }
        assertTrue(
            "$shown in $grid",
            shown.all {
                it.left >= grid.left && it.right <= grid.right && it.top >= grid.top &&
                    it.bottom <= grid.bottom
            },
        )
        assertEquals(3, shown.map { it.top.value.roundToInt() }.distinct().size)
        assertEquals(6, shown.map { it.left.value.roundToInt() }.distinct().size)
        val fourth = compose.onAllNodesWithTag("card:${key(app(19))}").fetchSemanticsNodes()
        assertTrue(fourth.isEmpty() || card(app(19)).top >= grid.bottom)
    }

    @Test
    fun the_fourth_row_scrolls_in_with_its_ring_whole_and_the_first_row_comes_back_the_same_way() {
        showLibrary(twentyThree)
        val grid = bounds("library:cards")
        val reach = 7.dp * FOCUSED_SCALE

        press(Key.DirectionDown, times = 3)
        assertTrue("${grownCard(app(19))} in $grid", grownCard(app(19)).bottom + reach <= grid.bottom + 1.dp)

        press(Key.DirectionUp, times = 3)
        assertTrue("${grownCard(app(1))} in $grid", grownCard(app(1)).top - reach >= grid.top - 1.dp)
    }

    /** Whether the pixel at ([x], [y]) differs from the panel 40 dp to its left, where a chevron's tip would be. */
    private fun cueAt(
        x: Dp,
        y: Dp,
    ): Boolean = abs(redAt(x, y) - redAt(x - 40.dp, y)) > 40

    @Test
    fun a_chevron_under_the_grid_says_cards_wait_below_and_one_at_its_top_says_cards_wait_above() {
        showLibrary(twentyThree)
        val grid = bounds("library:cards")
        val middle = (grid.left + grid.right) / 2
        // The bottom chevron's tip: its pill is centred 9 dp under the grid and the chevron is 9 dp tall.
        val bottomTip = grid.bottom + 13.5.dp
        val topTip = grid.top + 3.5.dp

        assertTrue("bottom cue at the start", cueAt(middle, bottomTip))
        assertTrue("no top cue at the start", !cueAt(middle, topTip))

        press(Key.DirectionDown, times = 3)
        assertTrue("no bottom cue with the last row in view", !cueAt(middle, bottomTip))
        assertTrue("top cue with the first row scrolled out", cueAt(middle, topTip))
    }

    /** The columns, in dp, of the row at [y] where the screen is lighter than the panel 60 dp to the left of the grid by at least [level]. */
    private fun lighterColumns(
        y: Dp,
        level: Int,
    ): ClosedFloatingPointRange<Float> {
        val grid = bounds("library:cards")
        val ground = redAt(grid.left - 8.dp, y)
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val row = (y.value * density).toInt()
        val lit =
            (((grid.left.value + 20f) * density).toInt() until ((grid.right.value - 20f) * density).toInt()).filter {
                pixels[it, row].red * 255 - ground >= level
            }
        return (lit.min() / density)..((lit.max() + 1) / density)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_bottom_chevron_sits_in_a_36_dp_wide_20_dp_tall_pill_of_15_percent_white_centred_under_the_grid() {
        showLibrary(twentyThree)
        val grid = bounds("library:cards")
        val centre = grid.bottom + 9.dp

        // 15 % white over the panel's 0x14 is 20 + 0.15 x 235 = 55; 18 is half of what it adds. Only the middle row spans the whole pill.
        val pill = lighterColumns(centre, level = 18)

        assertEquals(36f, pill.endInclusive - pill.start, 1f)
        assertEquals((grid.left.value + grid.right.value) / 2f, (pill.start + pill.endInclusive) / 2f, 1f)
        assertTrue("the pill ends inside the panel ${bounds("library:panel")}", centre + 10.dp <= bounds("library:panel").bottom)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_bottom_chevron_pill_has_12_dp_clear_above_it_to_the_last_row_and_12_dp_below_it_to_the_panel() {
        showLibrary(twentyThree)
        val grid = bounds("library:cards")
        val lastRow = card(app(18)).bottom
        val panel = bounds("library:panel").bottom
        val middle = (grid.left + grid.right) / 2
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val x = (middle.value * density).toInt()
        val ground = pixels[x, ((lastRow.value + 3f) * density).toInt()].red * 255

        // The pill, at the centre column, is the rows lighter than the panel's inside by half of what 15 % white adds; the panel's own rim is not.
        val lit =
            (((lastRow.value + 1f) * density).toInt() until ((panel.value - 1.5f) * density).toInt()).filter {
                pixels[x, it].red * 255 -
                    ground >=
                    18
            }

        val above = lit.min() / density - lastRow.value
        val below = panel.value - (lit.max() + 1) / density
        assertTrue("$above dp above the pill and $below dp below it", above >= 11.5f && below >= 11.5f)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_bottom_chevron_is_18_dp_wide() {
        showLibrary(twentyThree)
        val grid = bounds("library:cards")
        val centre = grid.bottom + 9.dp

        // The arms are 18 dp apart at their tops, 4.5 dp over the pill's middle, and 2 dp of stroke widen that to 20 dp.
        val tops = lighterColumns(centre - 4.dp, level = 70)

        assertEquals(20f, tops.endInclusive - tops.start, 1.5f)
    }

    @Test
    fun the_panel_keeps_31_dp_under_the_grid_and_ends_12_dp_over_the_screens_bottom() {
        showLibrary(twentyThree)

        val panel = bounds("library:panel")
        val screen = compose.onRoot().getBoundsInRoot()

        assertEquals(31f, (panel.bottom - bounds("library:cards").bottom).value, 0.5f)
        assertEquals(12.5f, (screen.bottom - panel.bottom).value, 0.5f)
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

    /** The names of the apps whose cards have focus: one, when the focus is in the grid. */
    private fun focusedApps(): List<String> =
        compose.onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config[SemanticsProperties.ContentDescription].single() }

    @Test
    fun the_23rd_app_is_seven_presses_from_the_first_and_no_key_goes_past_an_edge() {
        showLibrary(twentyThree)

        press(Key.DirectionUp)
        press(Key.DirectionLeft)
        assertEquals(listOf(app(1)), focusedApps())
        press(Key.DirectionDown, times = 3)
        assertEquals(listOf(app(19)), focusedApps())
        press(Key.DirectionRight, times = 4)
        assertEquals(listOf(app(23)), focusedApps())
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        assertEquals(listOf(app(23)), focusedApps())
    }

    @Test
    fun down_from_the_sixth_column_lands_on_the_last_card_of_the_short_last_row() {
        showLibrary(twentyThree)

        press(Key.DirectionRight, times = 5)
        press(Key.DirectionDown, times = 3)

        assertEquals(listOf(app(23)), focusedApps())
    }

    @Test
    fun down_into_the_fourth_row_keeps_the_column() {
        showLibrary(twentyThree)

        press(Key.DirectionRight, times = 2)
        press(Key.DirectionDown, times = 3)

        assertEquals(listOf(app(21)), focusedApps())
        press(Key.DirectionUp)
        assertEquals(listOf(app(15)), focusedApps())
    }

    @Test
    fun the_status_line_sits_at_the_bottom_of_every_card_on_one_baseline() {
        val long = "Long enough a name to wrap onto two lines"
        // Aaa takes the focus, and its growth with it; the cards measured here are idle.
        showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta"), libraryApp(long)))

        compose.onNodeWithTag("card:${key("Aaa")}").assertIsFocused()
        val bottom = card("Beta").bottom - 6.dp
        assertNear(bottom, statusText("Add to Home", "Beta").bottom)
        assertNear(bottom, statusText("Add to Home", long).bottom)
    }

    /** The layout bounds of the text of the card of [name] that holds [text]. */
    private fun statusText(
        text: String,
        name: String,
    ): DpRect =
        compose
            .onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("card:${key(name)}")), useUnmergedTree = true)
            .getBoundsInRoot()

    private val longNames = listOf("Long enough a name to wrap onto two lines", "Many words in a name that wraps onto two lines")

    /** The layout bottom of the last line of the name on the card of [name], in the root's dp. */
    private fun nameBottom(name: String): Dp {
        val node =
            compose.onNode(
                hasText(name.substringBefore(' '), substring = true) and hasAnyAncestor(hasTestTag("card:${key(name)}")),
                useUnmergedTree = true,
            )
        val layout = nameLayout(name)
        return node.getBoundsInRoot().top +
            (layout.getLineBottom(layout.lineCount - 1) / compose.activity.resources.displayMetrics.density).dp
    }

    private fun lastLineBottom(name: String): Dp {
        assertEquals("$name wraps onto two lines", 2, nameLayout(name).lineCount)
        return nameBottom(name)
    }

    /** The top of the highest text under the name on the card of [name]: where its status lines begin. */
    private fun statusTop(name: String): Dp {
        val density = compose.activity.resources.displayMetrics.density
        val texts =
            compose
                .onAllNodes(hasAnyAncestor(hasTestTag("card:${key(name)}")) and hasText("", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes()
        return (texts.drop(1).minOf { it.boundsInRoot.top } / density).dp
    }

    private fun assertNameClearsStatus() {
        val (focused, idle) = longNames
        compose.onNodeWithTag("card:${key(focused)}").assertIsFocused()
        val statusAt = statusTop(idle)
        assertTrue("idle: name ends ${lastLineBottom(idle)}, status starts $statusAt", statusAt - lastLineBottom(idle) >= 4.dp - 0.05.dp)
        // The focused card's name grows about the card's centre while its status line stays level with its neighbours'.
        val middle = (card(focused).top + card(focused).bottom) / 2
        val grownBottom = middle + (lastLineBottom(focused) - middle) * FOCUSED_SCALE
        assertTrue("focused: name ends $grownBottom, status starts $statusAt", statusAt - grownBottom >= 2.dp)
    }

    @Test
    fun a_two_line_name_ends_at_least_4_dp_above_the_status_line_and_a_focused_one_at_least_2_dp() {
        showLibrary(longNames.map { libraryApp(it) })

        assertNameClearsStatus()
    }

    private val describedName = "Lista de títulos grabados con altavoces"

    @Test
    fun in_the_chooser_a_two_line_name_with_a_descender_ends_at_least_6_dp_above_the_check_and_above_the_ring() {
        showLibrary(
            listOf(libraryApp("Aaa"), libraryApp("Bbb $describedName", onHome = true), libraryApp("Ccc $describedName")),
            art = LibraryArt(red, Color.Red),
            config = chooser,
        )

        for ((mark, name) in listOf("card:check" to "Bbb $describedName", "card:ring" to "Ccc $describedName")) {
            val top = compose.onNodeWithTag("$mark:${key(name)}", useUnmergedTree = true).getBoundsInRoot().top
            val room = top - lastLineBottom(name)
            assertTrue("the name ends $room above the $mark", room >= 6.dp - 0.05.dp)
        }
    }

    @Test
    fun a_card_keeps_its_outer_size_of_113_dp_tall_and_a_sixth_of_the_grid_wide() {
        showLibrary(listOf(libraryApp("Aaa"), libraryApp("Bbb $describedName")), art = LibraryArt(red, Color.Red), config = chooser)

        for (name in listOf("Aaa", "Bbb $describedName")) {
            assertEquals(113f, card(name).height.value, 0.5f)
            assertEquals(108.3f, card(name).width.value, 0.5f)
        }
    }

    @Test
    fun in_the_least_used_order_a_two_line_name_ends_at_least_4_dp_above_the_opened_line_and_a_focused_one_at_least_2_dp() {
        showLibrary(
            longNames.map { libraryApp(it) },
            config = TvConfig(appLibrary = true, libraryLeastUsed = true),
            record = OpenedRecord(times = mapOf(key(longNames[0]) to NOW - 3 * DAY), since = NOW - 90 * DAY),
        )
        press(Key.Menu)

        assertNameClearsStatus()
    }

    @Test
    fun in_the_least_used_order_the_status_line_is_the_opened_text_alone_and_the_art_keeps_the_usual_inset() {
        showLibrary(
            listOf(libraryApp("Aaa"), libraryApp("Beta", installedAt = NOW - DAY), libraryApp("Gamma", onHome = true)),
            config = TvConfig(appLibrary = true, libraryLeastUsed = true),
            record = OpenedRecord(times = mapOf(key("Gamma") to NOW - 3 * DAY), since = NOW - 90 * DAY),
        )
        val inset = { name: String -> art(name).left - card(name).left }
        assertNear(14.dp, inset("Beta"))

        press(Key.Menu)

        assertNear(14.dp, inset("Beta"))
        assertNear(14.dp, inset("Gamma"))
        assertEquals(listOf("Beta", "Never opened"), compose.textsOnCard("Beta"))
        assertEquals(listOf("Gamma"), compose.textsOnCard("Gamma").take(1))
        assertEquals(2, compose.textsOnCard("Gamma").size)
        assertTrue(compose.textsOnCard("Gamma")[1].startsWith("3 days ago, "))
    }

    /** The brightest red, 0 to 255, in the text line of [area] and the red of the card beside it. */
    private fun lineBrightness(area: DpRect): Pair<Int, Int> {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (area.left.value * density).toInt() until (area.right.value * density).toInt()
        val ys = (area.top.value * density).toInt() until (area.bottom.value * density).toInt()
        val peak = ys.maxOf { y -> xs.maxOf { x -> pixels[x, y].red } }
        return Math.round(peak * 255) to Math.round(pixels[xs.first, ys.first].red * 255)
    }

    @Test
    @Config(qualifiers = "+night")
    fun never_opened_draws_at_45_percent_of_the_alpha_of_a_last_opened_line() {
        showLibrary(
            listOf(libraryApp("Aaa"), libraryApp("Beta"), libraryApp("Gamma")),
            config = TvConfig(appLibrary = true, libraryLeastUsed = true),
            record = OpenedRecord(times = mapOf(key("Gamma") to NOW - 3 * DAY), since = NOW - 90 * DAY),
        )
        press(Key.Menu)

        fun lift(name: String): Float {
            val (peak, ground) = lineBrightness(compose.onNodeWithTag("card:opened:${key(name)}", useUnmergedTree = true).getBoundsInRoot())
            return (peak - ground).toFloat()
        }
        val ratio = lift("Beta") / lift("Gamma")
        assertTrue("never opened is $ratio of the opened line", ratio in 0.38f..0.52f)
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
    fun in_browse_mode_an_app_on_home_has_no_mark_and_an_empty_status_slot() {
        showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta"), libraryApp("Gamma", onHome = true)))

        compose.onAllNodes(hasContentDescription("On Home"), useUnmergedTree = true).assertCountEquals(0)
        val gamma = card("Gamma")
        val (peak, ground) = lineBrightness(DpRect(gamma.left + 6.dp, gamma.bottom - 20.dp, gamma.right - 6.dp, gamma.bottom - 6.dp))
        assertTrue("the status slot of an app on Home draws up to $peak over $ground", peak - ground <= 3)
    }

    /** The first and last column, in dp, where [area] holds a pixel lighter than its top left one by [TEXT_OVER_GROUND]; null for none. */
    private fun lightSpan(area: DpRect): ClosedFloatingPointRange<Float>? {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (area.left.value * density).toInt() until (area.right.value * density).toInt()
        val ys = (area.top.value * density).toInt() until (area.bottom.value * density).toInt()
        val ground = pixels[xs.first, ys.first].red
        val lit = xs.filter { x -> ys.any { y -> (pixels[x, y].red - ground) * 255 > TEXT_OVER_GROUND } }
        return if (lit.isEmpty()) null else (lit.first() / density)..((lit.last() + 1) / density)
    }

    @Test
    @Config(qualifiers = "+night")
    fun an_app_off_home_has_a_12_dp_plus_4_dp_before_its_add_to_home_line_at_the_lines_alpha() {
        showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta")))

        val text = statusText("Add to Home", "Beta")
        val plusArea = DpRect(text.left - 30.dp, text.top, text.left - 0.5.dp, text.bottom)
        val plus = lightSpan(plusArea)!!
        assertEquals(12f, plus.endInclusive - plus.start, 1f)
        assertEquals(4f, text.left.value - plus.endInclusive, 1f)
        val (plusPeak, ground) = lineBrightness(DpRect(text.left - 17.dp, text.top, text.left - 4.dp, text.bottom))
        val (textPeak, _) = lineBrightness(text)
        val ratio = (plusPeak - ground).toFloat() / (textPeak - ground)
        assertTrue("the plus peaks at $plusPeak, the line at $textPeak, over $ground", ratio in 0.7f..1.3f)
    }

    private val red =
        ImageBitmap(16, 9).also { image ->
            Canvas(image).drawRect(Rect(0f, 0f, 16f, 9f), Paint().apply { color = Color.Red })
        }

    private val chooser = TvConfig(appLibrary = true, appLibrarySelection = true)

    /** The chooser lists by label: the focus takes Aaa, on Home, and Bbb stays idle on Home. Ccc is the idle app off Home. */
    private fun showChooser() =
        showLibrary(
            listOf(libraryApp("Aaa", onHome = true), libraryApp("Bbb", onHome = true), libraryApp("Ccc"), libraryApp("Ddd")),
            art = LibraryArt(red, Color.Red),
            config = chooser,
        )

    /** The pixels of the screen, dp by dp, in [area] that are white to within 0.12. */
    private fun whitePixels(area: DpRect): Int {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (area.left.value * density).toInt() until (area.right.value * density).toInt()
        val ys = (area.top.value * density).toInt() until (area.bottom.value * density).toInt()
        return xs.sumOf { x ->
            ys.count { y ->
                val pixel = pixels[x, y]
                listOf(pixel.red, pixel.green, pixel.blue).all { it >= 0.88f }
            }
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun in_the_chooser_an_app_on_home_has_an_18_dp_check_centred_in_its_status_slot_and_none_off_home() {
        showChooser()

        val card = card("Bbb")
        val slot = DpRect(card.left + 6.dp, card.bottom - 20.dp, card.right - 6.dp, card.bottom - 6.dp)
        val check = compose.onNodeWithTag("card:check:${key("Bbb")}", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(18f, check.width.value, 0.5f)
        assertEquals(18f, check.height.value, 0.5f)
        assertEquals((card.left.value + card.right.value) / 2f, (check.left.value + check.right.value) / 2f, 1f)
        assertEquals((slot.top.value + slot.bottom.value) / 2f, (check.top.value + check.bottom.value) / 2f, 1f)
        compose.onNodeWithTag("card:check:${key("Bbb")}", useUnmergedTree = true).assertContentDescriptionEquals("On Home")
        compose.onNodeWithTag("card:check:${key("Ccc")}", useUnmergedTree = true).assertDoesNotExist()
    }

    /** The red channel of the pixel [across] and [down] (each 0 to 1) of the box tagged [tag] on the card of [name]. */
    private fun redInMark(
        tag: String,
        name: String,
        across: Float,
        down: Float,
    ): Int {
        val mark = compose.onNodeWithTag("$tag:${key(name)}", useUnmergedTree = true).getBoundsInRoot()
        return redAt(mark.left + mark.width * across, mark.top + mark.height * down)
    }

    @Test
    @Config(qualifiers = "+night")
    fun in_the_chooser_the_check_of_an_idle_card_is_a_white_085_disc_with_a_dark_check() {
        showChooser()

        val idleFill = redAt(card("Bbb").left + 4.dp, (card("Bbb").top + card("Bbb").bottom) / 2)
        val disc = redInMark("card:check", "Bbb", 0.5f, 0.1f)
        val stroke = redInMark("card:check", "Bbb", 0.58f, 0.51f)

        assertEquals("the disc", idleFill + 0.85f * (255 - idleFill), disc.toFloat(), 6f)
        assertTrue("the check is $stroke on a disc of $disc", stroke <= 0.35f * disc)
    }

    @Test
    @Config(qualifiers = "+night")
    fun in_the_chooser_the_check_of_the_focused_card_is_a_white_disc_with_a_dark_check() {
        showChooser()
        compose.onNodeWithTag("card:${key("Aaa")}").assertIsFocused()

        // The focused card grows about its centre, so the mark is read over its whole box, grown by 3 dp.
        val mark = compose.onNodeWithTag("card:check:${key("Aaa")}", useUnmergedTree = true).getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val reds =
            ((mark.left.value - 3f) * density).toInt().until(((mark.right.value + 3f) * density).toInt()).flatMap { x ->
                ((mark.top.value - 3f) * density).toInt().until(((mark.bottom.value + 3f) * density).toInt()).map { y ->
                    Math.round(pixels[x, y].red * 255)
                }
            }
        assertTrue("the disc: ${reds.max()}", reds.max() >= 235)
        assertTrue("the check: ${reds.min()}", reds.min() <= 40)
    }

    @Test
    @Config(qualifiers = "+night")
    fun in_the_chooser_an_app_off_home_shows_an_18_dp_ring_of_2_dp_white_070_filled_with_white_010_where_an_app_on_home_has_its_check() {
        showChooser()

        val ring = compose.onNodeWithTag("card:ring:${key("Ccc")}", useUnmergedTree = true).getBoundsInRoot()
        val idleFill = redAt(card("Ccc").left + 4.dp, (card("Ccc").top + card("Ccc").bottom) / 2)
        assertEquals(18f, ring.width.value, 0.5f)
        assertEquals(18f, ring.height.value, 0.5f)
        assertEquals("the stroke", idleFill + 0.70f * (255 - idleFill), redInMark("card:ring", "Ccc", 1f / 18f, 0.5f).toFloat(), 6f)
        assertEquals(
            "the stroke is 2 dp",
            idleFill + 0.70f * (255 - idleFill),
            redInMark("card:ring", "Ccc", 1.8f / 18f, 0.5f).toFloat(),
            8f,
        )
        assertEquals("the inside", idleFill + 0.10f * (255 - idleFill), redInMark("card:ring", "Ccc", 0.5f, 0.5f).toFloat(), 3f)
        compose.onNodeWithTag("card:ring:${key("Bbb")}", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+night")
    fun in_the_chooser_the_ring_of_an_app_off_home_is_clearly_lighter_than_the_check_disc_of_an_app_on_home() {
        showChooser()

        val ring = redInMark("card:ring", "Ccc", 1f / 18f, 0.5f)
        val disc = redInMark("card:check", "Bbb", 0.5f, 0.1f)
        assertTrue("the ring stroke is $ring against a disc of $disc", ring + 20 <= disc)
    }

    @Test
    @Config(qualifiers = "+night")
    fun in_the_chooser_no_white_lies_on_the_art_of_an_app_on_home_and_none_hangs_past_the_card() {
        showChooser()

        val card = card("Bbb")
        val art = art("Bbb")
        assertEquals("on the art", 0, whitePixels(art))
        val corner = DpRect(card.right - 12.dp, card.top - 12.dp, card.right + 12.dp, card.top + 12.dp)
        assertEquals("on the corner", 0, whitePixels(corner))
        val below = DpRect(card.left, card.bottom, card.right, card.bottom + 6.dp)
        assertEquals("under the card", 0, whitePixels(below))
    }

    @Test
    @Config(qualifiers = "+night")
    fun in_the_chooser_the_art_of_an_app_off_home_draws_at_half_alpha() {
        showChooser()

        val on = art("Bbb")
        val off = art("Ccc")
        val onRed = redAt((on.left + on.right) / 2, (on.top + on.bottom) / 2)
        val offRed = redAt((off.left + off.right) / 2, (off.top + off.bottom) / 2)
        assertEquals(255, onRed)
        assertEquals("half of the red art over the card", 142f, offRed.toFloat(), 14f)
    }

    @Test
    @Config(qualifiers = "+night")
    fun in_the_chooser_the_art_of_a_focused_app_off_home_draws_whole() {
        showChooser()
        press(Key.DirectionRight, times = 2)
        compose.onNodeWithTag("card:${key("Ccc")}").assertIsFocused()
        settle()

        val off = art("Ccc")
        assertEquals(255, redAt((off.left + off.right) / 2, (off.top + off.bottom) / 2))
    }

    @Test
    @Config(qualifiers = "+night")
    fun in_the_chooser_an_app_off_home_has_its_ring_and_no_status_text_focused_or_not() {
        showChooser()

        assertEquals(listOf("Ccc"), compose.textsOnCard("Ccc"))
        assertEquals(listOf("Bbb"), compose.textsOnCard("Bbb"))
        press(Key.DirectionRight, times = 2)
        compose.onNodeWithTag("card:${key("Ccc")}").assertIsFocused()

        assertEquals(listOf("Ccc"), compose.textsOnCard("Ccc"))
        compose.onNodeWithTag("card:ring:${key("Ccc")}", useUnmergedTree = true).assertExists()
    }

    /** The widest row, in dp, of pixels with red above 0.45 and green under 0.3 (the red art) or red above 0.45 alone ([white]) in [area]. */
    private fun span(
        area: DpRect,
        white: Boolean,
    ): Float {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (area.left.value * density).toInt() until (area.right.value * density).toInt()
        val ys = (area.top.value * density).toInt() until (area.bottom.value * density).toInt()
        val widest =
            ys.maxOf { y ->
                val lit = xs.filter { x -> pixels[x, y].red > 0.45f && (white || pixels[x, y].green < 0.3f) }
                if (lit.isEmpty()) 0 else lit.last() - lit.first() + 1
            }
        return widest / density
    }

    private fun around(
        tag: String,
        by: Dp,
    ): DpRect {
        val box = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
        return DpRect(box.left - by, box.top - by, box.right + by, box.bottom + by)
    }

    /** What OK does to Ccc in the chooser, frame by frame: the widest the new check gets, the widest and narrowest its art gets, and the check once settled. */
    private fun pulseFramesOfAddingCcc(config: TvConfig): Triple<Float, ClosedFloatingPointRange<Float>, Float> {
        showLibrary(
            listOf(libraryApp("Aaa", onHome = true), libraryApp("Bbb", onHome = true), libraryApp("Ccc"), libraryApp("Ddd")),
            art = LibraryArt(red, Color.Red),
            config = config,
        )
        press(Key.DirectionRight, times = 2)
        compose.onNodeWithTag("card:${key("Ccc")}").assertIsFocused()
        val checks = mutableListOf<Float>()
        val arts = mutableListOf<Float>()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        repeat(14) {
            compose.mainClock.advanceTimeByFrame()
            if (compose.onAllNodesWithTag("card:check:${key("Ccc")}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                checks += span(around("card:check:${key("Ccc")}", 4.dp), white = true)
                arts += span(art("Ccc"), white = false)
            }
        }
        compose.mainClock.autoAdvance = true
        settle()
        return Triple(checks.max(), arts.min()..arts.max(), span(around("card:check:${key("Ccc")}", 4.dp), white = true))
    }

    @Test
    @Config(qualifiers = "+night")
    fun in_the_chooser_the_check_of_a_card_whose_shortcut_changed_pulses_to_112_and_no_card_scales() {
        val (peak, art, settled) = pulseFramesOfAddingCcc(chooser)

        assertTrue("the check peaks at $peak dp, settled at $settled", peak >= 1.08f * settled)
        assertTrue("the art spans $art dp", art.endInclusive - art.start <= 1.5f)
    }

    @Test
    @Config(qualifiers = "+night")
    fun with_motion_reduced_the_check_of_a_card_whose_shortcut_changed_does_not_pulse() {
        val (peak, _, settled) = pulseFramesOfAddingCcc(chooser.copy(reducedMotion = true))

        assertTrue("the check peaks at $peak dp, settled at $settled", peak <= 1.02f * settled)
    }

    @Test
    @Config(qualifiers = "+night")
    fun with_animations_off_the_check_of_a_card_whose_shortcut_changed_does_not_pulse() {
        val (peak, _, settled) = pulseFramesOfAddingCcc(chooser.copy(animations = false))

        assertTrue("the check peaks at $peak dp, settled at $settled", peak <= 1.02f * settled)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_no_usage_status_is_one_line_under_the_order_chip_and_not_in_the_hint() {
        showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))

        press(Key.Menu)

        val count = bounds("library:count")
        val status = bounds("library:status")
        compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home", "Hold OK", "Options")
        compose.onNodeWithTag("library:status").assertTextEquals("No usage data yet")
        assertNear(count.right, status.right)
        assertTrue("status $status under count $count", status.top >= count.bottom)
        assertTrue("status $status beside the hint ${bounds("library:hint")}", status.left >= bounds("library:hint").left)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_hint_is_a_row_of_key_chips_as_tall_as_reorder_mode_draws_them() {
        showLibrary(nineApps)

        assertEquals(32f, bounds("library:hint").height.value, 1f)
    }

    @Test
    fun in_the_chooser_the_count_says_how_many_apps_are_on_home_and_follows_a_change() {
        showChooser()
        compose.onNodeWithTag("library:count").assertTextEquals("2 of 4 on Home")

        press(Key.DirectionCenter)

        compose.onNodeWithTag("library:count").assertTextEquals("1 of 4 on Home")
    }

    @Test
    fun in_browse_mode_the_count_stays_the_number_of_apps() {
        showLibrary(listOf(libraryApp("Aaa", onHome = true), libraryApp("Bbb"), libraryApp("Ccc")))

        compose.onNodeWithTag("library:count").assertTextEquals("3 apps")
    }

    /** The darkest red, 0 to 255, in [area]. */
    private fun darkestRed(area: DpRect): Int {
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val xs = (area.left.value * density).toInt() until (area.right.value * density).toInt()
        val ys = (area.top.value * density).toInt() until (area.bottom.value * density).toInt()
        return Math.round(ys.minOf { y -> xs.minOf { x -> pixels[x, y].red } } * 255)
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_focused_order_chip_is_filled_white_with_dark_text_and_a_dark_glyph() {
        showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))
        val chip = bounds("library:order")
        val middle = (chip.top + chip.bottom) / 2
        val glyph = compose.onNodeWithTag("library:order:glyph", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("idle fill ${rgbAt(chip.left + 3.dp, middle)}", rgbAt(chip.left + 3.dp, middle).all { it < 120 })

        press(Key.DirectionUp)

        compose.onNodeWithTag("library:order").assertIsFocused()
        assertTrue("focused fill ${rgbAt(chip.left + 3.dp, middle)}", rgbAt(chip.left + 3.dp, middle).all { it >= 230 })
        assertTrue("glyph is dark", darkestRed(glyph) < 90)
        assertTrue("text is dark", darkestRed(DpRect(glyph.right + 4.dp, chip.top + 6.dp, chip.right - 14.dp, chip.bottom - 6.dp)) < 90)
    }

    @Test
    fun the_art_of_a_one_line_card_starts_as_far_below_the_card_top_as_the_art_of_a_two_line_card() {
        showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta"), libraryApp(longNames[0])))

        val one = art("Beta").top - card("Beta").top
        val two = art(longNames[0]).top - card(longNames[0]).top
        assertTrue("one-line art $one below its card top, two-line art $two", abs(one.value - two.value) <= 1f)
    }

    @Test
    fun a_one_line_name_is_centred_between_the_art_and_the_status_line() {
        showLibrary(listOf(libraryApp("Aaa"), libraryApp("Beta"), libraryApp(longNames[0])))

        val above = nameTop("Beta") - art("Beta").bottom
        val below = statusTop("Beta") - nameBottom("Beta")
        assertTrue("$above under the art, $below above the status line", abs(above.value - below.value) <= 2f)
    }

    /** The layout top of the first line of the name on the card of [name], in the root's dp. */
    private fun nameTop(name: String): Dp =
        compose
            .onNode(
                hasText(name.substringBefore(' '), substring = true) and hasAnyAncestor(hasTestTag("card:${key(name)}")),
                useUnmergedTree = true,
            ).getBoundsInRoot()
            .top + (nameLayout(name).getLineTop(0) / compose.activity.resources.displayMetrics.density).dp

    /** The layout bounds of the status line of the card of [name]. */
    private fun status(name: String): DpRect =
        compose
            .onNode(hasText("Add to Home") and hasAnyAncestor(hasTestTag("card:${key(name)}")), useUnmergedTree = true)
            .getBoundsInRoot()

    /** The middle row, in pixels, of what is drawn like text in the card of [name] from [above] to [below] its status line. */
    private fun statusMiddle(
        name: String,
        above: Dp,
        below: Dp,
    ): Float {
        val card = card(name)
        val line = status(name)
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val left = ((card.left + 6.dp).value * density).toInt()
        val right = ((card.right - 6.dp).value * density).toInt()
        val top = ((line.top - above).value * density).toInt()
        val bottom = ((line.bottom + below).value * density).toInt()
        val ground = pixels[left, (top + bottom) / 2].red
        val rows = (top until bottom).filter { y -> (left until right).any { x -> (pixels[x, y].red - ground) * 255 > TEXT_OVER_GROUND } }
        assertTrue("no text in $card between $top and $bottom", rows.isNotEmpty())
        return (rows.first() + rows.last()) / 2f
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_status_line_of_the_focused_card_stays_on_the_line_of_its_neighbours() {
        showLibrary(nineApps)
        focusedCard("App 1")

        assertEquals(statusMiddle("App 2", 6.dp, 6.dp), statusMiddle("App 1", 6.dp, 6.dp), 1f)
    }

    /** The last index from [from] up to [to] whose [pixel] is as white as the ring; [from] when none is. */
    private fun lastWhite(
        from: Float,
        to: Float,
        pixel: (Int) -> Float,
    ): Float {
        var edge = from
        for (i in from.toInt() until to.toInt()) if (pixel(i) * 255 >= WHITE) edge = i.toFloat()
        return edge
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_ring_of_a_focused_card_stays_16_px_from_the_cards_beside_and_below_it() {
        showLibrary(nineApps)
        val focused = focusedCard("App 1")
        val beside = card("App 2")
        val below = card("App 7")
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()

        fun px(dp: Dp) = dp.value * density
        val midY = px((focused.top + focused.bottom) / 2).toInt()
        val midX = px((focused.left + focused.right) / 2).toInt()
        val across = px(beside.left) - lastWhite(px(focused.right), px(beside.left)) { pixels[it, midY].red }
        val down = px(below.top) - lastWhite(px(focused.bottom), px(below.top)) { pixels[midX, it].red }

        assertTrue("beside: $across px", across >= 16)
        assertTrue("below: $down px", down >= 16)
    }

    private enum class Side { Top, Bottom, Left, Right }

    /** How far the ring of [FOCUSED_SCALE] reaches from the card, in dp, with room to spare. */
    private val scanReach = 12.dp

    /**
     * Walks in from [scanReach] outside the middle of [side] of the card [name] towards it, and reads the ring as the run of
     * pixels that differ by at least [strong] from the same walk beside an idle card ("App 5"). Null while there is no such run.
     */
    private fun scanRing(
        pixels: PixelMap,
        name: String,
        side: Side,
        strong: Int = RING_STRONG,
    ): RingScan? {
        val density = compose.activity.resources.displayMetrics.density
        val card = card(name)
        val idle = card("App 5")

        fun px(dp: Dp) = (dp.value * density).toInt()
        val dx = px(idle.left) - px(card.left)
        val dy = px(idle.top) - px(card.top)
        val (x0, y0, stepX, stepY) =
            when (side) {
                Side.Top -> listOf(px((card.left + card.right) / 2), px(card.top) - px(scanReach), 0, 1)
                Side.Bottom -> listOf(px((card.left + card.right) / 2), px(card.bottom) + px(scanReach), 0, -1)
                Side.Left -> listOf(px(card.left) - px(scanReach), px((card.top + card.bottom) / 2), 1, 0)
                Side.Right -> listOf(px(card.right) + px(scanReach), px((card.top + card.bottom) / 2), -1, 0)
            }

        fun differs(step: Int): Int {
            val at = pixels[x0 + stepX * step, y0 + stepY * step]
            val ground = pixels[x0 + stepX * step + dx, y0 + stepY * step + dy]
            return Math.round((abs(at.red - ground.red) + abs(at.green - ground.green) + abs(at.blue - ground.blue)) * 255)
        }
        val steps = px(scanReach)
        val outer = (0 until steps).firstOrNull { differs(it) >= strong } ?: return null
        val inner = (outer until steps).firstOrNull { differs(it) < strong } ?: return null
        return RingScan(outer = outer, inner = inner, inside = differs(inner + 3))
    }

    /** Where the ring is along a walk in: [outer] and [inner] steps in, and how much the pixel [inside] it differs from the ground. */
    private data class RingScan(
        val outer: Int,
        val inner: Int,
        val inside: Int,
    )

    /** Moves focus from "App 1" to "App 2" frame by frame and fails on the first frame where a ring has no highlight inside it. */
    private fun assertEveryFrameOfAMoveHasTheHighlightInsideTheRing() {
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        var seen = 0
        repeat(FRAMES) { frame ->
            compose.mainClock.advanceTimeByFrame()
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            for (name in listOf("App 1", "App 2")) {
                for (side in listOf(Side.Top, Side.Bottom)) {
                    val ring = scanRing(pixels, name, side) ?: continue
                    seen++
                    assertTrue("frame $frame, $name, $side: $ring", ring.inside >= HALO_MIN)
                }
            }
        }
        assertTrue("rings seen on $seen scans", seen >= FRAMES)
    }

    @Test
    @Config(qualifiers = "+night")
    fun on_every_frame_of_a_focus_move_the_highlight_sits_inside_the_ring_of_the_card_that_gains_focus_and_the_one_that_loses_it() {
        showLibrary(nineApps)

        assertEveryFrameOfAMoveHasTheHighlightInsideTheRing()
    }

    @Test
    @Config(qualifiers = "+night")
    fun with_reduced_motion_the_highlight_is_inside_the_ring_on_every_frame_too() {
        showLibrary(
            nineApps,
            config = TvConfig(appLibrary = true, reducedMotion = true),
            motion = Motion(reducedMotion = true),
        )

        assertEveryFrameOfAMoveHasTheHighlightInsideTheRing()
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_ring_of_a_focused_card_is_as_far_from_it_on_all_four_sides() {
        showLibrary(nineApps)
        val grown = grownCard("App 1")
        val density = compose.activity.resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()

        // The walk starts 12 dp outside the card's own edge. Walking down or right, the pixel it stops at lies one further out.
        fun gap(
            side: Side,
            grownBy: Dp,
        ): Float {
            val ring = scanRing(pixels, "App 1", side, strong = RING_CORE)!!
            val farSide = if (side == Side.Bottom || side == Side.Right) 1 else 0
            return (scanReach.value - grownBy.value) * density - ring.inner + farSide
        }

        val card = card("App 1")
        val gaps =
            listOf(
                Side.Top to gap(Side.Top, card.top - grown.top),
                Side.Bottom to gap(Side.Bottom, grown.bottom - card.bottom),
                Side.Left to gap(Side.Left, card.left - grown.left),
                Side.Right to gap(Side.Right, grown.right - card.right),
            )
        val wanted = 3f * FOCUSED_SCALE * density
        assertTrue("gaps in px $gaps, wanted $wanted", gaps.all { (_, gap) -> abs(gap - wanted) <= 1.5f })
        assertTrue("gaps in px $gaps", gaps.maxOf { it.second } - gaps.minOf { it.second } <= 1f)
    }

    private fun ringAtTop(name: String): List<Int> {
        val grown = grownCard(name)
        return rgbAt((grown.left + grown.right) / 2, grown.top - 4.5.dp * FOCUSED_SCALE)
    }

    private fun assertRing(
        expected: List<Int>,
        actual: List<Int>,
    ) = assertTrue("expected $expected, got $actual", expected.zip(actual).all { (want, got) -> abs(want - got) <= 4 })

    @Test
    @Config(qualifiers = "+night")
    fun the_ring_of_a_card_whose_art_is_coloured_takes_that_colour_lifted_to_a_lightness_of_065() {
        showLibrary(nineApps, art = LibraryArt(white, Color(0xFFE53935)))

        assertRing(listOf(235, 100, 97), ringAtTop("App 1"))
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_ring_that_blends_with_its_cards_colour_gets_a_white_stroke_between_the_gap_and_the_ring() {
        showLibrary(nineApps, art = LibraryArt(white, Color(0xFFE53935)))

        val grown = grownCard("App 1")
        val gap = rgbAt((grown.left + grown.right) / 2, grown.top - 2.dp * FOCUSED_SCALE)
        assertTrue("the gap is $gap", gap.all { it >= 216 })
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_ring_of_a_card_whose_art_is_near_grey_is_white() {
        showLibrary(nineApps, art = LibraryArt(white, Color(0xFF808080)))

        assertRing(listOf(255, 255, 255), ringAtTop("App 1"))
    }

    @Test
    @Config(qualifiers = "+night")
    fun without_app_glow_the_ring_is_white_whatever_the_art() {
        showLibrary(nineApps, art = LibraryArt(white, Color(0xFFE53935)), config = TvConfig(appLibrary = true, appGlow = false))

        assertRing(listOf(255, 255, 255), ringAtTop("App 1"))
    }

    @Test
    @Config(qualifiers = "+night")
    fun with_cinematic_white_focus_the_ring_is_white_whatever_the_art() {
        showLibrary(nineApps, art = LibraryArt(white, Color(0xFFE53935)), config = TvConfig(appLibrary = true, cinematicWhiteFocus = true))

        assertRing(listOf(255, 255, 255), ringAtTop("App 1"))
    }

    @Test
    fun a_name_cut_to_two_lines_ends_in_an_ellipsis_with_no_space_before_it() {
        val labels = (6..14).map { words -> NAME_WORDS.take(words).joinToString(" ") }
        showLibrary(labels.map { libraryApp(it) })

        val shown =
            labels.map { label ->
                compose
                    .onNode(
                        hasText("Control", substring = true) and hasAnyAncestor(hasTestTag("card:${key(label)}")),
                        useUnmergedTree = true,
                    ).fetchSemanticsNode()
                    .config[SemanticsProperties.Text]
                    .joinToString("") { it.text }
            }

        val cut = shown.filter { it.endsWith(ELLIPSIS) }
        assertTrue("$shown", cut.size >= 3)
        assertTrue("$cut", cut.none { it.endsWith(" " + ELLIPSIS) })
    }

    /** The laid-out name on the card of [name]: the text of its first word is how the card is found. */
    private fun nameLayout(name: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose
            .onNode(
                hasText(name.substringBefore(' ').take(8), substring = true) and hasAnyAncestor(hasTestTag("card:${key(name)}")),
                useUnmergedTree = true,
            ).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action!!(results)
        return results.single()
    }

    private fun linesEndingInsideAWord(layout: TextLayoutResult): List<Int> {
        val text = layout.layoutInput.text.text
        return (0 until layout.lineCount - 1).filter { line ->
            val end = layout.getLineEnd(line)
            text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
        }
    }

    private fun TextLayoutResult.lineWidth(line: Int) = getLineRight(line) - getLineLeft(line)

    /** [layout]'s text laid out at its style's own size in the same box, as without autoSize. */
    private fun unshrunk(layout: TextLayoutResult): TextLayoutResult {
        val input = layout.layoutInput
        return TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
            .measure(input.text, input.style, input.overflow, input.softWrap, input.maxLines, constraints = input.constraints)
    }

    @Test
    fun a_name_with_a_long_word_shrinks_until_no_line_ends_inside_a_word() {
        val name = "Temporizadores y reloj"
        showLibrary(listOf(libraryApp(name)))

        val layout = nameLayout(name)
        assertEquals(name, layout.layoutInput.text.text)
        assertTrue("the name must break inside a word without autoSize", linesEndingInsideAWord(unshrunk(layout)).isNotEmpty())
        assertEquals(emptyList<Int>(), linesEndingInsideAWord(layout))
        assertTrue("${layout.lineCount} lines", layout.lineCount <= 2 && !layout.isLineEllipsized(layout.lineCount - 1))
    }

    @Test
    fun a_name_that_fits_keeps_the_full_size() {
        showLibrary(listOf(libraryApp("Plex")))

        val layout = nameLayout("Plex")
        assertEquals(unshrunk(layout).lineWidth(0), layout.lineWidth(0), WIDTH_ROUNDING)
    }

    @Test
    fun a_word_too_long_for_the_smallest_size_ends_in_an_ellipsis_within_two_lines_at_the_full_size() {
        val name = "Supercalifragilisticexpialidocious Antidisestablishmentarianism Pneumonoultramicroscopic"
        showLibrary(listOf(libraryApp(name)))

        val layout = nameLayout(name)
        assertTrue("${layout.lineCount} lines", layout.lineCount <= 2)
        assertTrue(
            layout.layoutInput.text.text
                .endsWith(ELLIPSIS),
        )
        assertEquals(unshrunk(layout).lineWidth(layout.lineCount - 1), layout.lineWidth(layout.lineCount - 1), WIDTH_ROUNDING)
    }

    private fun hintPart(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("library:hint")), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.textStyle(): TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single().layoutInput.style
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_hints_have_no_pill_round_them_and_each_key_keeps_its_fill() {
        showLibrary(nineApps)
        val hint = bounds("library:hint")
        val key = hintPart("OK").getBoundsInRoot()
        val middle = (hint.top + hint.bottom) / 2

        // Where a pill's backing would be: over the key's fill, in the row's top 2 dp.
        val ground = redAt(hint.right - 20.dp, hint.top + 2.dp)
        assertTrue(
            "over the key: ${redAt((key.left + key.right) / 2, hint.top + 2.dp)}, panel $ground",
            abs(redAt((key.left + key.right) / 2, hint.top + 2.dp) - ground) <= 6,
        )
        assertTrue("key fill ${redAt(key.left - 4.dp, middle)}", redAt(key.left - 4.dp, middle) - ground >= 20)
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_hints_action_is_plain_text_at_70_percent_of_the_text_colour_and_16_dp_lies_between_hints() {
        showLibrary(nineApps)

        val style = hintPart("Add to Home").textStyle()
        val gap = hintPart("Hold OK").getBoundsInRoot().left - 8.dp - hintPart("Add to Home").getBoundsInRoot().right

        assertEquals(0.70f, style.color.alpha, 0.005f)
        assertEquals(16f, gap.value, 1f)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_order_chip_has_a_1_dp_rim_of_24_percent_white_at_rest() {
        showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))
        val chip = bounds("library:order")
        val x = (chip.left + chip.right) / 2

        val inside = redAt(x, chip.top + 3.dp)
        val rim = maxOf(redAt(x, chip.top + 0.25.dp), redAt(x, chip.top + 0.75.dp))

        assertEquals(Math.round(inside + 0.24f * (255 - inside)).toFloat(), rim.toFloat(), 3f)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_order_chip_ends_with_the_menu_key_cap_after_its_label_and_4_dp_clear_of_its_top_and_bottom() {
        showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))
        val chip = bounds("library:order")
        val cap = compose.onNodeWithTag("library:order:key", useUnmergedTree = true).getBoundsInRoot()
        val label =
            compose
                .onNode(hasText("Usual order") and hasAnyAncestor(hasTestTag("library:order")), useUnmergedTree = true)
                .getBoundsInRoot()

        assertTrue("chip ${chip.width}, cap $cap, label $label", cap.left >= label.right && cap.right <= chip.right)
        assertTrue("cap $cap in chip $chip", cap.top - chip.top >= 4.dp && chip.bottom - cap.bottom >= 4.dp)
    }

    @Test
    @Config(qualifiers = "+night")
    fun a_focused_order_chip_draws_its_menu_key_cap_text_dark() {
        showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))
        val cap = compose.onNodeWithTag("library:order:key", useUnmergedTree = true).getBoundsInRoot()

        press(Key.DirectionUp)

        compose.onNodeWithTag("library:order").assertIsFocused()
        assertTrue("cap text is dark", darkestRed(cap) < 90)
    }

    @Test
    @Config(qualifiers = "+night")
    fun the_no_usage_status_is_set_in_the_hints_size_at_70_percent_of_the_text_colour() {
        showLibrary(nineApps, config = TvConfig(appLibrary = true, libraryLeastUsed = true))
        press(Key.Menu)

        val status = compose.onNodeWithTag("library:status").textStyle()

        assertEquals(hintPart("Add to Home").textStyle().fontSize, status.fontSize)
        assertEquals(0.70f, status.color.alpha, 0.005f)
    }

    private companion object {
        const val PANEL_X = 960
        const val PANEL_HEADER_Y = 103
        const val MAX_BLEED = 10
        const val WHITE = 230
        const val FRAMES = 24
        const val RING_STRONG = 200
        const val RING_CORE = 300
        const val HALO_MIN = 4
        const val FOCUSED_SCALE = 1.08f
        const val WIDTH_ROUNDING = 1.5f
        const val TEXT_OVER_GROUND = 60
        val ELLIPSIS = Char(0x2026).toString()
        val NAME_WORDS = "Control de TV con altavoces inteligentes para el salon principal de la casa de Maria Luisa".split(" ")
    }
}
