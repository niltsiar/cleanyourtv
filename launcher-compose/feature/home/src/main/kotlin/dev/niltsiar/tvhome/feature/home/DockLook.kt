package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.config.TvConfig

/**
 * The dock panel's look, from the theme: whether it is drawn at all ([band]), the percent [opacity] that sets its tint
 * (null: a tint that lightens on a bright wallpaper), [glass] for the light tint whatever the TV's night mode, and the
 * [edgeLight] of the focused tile on its rim and floor.
 */
@Immutable
public data class DockLook(
    val band: Boolean = true,
    val opacity: Int? = null,
    val glass: Boolean = false,
    val edgeLight: Boolean = true,
)

public fun TvConfig.dockLook(): DockLook =
    DockLook(
        band = dockBand,
        opacity = dockOpacity,
        glass = dockGlass,
        edgeLight = dockEdgeLight && dockBand && animations && !reducedMotion,
    )

private val DarkTint = Color(0xFF0B0D12)
private val LightTint = Color(0xFFF4F6FA)
private const val DARK_ALPHA = 0.32f
private const val LIGHT_ALPHA = 0.36f
private const val OPACITY_SCALE = 0.85f
private const val BRIGHT_BOOST = 0.10f
private const val DIM_LIGHTNESS = 60f
private const val BRIGHT_LIGHTNESS = 75f

/**
 * The tint over the dock's frost. [darkSurfaces] is the TV's night mode; [lightness] is the mean L* of the frost under
 * the dock. With an [opacity] the tint is 0.85 times its percent. Without, it is 0.32 (dark) or 0.36 (light), and
 * rises by 0.10 as the wallpaper under the dock goes from L* 60 to L* 75, so a bright picture does not wash the panel out.
 */
internal fun DockLook.tint(
    darkSurfaces: Boolean,
    lightness: Float,
): Color {
    val light = glass || !darkSurfaces
    val alpha =
        opacity?.let { OPACITY_SCALE * it / PERCENT }
            ?: (
                (if (light) LIGHT_ALPHA else DARK_ALPHA) +
                    BRIGHT_BOOST * ((lightness - DIM_LIGHTNESS) / (BRIGHT_LIGHTNESS - DIM_LIGHTNESS)).coerceIn(0f, 1f)
            )
    return (if (light) LightTint else DarkTint).copy(alpha = alpha)
}

private const val PERCENT = 100f
