package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.IntOffset
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.Look
import io.kotest.assertions.assertSoftly
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

    /** A spec as the values that make it, so that a failure names them. */
    fun FiniteAnimationSpec<*>.values(): String =
        when (this) {
            is TweenSpec<*> -> {
                val easing =
                    when (easing) {
                        FastOutSlowInEasing -> "fast out slow in"
                        LinearEasing -> "linear"
                        else -> "$easing"
                    }
                "tween of $durationMillis ms after $delay ms, $easing"
            }

            is SpringSpec<*> -> {
                "spring of damping $dampingRatio, stiffness $stiffness, visibility threshold $visibilityThreshold"
            }

            is SnapSpec<*> -> {
                "snap after $delay ms"
            }

            else -> {
                "$this"
            }
        }

    fun tween(ms: Int) = "tween of $ms ms after 0 ms, fast out slow in"

    fun linear(ms: Int) = "tween of $ms ms after 0 ms, linear"

    fun spring(
        damping: Float,
        stiffness: Float,
    ) = "spring of damping $damping, stiffness $stiffness, visibility threshold null"

    val snap = "snap after 0 ms"
    val motions =
        listOf(
            "Glass" to Motion(),
            "reduced motion" to Motion(reducedMotion = true),
            "animations off" to Motion(animations = false),
        )

    /** A spec factory, and the spec it gives today with each of [motions] in turn. */
    class Factory(
        val name: String,
        val spec: (Motion) -> FiniteAnimationSpec<*>,
        val today: List<String>,
    )

    val factories =
        listOf(
            Factory("focus in", { it.focusSpec(focused = true, from = 0f) }, listOf(tween(160), tween(90), snap)),
            Factory("focus out", { it.focusSpec(focused = false, from = 1f) }, listOf(tween(160), tween(90), snap)),
            Factory("pill fill", { it.pillFillSpec() }, listOf(tween(120), tween(90), snap)),
            Factory("press dip", { it.pressDipSpec() }, List(3) { tween(80) }),
            Factory("menu enter", { it.menuEnterSpec() }, listOf(spring(0.75f, 1500f), tween(90), snap)),
            Factory("button grow", { it.buttonGrowSpec() }, listOf(spring(0.8f, 1500f), tween(90), snap)),
            Factory("offer grow", { it.offerGrowSpec() }, listOf(spring(0.8f, 1500f), snap, snap)),
            Factory("button icon", { it.buttonIconSpec() }, listOf(tween(750), tween(90), snap)),
            Factory("button active", { it.buttonActiveSpec() }, listOf(tween(120), tween(90), snap)),
            Factory("offer pulse", { it.offerPulseSpec() }, List(3) { linear(600) }),
            Factory("scrim in", { it.scrimSpec(shown = true) }, listOf(tween(160), tween(90), snap)),
            Factory("scrim out", { it.scrimSpec(shown = false) }, listOf(tween(120), tween(90), snap)),
            Factory("dock light follow", { it.dockLightFollowSpec() }, List(3) { spring(1f, 500f) }),
            Factory("dock light fade", { Motion.dockLightFadeSpec() }, List(3) { tween(160) }),
            Factory("marquee pass", { Motion.marqueeSpec(1_000) }, List(3) { linear(1_000) }),
        )

    for (factory in factories) {
        test("the ${factory.name} spec is today's, with reduced motion and with animations off too") {
            assertSoftly {
                motions.zip(factory.today).forEach { (motion, today) ->
                    withClue(motion.first) { factory.spec(motion.second).values() shouldBe today }
                }
            }
        }
    }

    val expressiveMotions =
        listOf(
            "Expressive" to Motion(look = Look.Expressive),
            "Expressive with reduced motion" to Motion(reducedMotion = true, look = Look.Expressive),
            "Expressive with animations off" to Motion(animations = false, look = Look.Expressive),
        )
    val expressiveFactories =
        listOf(
            Factory("focus in", { it.focusSpec(focused = true, from = 0f) }, listOf(spring(1f, 800f), tween(90), snap)),
            Factory("focus out", { it.focusSpec(focused = false, from = 1f) }, listOf(tween(160), tween(90), snap)),
            Factory("pill fill", { it.pillFillSpec() }, listOf(spring(1f, 3800f), tween(90), snap)),
            Factory("press dip", { it.pressDipSpec() }, listOf(spring(0.9f, 1400f), tween(80), tween(80))),
            Factory("menu enter", { it.menuEnterSpec() }, listOf(spring(0.8f, 380f), tween(90), snap)),
            Factory("button grow", { it.buttonGrowSpec() }, listOf(spring(0.8f, 380f), tween(90), snap)),
            Factory("offer grow", { it.offerGrowSpec() }, listOf(spring(0.8f, 380f), snap, snap)),
            Factory("button icon", { it.buttonIconSpec() }, listOf(tween(750), tween(90), snap)),
            Factory("button active", { it.buttonActiveSpec() }, listOf(spring(1f, 3800f), tween(90), snap)),
            Factory("offer pulse", { it.offerPulseSpec() }, List(3) { linear(600) }),
            Factory("scrim in", { it.scrimSpec(shown = true) }, listOf(spring(1f, 1600f), tween(90), snap)),
            Factory("scrim out", { it.scrimSpec(shown = false) }, listOf(spring(1f, 3800f), tween(90), snap)),
            Factory("dock light follow", { it.dockLightFollowSpec() }, listOf(spring(0.8f, 380f), snap, snap)),
        )

    for (factory in expressiveFactories) {
        test("in Expressive the ${factory.name} spec is the motion scheme's, and Glass's stand-in with reduced motion or animations off") {
            assertSoftly {
                expressiveMotions.zip(factory.today).forEach { (motion, expected) ->
                    withClue(motion.first) { factory.spec(motion.second).values() shouldBe expected }
                }
            }
        }
    }

    test("in Expressive a pop is the fast spatial spring, which peaks under the Glass pop, so the room for it holds") {
        val motion = Motion(focusOvershoot = true, look = Look.Expressive)
        val values = samples(motion.focusSpec(focused = true, from = 0f), 0f, 1f, lastMs = 1_000)

        motion.focusSpec(focused = true, from = 0f).values() shouldBe spring(0.6f, 800f)
        withClue("peak ${values.max()}") { values.max() shouldBeGreaterThan 1f }
        val peakScale = 1f + 0.08f * values.max()
        peakScale shouldBeLessThanOrEqual motion.focusPeakScale(1.08f)
        motion.focusPeakScale(1.08f) shouldBe Motion(focusOvershoot = true).focusPeakScale(1.08f)
    }

    test("in Expressive a lazy list moves its items on the default spatial spring") {
        val spec = Motion(look = Look.Expressive).placementSpec.shouldBeInstanceOf<SpringSpec<IntOffset>>()

        spec.dampingRatio to spec.stiffness shouldBe (0.8f to 380f)
        spec.visibilityThreshold shouldBe IntOffset.VisibilityThreshold
    }
}
