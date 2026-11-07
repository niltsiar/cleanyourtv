package dev.niltsiar.tvhome.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import app.cash.molecule.RecompositionMode
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.OpenedRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

/** The library on the real presenter: keys go in at the panel, the choices come out of the fake host. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class LibraryOverlayUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: MoleculeLibraryViewModel
    private val effects = mutableListOf<LibraryEffect>()
    private val library =
        FakeLibrary(
            listOf(
                libraryApp("Alpha"),
                libraryApp("Beta"),
                libraryApp("Gamma", onHome = true),
                libraryApp("Delta", installedAt = NOW - DAY),
            ),
        )

    private fun showLibrary(config: TvConfig = TvConfig(appLibrary = true)) {
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
                LaunchedEffect(viewModel) { viewModel.effects.collect { effects += it } }
                val state = viewModel.uiState.collectAsState()
                LibraryOverlay(
                    state = { state.value },
                    layout = config.libraryLayout(),
                    actions = viewModel.actions,
                    art = { null },
                )
            }
        }
        viewModel.onOpen()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun the_panel_lists_the_apps_missing_from_home_first_and_focuses_the_first_card() {
        showLibrary()

        compose.onNodeWithTag("library").assertExists()
        compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("card:${key("Alpha")}").assertIsFocused()
        compose.onNodeWithTag("card:${key("Gamma")}").assertExists()
    }

    @Test
    fun keys_cannot_leave_the_panel_for_the_home_behind_it() {
        showLibrary()

        repeat(3) { press(Key.DirectionLeft) }
        press(Key.DirectionUp)
        press(Key.DirectionDown)

        compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()
    }

    @Test
    fun ok_on_a_missing_app_adds_it_and_focus_moves_to_the_next_missing_app() {
        showLibrary()
        press(Key.DirectionRight)

        press(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals(listOf("pkg.Alpha" to true), library.saved)
        compose.onNodeWithTag("card:${key("Beta")}").assertIsFocused()
    }

    @Test
    fun a_held_ok_opens_the_menu_and_back_closes_the_menu_then_the_library() {
        showLibrary()

        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        compose.onNodeWithTag("menu").assertExists()
        compose.onNodeWithTag("menu:Open").assertIsFocused()
        assertEquals(emptyList<LibraryEffect>(), effects)

        back()
        compose.onNodeWithTag("menu").assertDoesNotExist()
        compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()

        back()
        compose.onNodeWithTag("library").assertDoesNotExist()
    }

    @Test
    fun a_held_ok_opens_the_menu_16_dp_beside_its_card_and_inside_the_screen() {
        showLibrary()
        compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()
        val grown = compose.onNodeWithTag("card:${key("Delta")}").getBoundsInRoot()
        // Its bounds include its focus growth of 8%; the menu goes beside its slot in the row.
        val growth = grown.width * (1f - 1f / 1.08f) / 2f
        val card = DpRect(grown.left + growth, grown.top, grown.right - growth, grown.bottom)

        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        val menu = compose.onNodeWithTag("menu").getBoundsInRoot()
        val screen = compose.onRoot().getBoundsInRoot()
        assertTrue("menu $menu, card $card", menu.left >= card.right + 16.dp - 1.dp || menu.right <= card.left - 16.dp + 1.dp)
        assertTrue("menu $menu, screen $screen", menu.left >= screen.left && menu.right <= screen.right)
        assertTrue("menu $menu, screen $screen", menu.top >= screen.top && menu.bottom <= screen.bottom)
    }

    @Test
    fun an_empty_catalogue_shows_its_text_and_back_closes_it() {
        library.apps = emptyList()
        showLibrary()

        compose.onNodeWithTag("library:empty").assertExists()

        back()

        compose.onNodeWithTag("library").assertDoesNotExist()
    }

    @Test
    fun in_selection_mode_a_held_ok_opens_no_menu_and_ok_changes_the_shortcut() {
        showLibrary(TvConfig(appLibrary = true, appLibrarySelection = true))
        compose.onNodeWithTag("card:${key("Alpha")}").assertIsFocused()

        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()

        compose.onNodeWithTag("menu").assertDoesNotExist()
        assertEquals(listOf("pkg.Alpha" to true), library.saved)
    }

    private val leastUsed = TvConfig(appLibrary = true, libraryLeastUsed = true)

    private fun gammaOpenedThreeDaysAgo() {
        library.record = OpenedRecord(times = mapOf(key("Gamma") to NOW - 3 * DAY), since = NOW - 90 * DAY)
    }

    @Test
    fun the_menu_key_switches_the_order_the_header_and_hint_name_it_and_focus_stays_on_the_same_app() {
        gammaOpenedThreeDaysAgo()
        showLibrary(leastUsed)
        compose.onNodeWithTag("library:order").assertTextEquals("Usual order")
        compose.onNodeWithTag("library:hint").assertTextContains("Least used", substring = true)
        compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()

        press(Key.Menu)

        compose.onNodeWithTag("library:order").assertTextEquals("Least used")
        compose.onNodeWithTag("library:hint").assertTextContains("Usual order", substring = true)
        compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("card:${key("Gamma")}").assertIsFocused()

        press(Key.Menu)

        compose.onNodeWithTag("library:order").assertTextEquals("Usual order")
        compose.onNodeWithTag("card:${key("Gamma")}").assertIsFocused()
    }

    @Test
    fun with_the_key_off_the_menu_key_changes_nothing_and_the_header_names_no_order() {
        showLibrary()

        press(Key.Menu)

        compose.onNodeWithTag("library:order").assertDoesNotExist()
        compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home", "Hold OK", "Options")
        compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("card:${key("Alpha")}").assertIsFocused()
        compose.onNodeWithTag("card:opened:${key("Alpha")}", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun in_the_least_used_order_a_card_keeps_its_status_and_adds_when_it_was_last_opened() {
        gammaOpenedThreeDaysAgo()
        showLibrary(leastUsed)
        compose.onNodeWithTag("card:opened:${key("Gamma")}", useUnmergedTree = true).assertDoesNotExist()

        press(Key.Menu)

        compose.onNodeWithTag("card:opened:${key("Gamma")}", useUnmergedTree = true).assertTextContains("3 days ago, ", substring = true)
        compose.onNodeWithTag("card:opened:${key("Alpha")}", useUnmergedTree = true).assertTextEquals("Never opened")
    }

    @Test
    fun in_browse_mode_the_hint_is_the_chips_of_ok_and_a_held_ok_and_an_app_that_opens_says_open() {
        showLibrary(TvConfig(appLibrary = true, libraryOkOpens = true))

        compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Open", "Hold OK", "Options")
    }

    @Test
    fun in_the_chooser_the_hint_says_ok_removes_an_app_on_home_and_ok_adds_one_off_home() {
        showLibrary(TvConfig(appLibrary = true, appLibrarySelection = true))
        compose.onNodeWithTag("card:${key("Alpha")}").assertIsFocused()
        compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home")

        repeat(3) { press(Key.DirectionRight) }

        compose.onNodeWithTag("card:${key("Gamma")}").assertIsFocused()
        compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Remove from Home")
    }

    @Test
    fun the_status_line_names_only_what_the_card_does_not_already_say() {
        showLibrary()

        assertEquals(listOf("Alpha", "Add to Home"), compose.textsOnCard("Alpha"))
        assertEquals(listOf("Delta", "New · Add to Home"), compose.textsOnCard("Delta"))
        assertEquals(listOf("Gamma"), compose.textsOnCard("Gamma"))
    }

    @Test
    fun with_library_ok_opens_a_card_off_home_has_no_status_text_either() {
        showLibrary(TvConfig(appLibrary = true, libraryOkOpens = true))

        assertEquals(listOf("Alpha"), compose.textsOnCard("Alpha"))
        assertEquals(listOf("Delta"), compose.textsOnCard("Delta"))
    }

    @Test
    fun in_the_least_used_order_a_card_shows_the_opened_line_alone_whatever_its_home_state() {
        gammaOpenedThreeDaysAgo()
        showLibrary(leastUsed)

        press(Key.Menu)

        assertEquals(listOf("Alpha", "Never opened"), compose.textsOnCard("Alpha"))
        assertEquals(listOf("Delta", "Never opened"), compose.textsOnCard("Delta"))
        assertEquals(2, compose.textsOnCard("Gamma").size)
        assertTrue(compose.textsOnCard("Gamma")[1].startsWith("3 days ago, "))
    }

    @Test
    fun with_no_open_time_for_any_app_the_least_used_order_shows_no_opened_text_and_a_status_says_so() {
        showLibrary(leastUsed)
        compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home", "Hold OK", "Options", "Menu", "Least used")

        press(Key.Menu)

        compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home", "Hold OK", "Options", "Menu", "Usual order")
        compose.onNodeWithTag("library:status").assertTextEquals("No usage data yet")
        compose.onNodeWithTag("card:opened:${key("Alpha")}", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(listOf("Alpha", "Add to Home"), compose.textsOnCard("Alpha"))
    }

    @Test
    fun with_an_open_time_the_library_does_not_say_there_is_no_usage_data() {
        gammaOpenedThreeDaysAgo()
        showLibrary(leastUsed)

        press(Key.Menu)

        compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Add to Home", "Hold OK", "Options", "Menu", "Usual order")
        compose.onNodeWithTag("library:status").assertDoesNotExist()
    }

    @Test
    fun in_selection_mode_the_panel_is_titled_choose_home_apps() {
        showLibrary(TvConfig(appLibrary = true, appLibrarySelection = true))
        compose.onNodeWithText("Choose Home apps").assertExists()
        compose.onNodeWithText("App library").assertDoesNotExist()
    }

    @Test
    fun in_browse_mode_the_panel_is_titled_app_library() {
        showLibrary()
        compose.onNodeWithText("App library").assertExists()
        compose.onNodeWithText("Choose Home apps").assertDoesNotExist()
    }

    @Test
    fun while_the_order_chip_has_focus_the_hint_says_what_ok_does_on_it() {
        gammaOpenedThreeDaysAgo()
        showLibrary(leastUsed)

        press(Key.DirectionUp)
        orderChip().assertIsFocused()
        compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Least used")

        press(Key.DirectionCenter)
        compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Usual order")

        press(Key.DirectionDown)
        compose.onNodeWithTag("library:hint").assertTextContains("Menu", substring = true)
    }

    @Test
    fun the_order_chip_shows_a_sort_glyph_14_dp_before_its_text() {
        showLibrary(leastUsed)

        val chip = orderChip().getBoundsInRoot()
        val glyph = compose.onNodeWithTag("library:order:glyph", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(14f, glyph.width.value, 1f)
        assertEquals(14f, glyph.height.value, 1f)
        assertTrue("glyph $glyph inside chip $chip", glyph.left >= chip.left && glyph.right <= chip.right)
        assertTrue("glyph ends ${glyph.right}, text starts ${textLeft()}", glyph.right <= textLeft())
    }

    private fun textLeft() =
        compose
            .onNode(hasText("Usual order") and hasAnyAncestor(hasTestTag("library:order")), useUnmergedTree = true)
            .getBoundsInRoot()
            .left

    private fun orderChip() = compose.onNodeWithTag("library:order")

    @Test
    fun up_from_the_first_row_focuses_the_order_chip_and_down_returns_to_the_card_that_had_focus() {
        showLibrary(leastUsed)
        press(Key.DirectionRight)
        compose.onNodeWithTag("card:${key("Alpha")}").assertIsFocused()

        press(Key.DirectionUp)

        orderChip().assertIsFocused()
        listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp).forEach { press(it) }
        orderChip().assertIsFocused()

        press(Key.DirectionDown)

        compose.onNodeWithTag("card:${key("Alpha")}").assertIsFocused()
    }

    @Test
    fun ok_on_the_order_chip_switches_the_order_and_focus_stays_on_the_chip() {
        gammaOpenedThreeDaysAgo()
        showLibrary(leastUsed)
        press(Key.DirectionUp)
        orderChip().assertIsFocused()
        orderChip().assertTextEquals("Usual order")

        press(Key.DirectionCenter)

        orderChip().assertIsFocused()
        orderChip().assertTextEquals("Least used")
        compose.onNodeWithTag("library:hint").assertTextEquals("OK", "Usual order")

        press(Key.DirectionDown)

        compose.onNodeWithTag("card:${key("Delta")}").assertIsFocused()
    }
}
