package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.Wallpaper
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
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

            test("a name cut short keeps its whole shadow and its place") {
                showOnWhite("Sunrise and aurora over the northern lakes of Europe, seen from a mountain hut at dawn ".repeat(2))

                val node = compose.onNodeWithTag("caption").fetchSemanticsNode()
                val left = node.positionInRoot.x.toInt()
                val top = node.positionInRoot.y.toInt()
                val right = left + node.size.width
                val bottom = top + node.size.height
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                // 960 dp wide at 2 px per dp: the box ends 62 dp from the right and is 520 dp wide.
                right shouldBe 1920 - 124
                left shouldBe right - 1040
                withClue("shadow under the text box") { pixels.darkest(left, right, bottom, bottom + 12) shouldBeLessThan 245 }
                withClue("shadow left of the text box") { pixels.darkest(left - 10, left, top, bottom) shouldBeLessThan 245 }
            }
        }
    })

private class WallpaperCaptionTestFixture : JUnit4RulesContext() {
    val compose = rule(createComposeRule())

    fun show(wallpaper: Wallpaper) {
        val state = homeUiState(homeApps(), Evening, null, Locale.ENGLISH, wallpaper = wallpaper)
        compose.setContent {
            TvTheme(look = Look.Glass) {
                HomeScreen(state = { state }, layout = SixColumns.homeLayout(), onFocus = {}, onClick = {})
            }
        }
        compose.waitForIdle()
    }

    /** The caption alone on white, where its white text vanishes and only its shadow shows. */
    fun showOnWhite(caption: String) {
        compose.setContent {
            TvTheme(look = Look.Glass) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    WallpaperCaption(
                        caption = { caption },
                        crossfade = false,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 110.dp, end = 62.dp).testTag("caption"),
                    )
                }
            }
        }
        compose.waitForIdle()
    }
}

/** The lowest grey level, 0 to 255, in the rectangle from [x0], [y0] to [x1], [y1] (exclusive). */
private fun PixelMap.darkest(
    x0: Int,
    x1: Int,
    y0: Int,
    y1: Int,
): Int = (x0 until x1).minOf { x -> (y0 until y1).minOf { y -> this[x, y].let { ((it.red + it.green + it.blue) / 3 * 255).toInt() } } }
