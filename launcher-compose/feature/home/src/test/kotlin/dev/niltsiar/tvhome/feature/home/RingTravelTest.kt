package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.oklch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** How the focus ring's colour goes from one tile to the next. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class RingTravelTest {
    private val red = Color(0xFFFF8A8A)
    private val blue = Color(0xFF8AB4FF)
    private val green = Color(0xFF8AE6A0)

    private fun TestScope.frames() = CoroutineScope(coroutineContext + TestMonotonicFrameClock(this))

    private fun lab(color: Color): FloatArray {
        val lch = oklch(color)
        val hue = Math.toRadians(lch.h.toDouble())
        return floatArrayOf(lch.l, lch.c * cos(hue).toFloat(), lch.c * sin(hue).toFloat())
    }

    private fun assertNear(
        expected: Color,
        actual: Color,
    ) = assertTrue(
        "expected $expected, got $actual",
        listOf(
            expected.red - actual.red,
            expected.green - actual.green,
            expected.blue - actual.blue,
        ).all { abs(it) < 0.01f },
    )

    @Test
    fun the_first_ring_shows_its_own_colour_at_once() =
        runTest {
            val travel = RingTravel()

            travel.arrive("a", red, Motion(), frames())

            assertEquals(red, travel.colourFor("a", red))
        }

    @Test
    fun a_tile_that_takes_focus_from_another_starts_at_the_old_ring_and_reaches_its_own_in_220_ms() =
        runTest {
            val travel = RingTravel()
            val scope = frames()
            travel.arrive("a", red, Motion(), scope)

            travel.arrive("b", blue, Motion(), scope)

            assertNear(red, travel.colourFor("b", blue))
            assertEquals("the old tile keeps its own ring", red, travel.colourFor("a", red))
            advanceTimeBy(110)
            runCurrent()
            val middle = travel.colourFor("b", blue)
            assertNotEquals(red, middle)
            assertNotEquals(blue, middle)
            advanceTimeBy(130)
            runCurrent()
            assertEquals(blue, travel.colourFor("b", blue))
        }

    @Test
    fun the_colours_in_between_are_blended_in_oklab() =
        runTest {
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

            assertTrue("share $share", share in 0.05f..0.95f)
            for (axis in 1..2) assertEquals("axis $axis", from[axis] + (to[axis] - from[axis]) * share, middle[axis], 0.01f)
        }

    @Test
    fun a_move_in_the_middle_of_a_blend_starts_at_the_colour_the_ring_had_reached() =
        runTest {
            val travel = RingTravel()
            val scope = frames()
            travel.arrive("a", red, Motion(), scope)
            travel.arrive("b", blue, Motion(), scope)
            advanceTimeBy(80)
            runCurrent()
            val reached = travel.colourFor("b", blue)

            travel.arrive("c", green, Motion(), scope)

            assertNear(reached, travel.colourFor("c", green))
            assertNotEquals(blue, reached)
        }

    @Test
    fun a_tile_that_takes_focus_after_the_tiles_lost_it_starts_at_its_own_colour() =
        runTest {
            val travel = RingTravel()
            val scope = frames()
            travel.arrive("a", red, Motion(), scope)

            travel.reset()
            travel.arrive("b", blue, Motion(), scope)

            assertEquals(blue, travel.colourFor("b", blue))
        }

    @Test
    fun with_animations_off_the_ring_takes_its_colour_at_once() =
        runTest {
            val travel = RingTravel()
            val scope = frames()
            val still = Motion(animations = false)
            travel.arrive("a", red, still, scope)

            travel.arrive("b", blue, still, scope)
            advanceTimeBy(20)
            runCurrent()

            assertEquals(blue, travel.colourFor("b", blue))
        }

    @Test
    fun a_ring_that_is_fading_out_mid_travel_keeps_the_colour_it_had_reached() =
        runTest {
            val travel = RingTravel()
            val scope = frames()
            travel.arrive("a", red, Motion(), scope)
            travel.arrive("b", blue, Motion(), scope)
            advanceTimeBy(80)
            runCurrent()
            val reached = travel.colourFor("b", blue)

            travel.arrive("c", green, Motion(), scope)

            assertNear(reached, travel.colourFor("b", blue))
            assertNotEquals(blue, travel.colourFor("b", blue))
        }

    @Test
    fun a_ring_that_loses_focus_to_the_top_bar_mid_travel_keeps_the_colour_it_had_reached() =
        runTest {
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

    @Test
    fun a_ring_whose_travel_was_over_shows_its_own_colour_after_it_loses_focus() =
        runTest {
            val travel = RingTravel()
            val scope = frames()
            travel.arrive("a", red, Motion(), scope)
            travel.arrive("b", blue, Motion(), scope)
            advanceTimeBy(300)
            runCurrent()

            travel.arrive("c", green, Motion(), scope)

            assertEquals(blue, travel.colourFor("b", blue))
        }

    @Test
    fun with_animations_off_the_new_ring_shows_its_colour_in_the_frame_it_gets_focus() =
        runTest {
            val travel = RingTravel()
            val scope = frames()
            val still = Motion(animations = false)
            travel.arrive("a", red, still, scope)

            travel.arrive("b", blue, still, scope)

            assertEquals(blue, travel.colourFor("b", blue))
        }
}
