package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeRefreshFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private var apps by mutableStateOf(NineApps)
    private var focused by mutableStateOf<String?>(null)

    private fun showHome(config: TvConfig) {
        compose.setContent {
            TvTheme {
                HomeScreen(
                    state = { homeUiState(homeApps(apps, config = config), Evening, focused, Locale.ENGLISH) },
                    layout = config.homeLayout(),
                    onFocus = { focused = it },
                    onClick = {},
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
    fun focus_stays_on_a_tile_when_the_focused_dock_app_is_removed() {
        showHome(SixColumns)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()

        apps = apps.filterNot { it.key == "pkg.3" }
        compose.waitForIdle()

        compose.onNodeWithTag("tile:pkg.3").assertDoesNotExist()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    @Test
    fun focus_stays_on_a_tile_when_the_focused_grid_app_is_removed() {
        showHome(SixColumns)
        press(Key.DirectionDown)
        compose.onNodeWithTag("tile:pkg.7").assertIsFocused()

        apps = apps.filterNot { it.key == "pkg.7" }
        compose.waitForIdle()

        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    @Test
    fun four_columns_make_a_four_tile_dock_and_a_four_wide_grid() {
        showHome(TvConfig(columns = 4))
        repeat(3) { press(Key.DirectionRight) }
        compose.onNodeWithTag("tile:pkg.4").assertIsFocused()

        press(Key.DirectionDown)

        compose.onNodeWithTag("tile:pkg.8").assertIsFocused()
    }

    @Test
    fun left_and_right_stop_at_the_ends_of_the_dock() {
        showHome(TvConfig(columns = 4))

        repeat(5) { press(Key.DirectionRight) }
        compose.onNodeWithTag("tile:pkg.4").assertIsFocused()

        repeat(5) { press(Key.DirectionLeft) }
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    @Test
    fun a_single_row_walks_through_every_app_sideways() {
        showHome(TvConfig(columns = 4, singleRow = true))
        repeat(8) { press(Key.DirectionRight) }

        compose.onNodeWithTag("tile:pkg.9").assertIsFocused()
    }
}
