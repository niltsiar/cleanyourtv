package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    private fun SpringSpec<*>.shape() = dampingRatio to stiffness

    /** The value of [spec] going from [from] to [to], at each millisecond from 0 to [lastMs]. */
    private fun samples(
        spec: FiniteAnimationSpec<Float>,
        from: Float,
        to: Float,
        lastMs: Int,
    ): List<Float> {
        val animation = TargetBasedAnimation(spec, Float.VectorConverter, from, to)
        return List(lastMs + 1) { animation.getValueFromNanos(it * 1_000_000L) }
    }

    @Test
    fun a_focused_tile_grows_to_its_scale_in_160_ms_by_default() {
        val motion = Motion()

        assertEquals(1.08f, motion.focusScale(1.08f), 0f)
        assertEquals(160, (motion.focusSpec(focused = true, from = 0f) as TweenSpec<Float>).durationMillis)
        assertEquals(160, (motion.focusSpec(focused = false, from = 1f) as TweenSpec<Float>).durationMillis)
        assertEquals(1.08f, motion.focusPeakScale(1.08f), 0f)
    }

    @Test
    fun with_focus_overshoot_focus_in_peaks_at_18_at_120_ms_and_settles_at_1_at_320_ms() {
        val motion = Motion(focusOvershoot = true)
        val values = samples(motion.focusSpec(focused = true, from = 0f), 0f, 1f, lastMs = 400)

        assertEquals(1.8f, values[120], 0.001f)
        assertEquals(1.8f, values.max(), 0.001f)
        assertEquals(1f, values[320], 0f)
        assertEquals(1f, values[400], 0f)
        assertTrue("half way to the peak at ${values[60]}, linear would be 0.9", values[60] > 0.9f)
        assertEquals(1.144f, motion.focusPeakScale(1.08f), 0.0001f)
        assertEquals(1.08f, motion.focusScale(1.08f), 0f)
    }

    @Test
    fun with_focus_overshoot_focus_in_starts_from_the_progress_it_has() {
        val values = samples(Motion(focusOvershoot = true).focusSpec(focused = true, from = 0.4f), 0.4f, 1f, lastMs = 320)

        assertEquals(0.4f, values[0], 0f)
        assertTrue("step at 1 ms: ${values[1]}", values[1] - values[0] < 0.05f)
        assertEquals(1.8f, values[120], 0.001f)
    }

    @Test
    fun with_focus_overshoot_focus_out_from_the_peak_never_goes_below_0_and_ends_at_0_in_160_ms() {
        val values = samples(Motion(focusOvershoot = true).focusSpec(focused = false, from = 1.8f), 1.8f, 0f, lastMs = 200)

        assertTrue("lowest ${values.min()}", values.min() >= 0f)
        assertEquals(0f, values[160], 0f)
    }

    @Test
    fun without_animations_a_focused_tile_does_not_grow_and_focus_jumps() {
        val motion = Motion(animations = false)

        assertEquals(1f, motion.focusScale(1.1f), 0f)
        assertTrue(motion.focusSpec(focused = true, from = 0f) is SnapSpec)
        assertTrue(Motion(animations = false, focusOvershoot = true).focusSpec(focused = true, from = 0f) is SnapSpec)
        assertEquals(1f, Motion(animations = false, focusOvershoot = true).focusPeakScale(1.08f), 0f)
        assertTrue(motion.springSpec<Float>(0.75f, Spring.StiffnessMedium) is SnapSpec)
        assertTrue(motion.tweenSpec<Float>(140) is SnapSpec)
        assertNull(motion.placementSpec)
    }

    @Test
    fun with_reduced_motion_focus_is_a_90_ms_tween_with_no_overshoot_and_the_tile_still_grows() {
        val motion = Motion(reducedMotion = true)

        assertEquals(1.1f, motion.focusScale(1.1f), 0f)
        assertEquals(90, (motion.focusSpec(focused = true, from = 0f) as TweenSpec<Float>).durationMillis)
        assertEquals(90, (motion.springSpec<Float>(0.3f, Spring.StiffnessLow) as TweenSpec<Float>).durationMillis)
        assertEquals(90, (motion.placementSpec as TweenSpec<IntOffset>).durationMillis)
    }

    @Test
    fun reduced_motion_drops_the_focus_overshoot_peak() {
        val motion = Motion(reducedMotion = true, focusOvershoot = true)
        val values = samples(motion.focusSpec(focused = true, from = 0f), 0f, 1f, lastMs = 120)

        assertTrue("peaked at ${values.max()}", values.max() <= 1f)
        assertEquals(1f, values[90], 0f)
        assertEquals(1.1f, motion.focusPeakScale(1.1f), 0f)
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
