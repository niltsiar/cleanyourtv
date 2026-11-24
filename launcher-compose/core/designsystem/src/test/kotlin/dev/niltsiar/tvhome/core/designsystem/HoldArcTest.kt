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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
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
import io.kotest.matchers.shouldBe

/** While OK is held on a focused [FocusSurface] with [holdOk], an arc fills its gap clockwise from the top centre. */
val HoldArcTest by testSuite {
    robolectricTestSuite<HoldArcTestContent>(
        "sdk 31, mdpi",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "mdpi"
            },
    )
}

class HoldArcTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())
                val hold = OkHold { compose.mainClock.currentTime }
                var opened = 0

                /**
                 * Two 100 x 60 dp surfaces 40 dp apart on a black 320 x 200 dp scene, white rings, no glow, both holding OK on
                 * [hold]; the first focused and settled. The first spans 40 to 140 dp across and 70 to 130 dp down.
                 */
                fun show(
                    style: FocusStyle,
                    motion: Motion = Motion(),
                ) {
                    compose.mainClock.autoAdvance = false
                    compose.runOnUiThread {
                        compose.activity.window.decorView
                            .requestFocusFromTouch()
                    }
                    val first = FocusRequester()
                    compose.setContent {
                        TvTheme(look = Look.Glass, motion = motion, focusStyle = style) {
                            Box(
                                Modifier.size(320.dp, 200.dp).background(Color.Black).testTag("scene"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row {
                                    FocusSurface(
                                        {},
                                        Modifier.size(100.dp, 60.dp).focusRequester(first).holdOk(hold, { opened++ }),
                                        glow = false,
                                    ) {}
                                    Box(Modifier.size(40.dp, 60.dp))
                                    FocusSurface({}, Modifier.size(100.dp, 60.dp).holdOk(hold, {}), glow = false) {}
                                }
                            }
                        }
                    }
                    compose.mainClock.advanceTimeByFrame()
                    compose.runOnUiThread { first.requestFocus() }
                    compose.mainClock.advanceTimeBy(2_000)
                }

                /**
                 * The red in the gap 1.5 dp out from the middle of the first surface's right side, a quarter of the way round,
                 * and of its left side, three quarters round, both grown [scale] times about its centre at (90, 100) dp; then
                 * the same right side point on the second surface, at rest.
                 */
                fun gapReds(scale: Float = TILE_FOCUSED_SCALE): Triple<Float, Float, Float> {
                    val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
                    val reach = (50f + 1.5f) * scale
                    return Triple(
                        pixels[(90f + reach).toInt(), 100].red,
                        pixels[(90f - reach).toInt(), 100].red,
                        pixels[180 + 101, 100].red,
                    )
                }

                fun okDown() = compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }

                fun okUp() = compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
            }
        } asContextForEach {
            for (style in FocusStyle.entries) {
                test("with glass_style ${style.name} half the hold fills the gap of the held tile half way round and the up clears it") {
                    show(style)
                    withClue("the gap before the press") { gapReds().first shouldBeLessThanOrEqual 0.05f }

                    okDown()
                    compose.mainClock.advanceTimeBy(HOLD_OK_MS / 2)

                    val (quarter, threeQuarters, other) = gapReds()
                    withClue("a quarter round") { quarter shouldBeGreaterThanOrEqual 0.95f }
                    withClue("three quarters round") { threeQuarters shouldBeLessThanOrEqual 0.05f }
                    withClue("the tile at rest") { other shouldBeLessThanOrEqual 0.05f }

                    okUp()
                    compose.mainClock.advanceTimeByFrame()
                    withClue("the gap after the up") { gapReds().first shouldBeLessThanOrEqual 0.05f }
                }
            }

            test("with animations off a held ok draws no arc and still opens the menu") {
                show(FocusStyle.Glass, Motion(animations = false))

                okDown()
                compose.mainClock.advanceTimeBy(HOLD_OK_MS / 2)

                withClue("a quarter round") { gapReds(scale = 1f).first shouldBeLessThanOrEqual 0.05f }
                compose.mainClock.advanceTimeBy(HOLD_OK_MS)
                opened shouldBe 1
            }
        }
    })
