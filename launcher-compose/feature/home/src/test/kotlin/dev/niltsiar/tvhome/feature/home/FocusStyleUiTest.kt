package dev.niltsiar.tvhome.feature.home

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.FocusStyle
import dev.niltsiar.tvhome.core.designsystem.FocusSurface
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe

/** What a focused tile draws in each [FocusStyle], read from pixels: a 100 dp grey tile on a mid-grey frame, at 2 pixels to the dp. */
val FocusStyleUiTest by testSuite {
    robolectricTestSuite<FocusStyleUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class FocusStyleUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { FocusStyleUiTestFixture() } asContextForEach {
            test("glass lights the top of the tile and glows past the ring") {
                showFocusedTile(FocusStyle.Glass)

                withClue("sheen ${sheen()}") { sheen().red shouldBeGreaterThan tile.red + 0.08f }
                withClue("glow ${glow()}") { glow() shouldBeGreaterThan 0.015f }
                withClue("ring ${ring()}") { (ring().red > 0.95f && ring().blue > 0.95f).shouldBeTrue() }
            }

            test("rim draws the ring and its keyline and nothing else") {
                showFocusedTile(FocusStyle.Rim)

                sheen().red shouldBe (tile.red plusOrMinus 0.01f)
                glow() shouldBe (0f plusOrMinus 0.003f)
                withClue("ring ${ring()}") { (ring().red > 0.95f && ring().blue > 0.95f).shouldBeTrue() }
                withClue("keyline ${keyline()}") { keyline().red shouldBeLessThan frame.red - 0.04f }
            }

            test("rim draws its ring at least 2 px wider than glass on the same tile") {
                showFocusedTile(FocusStyle.Glass)
                val glass = ringPixels()
                style = FocusStyle.Rim
                compose.waitForIdle()
                val rim = ringPixels()

                withClue("glass $glass px, rim $rim px") { (glass >= 6 && rim >= glass + 2).shouldBeTrue() }
            }

            test("halo draws the ring and a glow in its colour under the tile half as strong beside it where rim draws none") {
                showFocusedTile(FocusStyle.Halo, ring = coral)

                sheen().red shouldBe (tile.red plusOrMinus 0.01f)
                withClue("ring ${ring()}") { (ring().red > 0.95f && ring().blue in 0.2f..0.3f).shouldBeTrue() }
                withClue("keyline ${keyline()}") { keyline().red shouldBeLessThan frame.red - 0.04f }
                val under = redUnder()
                withClue("under $under, bluer by ${glow()}") { (under >= 0.3f && under >= 3f * glow()).shouldBeTrue() }
                withClue("beside ${redBeside()}, under $under") { redBeside() / under shouldBeIn 0.4f..0.6f }

                style = FocusStyle.Rim
                compose.waitForIdle()
                redUnder() shouldBe (0f plusOrMinus 0.003f)
            }
        }
    })

private class FocusStyleUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createComposeRule())

    val requester = FocusRequester()

    val frame = Color(0xFF202020)

    val tile = Color(0xFF404040)

    val coral = Color(0xFFFF6040)

    var style by mutableStateOf(FocusStyle.Glass)

    fun showFocusedTile(
        style: FocusStyle,
        ring: Color? = null,
    ) {
        this.style = style
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            TvTheme(focusStyle = this.style) {
                Box(Modifier.size(180.dp).background(frame).testTag("frame"), contentAlignment = Alignment.Center) {
                    Box(Modifier.align(Alignment.TopStart).size(1.dp).focusable())
                    FocusSurface(
                        onClick = {},
                        modifier = Modifier.size(100.dp).focusRequester(requester),
                        ring = ring?.let { colour -> { colour } },
                    ) {
                        Box(Modifier.fillMaxSize().background(tile))
                    }
                }
            }
        }
        compose.runOnUiThread { view.requestFocusFromTouch() }
        compose.waitForIdle()
        compose.runOnUiThread { requester.requestFocus() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /** The pixel at ([x], [y]) dp of the frame. */
    fun at(
        x: Float,
        y: Float,
    ): Color = compose.onNodeWithTag("frame").captureToImage().toPixelMap()[(x * 2).toInt(), (y * 2).toInt()]

    /** The tile grows 1.08 times about the frame's centre, 90 dp; [out] dp past its edge at rest is this far above the centre. */
    fun above(out: Float): Float = 90f - 1.08f * (50f + out)

    /** Ten dp under the grown tile's top edge, in the tile. */
    fun sheen(): Color = at(90f, above(0f) + 10f)

    /** The middle of the 3 dp ring, past its 3 dp gap. */
    fun ring(): Color = at(90f, above(4.5f))

    /** The middle of the 2 dp black stroke that holds the ring on a bright wallpaper. */
    fun keyline(): Color = at(90f, above(3f + 3f + 1f + 1f))

    /** How much bluer than the frame the glow makes it two dp past the keyline under the tile, where it is strongest: the light is above. */
    fun glow(): Float = at(90f, 90f + 1.08f * (50f + 9f) + 2f).blue - frame.blue

    /** How much redder than the frame it is two dp past the keyline under the tile, and beside it. */
    fun redUnder(): Float = at(90f, 90f + 1.08f * (50f + 9f) + 2f).red - frame.red

    fun redBeside(): Float = at(90f + 1.08f * (50f + 9f) + 2f, 90f).red - frame.red

    /** The pixels of the white ring down the middle of the tile, from the frame above it to the grown tile's top edge. */
    fun ringPixels(): Int {
        val pixels = compose.onNodeWithTag("frame").captureToImage().toPixelMap()
        return (0 until (above(0f) * 2).toInt()).count { y -> pixels[180, y].let { it.red > 0.9f && it.blue > 0.9f } }
    }
}
