package dev.niltsiar.tvhome.feature.clock

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.designsystem.ScreensaverGlows
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.random.Random

val ClockSceneStateTest by testSuite {
    robolectricTestSuite<ClockSceneStateTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class ClockSceneStateTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())

                fun play(
                    state: ClockSceneState,
                    cycleGlow: Boolean = true,
                    shapes: ShapeChange? = null,
                ) {
                    compose.mainClock.autoAdvance = false
                    compose.setContent { LaunchedEffect(state) { state.run(cycleGlow, shapes) } }
                }

                /** The clock's minute move starts [MOVE_MS] after its fade-in, and its fade-out ends where it moves. */
                val minute = FADE_IN_MS + MOVE_MS

                fun advance(ms: Long) = compose.mainClock.advanceTimeBy(ms)
            }
        } asContextForEach {
            test("the clock fades in over 1200 ms") {
                val state = ClockSceneState(Random(3))
                play(state)
                advance(FADE_IN_MS / 2L)
                val fading = state.fade.value
                withClue("fading in, was $fading") {
                    fading shouldBeGreaterThan 0f
                    fading shouldBeLessThan 1f
                }
                advance(FADE_IN_MS.toLong())
                state.fade.value shouldBe (1f plusOrMinus 0f)
            }

            test("after a minute it fades out in 900 ms moves while unseen and fades back in over 1200 ms") {
                val state = ClockSceneState(Random(3))
                play(state)
                advance(FADE_IN_MS + 100L)
                val first = state.spot

                advance(MOVE_MS - 200)
                withClue("still in place just before the minute") { state.spot shouldBe first }
                state.fade.value shouldBe (1f plusOrMinus 0f)

                advance(200L + FADE_OUT_MS / 2)
                withClue("fading out") { state.fade.value shouldBeIn 0.01f..0.99f }
                withClue("not moved while visible") { state.spot shouldBe first }

                advance(FADE_OUT_MS.toLong())
                withClue("moved once the fade-out ended") { state.spot shouldNotBe first }
                state.fade.value shouldBeLessThan 1f

                advance(FADE_IN_MS + 100L)
                state.fade.value shouldBe (1f plusOrMinus 0f)
            }

            test("the glow walks to the next palette colour in 16 steps of half a second") {
                val state = ClockSceneState(Random(3))
                val start = state.glow
                val next = ScreensaverGlows[(ScreensaverGlows.indexOf(start) + 1) % ScreensaverGlows.size]
                play(state)
                advance(FADE_IN_MS + MOVE_MS)
                withClue("steps are 500 ms apart: none before the first") { state.glow shouldBe start }

                advance(GLOW_STEP_MS + 100)
                val firstStep = state.glow
                firstStep shouldNotBe start
                firstStep shouldNotBe next

                advance(GLOW_STEP_MS * GLOW_STEPS)
                state.glow shouldBe next
            }

            test("the shape takes its next turn over the clock's fade-out and fade-in and then rests") {
                val state = ClockSceneState(Random(3))
                val first = state.turn
                play(state, cycleGlow = false, shapes = ShapeChange.Animate)
                advance(minute - 100)
                state.turn shouldBe first
                state.change.value shouldBe (1f plusOrMinus 0f)

                advance(100L + FADE_OUT_MS / 2)
                state.turn shouldBe first + 1
                withClue("leaving") { state.change.value shouldBeIn 0.01f..0.42f }
                advance(FADE_OUT_MS.toLong())
                withClue("arriving") { state.change.value shouldBeIn 0.44f..0.99f }

                advance(FADE_IN_MS.toLong())
                state.change.value shouldBe (1f plusOrMinus 0f)
                advance(MOVE_MS - 1_000)
                withClue("at rest until the next minute") {
                    state.turn shouldBe first + 1
                    state.change.value shouldBe (1f plusOrMinus 0f)
                }
            }

            test("with reduced motion the shape changes in seven steps of 300 ms and holds between them") {
                val state = ClockSceneState(Random(3))
                play(state, cycleGlow = false, shapes = ShapeChange.Step)
                // The frame clock rounds each advance up to a whole frame, so each sample is taken at a time from the start.
                val start = compose.mainClock.currentTime + minute

                fun at(ms: Long) = compose.mainClock.advanceTimeBy(start + ms - compose.mainClock.currentTime, ignoreFrameDuration = true)
                val seen = mutableListOf<Float>()
                for (step in 1..SHAPE_STEPS) {
                    at(SHAPE_STEP_MS * (step - 1) + 100)
                    val before = state.change.value
                    at(SHAPE_STEP_MS * step - 50)
                    withClue("held inside step $step") { state.change.value shouldBe before }
                    at(SHAPE_STEP_MS * step + 50)
                    seen += state.change.value
                }
                seen shouldBe (1..SHAPE_STEPS).map { it / SHAPE_STEPS.toFloat() }
            }

            test("with animations off the shape takes its next turn at once where the clock moves") {
                val state = ClockSceneState(Random(3))
                val first = state.turn
                play(state, cycleGlow = false, shapes = ShapeChange.Cut)
                advance(FADE_IN_MS + 100L)
                val spot = state.spot
                advance(MOVE_MS - 100 + FADE_OUT_MS / 2L)
                withClue("not before the clock moves") { state.turn shouldBe first }
                advance(FADE_OUT_MS.toLong())
                state.spot shouldNotBe spot
                state.turn shouldBe first + 1
                withClue("never part way") { state.change.value shouldBe (1f plusOrMinus 0f) }
            }

            test("without a shape the turn never changes") {
                val state = ClockSceneState(Random(3))
                val first = state.turn
                play(state, cycleGlow = false)

                advance(MOVE_MS * 3)

                state.turn shouldBe first
            }

            test("without the glow the colour never changes") {
                val state = ClockSceneState(Random(3))
                val start = state.glow
                play(state, cycleGlow = false)

                advance(MOVE_MS * 3)

                state.glow shouldBe start
            }
        }
    })
