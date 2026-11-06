package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

private const val BLUR_RADIUS = 2
private const val BLUR_PASSES = 3
private const val SATURATION = 1.45f
private const val BRIGHTNESS = 1.08f
private const val OPAQUE = 0xFF shl 24
private const val CHANNEL = 0xFF

/** The most HSL saturation the dock's glass takes from the wallpaper, so a sky colour does not pool at one end of the panel. */
private const val GLASS_MAX_SATURATION = 0.35f

private const val LIGHTNESS_PERCENTILE = 0.9f
private const val DESIGN_WIDTH_DP = 960f
private const val DESIGN_HEIGHT_DP = 540f

/** Every place the header's title and subtitle can stand, from the dock position to the grid position, as fractions of the screen. */
private val HeaderBand = Rect(80f / 1920f, 160f / 1080f, 1100f / 1920f, 640f / 1080f)

/** The right half of the top bar, where its buttons are. */
private val TopBarButtonsBand =
    Rect(
        left = 0.5f,
        top = TopBarTop.value / DESIGN_HEIGHT_DP,
        right = 1f - ScreenMargin.value / DESIGN_WIDTH_DP,
        bottom = (TopBarTop + TopBarHeight).value / DESIGN_HEIGHT_DP,
    )

/** Where the wallpaper's chrome measurements run: off the main thread, unless a test runs them in line. */
internal val LocalBakeDispatcher = staticCompositionLocalOf<CoroutineDispatcher> { Dispatchers.Default }

private class Baked(
    val frost: ImageBitmap,
    val dockFrost: ImageBitmap?,
    val dockLightness: Float,
    val headerLightness: Float,
    val topBarLightness: Float,
    val heroBrightShare: Float,
)

/**
 * Keeps [glass]'s frost on the wallpaper's [Wallpaper.thumbnail] and sky, baked off the main thread each time either
 * changes, and [GlassState.dockLightness] on the part of it under the [dock] (as fractions of the screen). It also keeps
 * [GlassState.headerLightness] and [GlassState.topBarLightness], measured on the thumbnail and sky before the scrim, and
 * [GlassState.heroBrightShare] on the [hero] box (as fractions of the screen), in the same pass on [LocalBakeDispatcher]. No
 * thumbnail, no frost and a lightness of 0, which gives the base tint and the fixed scrims. With [dockGlass] it also
 * keeps [GlassState.dockFrost], the frost with its HSL saturation held to 0.35.
 */
@Composable
internal fun ChromeFrostEffect(
    glass: GlassState,
    wallpaper: () -> Wallpaper,
    skyIntensity: Int,
    dock: Rect,
    hero: Rect,
    dockGlass: Boolean,
) {
    val bakeDispatcher = LocalBakeDispatcher.current
    LaunchedEffect(glass, skyIntensity, dock, hero, dockGlass, bakeDispatcher) {
        snapshotFlow { wallpaper().let { it.thumbnail to it.sky } }.distinctUntilChanged().collectLatest { (thumbnail, sky) ->
            val baked =
                thumbnail?.let {
                    withContext(bakeDispatcher) {
                        val frost = bakeFrost(it, sky, skyIntensity / 100f)
                        val pixels = IntArray(frost.width * frost.height).also { buffer -> frost.readPixels(buffer) }
                        val base = unscrimmedPixels(it, sky, skyIntensity / 100f)
                        Baked(
                            frost,
                            dockFrost =
                                if (dockGlass) {
                                    bitmapOf(
                                        pixels.copyOf().also { p ->
                                            clampSaturation(p, GLASS_MAX_SATURATION)
                                        },
                                        frost,
                                    )
                                } else {
                                    null
                                },
                            dockLightness = meanLightness(pixels, frost.width, frost.height, dock),
                            headerLightness = lightnessPercentile(base, it.width, it.height, HeaderBand, LIGHTNESS_PERCENTILE),
                            topBarLightness = lightnessPercentile(base, it.width, it.height, TopBarButtonsBand, LIGHTNESS_PERCENTILE),
                            heroBrightShare = brightShare(base, it.width, it.height, hero, HERO_BRIGHT_LUMINANCE),
                        )
                    }
                }
            glass.frost = baked?.frost
            glass.dockFrost = baked?.dockFrost
            glass.dockLightness = baked?.dockLightness ?: 0f
            glass.headerLightness = baked?.headerLightness ?: 0f
            glass.topBarLightness = baked?.topBarLightness ?: 0f
            glass.heroBrightShare = baked?.heroBrightShare ?: 0f
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
    val frost = paint(thumbnail, sky, skyAlpha, scrim = true)
    val pixels = IntArray(width * height)
    frost.readPixels(pixels)
    blur(pixels, width, height)
    saturate(pixels)
    brighten(pixels)
    frost.asAndroidBitmap().setPixels(pixels, 0, width, 0, 0, width, height)
    return frost
}

/** An opaque bitmap the size of [like] holding [pixels]. */
private fun bitmapOf(
    pixels: IntArray,
    like: ImageBitmap,
): ImageBitmap =
    ImageBitmap(like.width, like.height).also { it.asAndroidBitmap().setPixels(pixels, 0, like.width, 0, 0, like.width, like.height) }

/** The [thumbnail] under the [sky] at [skyAlpha], and under Home's fixed scrim when [scrim] is true. */
private fun paint(
    thumbnail: ImageBitmap,
    sky: SkyPalette?,
    skyAlpha: Float,
    scrim: Boolean,
): ImageBitmap {
    val image = ImageBitmap(thumbnail.width, thumbnail.height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(thumbnail.width.toFloat(), thumbnail.height.toFloat())) {
        drawImage(thumbnail)
        if (sky != null && skyAlpha > 0f) drawSky(sky, skyAlpha)
        if (scrim) drawHomeScrim()
    }
    return image
}

/** The opaque pixels of the [thumbnail] under the [sky], as the wallpaper looks before any scrim. */
private fun unscrimmedPixels(
    thumbnail: ImageBitmap,
    sky: SkyPalette?,
    skyAlpha: Float,
): IntArray = IntArray(thumbnail.width * thumbnail.height).also { paint(thumbnail, sky, skyAlpha, scrim = false).readPixels(it) }

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

/**
 * Holds the HSL saturation of each opaque pixel to [max], keeping its hue and lightness: the channels move toward the
 * pixel's lightness together.
 */
internal fun clampSaturation(
    pixels: IntArray,
    max: Float,
) {
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = (pixel shr 16 and CHANNEL) / CHANNEL.toFloat()
        val g = (pixel shr 8 and CHANNEL) / CHANNEL.toFloat()
        val b = (pixel and CHANNEL) / CHANNEL.toFloat()
        val high = maxOf(r, g, b)
        val low = minOf(r, g, b)
        val lightness = (high + low) / 2f
        val chroma = high - low
        val room = 1f - abs(2f * lightness - 1f)
        if (chroma <= max * room) continue
        val keep = max * room / chroma
        pixels[i] = OPAQUE or (squeeze(r, lightness, keep) shl 16) or (squeeze(g, lightness, keep) shl 8) or squeeze(b, lightness, keep)
    }
}

private fun squeeze(
    channel: Float,
    lightness: Float,
    keep: Float,
): Int = ((lightness + (channel - lightness) * keep) * CHANNEL).roundToInt().coerceIn(0, CHANNEL)

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
    var sum = 0f
    var count = 0
    forEachLightness(pixels, width, height, band) {
        sum += it
        count++
    }
    return sum / count
}

/** The share (0 to 1) of the opaque [pixels] inside [band] whose relative luminance is above [above]; [meanLightness] has the rest of the contract. */
internal fun brightShare(
    pixels: IntArray,
    width: Int,
    height: Int,
    band: Rect,
    above: Float,
): Float {
    var bright = 0
    var count = 0
    forEachPixel(pixels, width, height, band) {
        if (luminanceOf(it) > above) bright++
        count++
    }
    return bright.toFloat() / count
}

/** The CIE L* below which [percentile] (0 to 1) of the pixels inside [band] lie; [meanLightness] has the rest of the contract. */
internal fun lightnessPercentile(
    pixels: IntArray,
    width: Int,
    height: Int,
    band: Rect,
    percentile: Float,
): Float {
    val values = FloatArray(bandPixelCount(width, height, band))
    var next = 0
    forEachLightness(pixels, width, height, band) { values[next++] = it }
    values.sort()
    return values[((values.size - 1) * percentile).roundToInt()]
}

private fun bandPixelCount(
    width: Int,
    height: Int,
    band: Rect,
): Int {
    val x = bandColumns(width, band)
    val y = bandRows(height, band)
    return (x.last - x.first + 1) * (y.last - y.first + 1)
}

private fun bandColumns(
    width: Int,
    band: Rect,
): IntRange {
    val left = (band.left * width).roundToInt().coerceIn(0, width - 1)
    val right = (band.right * width).roundToInt().coerceIn(left + 1, width)
    return left until right
}

private fun bandRows(
    height: Int,
    band: Rect,
): IntRange {
    val top = (band.top * height).roundToInt().coerceIn(0, height - 1)
    val bottom = (band.bottom * height).roundToInt().coerceIn(top + 1, height)
    return top until bottom
}

private inline fun forEachLightness(
    pixels: IntArray,
    width: Int,
    height: Int,
    band: Rect,
    action: (Float) -> Unit,
) = forEachPixel(pixels, width, height, band) { action(lightnessOf(it)) }

private inline fun forEachPixel(
    pixels: IntArray,
    width: Int,
    height: Int,
    band: Rect,
    action: (Int) -> Unit,
) {
    for (y in bandRows(height, band)) {
        for (x in bandColumns(width, band)) action(pixels[y * width + x])
    }
}

private fun luminanceOf(pixel: Int): Float =
    0.2126f * linear(pixel shr 16 and CHANNEL) + 0.7152f * linear(pixel shr 8 and CHANNEL) + 0.0722f * linear(pixel and CHANNEL)

private fun lightnessOf(pixel: Int): Float {
    val luminance = luminanceOf(pixel)
    return if (luminance > LAB_EPSILON) 116f * cbrt(luminance) - 16f else LAB_KAPPA * luminance
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
