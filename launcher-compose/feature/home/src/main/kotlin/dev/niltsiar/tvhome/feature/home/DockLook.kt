package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlin.math.pow

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
private const val DARK_ALPHA = 0.16f
private const val LIGHT_ALPHA = 0.20f
private const val OPACITY_SCALE = 0.85f
private const val BRIGHT_BOOST = 0.06f
private const val DIM_LIGHTNESS = 60f
private const val BRIGHT_LIGHTNESS = 75f

/** The most relative luminance the light tray has over any picture. */
private const val TRAY_MAX_LUMINANCE = 0.55f

/** The most the light tray lifts a dark picture's relative luminance, up to L* [DARK_UNTIL_LIGHTNESS]. */
private const val DARK_TRAY_LIFT = 0.02f
private const val DARK_UNTIL_LIGHTNESS = 30f

/** Whether the tray takes the light tint: with [DockLook.glass], or without night mode. */
internal fun DockLook.light(darkSurfaces: Boolean): Boolean = glass || !darkSurfaces

/**
 * The tint over the dock's frost, which carries the picture's colour. [darkSurfaces] is the TV's night mode; [lightness]
 * is the mean L* of the frost under the dock. With an [opacity] the tint is 0.85 times its percent. Without, it is 0.16
 * (dark) or 0.20 (light), and rises by 0.06 as the wallpaper under the dock goes from L* 60 to L* 75. A light tint
 * darkens, as far as it needs, for a tray over a bright frost to keep a mean relative luminance of at most 0.55, and
 * for a tray over a dark frost (L* 30 or less, but over 0: a lightness of 0 means no frost, which keeps the base tint) to
 * lift the frost's relative luminance by 0.02 at most, so the picture shows.
 */
internal fun DockLook.tint(
    darkSurfaces: Boolean,
    lightness: Float,
): Color {
    val light = light(darkSurfaces)
    val alpha =
        opacity?.let { OPACITY_SCALE * it / PERCENT }
            ?: (
                (if (light) LIGHT_ALPHA else DARK_ALPHA) +
                    BRIGHT_BOOST * ((lightness - DIM_LIGHTNESS) / (BRIGHT_LIGHTNESS - DIM_LIGHTNESS)).coerceIn(0f, 1f)
            )
    if (!light) return DarkTint.copy(alpha = alpha)
    return (if (opacity == null) LightTint.scaledToKeepTray(lightness, alpha) else LightTint).copy(alpha = alpha)
}

/**
 * This tint's colour dimmed so that a tray of the frost at L* [lightness] under it at [alpha] has a mean relative
 * luminance of [TRAY_MAX_LUMINANCE] at most, and over a frost darker than L* [DARK_UNTIL_LIGHTNESS] lifts the frost's by
 * [DARK_TRAY_LIFT] at most, a limit that relaxes from L* [DARK_UNTIL_LIGHTNESS] until it no longer binds at [DIM_LIGHTNESS].
 * The frost is taken as a grey of that lightness, and the channels mix on their encoded values as the screen mixes them.
 */
private fun Color.scaledToKeepTray(
    lightness: Float,
    alpha: Float,
): Color {
    val frostLuminance = luminanceOfLightness(lightness)
    val darkLift = if (lightness > 0f) DARK_TRAY_LIFT else TRAY_MAX_LUMINANCE
    val lift =
        darkLift + TRAY_MAX_LUMINANCE * ((lightness - DARK_UNTIL_LIGHTNESS) / (DIM_LIGHTNESS - DARK_UNTIL_LIGHTNESS)).coerceIn(0f, 1f)
    val frost = encoded(frostLuminance)
    val limit = encoded(minOf(TRAY_MAX_LUMINANCE, frostLuminance + lift))
    val grey = (limit - (1f - alpha) * frost) / alpha
    val own = (red + green + blue) / 3f
    if (grey >= own) return this
    val k = grey.coerceAtLeast(0f) / own
    return Color(red * k, green * k, blue * k)
}

/** The relative luminance of CIE L* [lightness]. */
private fun luminanceOfLightness(lightness: Float): Float = ((lightness + 16f) / 116f).pow(3).coerceIn(0f, 1f)

/** The sRGB value (0 to 1) of a grey of relative luminance [luminance], at least 0.0031308. */
private fun encoded(luminance: Float): Float = 1.055f * luminance.pow(1f / 2.4f) - 0.055f

private const val PERCENT = 100f
