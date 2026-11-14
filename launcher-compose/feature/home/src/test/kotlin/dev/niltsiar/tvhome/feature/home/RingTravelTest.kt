package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.designsystem.Motion
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow

/** How the focus ring's colour goes from one tile to the next. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
val RingTravelTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val red = Color(0xFFFF8A8A)

            val blue = Color(0xFF8AB4FF)

            val green = Color(0xFF8AE6A0)

            fun TestScope.frames() = CoroutineScope(coroutineContext + TestMonotonicFrameClock(this))

            fun toLinear(channel: Float): Float = if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

            /** The colour as OKLab: lightness, then the two opponent axes. */
            fun lab(color: Color): FloatArray {
                val r = toLinear(color.red)
                val g = toLinear(color.green)
                val b = toLinear(color.blue)
                val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
                val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
                val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
                return floatArrayOf(
                    0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
                    1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
                    0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
                )
            }

            fun assertNear(
                expected: Color,
                actual: Color,
            ) = withClue("expected $expected, got $actual") {
                (
                    listOf(
                        expected.red - actual.red,
                        expected.green - actual.green,
                        expected.blue - actual.blue,
                    ).all { abs(it) < 0.01f }
                ).shouldBeTrue()
            }
        }
    } asContextForEach {
        test("the first ring shows its own colour at once") {
            with(it.testScope) {
                val travel = RingTravel()

                travel.arrive("a", red, Motion(), frames())

                travel.colourFor("a", red) shouldBe red
            }
        }

        test("a tile that takes focus from another starts at the old ring and reaches its own in 220 ms") {
            with(it.testScope) {
                val travel = RingTravel()
                val scope = frames()
                travel.arrive("a", red, Motion(), scope)

                travel.arrive("b", blue, Motion(), scope)

                assertNear(red, travel.colourFor("b", blue))
                withClue("the old tile keeps its own ring") { travel.colourFor("a", red) shouldBe red }
                advanceTimeBy(110)
                runCurrent()
                val middle = travel.colourFor("b", blue)
                middle shouldNotBe red
                middle shouldNotBe blue
                advanceTimeBy(130)
                runCurrent()
                travel.colourFor("b", blue) shouldBe blue
            }
        }

        test("the colours in between are blended in oklab") {
            with(it.testScope) {
                val travel = RingTravel()
                val scope = frames()
                travel.arrive("a", red, Motion(), scope)
                travel.arrive("b", blue, Motion(), scope)
                advanceTimeBy(80)
                runCurrent()

                val from = lab(red)
                val to = lab(blue)
                val middle = lab(travel.colourFor("b", blue))
                val share = (middle[0] - from[0]) / (to[0] - from[0])

                withClue("share $share") { share shouldBeIn 0.05f..0.95f }
                for (axis in 1..2) {
                    withClue("axis $axis") {
                        middle[axis] shouldBe
                            (from[axis] + (to[axis] - from[axis]) * share plusOrMinus 0.01f)
                    }
                }
            }
        }

        test("a move in the middle of a blend starts at the colour the ring had reached") {
            with(it.testScope) {
                val travel = RingTravel()
                val scope = frames()
                travel.arrive("a", red, Motion(), scope)
                travel.arrive("b", blue, Motion(), scope)
                advanceTimeBy(80)
                runCurrent()
                val reached = travel.colourFor("b", blue)

                travel.arrive("c", green, Motion(), scope)

                assertNear(reached, travel.colourFor("c", green))
                reached shouldNotBe blue
            }
        }

        test("a tile that takes focus after the tiles lost it starts at its own colour") {
            with(it.testScope) {
                val travel = RingTravel()
                val scope = frames()
                travel.arrive("a", red, Motion(), scope)

                travel.reset()
                travel.arrive("b", blue, Motion(), scope)

                travel.colourFor("b", blue) shouldBe blue
            }
        }

        test("with animations off the ring takes its colour at once") {
            with(it.testScope) {
                val travel = RingTravel()
                val scope = frames()
                val still = Motion(animations = false)
                travel.arrive("a", red, still, scope)

                travel.arrive("b", blue, still, scope)
                advanceTimeBy(20)
                runCurrent()

                travel.colourFor("b", blue) shouldBe blue
            }
        }

        test("a ring that is fading out mid travel keeps the colour it had reached") {
            with(it.testScope) {
                val travel = RingTravel()
                val scope = frames()
                travel.arrive("a", red, Motion(), scope)
                travel.arrive("b", blue, Motion(), scope)
                advanceTimeBy(80)
                runCurrent()
                val reached = travel.colourFor("b", blue)

                travel.arrive("c", green, Motion(), scope)

                assertNear(reached, travel.colourFor("b", blue))
                travel.colourFor("b", blue) shouldNotBe blue
            }
        }

        test("a ring that loses focus to the top bar mid travel keeps the colour it had reached") {
            with(it.testScope) {
                val travel = RingTravel()
                val scope = frames()
                travel.arrive("a", red, Motion(), scope)
                travel.arrive("b", blue, Motion(), scope)
                advanceTimeBy(80)
                runCurrent()
                val reached = travel.colourFor("b", blue)

                travel.reset()

                assertNear(reached, travel.colourFor("b", blue))
            }
        }

        test("a ring whose travel was over shows its own colour after it loses focus") {
            with(it.testScope) {
                val travel = RingTravel()
                val scope = frames()
                travel.arrive("a", red, Motion(), scope)
                travel.arrive("b", blue, Motion(), scope)
                advanceTimeBy(300)
                runCurrent()

                travel.arrive("c", green, Motion(), scope)

                travel.colourFor("b", blue) shouldBe blue
            }
        }

        test("with animations off the new ring shows its colour in the frame it gets focus") {
            with(it.testScope) {
                val travel = RingTravel()
                val scope = frames()
                val still = Motion(animations = false)
                travel.arrive("a", red, still, scope)

                travel.arrive("b", blue, still, scope)

                travel.colourFor("b", blue) shouldBe blue
            }
        }
    }
}
