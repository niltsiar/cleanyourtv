package dev.niltsiar.tvhome.data.apps

import kotlin.math.pow

/**
 * The mean relative luminance from which art counts as light: 1.5 to 1 against the lightest tray, which holds 0.55.
 */
private const val LIGHT_ART_LUMINANCE = 0.35f
private const val CHANNEL_MAX = 255f

/**
 * Whether a banner is light enough to lose its edge on a light surface. [pixels] is the art drawn to [VIVID_SIDE] squared
 * (ARGB); a clear pixel counts as black, as the dark tile behind the art shows through it.
 */
internal fun isLightArt(pixels: IntArray): Boolean = pixels.sumOf { luminance(it).toDouble() } / pixels.size >= LIGHT_ART_LUMINANCE

private fun luminance(pixel: Int): Float {
    val alpha = (pixel ushr 24) / CHANNEL_MAX
    return alpha * (0.2126f * linear(pixel shr 16 and 0xFF) + 0.7152f * linear(pixel shr 8 and 0xFF) + 0.0722f * linear(pixel and 0xFF))
}

private fun linear(channel: Int): Float {
    val c = channel / CHANNEL_MAX
    return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
}
