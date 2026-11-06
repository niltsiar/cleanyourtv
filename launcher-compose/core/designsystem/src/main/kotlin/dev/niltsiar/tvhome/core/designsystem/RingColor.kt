package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val GREY_BELOW_SATURATION = 0.25f
private const val RING_MIN_LIGHTNESS = 0.65f
private const val MIN_RING_CONTRAST = 3f

/**
 * The ring of a focused tile for an app whose art gives [art]: white with [whiteFocus] or when the tile is not [coloured],
 * else the app's [ringColor].
 */
public fun focusRingColour(
    art: Color,
    coloured: Boolean,
    whiteFocus: Boolean,
): Color = if (whiteFocus || !coloured) Color.White else ringColor(art)

/**
 * The focus ring of an app whose art gives [brand]: white when the brand's HSL saturation is below 0.25 (a dark or grey
 * app colour is pale grey-blue on a light picture, and one ring colour for those is white); otherwise the brand with its
 * HSL lightness raised to at least 0.65, hue and saturation kept. A brand not known yet keeps white.
 */
public fun ringColor(brand: Color): Color {
    if (brand.isUnspecified) return Color.White
    val high = max(brand.red, max(brand.green, brand.blue))
    val low = min(brand.red, min(brand.green, brand.blue))
    val lightness = (high + low) / 2f
    val chroma = high - low
    val saturation = if (chroma == 0f) 0f else chroma / (1f - abs(2f * lightness - 1f))
    if (saturation < GREY_BELOW_SATURATION) return Color.White
    if (lightness >= RING_MIN_LIGHTNESS) return Color(brand.red, brand.green, brand.blue)
    val hue =
        when (high) {
            brand.red -> ((brand.green - brand.blue) / chroma).mod(6f)
            brand.green -> (brand.blue - brand.red) / chroma + 2f
            else -> (brand.red - brand.green) / chroma + 4f
        }
    val newChroma = (1f - abs(2f * RING_MIN_LIGHTNESS - 1f)) * saturation
    val m = RING_MIN_LIGHTNESS - newChroma / 2f
    val x = newChroma * (1f - abs(hue.mod(2f) - 1f))
    return when (hue.toInt()) {
        0 -> Color(newChroma + m, x + m, m)
        1 -> Color(x + m, newChroma + m, m)
        2 -> Color(m, newChroma + m, x + m)
        3 -> Color(m, x + m, newChroma + m)
        4 -> Color(x + m, m, newChroma + m)
        else -> Color(newChroma + m, m, x + m)
    }
}

/**
 * Whether a coloured [ring] reads as a thick border of a tile of colour [brand], which it comes from: its contrast ratio
 * against the tile is under 3:1. A white ring never does, and a brand not known yet has none.
 */
public fun ringBlendsWith(
    ring: Color,
    brand: Color,
): Boolean {
    if (brand.isUnspecified || ring == Color.White) return false
    val light = max(ring.luminance(), brand.luminance())
    val dark = min(ring.luminance(), brand.luminance())
    return (light + 0.05f) / (dark + 0.05f) < MIN_RING_CONTRAST
}
