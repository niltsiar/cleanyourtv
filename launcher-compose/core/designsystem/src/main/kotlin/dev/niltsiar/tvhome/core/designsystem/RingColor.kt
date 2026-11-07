package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val GREY_BELOW_SATURATION = 0.25f
private const val MUTED_FROM_SATURATION = 0.08f
private const val MUTED_BELOW_SATURATION = 0.45f
private const val MUTED_RING_VALUE = 0.85f
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
 * The focus ring of an app whose art gives [brand]. A muted brand, HSV saturation from 0.08 up to 0.45 (slate teal art
 * is one), keeps its hue with saturation 0.45 and value at least 0.85, so the ring still says which app it is. Under
 * 0.08, or when the brand's HSL saturation is below 0.25 (a dark or grey app colour is pale grey-blue on a light picture,
 * and one ring colour for those is white), the ring is white; otherwise the brand with its HSL lightness raised to at
 * least 0.65, hue and saturation kept. A brand not known yet keeps white.
 */
public fun ringColor(brand: Color): Color {
    if (brand.isUnspecified) return Color.White
    val high = max(brand.red, max(brand.green, brand.blue))
    val low = min(brand.red, min(brand.green, brand.blue))
    val lightness = (high + low) / 2f
    val chroma = high - low
    val valueSaturation = if (high == 0f) 0f else chroma / high
    if (valueSaturation < MUTED_FROM_SATURATION) return Color.White
    val hue =
        when (high) {
            brand.red -> ((brand.green - brand.blue) / chroma).mod(6f)
            brand.green -> (brand.blue - brand.red) / chroma + 2f
            else -> (brand.red - brand.green) / chroma + 4f
        }
    if (valueSaturation < MUTED_BELOW_SATURATION) {
        val value = max(high, MUTED_RING_VALUE)
        val mutedChroma = value * MUTED_BELOW_SATURATION
        return fromHue(hue, mutedChroma, value - mutedChroma)
    }
    val saturation = chroma / (1f - abs(2f * lightness - 1f))
    if (saturation < GREY_BELOW_SATURATION) return Color.White
    if (lightness >= RING_MIN_LIGHTNESS) return Color(brand.red, brand.green, brand.blue)
    val newChroma = (1f - abs(2f * RING_MIN_LIGHTNESS - 1f)) * saturation
    return fromHue(hue, newChroma, RING_MIN_LIGHTNESS - newChroma / 2f)
}

/** The colour of [hue] (0 up to 6 sectors) with [chroma], its lowest channel at [floor]. */
private fun fromHue(
    hue: Float,
    chroma: Float,
    floor: Float,
): Color {
    val x = chroma * (1f - abs(hue.mod(2f) - 1f))
    return when (hue.toInt()) {
        0 -> Color(chroma + floor, x + floor, floor)
        1 -> Color(x + floor, chroma + floor, floor)
        2 -> Color(floor, chroma + floor, x + floor)
        3 -> Color(floor, x + floor, chroma + floor)
        4 -> Color(x + floor, floor, chroma + floor)
        else -> Color(chroma + floor, floor, x + floor)
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
