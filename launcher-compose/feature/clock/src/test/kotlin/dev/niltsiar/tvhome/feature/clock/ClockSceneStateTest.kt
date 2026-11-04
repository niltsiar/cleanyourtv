package dev.niltsiar.tvhome.feature.clock

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ClockSceneStateTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun play(
        state: ClockSceneState,
        cycleGlow: Boolean = true,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent { LaunchedEffect(state) { state.run(cycleGlow) } }
    }

    private fun advance(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    @Test
    fun the_clock_fades_in_over_1200_ms() {
        val state = ClockSceneState(Random(3))
        play(state)
        advance(FADE_IN_MS / 2L)
        val fading = state.fade.value
        assertTrue("fading in, was $fading", fading > 0f && fading < 1f)
        advance(FADE_IN_MS.toLong())
        assertEquals(1f, state.fade.value, 0f)
    }

    @Test
    fun after_a_minute_it_fades_out_in_900_ms_moves_while_unseen_and_fades_back_in_over_1200_ms() {
        val state = ClockSceneState(Random(3))
        play(state)
        advance(FADE_IN_MS + 100L)
        val first = state.spot

        advance(MOVE_MS - 200)
        assertEquals("still in place just before the minute", first, state.spot)
        assertEquals(1f, state.fade.value, 0f)

        advance(200L + FADE_OUT_MS / 2)
        assertTrue("fading out", state.fade.value in 0.01f..0.99f)
        assertEquals("not moved while visible", first, state.spot)

        advance(FADE_OUT_MS.toLong())
        assertNotEquals("moved once the fade-out ended", first, state.spot)
        assertTrue(state.fade.value < 1f)

        advance(FADE_IN_MS + 100L)
        assertEquals(1f, state.fade.value, 0f)
    }

    @Test
    fun the_glow_walks_to_the_next_palette_colour_in_16_steps_of_half_a_second() {
        val state = ClockSceneState(Random(3))
        val start = state.glow
        val next = GlowPalette[(GlowPalette.indexOf(start) + 1) % GlowPalette.size]
        play(state)
        advance(FADE_IN_MS + MOVE_MS)
        assertEquals("steps are 500 ms apart: none before the first", start, state.glow)

        advance(GLOW_STEP_MS + 100)
        val firstStep = state.glow
        assertNotEquals(start, firstStep)
        assertNotEquals(next, firstStep)

        advance(GLOW_STEP_MS * GLOW_STEPS)
        assertEquals(next, state.glow)
    }

    @Test
    fun without_the_glow_the_colour_never_changes() {
        val state = ClockSceneState(Random(3))
        val start = state.glow
        play(state, cycleGlow = false)

        advance(MOVE_MS * 3)

        assertEquals(start, state.glow)
    }
}
