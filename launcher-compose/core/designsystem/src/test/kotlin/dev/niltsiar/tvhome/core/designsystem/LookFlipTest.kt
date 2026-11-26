package dev.niltsiar.tvhome.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * [LookFlip] over a 200 x 100 px page of one colour with a focused 40 x 20 px chip at (20, 40) px, so the hole grows from
 * (40, 50) px. A flip's change turns the page from [Old] to [New]; the picture is the page before it.
 */
val LookFlipTest by testSuite {
    robolectricTestSuite<LookFlipTestContent>(
        "sdk 31, mdpi",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "mdpi"
            },
    )
}

private val Old = Color(0xFF3050A0)
private val New = Color(0xFF20C060)
private val Chip = Color(0xFFE0E0E0)

class LookFlipTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())
                lateinit var flip: LookFlip
                var colour by mutableStateOf(Old)
                var enabled by mutableStateOf(true)
                var shown by mutableStateOf(true)
                var changes = 0

                fun show() {
                    compose.mainClock.autoAdvance = false
                    // The native graphics mode starts in touch mode, where nothing takes key focus.
                    compose.runOnUiThread {
                        compose.activity.window.decorView
                            .requestFocusFromTouch()
                    }
                    val chip = FocusRequester()
                    compose.setContent {
                        flip = rememberLookFlip()
                        Box(Modifier.size(200.dp, 100.dp).testTag("scene")) {
                            if (shown) {
                                Box(Modifier.size(200.dp, 100.dp).lookFlip(flip, enabled).background(colour)) {
                                    Box(
                                        Modifier
                                            .offset(20.dp, 40.dp)
                                            .size(40.dp, 20.dp)
                                            .focusRequester(chip)
                                            .focusable()
                                            .background(Chip),
                                    )
                                }
                            }
                        }
                    }
                    compose.mainClock.advanceTimeByFrame()
                    compose.runOnUiThread { chip.requestFocus() }
                    compose.mainClock.advanceTimeByFrame()
                }

                /** The scene's pixels; capturing draws the page, which records what the next picture shows. */
                fun pixels() = compose.onNodeWithTag("scene").captureToImage().toPixelMap()

                /** A flip to [to], on the Glass flip spec once the page shows it. */
                fun flipTo(to: Color) {
                    pixels()
                    compose.runOnUiThread {
                        flip.flip({
                            changes++
                            colour = to
                        }) {
                            snapshotFlow { colour }.first { it == to }
                            Motion().flipSpec(Look.Glass)
                        }
                    }
                }

                fun advance(ms: Long) {
                    val start = compose.mainClock.currentTime
                    while (compose.mainClock.currentTime - start < ms) compose.mainClock.advanceTimeByFrame()
                }

                fun picture(): ImageBitmap? = flip.picture

                fun recomposersIdle(): Boolean =
                    runBlocking { Recomposer.runningRecomposers.value.all { it.state.first() == Recomposer.State.Idle } }
            }
        } asContextForEach {
            test("200 ms in the old page shows outside a hole round the focused chip, and the new page inside it") {
                show()
                flipTo(New)
                advance(200)

                val pixels = pixels()
                withClue("the chip's centre") { pixels[40, 50] shouldBe Chip }
                withClue("40 px right of it") { pixels[80, 50] shouldBe New }
                withClue("the far corner") { pixels[199, 99] shouldBe Old }
                withClue("the far top corner") { pixels[199, 0] shouldBe Old }
                flip.captures shouldBe 1
                changes shouldBe 1
            }

            test("the picture is half the page's size") {
                show()
                flipTo(New)
                val picture = picture().shouldNotBeNull()
                picture.width shouldBe 100
                picture.height shouldBe 50
            }

            test("when the flip ends the picture goes and nothing asks for a frame") {
                show()
                flipTo(New)
                advance(200)
                pixels()
                withClue("recomposers idle mid-flip") { recomposersIdle() shouldBe false }

                advance(Motion.FLIP_MS.toLong())
                val pixels = pixels()

                flip.holdsPicture shouldBe false
                flip.node!!
                    .hole.shaded
                    .shouldBeNull()
                pixels[199, 99] shouldBe New
                withClue("recomposers idle after the flip") { recomposersIdle() shouldBe true }
            }

            test("closing the page mid-flip lets go of the picture") {
                show()
                flipTo(New)
                advance(200)
                flip.holdsPicture shouldBe true

                shown = false
                compose.mainClock.advanceTimeByFrame()

                flip.holdsPicture shouldBe false
                flip.node.shouldBeNull()
            }

            test("a second flip mid-flip takes the screen as it shows then and lets go of the first picture") {
                show()
                flipTo(New)
                advance(200)
                pixels()
                val first = picture().shouldNotBeNull()
                flip.node!!.hole.shaded shouldBeSameInstanceAs first

                flipTo(Old)
                val second = picture().shouldNotBeNull()
                second shouldNotBeSameInstanceAs first
                flip.captures shouldBe 2
                val shot = second.toPixelMap()
                withClue("inside the first hole, the new page") { shot[40, 25] shouldBe New }
                withClue("outside it, the first picture") { shot[99, 49] shouldBe Old }

                val pixels = pixels()
                flip.node!!.hole.shaded shouldBeSameInstanceAs second
                withClue("the second picture covers the page at the start of its flip") { pixels[80, 50] shouldBe New }

                advance(Motion.FLIP_MS.toLong() + 100)
                pixels()
                flip.holdsPicture shouldBe false
                flip.node!!
                    .hole.shaded
                    .shouldBeNull()
            }

            test("with the modifier turned off after the page drew the change is a cut and nothing is captured") {
                show()
                pixels()
                enabled = false
                compose.waitForIdle()
                compose.mainClock.advanceTimeByFrame()
                flipTo(New)
                compose.waitForIdle()
                compose.mainClock.advanceTimeByFrame()

                changes shouldBe 1
                flip.captures shouldBe 0
                flip.holdsPicture shouldBe false
                pixels()[199, 99] shouldBe New
            }

            test("before the page has drawn the change is a cut") {
                show()
                compose.runOnUiThread { flip.flip({ colour = New }) { Motion().flipSpec(Look.Glass) } }

                flip.captures shouldBe 0
                flip.holdsPicture shouldBe false
                colour shouldBe New
            }

            test("a look that never arrives lets go of the picture after the wait") {
                show()
                pixels()
                compose.runOnUiThread { flip.flip({}) { awaitCancellation() } }
                flip.holdsPicture shouldBe true

                advance(LookFlip.ARRIVAL_MS + 100)

                flip.holdsPicture shouldBe false
                pixels()[199, 99] shouldBe Old
            }

            test("release ends a flip and lets go of the picture") {
                show()
                flipTo(New)
                advance(200)

                compose.runOnUiThread { flip.release() }
                val pixels = pixels()

                flip.holdsPicture shouldBe false
                flip.node!!
                    .hole.shaded
                    .shouldBeNull()
                pixels[199, 99] shouldBe New
            }
        }
    })
