package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset

/**
 * The theme's say over motion: `animations`, `reduced_motion` and `focus_overshoot` of the config, read from [TvTheme.motion].
 * With [animations] off nothing moves: values jump. With [reducedMotion] every animation is a tween of at most
 * [REDUCED_MS], with no overshoot, and endless loops stand still ([animated] is false). Focus moves in [FOCUS_MS];
 * [focusOvershoot] makes it a spring that passes its target before it settles.
 */
@Immutable
public data class Motion(
    public val animations: Boolean = true,
    public val reducedMotion: Boolean = false,
    public val focusOvershoot: Boolean = false,
) {
    /** Springs and endless loops run. */
    public val animated: Boolean get() = animations && !reducedMotion

    /** How a focused tile's scale and ring follow focus. */
    public val focusSpec: FiniteAnimationSpec<Float>
        get() = if (focusOvershoot) springSpec(OVERSHOOT_DAMPING, Spring.StiffnessMedium) else tweenSpec(FOCUS_MS)

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

        private const val OVERSHOOT_DAMPING = 0.5f
    }
}
