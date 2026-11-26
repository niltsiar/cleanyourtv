package dev.niltsiar.tvhome.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
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
import dev.niltsiar.tvhome.core.config.ExpressiveTile
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.expressive.ChargeLine
import dev.niltsiar.tvhome.core.designsystem.expressive.toneSolves
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

private val Art = Color(0xFF3B5B8C)

/** In Expressive a held OK charges the held tile or pill after a dead zone, and the up or the menu ends it. */
val ChargeTest by testSuite {
    robolectricTestSuite<ChargeTestContent>(
        "sdk 31, mdpi",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "mdpi"
            },
    )
}

class ChargeTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())
                val hold = OkHold { compose.mainClock.currentTime }
                var opened = 0
                var motionNow by mutableStateOf(Motion())

                /** [content], given the modifier of the held item, focused and settled on a black 320 x 200 dp scene in Expressive. */
                fun show(
                    tile: ExpressiveTile = ExpressiveTile.Capsule,
                    motion: Motion = Motion(look = Look.Expressive),
                    content: @Composable (Modifier) -> Unit = { held ->
                        FocusSurface({}, held.size(100.dp, 60.dp), glow = false) {
                            Box(Modifier.size(100.dp, 60.dp).background(Art))
                        }
                    },
                ) {
                    compose.mainClock.autoAdvance = false
                    compose.runOnUiThread {
                        compose.activity.window.decorView
                            .requestFocusFromTouch()
                    }
                    val first = FocusRequester()
                    motionNow = motion
                    compose.setContent {
                        TvTheme(look = Look.Expressive, motion = motionNow, expressiveTile = tile) {
                            Box(
                                Modifier.size(320.dp, 200.dp).background(Color.Black).testTag("scene"),
                                contentAlignment = Alignment.Center,
                            ) {
                                content(Modifier.focusRequester(first).holdOk(hold, { opened++ }))
                            }
                        }
                    }
                    compose.mainClock.advanceTimeByFrame()
                    compose.runOnUiThread { first.requestFocus() }
                    compose.mainClock.advanceTimeBy(2_000)
                }

                fun scene(): IntArray =
                    compose.onNodeWithTag("scene").captureToImage().toPixelMap().let { map ->
                        IntArray(map.width * map.height) { map[it % map.width, it / map.width].toArgb() }
                    }

                /** The scene's row through the middle of the tile, from its left edge to a third of the way in. */
                fun leftEdgeRow(): IntArray =
                    compose.onNodeWithTag("scene").captureToImage().toPixelMap().let { map ->
                        IntArray(70) { map[60 + it, 100].toArgb() }
                    }

                /** The x of the art's left edge on the row through the tile's middle: its first pixel in the art's colour. */
                fun artLeft(): Int =
                    compose.onNodeWithTag("scene").captureToImage().toPixelMap().let { map ->
                        (0 until map.width).first { x ->
                            val pixel = map[x, 100]
                            abs(pixel.red - Art.red) < 0.02f && abs(pixel.green - Art.green) < 0.02f && abs(pixel.blue - Art.blue) < 0.02f
                        }
                    }

                fun okDown() = compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }

                fun okUp() = compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }

                /** Runs the test clock [ms] on, a frame at a time; with [drawn], each frame is drawn, as a TV draws every frame of a hold. */
                fun frames(
                    ms: Long,
                    drawn: Boolean = false,
                ) {
                    val end = compose.mainClock.currentTime + ms
                    while (compose.mainClock.currentTime < end) {
                        compose.mainClock.advanceTimeByFrame()
                        if (drawn) scene()
                    }
                }
            }
        } asContextForEach {
            for (tile in ExpressiveTile.entries) {
                test("a ${tile.name} tile shows nothing 100 ms into a held ok, charges by 300 ms, and the up clears it on the next frame") {
                    show(tile)
                    val rest = scene()

                    okDown()
                    frames(100)
                    withClue("100 ms") { scene().contentEquals(rest) shouldBe true }
                    frames(200)
                    withClue("300 ms") { scene().contentEquals(rest) shouldBe false }

                    okUp()
                    compose.mainClock.advanceTimeByFrame()
                    withClue("the frame after the up") { scene().contentEquals(rest) shouldBe true }
                    opened shouldBe 0
                }

                test("a ${tile.name} tile's charge samples no line after its first frame") {
                    show(tile)
                    okDown()
                    frames(140, drawn = true)
                    val built = ChargeLine.built

                    frames(420, drawn = true)

                    ChargeLine.built shouldBe built
                    opened shouldBe 1
                }
            }

            test("a charged tile tightens, so its art's left edge moves in by the end of the hold") {
                show()
                val rest = artLeft()

                okDown()
                frames(540)

                artLeft() shouldBeGreaterThan rest
            }

            test("with reduced motion a held ok draws a line from the first frame and the tile keeps its size") {
                show(motion = Motion(reducedMotion = true, look = Look.Expressive))
                val rest = scene()
                val edge = leftEdgeRow()

                okDown()
                frames(100)
                withClue("100 ms") { scene().contentEquals(rest) shouldBe false }
                frames(440)
                withClue("the left edge at 540 ms") { leftEdgeRow().contentEquals(edge) shouldBe true }
            }

            test("with animations off a held ok draws no charge and still opens the menu at 550 ms") {
                show(motion = Motion(animations = false, look = Look.Expressive))
                val rest = scene()

                okDown()
                frames(300)
                scene().contentEquals(rest) shouldBe true
                frames(260)
                opened shouldBe 1
            }

            for ((name, off) in listOf("animations off" to Motion(animations = false), "reduced motion" to Motion(reducedMotion = true))) {
                test("after a charge, a held ok with $name leaves the tile's shape alone") {
                    show()
                    okDown()
                    frames(300, drawn = true)
                    okUp()
                    motionNow = off
                    frames(2_000)
                    val rest = scene()
                    val edge = leftEdgeRow()

                    okDown()
                    frames(300)

                    if (off.animations) {
                        withClue("the tile's left edge at 300 ms") { leftEdgeRow().contentEquals(edge) shouldBe true }
                    } else {
                        withClue("the scene at 300 ms") { scene().contentEquals(rest) shouldBe true }
                    }
                }
            }

            test("a hold while the band travels between two app colours solves no palette tone") {
                val red = Color(0xFFE0453A)
                val blue = Color(0xFF2F9FD8)
                show { held ->
                    // A band that moves on every frame, as a ring travelling from one app's colour to the next does.
                    val travel = ColorProducer { lerp(red, blue, (compose.mainClock.currentTime % 997) / 997f) }
                    FocusSurface({}, held.size(100.dp, 60.dp), glow = false, ring = travel) {
                        Box(Modifier.size(100.dp, 60.dp).background(Art))
                    }
                }
                okDown()
                frames(140, drawn = true)
                val solved = toneSolves

                frames(260, drawn = true)

                toneSolves shouldBe solved
            }

            test("a round button shows nothing 100 ms into a held ok, a wave by 300 ms, and the up clears it on the next frame") {
                val glass = GlassState()
                show { held -> GlassIconButton(glass, "Settings", {}, held) { _, _ -> } }
                val rest = scene()

                okDown()
                frames(100)
                withClue("100 ms") { scene().contentEquals(rest) shouldBe true }
                frames(200)
                withClue("300 ms") { scene().contentEquals(rest) shouldBe false }

                okUp()
                compose.mainClock.advanceTimeByFrame()
                withClue("the frame after the up") { scene().contentEquals(rest) shouldBe true }
            }
        }
    })
