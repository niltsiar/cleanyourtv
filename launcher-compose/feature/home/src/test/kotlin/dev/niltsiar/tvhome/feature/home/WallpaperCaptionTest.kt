package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.Wallpaper
import java.util.Locale

val WallpaperCaptionTest by testSuite {
    robolectricTestSuite<WallpaperCaptionTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class WallpaperCaptionTestContent :
    RobolectricTestSuiteContent({
        testFixture { WallpaperCaptionTestFixture() } asContextForEach {
            test("the pictures name shows on home") {
                show(Wallpaper(caption = "Sunrise and aurora over northern Europe"))
                compose.onNodeWithText("Sunrise and aurora over northern Europe").assertIsDisplayed()
            }

            test("a picture without a name shows none") {
                show(Wallpaper())
                compose.onNodeWithText("Sunrise and aurora over northern Europe").assertDoesNotExist()
            }
        }
    })

private class WallpaperCaptionTestFixture : JUnit4RulesContext() {
    val compose = rule(createComposeRule())

    fun show(wallpaper: Wallpaper) {
        val state = homeUiState(homeApps(), Evening, null, Locale.ENGLISH, wallpaper = wallpaper)
        compose.setContent {
            TvTheme {
                HomeScreen(state = { state }, layout = SixColumns.homeLayout(), onFocus = {}, onClick = {})
            }
        }
        compose.waitForIdle()
    }
}
