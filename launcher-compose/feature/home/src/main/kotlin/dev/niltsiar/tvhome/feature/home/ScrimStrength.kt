package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.lerp

private const val FIXED_UNTIL_LIGHTNESS = 60f
private const val STRONGEST_FROM_LIGHTNESS = 80f

/** How dark the two scrims over the wallpaper are: black at [top] alpha from the top edge, at [left] from the left edge. */
@Immutable
internal data class ScrimStrength(
    val top: Float,
    val left: Float,
) {
    companion object {
        val Fixed = ScrimStrength(top = 0.55f, left = 0.40f)
        val Strongest = ScrimStrength(top = 0.70f, left = 0.60f)
    }
}

/**
 * The scrims for a wallpaper whose 90th-percentile CIE L* behind the header is [lightness]: [ScrimStrength.Fixed] up to
 * L* 60, [ScrimStrength.Strongest] from L* 80, and in between a straight line from one to the other.
 */
internal fun scrimFor(lightness: Float): ScrimStrength {
    val t = ((lightness - FIXED_UNTIL_LIGHTNESS) / (STRONGEST_FROM_LIGHTNESS - FIXED_UNTIL_LIGHTNESS)).coerceIn(0f, 1f)
    return ScrimStrength(
        top = lerp(ScrimStrength.Fixed.top, ScrimStrength.Strongest.top, t),
        left = lerp(ScrimStrength.Fixed.left, ScrimStrength.Strongest.left, t),
    )
}
