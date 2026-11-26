package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset
import dev.niltsiar.tvhome.core.config.Look

/**
 * The theme's say over motion: `animations`, `reduced_motion` and `focus_overshoot` of the config, read from [TvTheme.motion].
 * With [animations] off nothing moves: values jump. With [reducedMotion] every animation is a tween of at most
 * [REDUCED_MS], with no overshoot, and endless loops stand still ([animated] is false). Focus moves in [FOCUS_MS].
 * With [focusOvershoot] and [animated], gaining focus pops instead: the focus progress eases to 1.8 in 120 ms and back
 * to 1 by 320 ms, so the item grows 1.8 times its growth before it settles. Losing focus is the [FOCUS_MS] tween in every
 * case, with no undershoot. Each design-system animation takes its spec from one of the factories here.
 *
 * In [Look.Expressive] the springs, while [animated], are the Material 3 motion schemes': sizes and places move on
 * [MotionScheme.expressive]'s spatial springs, colours and alphas on its effects springs, which never overshoot, and a
 * press on [MotionScheme.standard]'s fast spatial spring. Gaining focus is a critically damped spring at the fast spatial
 * stiffness, or the fast spatial spring itself when it pops, whose peak stays under the pop's of [Look.Glass], so
 * [focusPeakScale] holds in both looks. [TvTheme.motion] gives the window's look.
 */
@Immutable
public data class Motion(
    public val animations: Boolean = true,
    public val reducedMotion: Boolean = false,
    public val focusOvershoot: Boolean = false,
    public val look: Look = Look.Glass,
) {
    private val expressive: Boolean get() = look == Look.Expressive && animated

    /** Springs and endless loops run. */
    public val animated: Boolean get() = animations && !reducedMotion

    /** Gaining focus pops: [focusOvershoot] with [animated]. */
    public val pops: Boolean get() = focusOvershoot && animated

    /**
     * How a tile's scale and ring follow focus, from the progress [from] it has now: to 1 when [focused], to 0 when not.
     * The pop's first keyframe is [from] because a keyframe spec eases its first stretch only from a keyframe at 0.
     */
    public fun focusSpec(
        focused: Boolean,
        from: Float,
    ): FiniteAnimationSpec<Float> =
        when {
            focused && expressive && pops -> {
                MotionScheme.expressive().fastSpatialSpec()
            }

            focused && expressive -> {
                spring(Spring.DampingRatioNoBouncy, MotionScheme.expressive().fastSpatialSpec<Float>().stiffness())
            }

            focused && pops -> {
                keyframes {
                    durationMillis = OVERSHOOT_MS
                    from at 0 using FastOutSlowInEasing
                    OVERSHOOT_PEAK at OVERSHOOT_PEAK_MS using FastOutSlowInEasing
                }
            }

            else -> {
                tweenSpec(FOCUS_MS)
            }
        }

    /** The most a tile grown to [target] draws: [focusScale], or at the peak of the pop of [focusSpec] when it pops. */
    public fun focusPeakScale(target: Float): Float = if (pops) 1f + (target - 1f) * OVERSHOOT_PEAK else focusScale(target)

    /** How a lazy list moves an item to its new place; null leaves it jumping. */
    public val placementSpec: FiniteAnimationSpec<IntOffset>?
        get() =
            when {
                !animations -> null
                reducedMotion -> tween(REDUCED_MS)
                expressive -> MotionScheme.expressive().defaultSpatialSpec<IntOffset>().withThreshold(IntOffset.VisibilityThreshold)
                else -> spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
            }

    /** The size a focused tile grows to: [target], or 1 with animations off. */
    public fun focusScale(target: Float): Float = if (animations) target else 1f

    /** A spring, or its stand-in under [reducedMotion] and [animations] off. */
    public fun <T> springSpec(
        dampingRatio: Float,
        stiffness: Float,
    ): FiniteAnimationSpec<T> =
        when {
            !animations -> snap()
            reducedMotion -> tween(REDUCED_MS)
            else -> spring(dampingRatio, stiffness)
        }

    /** A tween of [durationMillis], cut to [REDUCED_MS] under [reducedMotion], or a jump with animations off. */
    public fun <T> tweenSpec(durationMillis: Int): FiniteAnimationSpec<T> =
        when {
            !animations -> snap()
            reducedMotion -> tween(minOf(durationMillis, REDUCED_MS))
            else -> tween(durationMillis)
        }

    /** How a [FocusPill] fills as it gains focus and empties as it loses it. */
    public fun pillFillSpec(): FiniteAnimationSpec<Float> =
        if (expressive) MotionScheme.expressive().fastEffectsSpec() else tweenSpec(PILL_FILL_MS)

    /** Each half of a [pressDip], down and back up. With animations off the dip jumps instead and takes no spec. */
    public fun pressDipSpec(): FiniteAnimationSpec<Float> =
        if (expressive) MotionScheme.standard().fastSpatialSpec() else tween(PRESS_DIP_MS)

    /** How a [GlassMenu] or a [GlassDropdown] rises, grows and fades in. */
    public fun menuEnterSpec(): FiniteAnimationSpec<Float> =
        if (expressive) {
            MotionScheme.expressive().defaultSpatialSpec()
        } else {
            springSpec(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
        }

    /** How a [GlassIconButton] with a focus label grows into its pill as it gains focus, and back. */
    public fun buttonGrowSpec(): FiniteAnimationSpec<Float> =
        if (expressive) {
            MotionScheme.expressive().defaultSpatialSpec()
        } else {
            springSpec(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
        }

    /** How an offered [GlassIconButton] grows into its pill and back: [buttonGrowSpec] when [animated], else a jump. */
    public fun offerGrowSpec(): FiniteAnimationSpec<Float> = if (animated) buttonGrowSpec() else snap()

    /** How a [GlassIconButton]'s icon follows its focus, over [ICON_MS]. */
    public fun buttonIconSpec(): FiniteAnimationSpec<Float> = tweenSpec(ICON_MS)

    /** How a [GlassIconButton] takes on the look of an open panel, and drops it. */
    public fun buttonActiveSpec(): FiniteAnimationSpec<Float> =
        if (expressive) MotionScheme.expressive().fastEffectsSpec() else tweenSpec(BUTTON_ACTIVE_MS)

    /** One pulse of an offered [GlassIconButton]'s rim, which runs only when [animated]. */
    public fun offerPulseSpec(): FiniteAnimationSpec<Float> = tween(OFFER_PULSE_MS, easing = LinearEasing)

    /** How an [OverlayHost]'s scrim fades in as an overlay opens ([shown]), and out as it closes. */
    public fun scrimSpec(shown: Boolean): FiniteAnimationSpec<Float> =
        when {
            !expressive -> tweenSpec(if (shown) SCRIM_IN_MS else SCRIM_OUT_MS)
            shown -> MotionScheme.expressive().defaultEffectsSpec()
            else -> MotionScheme.expressive().fastEffectsSpec()
        }

    /** How the hole of a look flip grows over the page: in the look it reveals, over about [FLIP_MS]. */
    public fun flipSpec(): FiniteAnimationSpec<Float> = if (expressive) MotionScheme.expressive().slowSpatialSpec() else tweenSpec(FLIP_MS)

    public companion object {
        /**
         * How the dock's edge light catches up with a newly focused tile ([DockEdgeLight]) in [look], whatever the motion
         * keys: in [Look.Expressive] on the default spatial spring, which overshoots.
         */
        public fun dockLightFollowSpec(look: Look = Look.Glass): FiniteAnimationSpec<Float> =
            when (look) {
                Look.Glass -> spring(DOCK_LIGHT_DAMPING, DOCK_LIGHT_STIFFNESS)
                Look.Expressive -> MotionScheme.expressive().defaultSpatialSpec()
            }

        /** How the dock's edge light fades in and out ([DockEdgeLight.show]), whatever the motion keys. */
        public fun dockLightFadeSpec(): FiniteAnimationSpec<Float> = tween(DOCK_LIGHT_FADE_MS)

        /** One pass of a [MarqueeLine], [durationMillis] long at its steady speed, whatever the motion keys. */
        public fun marqueeSpec(durationMillis: Int): FiniteAnimationSpec<Float> = tween(durationMillis, easing = LinearEasing)

        /** The longest any reduced-motion animation runs, in milliseconds. */
        public const val REDUCED_MS: Int = 90

        /** How long a focused tile takes to grow, in milliseconds. */
        public const val FOCUS_MS: Int = 160

        /** How long a button's icon moves when the button takes focus, in milliseconds, with the default easing. */
        public const val ICON_MS: Int = 750

        /** How far past 1 the focus progress of a pop goes, as a share of the growth. */
        private const val OVERSHOOT_PEAK = 1.8f

        /** When the pop of [focusSpec] peaks, and when it settles, in milliseconds. */
        private const val OVERSHOOT_PEAK_MS = 120
        private const val OVERSHOOT_MS = 320

        private const val PILL_FILL_MS = 120

        /** How long each half of a press dip takes, in milliseconds. */
        internal const val PRESS_DIP_MS: Int = 80
        private const val BUTTON_ACTIVE_MS = 120

        /** How long an offered button's rim takes to light and fade, in milliseconds. */
        internal const val OFFER_PULSE_MS: Int = 600
        private const val SCRIM_IN_MS = 160
        private const val SCRIM_OUT_MS = 120
        private const val DOCK_LIGHT_DAMPING = 1f
        private const val DOCK_LIGHT_STIFFNESS = 500f
        private const val DOCK_LIGHT_FADE_MS = 160

        /** How long a look flip takes, in milliseconds. */
        public const val FLIP_MS: Int = 500
    }
}

/** The stiffness of a spring spec from a [MotionScheme]. */
private fun FiniteAnimationSpec<*>.stiffness(): Float = (this as SpringSpec<*>).stiffness

/** This scheme spring with [threshold] as its visibility threshold. */
private fun <T> FiniteAnimationSpec<T>.withThreshold(threshold: T): FiniteAnimationSpec<T> =
    (this as SpringSpec<T>).let { spring(it.dampingRatio, it.stiffness, threshold) }
