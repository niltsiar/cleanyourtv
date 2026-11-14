package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.Wallpaper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class WallpaperCaptionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(wallpaper: Wallpaper) {
        val state = homeUiState(homeApps(), Evening, null, Locale.ENGLISH, wallpaper = wallpaper)
        compose.setContent {
            TvTheme {
                HomeScreen(state = { state }, layout = SixColumns.homeLayout(), onFocus = {}, onClick = {})
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun the_pictures_name_shows_on_home() {
        show(Wallpaper(caption = "Sunrise and aurora over northern Europe"))
        compose.onNodeWithText("Sunrise and aurora over northern Europe").assertIsDisplayed()
    }

    @Test
    fun a_picture_without_a_name_shows_none() {
        show(Wallpaper())
        compose.onNodeWithText("Sunrise and aurora over northern Europe").assertDoesNotExist()
    }
}
