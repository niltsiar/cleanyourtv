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
                ) {
                    compose.mainClock.autoAdvance = false
                    compose.setContent { LaunchedEffect(state) { state.run(cycleGlow) } }
                }

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
                val next = GlowPalette[(GlowPalette.indexOf(start) + 1) % GlowPalette.size]
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

            test("without the glow the colour never changes") {
                val state = ClockSceneState(Random(3))
                val start = state.glow
                play(state, cycleGlow = false)

                advance(MOVE_MS * 3)

                state.glow shouldBe start
            }
        }
    })
