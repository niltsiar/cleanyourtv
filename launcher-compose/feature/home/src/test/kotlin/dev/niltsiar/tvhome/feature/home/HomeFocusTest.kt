package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class HomeFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun showHome() {
        val state = homeUiState(HomeApps(NineApps), Evening, null, Locale.ENGLISH)
        compose.setContent {
            TvTheme(glassKind = GlassKind.Flat) {
                HomeScreen(state = { state }, onFocus = {}, onClick = {})
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
    fun down_from_the_top_bar_moves_focus_back_to_the_dock() {
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Inputs").assertIsFocused()

        press(Key.DirectionDown)

        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("dock"))).assertIsFocused()
    }

    @Test
    fun down_from_the_top_bar_returns_to_the_dock_tile_that_had_focus() {
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionDown)

        compose.onNodeWithTag("tile:pkg.3").assertIsFocused()
    }
}
