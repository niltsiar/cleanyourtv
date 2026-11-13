package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset

/**
 * The theme's say over motion: `animations`, `reduced_motion` and `focus_overshoot` of the config, read from [TvTheme.motion].
 * With [animations] off nothing moves: values jump. With [reducedMotion] every animation is a tween of at most
 * [REDUCED_MS], with no overshoot, and endless loops stand still ([animated] is false). Focus moves in [FOCUS_MS].
 * With [focusOvershoot] and [animated], gaining focus pops instead: the focus progress eases to 1.8 in 120 ms and back
 * to 1 by 320 ms, so the item grows 1.8 times its growth before it settles. Losing focus is the [FOCUS_MS] tween in every
 * case, with no undershoot.
 */
@Immutable
public data class Motion(
    public val animations: Boolean = true,
    public val reducedMotion: Boolean = false,
    public val focusOvershoot: Boolean = false,
) {
    /** Springs and endless loops run. */
    public val animated: Boolean get() = animations && !reducedMotion

    /**
     * How a tile's scale and ring follow focus, from the progress [from] it has now: to 1 when [focused], to 0 when not.
     * The pop's first keyframe is [from] because a keyframe spec eases its first stretch only from a keyframe at 0.
     */
    public fun focusSpec(
        focused: Boolean,
        from: Float,
    ): FiniteAnimationSpec<Float> =
        if (focused && focusOvershoot && animated) {
            keyframes {
                durationMillis = OVERSHOOT_MS
                from at 0 using FastOutSlowInEasing
                OVERSHOOT_PEAK at OVERSHOOT_PEAK_MS using FastOutSlowInEasing
            }
        } else {
            tweenSpec(FOCUS_MS)
        }

    /** The most a tile grown to [target] draws: [focusScale], or at the peak of the pop of [focusSpec] when it pops. */
    public fun focusPeakScale(target: Float): Float =
        if (focusOvershoot && animated) 1f + (target - 1f) * OVERSHOOT_PEAK else focusScale(target)

    /** How a lazy list moves an item to its new place; null leaves it jumping. */
    public val placementSpec: FiniteAnimationSpec<IntOffset>?
        get() =
            when {
                !animations -> null
                reducedMotion -> tween(REDUCED_MS)
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

    public companion object {
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
    }
}
