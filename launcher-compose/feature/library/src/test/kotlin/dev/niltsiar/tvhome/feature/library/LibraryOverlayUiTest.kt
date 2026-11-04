package dev.niltsiar.tvhome.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The library on the real presenter: keys go in at the panel, the choices come out of the fake host. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class LibraryOverlayUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val events = Channel<LibraryEvent>(Channel.UNLIMITED)
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
                val glass = rememberGlassState()
                val state =
                    rememberUpdatedState(
                        libraryPresenter(events.receiveAsFlow(), library.sources, config, { effects += it }, Locale.ENGLISH),
                    )
                LibraryOverlay(
                    state = { state.value },
                    layout = config.libraryLayout(),
                    actions =
                        LibraryActions(
                            onFocus = { events.trySend(LibraryEvent.Focus(it)) },
                            onClick = { events.trySend(LibraryEvent.Click(it)) },
                            onHold = { events.trySend(LibraryEvent.Hold(it)) },
                            onPick = { events.trySend(LibraryEvent.Pick(it)) },
                            onBack = { events.trySend(LibraryEvent.Back) },
                        ),
                    glass = glass,
                    art = { null },
                )
            }
        }
        events.trySend(LibraryEvent.Open)
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
