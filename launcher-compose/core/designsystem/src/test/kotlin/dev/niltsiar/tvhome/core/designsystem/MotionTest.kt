package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    private fun SpringSpec<*>.shape() = dampingRatio to stiffness

    @Test
    fun a_focused_tile_grows_to_its_scale_in_160_ms_by_default() {
        val motion = Motion()

        assertEquals(1.08f, motion.focusScale(1.08f), 0f)
        assertEquals(160, (motion.focusSpec as TweenSpec<Float>).durationMillis)
    }

    @Test
    fun with_focus_overshoot_the_focus_springs_past_its_scale() {
        val motion = Motion(focusOvershoot = true)

        assertEquals(1.08f, motion.focusScale(1.08f), 0f)
        assertEquals(0.5f to Spring.StiffnessMedium, (motion.focusSpec as SpringSpec<Float>).shape())
    }

    @Test
    fun without_animations_a_focused_tile_does_not_grow_and_focus_jumps() {
        val motion = Motion(animations = false)

        assertEquals(1f, motion.focusScale(1.1f), 0f)
        assertTrue(motion.focusSpec is SnapSpec)
        assertTrue(motion.springSpec<Float>(0.75f, Spring.StiffnessMedium) is SnapSpec)
        assertTrue(motion.tweenSpec<Float>(140) is SnapSpec)
        assertNull(motion.placementSpec)
    }

    @Test
    fun with_reduced_motion_focus_is_a_90_ms_tween_with_no_overshoot_and_the_tile_still_grows() {
        val motion = Motion(reducedMotion = true)

        assertEquals(1.1f, motion.focusScale(1.1f), 0f)
        assertEquals(90, (motion.focusSpec as TweenSpec<Float>).durationMillis)
        assertEquals(90, (motion.springSpec<Float>(0.3f, Spring.StiffnessLow) as TweenSpec<Float>).durationMillis)
        assertEquals(90, (motion.placementSpec as TweenSpec<IntOffset>).durationMillis)
    }

    @Test
    fun reduced_motion_cuts_a_long_tween_to_90_ms_and_leaves_a_short_one_alone() {
        val motion = Motion(reducedMotion = true)

        assertEquals(90, (motion.tweenSpec<Float>(140) as TweenSpec<Float>).durationMillis)
        assertEquals(60, (motion.tweenSpec<Float>(60) as TweenSpec<Float>).durationMillis)
        assertEquals(140, (Motion().tweenSpec<Float>(140) as TweenSpec<Float>).durationMillis)
    }

    @Test
    fun a_lazy_list_places_items_on_the_stock_spring_by_default() {
        val spec = Motion().placementSpec as SpringSpec<IntOffset>

        assertEquals(Spring.DampingRatioNoBouncy to Spring.StiffnessMediumLow, spec.shape())
        assertEquals(IntOffset.VisibilityThreshold, spec.visibilityThreshold)
    }

    @Test
    fun endless_loops_run_only_with_animations_on_and_reduced_motion_off() {
        assertTrue(Motion().animated)
        assertFalse(Motion(reducedMotion = true).animated)
        assertFalse(Motion(animations = false).animated)
    }
}
