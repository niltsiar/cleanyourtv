package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class WifiLightTest {
    private val parts = 0..2

    @Test
    fun all_three_parts_are_lit_at_both_ends_of_the_run() {
        for (part in parts) {
            assertEquals(1f, wifiLight(0f, part), 0f)
            assertEquals(1f, wifiLight(1f, part), 0f)
        }
    }

    @Test
    fun all_three_parts_fade_down_together_before_any_lights_again() {
        val early = parts.map { wifiLight(0.15f, it) }
        assertTrue(early.all { it < 1f })
        assertEquals(1, early.distinct().size)
        for (part in parts) assertEquals(0.2f, wifiLight(0.23f, part), 0f)
    }

    @Test
    fun the_dot_then_the_inner_arc_then_the_outer_arc_light_in_turn() {
        val lit = { part: Int -> (0..100).first { wifiLight(it / 100f, part) > 0.9f && it > 25 } }

        assertTrue(lit(0) < lit(1))
        assertTrue(lit(1) < lit(2))
    }

    @Test
    fun each_part_only_brightens_once_it_has_started_to_light() {
        for (part in parts) {
            val values = (24..100).map { wifiLight(it / 100f, part) }
            assertEquals(values.sorted(), values)
        }
    }

    @Test
    fun the_inputs_arrow_slides_out_and_back_once() {
        assertEquals(0f, inputsArrowSlide(0f), 1e-6f)
        assertEquals(1f, inputsArrowSlide(0.5f), 1e-6f)
        assertEquals(0f, inputsArrowSlide(1f), 1e-6f)
        assertTrue(inputsArrowSlide(0.25f) in 0.1f..0.9f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class FocusTimelineTest {
    @get:Rule
    val compose = createComposeRule()

    private val source = MutableInteractionSource()
    private val focus = FocusInteraction.Focus()
    private lateinit var value: () -> Float

    private fun show(animated: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val timeline = rememberFocusTimeline(source, durationMs = 1_000, animated = animated)
            SideEffect { value = { timeline.value } }
        }
        compose.mainClock.advanceTimeBy(50)
    }

    private fun emit(interaction: Interaction) {
        runBlocking { source.emit(interaction) }
        compose.mainClock.advanceTimeBy(20)
    }

    @Test
    fun it_rests_at_one_and_runs_from_zero_to_one_when_focus_arrives() {
        show()
        assertEquals(1f, value(), 0f)

        emit(focus)
        compose.mainClock.advanceTimeBy(480)
        assertEquals(0.5f, value(), 0.1f)

        compose.mainClock.advanceTimeBy(600)
        assertEquals(1f, value(), 0f)
    }

    @Test
    fun losing_focus_mid_run_settles_to_one_without_going_back() {
        show()
        emit(focus)
        compose.mainClock.advanceTimeBy(300)
        val before = value()

        emit(FocusInteraction.Unfocus(focus))
        val seen = mutableListOf<Float>()
        repeat(20) {
            compose.mainClock.advanceTimeBy(20)
            seen += value()
        }

        assertTrue(before < 1f)
        assertEquals(seen.sorted(), seen)
        assertEquals(1f, seen.last(), 0f)
    }

    @Test
    fun losing_focus_after_the_run_replays_nothing() {
        show()
        emit(focus)
        compose.mainClock.advanceTimeBy(1_100)

        emit(FocusInteraction.Unfocus(focus))
        compose.mainClock.advanceTimeBy(300)

        assertEquals(1f, value(), 0f)
    }

    @Test
    fun it_stays_at_one_with_animations_off() {
        show(animated = false)

        emit(focus)
        compose.mainClock.advanceTimeBy(300)

        assertEquals(1f, value(), 0f)
    }
}
