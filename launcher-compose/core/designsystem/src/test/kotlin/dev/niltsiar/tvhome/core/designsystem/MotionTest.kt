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
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

val MotionTest by testSuite {
    fun SpringSpec<*>.shape() = dampingRatio to stiffness

    /** The value of [spec] going from [from] to [to], at each millisecond from 0 to [lastMs]. */
    fun samples(
        spec: FiniteAnimationSpec<Float>,
        from: Float,
        to: Float,
        lastMs: Int,
    ): List<Float> {
        val animation = TargetBasedAnimation(spec, Float.VectorConverter, from, to)
        return List(lastMs + 1) { animation.getValueFromNanos(it * 1_000_000L) }
    }

    test("a focused tile grows to its scale in 160 ms by default") {
        val motion = Motion()

        motion.focusScale(1.08f) shouldBe (1.08f plusOrMinus 0f)
        (motion.focusSpec(focused = true, from = 0f) as TweenSpec<Float>).durationMillis shouldBe 160
        (motion.focusSpec(focused = false, from = 1f) as TweenSpec<Float>).durationMillis shouldBe 160
        motion.focusPeakScale(1.08f) shouldBe (1.08f plusOrMinus 0f)
    }

    test("with focus overshoot focus in peaks at 1.8 at 120 ms and settles at 1 at 320 ms") {
        val motion = Motion(focusOvershoot = true)
        val values = samples(motion.focusSpec(focused = true, from = 0f), 0f, 1f, lastMs = 400)

        values[120] shouldBe (1.8f plusOrMinus 0.001f)
        values.max() shouldBe (1.8f plusOrMinus 0.001f)
        values[320] shouldBe (1f plusOrMinus 0f)
        values[400] shouldBe (1f plusOrMinus 0f)
        withClue("half way to the peak at ${values[60]}, linear would be 0.9") { values[60] shouldBeGreaterThan 0.9f }
        motion.focusPeakScale(1.08f) shouldBe (1.144f plusOrMinus 0.0001f)
        motion.focusScale(1.08f) shouldBe (1.08f plusOrMinus 0f)
    }

    test("with focus overshoot focus in starts from the progress it has") {
        val values = samples(Motion(focusOvershoot = true).focusSpec(focused = true, from = 0.4f), 0.4f, 1f, lastMs = 320)

        values[0] shouldBe (0.4f plusOrMinus 0f)
        withClue("step at 1 ms: ${values[1]}") { values[1] - values[0] shouldBeLessThan 0.05f }
        values[120] shouldBe (1.8f plusOrMinus 0.001f)
    }

    test("with focus overshoot focus out from the peak never goes below 0 and ends at 0 in 160 ms") {
        val values = samples(Motion(focusOvershoot = true).focusSpec(focused = false, from = 1.8f), 1.8f, 0f, lastMs = 200)

        withClue("lowest ${values.min()}") { values.min() shouldBeGreaterThanOrEqual 0f }
        values[160] shouldBe (0f plusOrMinus 0f)
    }

    test("without animations a focused tile does not grow and focus jumps") {
        val motion = Motion(animations = false)

        motion.focusScale(1.1f) shouldBe (1f plusOrMinus 0f)
        motion.focusSpec(focused = true, from = 0f).shouldBeInstanceOf<SnapSpec<*>>()
        Motion(animations = false, focusOvershoot = true).focusSpec(focused = true, from = 0f).shouldBeInstanceOf<SnapSpec<*>>()
        Motion(animations = false, focusOvershoot = true).focusPeakScale(1.08f) shouldBe (1f plusOrMinus 0f)
        motion.springSpec<Float>(0.75f, Spring.StiffnessMedium).shouldBeInstanceOf<SnapSpec<*>>()
        motion.tweenSpec<Float>(140).shouldBeInstanceOf<SnapSpec<*>>()
        motion.placementSpec.shouldBeNull()
    }

    test("with reduced motion focus is a 90 ms tween with no overshoot and the tile still grows") {
        val motion = Motion(reducedMotion = true)

        motion.focusScale(1.1f) shouldBe (1.1f plusOrMinus 0f)
        (motion.focusSpec(focused = true, from = 0f) as TweenSpec<Float>).durationMillis shouldBe 90
        (motion.springSpec<Float>(0.3f, Spring.StiffnessLow) as TweenSpec<Float>).durationMillis shouldBe 90
        (motion.placementSpec as TweenSpec<IntOffset>).durationMillis shouldBe 90
    }

    test("reduced motion drops the focus overshoot peak") {
        val motion = Motion(reducedMotion = true, focusOvershoot = true)
        val values = samples(motion.focusSpec(focused = true, from = 0f), 0f, 1f, lastMs = 120)

        withClue("peaked at ${values.max()}") { values.max() shouldBeLessThanOrEqual 1f }
        values[90] shouldBe (1f plusOrMinus 0f)
        motion.focusPeakScale(1.1f) shouldBe (1.1f plusOrMinus 0f)
    }

    test("reduced motion cuts a long tween to 90 ms and leaves a short one alone") {
        val motion = Motion(reducedMotion = true)

        (motion.tweenSpec<Float>(140) as TweenSpec<Float>).durationMillis shouldBe 90
        (motion.tweenSpec<Float>(60) as TweenSpec<Float>).durationMillis shouldBe 60
        (Motion().tweenSpec<Float>(140) as TweenSpec<Float>).durationMillis shouldBe 140
    }

    test("a lazy list places items on the stock spring by default") {
        val spec = Motion().placementSpec as SpringSpec<IntOffset>

        spec.shape() shouldBe (Spring.DampingRatioNoBouncy to Spring.StiffnessMediumLow)
        spec.visibilityThreshold shouldBe IntOffset.VisibilityThreshold
    }

    test("endless loops run only with animations on and reduced motion off") {
        Motion().animated.shouldBeTrue()
        Motion(reducedMotion = true).animated.shouldBeFalse()
        Motion(animations = false).animated.shouldBeFalse()
    }
}
