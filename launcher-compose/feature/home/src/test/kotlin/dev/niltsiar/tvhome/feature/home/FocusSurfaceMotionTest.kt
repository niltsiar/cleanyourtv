package dev.niltsiar.tvhome.feature.home

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.FocusSurface
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe

/** Widths are what the tile draws, read from pixels: the growth is drawn, so the native graphics mode is needed. */
val FocusSurfaceMotionTest by testSuite {
    robolectricTestSuite<FocusSurfaceMotionTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class FocusSurfaceMotionTestContent :
    RobolectricTestSuiteContent({
        testFixture { FocusSurfaceMotionTestFixture() } asContextForEach {
            test("a focused tile grows to 108 percent without passing it by default") {
                val widths = widthsAfterFocus(Motion())

                withClue("half way ${widths[4]}") { widths[4] shouldBeIn 102f..107f }
                widths.last() shouldBe (108f plusOrMinus 0.5f)
                withClue("peaked at ${widths.max()}") { widths.max() shouldBeLessThanOrEqualTo 108.5f }
            }

            test("a focused tile pops to 114 percent then settles at 108 percent when focus overshoot is on") {
                val widths = widthsAfterFocus(Motion(focusOvershoot = true))
                val peak = widths.indexOf(widths.max())

                // The test clock rounds each 10 ms step up to a 16 ms frame, and no frame falls on the 120 ms peak of 114.4 dp.
                withClue("peaked at ${widths[peak]}") { widths[peak] shouldBeIn 113.4f..114.9f }
                withClue("peaked at frame $peak: ${widths.take(24)}") { peak shouldBeIn 6..9 }
                withClue("settled ${widths.drop(20).distinct()}") { (widths.drop(20).all { it in 107.5f..108.5f }).shouldBeTrue() }
            }

            test("a focused tile does not grow when animations are off") {
                val widths = widthsAfterFocus(Motion(animations = false))

                withClue("widths ${widths.distinct()}") { (widths.all { it in 99.5f..100.5f }).shouldBeTrue() }
            }

            test("a focused tile reaches 108 percent in 100 ms without overshoot with reduced motion") {
                val widths = widthsAfterFocus(Motion(reducedMotion = true))

                widths[9] shouldBe (108f plusOrMinus 0.5f)
                withClue("peaked at ${widths.max()}") { widths.max() shouldBeLessThanOrEqualTo 108.5f }
            }

            test("a focused tile is already growing in the second frame after focus") {
                showTile(Motion())
                repeat(2) { compose.mainClock.advanceTimeByFrame() }

                val width = drawnWidth()
                withClue("width $width") { width shouldBeGreaterThan 100f }
            }

            test("a growing tile keeps its bounds so accessibility has nothing to update") {
                showTile(Motion())
                repeat(5) { compose.mainClock.advanceTimeByFrame() }
                val growing = drawnWidth()
                val bounds = compose.onNodeWithTag("tile").getBoundsInRoot()

                withClue("drawn $growing") { growing shouldBeGreaterThan 100f }
                (bounds.right - bounds.left).value shouldBe (100f plusOrMinus 0.01f)
            }

            test("a focused ring has a 2 dp stroke of black 055 outside its hairline on a bright backdrop and none at rest") {
                compose.setContent {
                    view = LocalView.current
                    TvTheme {
                        Box(Modifier.size(140.dp).background(Color.White).testTag("frame"), contentAlignment = Alignment.Center) {
                            Box(Modifier.align(Alignment.TopStart).size(1.dp).focusable())
                            FocusSurface(
                                onClick = {},
                                glow = false,
                                modifier = Modifier.size(100.dp).focusRequester(requester).testTag("tile"),
                            ) {
                                Box(Modifier.fillMaxSize().background(Color.Red))
                            }
                        }
                    }
                }
                compose.runOnUiThread { view.requestFocusFromTouch() }
                compose.waitForIdle()
                // At 1.08 the ring's pieces lie, in dp from the tile's grown edge: gap 3.24, ring to 6.48, hairline to 7.56, stroke to 9.72.
                val strokeAt = 70f - 54f - 8.1f
                val strokeOuterAt = 70f - 54f - 9.4f
                val outsideAt = 70f - 54f - 10.3f
                greyAt(strokeAt) shouldBe (1f plusOrMinus 0.01f)

                compose.runOnUiThread { requester.requestFocus() }
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitForIdle()

                greyAt(strokeAt) shouldBe (1f - 0.55f plusOrMinus 0.04f)
                greyAt(strokeOuterAt) shouldBe (1f - 0.55f plusOrMinus 0.04f)
                greyAt(outsideAt) shouldBe (1f plusOrMinus 0.01f)
            }
        }
    })

private class FocusSurfaceMotionTestFixture : JUnit4RulesContext() {
    val compose = rule(createComposeRule())

    val requester = FocusRequester()

    lateinit var view: View

    var restRedness = 1f

    /** A 100 dp red tile on black, with room around it to grow, and a focusable dot before it. */
    fun showTile(motion: Motion) {
        compose.setContent {
            view = LocalView.current
            TvTheme(motion = motion) {
                Box(Modifier.size(140.dp).background(Color.Black).testTag("frame"), contentAlignment = Alignment.Center) {
                    Box(Modifier.align(Alignment.TopStart).size(1.dp).focusable())
                    FocusSurface(onClick = {}, glow = false, modifier = Modifier.size(100.dp).focusRequester(requester).testTag("tile")) {
                        Box(Modifier.fillMaxSize().background(Color.Red))
                    }
                }
            }
        }
        // The native graphics mode starts in touch mode, where nothing takes key focus; leaving it focuses the first
        // target, a dot beside the tile, so the tile is at rest before the focus that is measured.
        compose.runOnUiThread { view.requestFocusFromTouch() }
        compose.waitForIdle()
        restRedness = redness()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { requester.requestFocus() }
    }

    /** The width in dp the tile draws, against its 100 dp at rest. */
    fun drawnWidth(): Float = 100f * redness() / restRedness

    /** How much red the tile draws over green across its middle row (the white ring adds none), in pixels of pure red. */
    fun redness(): Float {
        val image = compose.onNodeWithTag("frame").captureToImage().toPixelMap()
        val row = image.height / 2
        val redness = { x: Int -> image[x, row].let { it.red - it.green } }
        return (0 until image.width).sumOf { x -> redness(x).toDouble() }.toFloat() / redness(image.width / 2)
    }

    /** Focuses a 100 dp tile and returns its drawn width in dp after each 10 ms. */
    fun widthsAfterFocus(motion: Motion): List<Float> {
        showTile(motion)
        return List(100) {
            compose.mainClock.advanceTimeBy(10)
            drawnWidth()
        }
    }

    /** The grey of the tile frame's middle row at [dp] in from its left edge; the frame is 140 dp wide at 2 pixels to the dp. */
    fun greyAt(dp: Float): Float {
        val image = compose.onNodeWithTag("frame").captureToImage().toPixelMap()
        return image[(dp * 2f).toInt(), image.height / 2].green
    }
}
