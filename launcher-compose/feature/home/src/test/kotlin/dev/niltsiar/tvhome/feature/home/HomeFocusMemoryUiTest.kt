package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A Home created again, on the real view model, puts focus back on the tile it remembers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeFocusMemoryUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showHome(
        config: TvConfig,
        remembered: String?,
        slot: Int,
    ) {
        val memory =
            FocusMemory().also {
                it.key = remembered
                it.slot = slot
            }
        compose.setContent {
            TvTheme {
                val viewModel = rememberHomeViewModel(sources(focusMemory = memory), config, mutableListOf())
                val state = viewModel.uiState.collectAsState()
                HomeScreen(
                    state = { state.value },
                    layout = config.homeLayout(),
                    onFocus = viewModel::onFocus,
                    onClick = {},
                    topBar = config.topBarLayout(),
                    actions = viewModel.actions,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun in_a_single_row_a_tile_beyond_the_first_screen_gets_focus_and_is_scrolled_into_view() {
        showHome(SixColumns.copy(singleRow = true), remembered = "pkg.9", slot = 8)

        compose.onNodeWithTag("tile:pkg.9").assertIsFocused().assertIsDisplayed()
    }

    @Test
    fun a_tile_in_the_grid_gets_focus() {
        showHome(SixColumns, remembered = "pkg.8", slot = 7)

        compose.onNodeWithTag("tile:pkg.8").assertIsFocused()
    }

    @Test
    fun with_nothing_remembered_the_first_tile_gets_focus() {
        showHome(SixColumns, remembered = null, slot = 0)

        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }
}
