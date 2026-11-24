package dev.niltsiar.tvhome.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual

/** The ring of a [FocusSurface] that gains focus shows whole on the first frame; the one that loses it fades. */
val FocusRingFirstFrameTest by testSuite {
    robolectricTestSuite<FocusRingFirstFrameTestContent>(
        "sdk 31, mdpi",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "mdpi"
            },
    )
}

class FocusRingFirstFrameTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())
                val first = FocusRequester()
                val second = FocusRequester()

                /** Two 100 x 60 dp surfaces 40 dp apart on a black 320 x 200 dp scene, white rings, no glow; the first focused and settled. */
                fun show(style: FocusStyle) {
                    compose.mainClock.autoAdvance = false
                    compose.runOnUiThread {
                        compose.activity.window.decorView
                            .requestFocusFromTouch()
                    }
                    compose.setContent {
                        TvTheme(look = Look.Glass, focusStyle = style) {
                            Box(
                                Modifier.size(320.dp, 200.dp).background(Color.Black).testTag("scene"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row {
                                    FocusSurface({}, Modifier.size(100.dp, 60.dp).focusRequester(first), glow = false) {}
                                    Box(Modifier.size(40.dp, 60.dp))
                                    FocusSurface({}, Modifier.size(100.dp, 60.dp).focusRequester(second), glow = false) {}
                                }
                            }
                        }
                    }
                    compose.mainClock.advanceTimeByFrame()
                    compose.runOnUiThread { first.requestFocus() }
                    compose.mainClock.advanceTimeBy(2_000)
                }

                /**
                 * The red, at mid height, of the first surface's ring as it stood grown (4 dp out) and of the second surface's ring
                 * before it grows. The row starts at (320 - 240) / 2 = 40 dp, and a ring's middle lies 4 to 5 dp out from its surface.
                 */
                fun ringReds(): Pair<Float, Float> {
                    val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
                    return pixels[40 - 4 - 5, 100].red to pixels[40 + 140 - 5, 100].red
                }

                /** The red, at mid height, where the first surface's ring would be at rest. */
                fun restingFirstRingRed(): Float =
                    compose
                        .onNodeWithTag("scene")
                        .captureToImage()
                        .toPixelMap()[40 - 5, 100]
                        .red
            }
        } asContextForEach {
            for (style in FocusStyle.entries) {
                test("with glass_style ${style.name} the new ring is whole on the first frame and the old one still fades") {
                    show(style)
                    compose.runOnUiThread { second.requestFocus() }
                    compose.mainClock.advanceTimeByFrame()

                    val (old, new) = ringReds()
                    withClue("the new ring on its first frame") { new shouldBeGreaterThanOrEqual 0.95f }
                    withClue("the old ring on the same frame") { old shouldBeGreaterThanOrEqual 0.5f }

                    compose.mainClock.advanceTimeBy(2_000)
                    withClue("the old ring once it has faded") { restingFirstRingRed() shouldBeLessThanOrEqual 0.05f }
                }
            }
        }
    })
