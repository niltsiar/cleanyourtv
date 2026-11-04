package dev.niltsiar.tvhome.feature.clock

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ClockMotionTest {
    private val screen = IntSize(1920, 1080)
    private val clock = IntSize(700, 320)

    @Test
    fun every_spot_keeps_the_whole_clock_on_screen_and_off_the_edges() {
        val random = Random(1)
        repeat(500) {
            val spot = Spot.random(random)
            assertTrue(spot.x in 0.1f..0.9f && spot.y in 0.1f..0.9f)
            val corner = screen.cornerAt(clock, spot)
            assertTrue(corner.x > 0 && corner.y > 0)
            assertTrue(corner.x + clock.width < screen.width && corner.y + clock.height < screen.height)
        }
    }

    @Test
    fun the_extreme_spots_sit_a_tenth_of_the_free_room_from_each_edge() {
        assertEquals(IntOffset(122, 76), screen.cornerAt(clock, Spot(0.1f, 0.1f)))
        assertEquals(IntOffset(1098, 684), screen.cornerAt(clock, Spot(0.9f, 0.9f)))
    }

    @Test
    fun a_clock_wider_than_the_screen_stays_at_the_origin() {
        assertEquals(IntOffset(0, 0), IntSize(500, 300).cornerAt(IntSize(600, 400), Spot(0.8f, 0.8f)))
    }

    @Test
    fun the_glow_is_centred_behind_the_clock() {
        val spot = Spot(0.5f, 0.5f)
        val corner = screen.cornerAt(clock, spot)
        assertEquals(Offset(corner.x + 350f, corner.y + 160f), screen.centerOf(clock, spot))
    }

    @Test
    fun the_glow_takes_16_steps_from_one_colour_to_the_next() {
        val from = GlowPalette[0]
        val to = GlowPalette[1]
        assertEquals(from, glowStep(from, to, 0))
        assertEquals(to, glowStep(from, to, GLOW_STEPS))
        assertTrue(glowStep(from, to, GLOW_STEPS / 2) != from && glowStep(from, to, GLOW_STEPS / 2) != to)
    }

    @Test
    fun the_glow_palette_is_the_reference_launchers_five_colours() {
        assertEquals(
            listOf(0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C).map { Color(it) },
            GlowPalette,
        )
    }
}
