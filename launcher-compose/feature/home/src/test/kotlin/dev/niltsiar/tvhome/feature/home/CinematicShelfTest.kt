package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class CinematicShelfTest {
    @get:Rule
    val compose = createComposeRule()

    private var focusedKey by mutableStateOf<String?>(null)
    private val artAsked = mutableListOf<String>()

    @Before
    fun showHome() {
        compose.setContent {
            TvTheme {
                HomeScreen(
                    state = { homeUiState(homeApps(), Evening, focusedKey, Locale.ENGLISH, cinematic = true) },
                    layout = SixColumns.homeLayout(),
                    onFocus = { focusedKey = it },
                    onClick = {},
                    shelfLayout = ShelfLayout(cinematic = true, scrimAlpha = 0f, animated = false),
                    heroArt = {
                        artAsked += it
                        null
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun a_focused_dock_tile_puts_its_name_on_the_cinematic_shelf_and_leaves_the_plain_title_empty() {
        compose.onNodeWithTag("shelf:name").assertTextEquals("App 1")
        compose.onNodeWithTag("shelf:title").assertTextEquals("")

        press(Key.DirectionRight)

        compose.onNodeWithTag("shelf:name").assertTextEquals("App 2")
    }

    @Test
    fun a_grid_tile_goes_back_to_the_plain_shelf_title() {
        press(Key.DirectionDown)

        compose.onNodeWithTag("shelf:title").assertTextEquals("App 7")
    }

    @Test
    fun each_focused_dock_app_asks_for_its_art_once_per_focus() {
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        assertEquals(listOf("pkg.1", "pkg.2", "pkg.3"), artAsked)
    }
}
