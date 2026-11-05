package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.model.SkyPalette
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

private const val BLUR_RADIUS = 2
private const val BLUR_PASSES = 3
private const val SATURATION = 1.45f
private const val BRIGHTNESS = 1.08f
private const val OPAQUE = 0xFF shl 24
private const val CHANNEL = 0xFF

/**
 * Keeps [glass]'s frost on the wallpaper's [Wallpaper.thumbnail] and sky, baked off the main thread each time either
 * changes, and [GlassState.dockLightness] on the part of it under the [dock] (as fractions of the screen). No
 * thumbnail, no frost.
 */
@Composable
internal fun ChromeFrostEffect(
    glass: GlassState,
    wallpaper: () -> Wallpaper,
    skyIntensity: Int,
    dock: Rect,
) {
    LaunchedEffect(glass, skyIntensity, dock) {
        snapshotFlow { wallpaper().let { it.thumbnail to it.sky } }.distinctUntilChanged().collectLatest { (thumbnail, sky) ->
            val baked =
                thumbnail?.let {
                    withContext(Dispatchers.Default) {
                        val frost = bakeFrost(it, sky, skyIntensity / 100f)
                        val pixels = IntArray(frost.width * frost.height).also { buffer -> frost.readPixels(buffer) }
                        frost to meanLightness(pixels, frost.width, frost.height, dock)
                    }
                }
            glass.frost = baked?.first
            baked?.second?.let { glass.dockLightness = it }
        }
    }
}

/**
 * The frost of the chrome surfaces: [thumbnail] under the [sky] at [skyAlpha] and Home's scrim, box-blurred and made
 * more colourful and a little brighter. It allocates one bitmap of the thumbnail's size and two pixel buffers; call it off the main thread.
 */
internal fun bakeFrost(
    thumbnail: ImageBitmap,
    sky: SkyPalette?,
    skyAlpha: Float,
): ImageBitmap {
    val width = thumbnail.width
    val height = thumbnail.height
    val frost = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(frost), Size(width.toFloat(), height.toFloat())) {
        drawImage(thumbnail)
        if (sky != null && skyAlpha > 0f) drawSky(sky, skyAlpha)
        drawHomeScrim()
    }
    val pixels = IntArray(width * height)
    frost.readPixels(pixels)
    blur(pixels, width, height)
    saturate(pixels)
    brighten(pixels)
    frost.asAndroidBitmap().setPixels(pixels, 0, width, 0, 0, width, height)
    return frost
}

/** [BLUR_PASSES] box blurs of [BLUR_RADIUS] across and down the opaque [pixels] of a [width] by [height] image, in place. */
internal fun blur(
    pixels: IntArray,
    width: Int,
    height: Int,
) {
    val scratch = IntArray(pixels.size)
    repeat(BLUR_PASSES) {
        blurLines(pixels, scratch, lines = height, length = width, step = 1, lineStep = width)
        blurLines(scratch, pixels, lines = width, length = height, step = width, lineStep = 1)
    }
}

/** A box blur along each of [lines] lines of [length] pixels, from [from] into [to]; the pixels past an end repeat it. */
private fun blurLines(
    from: IntArray,
    to: IntArray,
    lines: Int,
    length: Int,
    step: Int,
    lineStep: Int,
) {
    val span = BLUR_RADIUS * 2 + 1
    for (line in 0 until lines) {
        val start = line * lineStep
        for (i in 0 until length) {
            var r = 0
            var g = 0
            var b = 0
            for (k in -BLUR_RADIUS..BLUR_RADIUS) {
                val pixel = from[start + (i + k).coerceIn(0, length - 1) * step]
                r += pixel shr 16 and CHANNEL
                g += pixel shr 8 and CHANNEL
                b += pixel and CHANNEL
            }
            to[start + i * step] = OPAQUE or (r / span shl 16) or (g / span shl 8) or (b / span)
        }
    }
}

/** Pushes each pixel's colour away from its grey by [SATURATION]. */
internal fun saturate(pixels: IntArray) {
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = pixel shr 16 and CHANNEL
        val g = pixel shr 8 and CHANNEL
        val b = pixel and CHANNEL
        val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
        pixels[i] = OPAQUE or (boost(luma, r) shl 16) or (boost(luma, g) shl 8) or boost(luma, b)
    }
}

/** Lifts every channel of the opaque [pixels] by [BRIGHTNESS]. */
internal fun brighten(pixels: IntArray) {
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = (pixel shr 16 and CHANNEL) * BRIGHTNESS
        val g = (pixel shr 8 and CHANNEL) * BRIGHTNESS
        val b = (pixel and CHANNEL) * BRIGHTNESS
        pixels[i] =
            OPAQUE or (r.roundToInt().coerceAtMost(CHANNEL) shl 16) or (g.roundToInt().coerceAtMost(CHANNEL) shl 8) or
            b.roundToInt().coerceAtMost(CHANNEL)
    }
}

/**
 * The mean CIE L* (0 black to 100 white) of the opaque [pixels] of a [width] by [height] image that lie inside [band],
 * given as fractions of the image.
 */
internal fun meanLightness(
    pixels: IntArray,
    width: Int,
    height: Int,
    band: Rect,
): Float {
    val left = (band.left * width).roundToInt().coerceIn(0, width - 1)
    val right = (band.right * width).roundToInt().coerceIn(left + 1, width)
    val top = (band.top * height).roundToInt().coerceIn(0, height - 1)
    val bottom = (band.bottom * height).roundToInt().coerceIn(top + 1, height)
    var sum = 0f
    for (y in top until bottom) {
        for (x in left until right) {
            val pixel = pixels[y * width + x]
            val luminance =
                0.2126f * linear(pixel shr 16 and CHANNEL) + 0.7152f * linear(pixel shr 8 and CHANNEL) + 0.0722f * linear(pixel and CHANNEL)
            sum += if (luminance > LAB_EPSILON) 116f * cbrt(luminance) - 16f else LAB_KAPPA * luminance
        }
    }
    return sum / ((right - left) * (bottom - top))
}

private const val LAB_EPSILON = 216f / 24389f
private const val LAB_KAPPA = 24389f / 27f

private fun linear(channel: Int): Float {
    val c = channel / 255f
    return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
}

private fun boost(
    luma: Float,
    channel: Int,
): Int = (luma + (channel - luma) * SATURATION).roundToInt().coerceIn(0, CHANNEL)
