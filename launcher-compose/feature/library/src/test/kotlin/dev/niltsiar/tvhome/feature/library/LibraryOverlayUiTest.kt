package dev.niltsiar.tvhome.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import app.cash.molecule.RecompositionMode
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.TvTheme
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
            TvTheme(glassKind = GlassKind.Flat) {
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
        compose.onNodeWithTag("card:${key("Gamma")}").assertIsFocused()

        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()

        compose.onNodeWithTag("menu").assertDoesNotExist()
        assertEquals(listOf("pkg.Gamma" to false), library.saved)
    }
}
