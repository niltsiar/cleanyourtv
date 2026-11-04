package dev.niltsiar.tvhome.data.apps

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The side, in pixels, of the square an app's art is reduced to before its colour is picked. */
internal const val VIVID_SIDE = 12

/** The colour of an app with no art. */
internal const val NO_ART_ACCENT: Int = 0xFF444444.toInt()

private const val MIN_SCORE = 0.25f
private const val BRIGHTNESS_BOOST = 1.4f
private const val SATURATION_BOOST = 1.1f
private const val MIN_VALUE = 0.5f
private const val MIN_AVERAGE_VALUE = 0.55f
private const val HUE_SECTOR = 60f
private const val CHANNEL_MAX = 255f

/**
 * The colour a tile's focus highlight and the cinematic glow take: the most saturated, not too dark pixel of [pixels]
 * (the art drawn to [VIVID_SIDE] squared, ARGB), a little more saturated and no darker than half brightness. Art with
 * no such pixel, like a white logo, gives its average colour lifted to at least 55% brightness.
 */
internal fun vividColor(pixels: IntArray): Int {
    var best = NO_ART_ACCENT
    var bestScore = -1f
    var red = 0L
    var green = 0L
    var blue = 0L
    for (p in pixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        red += r
        green += g
        blue += b
        val top = max(r, max(g, b))
        val bottom = min(r, min(g, b))
        val saturation = if (top == 0) 0f else (top - bottom) / top.toFloat()
        val score = saturation * min(1f, top / CHANNEL_MAX * BRIGHTNESS_BOOST)
        if (score > bestScore) {
            bestScore = score
            best = p
        }
    }
    val hsv = FloatArray(3)
    if (bestScore < MIN_SCORE) {
        val n = pixels.size
        rgbToHsv(0xFF shl 24 or ((red / n).toInt() shl 16) or ((green / n).toInt() shl 8) or (blue / n).toInt(), hsv)
        hsv[2] = max(hsv[2], MIN_AVERAGE_VALUE)
    } else {
        rgbToHsv(best, hsv)
        hsv[1] = min(1f, hsv[1] * SATURATION_BOOST)
        hsv[2] = max(MIN_VALUE, hsv[2])
    }
    return hsvToRgb(hsv)
}

/** [hsv] gets hue in degrees, saturation and value from 0 to 1, as android.graphics.Color.colorToHSV gives them. */
internal fun rgbToHsv(
    color: Int,
    hsv: FloatArray,
) {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    val top = max(r, max(g, b))
    val delta = top - min(r, min(g, b))
    hsv[2] = top / CHANNEL_MAX
    hsv[1] = if (top == 0) 0f else delta / top.toFloat()
    var hue =
        when {
            delta == 0 -> 0f
            top == r -> (g - b) / delta.toFloat()
            top == g -> 2f + (b - r) / delta.toFloat()
            else -> 4f + (r - g) / delta.toFloat()
        } * HUE_SECTOR
    if (hue < 0f) hue += 6 * HUE_SECTOR
    hsv[0] = hue
}

/** The opaque colour of [hsv], as android.graphics.Color.HSVToColor gives it. */
internal fun hsvToRgb(hsv: FloatArray): Int {
    val sector = hsv[0] / HUE_SECTOR
    val s = hsv[1].coerceIn(0f, 1f)
    val v = hsv[2].coerceIn(0f, 1f)
    val index = floor(sector).toInt().mod(6)
    val f = sector - floor(sector)
    val p = v * (1f - s)
    val q = v * (1f - s * f)
    val t = v * (1f - s * (1f - f))
    val (r, g, b) =
        when (index) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
    return 0xFF shl 24 or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

private fun channel(value: Float): Int = (value * CHANNEL_MAX).roundToInt().coerceIn(0, 255)
