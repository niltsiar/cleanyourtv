package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A colour as OKLCH: lightness [l] 0 to 1, chroma [c] and hue [h] in degrees. */
public data class Oklch(
    val l: Float,
    val c: Float,
    val h: Float,
)

private const val GREY_BELOW_SATURATION = 0.25f
private const val RING_MIN_LIGHTNESS = 0.65f

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

public fun oklch(color: Color): Oklch {
    val r = toLinear(color.red)
    val g = toLinear(color.green)
    val b = toLinear(color.blue)
    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
    val lightness = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
    val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
    val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    val hue = Math.toDegrees(atan2(bb, a).toDouble()).toFloat()
    return Oklch(lightness, hypot(a, bb), if (hue < 0f) hue + 360f else hue)
}

private fun toLinear(channel: Float): Float = if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)
