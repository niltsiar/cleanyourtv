package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The App library pill and the top-bar button: how focus reaches them and what OK does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeLibraryUiTest {
    @get:Rule
    val compose = createComposeRule()

    private var opened = 0
    private var focusedKey by mutableStateOf<String?>(null)
    private val toggles = mutableListOf<String>()

    private fun showHome(
        config: TvConfig = SixColumns.copy(appLibrary = true),
        reordering: Boolean = false,
    ) {
        val apps = homeApps(config = config)
        val reorder = if (reordering) ReorderView(carried = null, dimmed = emptySet(), hint = ReorderHint.Reorder) else null
        compose.setContent {
            TvTheme(glassKind = GlassKind.Flat) {
                HomeScreen(
                    state = { homeUiState(apps, Evening, focusedKey, Locale.ENGLISH, reorder = reorder) },
                    layout = config.homeLayout(),
                    onFocus = { focusedKey = it },
                    onClick = {},
                    topBar = config.topBarLayout(),
                    actions = HomeActions(onToggleReorder = { toggles += "toggle" }),
                    onOpenLibrary = { opened++ },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun right_past_the_last_tile_focuses_the_pill_and_ok_opens_the_library() {
        showHome()
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("library:pill").assertIsFocused()

        press(Key.DirectionCenter)
        assertEquals(1, opened)
    }

    @Test
    fun down_from_the_last_row_focuses_the_pill_and_up_returns_to_the_tile() {
        showHome()
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.8").assertIsFocused()

        press(Key.DirectionDown)
        compose.onNodeWithTag("library:pill").assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        compose.onNodeWithTag("library:pill").assertIsFocused()

        press(Key.DirectionUp)
        compose.onNodeWithTag("tile:pkg.8").assertIsFocused()
    }

    @Test
    fun down_from_a_dock_tile_over_the_short_last_row_goes_to_that_row_not_to_the_pill() {
        showHome()
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.5").assertIsFocused()

        press(Key.DirectionDown)

        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
    }

    @Test
    fun without_the_app_library_there_is_no_pill_and_down_from_the_last_row_stays() {
        showHome(SixColumns)
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        compose.onNodeWithTag("library:pill").assertDoesNotExist()
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()
    }

    @Test
    fun a_top_bar_button_with_library_button_opens_the_library() {
        showHome(SixColumns.copy(appLibrary = true, libraryButton = true, showLibraryPill = false))
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("App library").assertIsFocused()

        press(Key.DirectionCenter)

        assertEquals(1, opened)
        compose.onNodeWithTag("library:pill").assertDoesNotExist()
    }

    @Test
    fun ok_on_the_pill_in_reorder_mode_ends_the_mode_and_opens_the_library() {
        showHome(reordering = true)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("library:pill").assertIsFocused()

        press(Key.DirectionCenter)

        assertEquals(listOf("toggle"), toggles)
        assertEquals(1, opened)
    }
}
