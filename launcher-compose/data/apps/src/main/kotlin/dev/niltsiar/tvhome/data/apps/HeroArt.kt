package dev.niltsiar.tvhome.data.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The size of the softened art: it is stretched over the screen, so bilinear filtering of a picture that small is the blur. */
internal const val HERO_W = 48
internal const val HERO_H = 27

/** The art is drawn this many times larger, then halved twice: each halving averages 2x2 pixels, where one 4x step skips most. */
private const val HERO_OVERSAMPLE = 4
private const val SATURATION = 1.7f
private const val BLUR_RADIUS = 2
private const val BLUR_PASSES = 3

/**
 * An app's artwork for the cinematic shelf's backdrop: drawn on black at 192x108, halved twice, saturated and blurred
 * with three box passes, so the stretched picture shows no seams. About 5 KB.
 */
internal fun heroArtFrom(art: Drawable): ImageBitmap {
    val width = HERO_W * HERO_OVERSAMPLE
    val height = HERO_H * HERO_OVERSAMPLE
    val big = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(big)
    canvas.drawColor(Color.BLACK)
    art.setBounds(0, 0, width, height)
    art.draw(canvas)
    val pixels = IntArray(width * height)
    big.getPixels(pixels, 0, width, 0, 0, width, height)
    big.recycle()
    val soft = softenHeroArt(pixels)
    return Bitmap.createBitmap(soft, HERO_W, HERO_H, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The [HERO_W] by [HERO_H] pixels (ARGB, opaque) made from the 192x108 pixels of [source], which are opaque too. */
internal fun softenHeroArt(source: IntArray): IntArray {
    require(source.size == HERO_W * HERO_H * HERO_OVERSAMPLE * HERO_OVERSAMPLE) { "expected 192x108 pixels" }
    var pixels = halve(halve(source, HERO_W * HERO_OVERSAMPLE), HERO_W * HERO_OVERSAMPLE / 2)
    pixels = saturate(pixels)
    repeat(BLUR_PASSES) {
        pixels = boxBlur(pixels, HERO_W, HERO_H, BLUR_RADIUS, horizontal = true)
        pixels = boxBlur(pixels, HERO_W, HERO_H, BLUR_RADIUS, horizontal = false)
    }
    return pixels
}

/** Each pixel of the result is the rounded average of a 2x2 block of [src], which is [width] pixels wide. */
internal fun halve(
    src: IntArray,
    width: Int,
): IntArray {
    val height = src.size / width
    val w = width / 2
    val h = height / 2
    val out = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val a = src[2 * y * width + 2 * x]
            val b = src[2 * y * width + 2 * x + 1]
            val c = src[(2 * y + 1) * width + 2 * x]
            val d = src[(2 * y + 1) * width + 2 * x + 1]
            out[y * w + x] =
                opaque(
                    average4(a shr 16, b shr 16, c shr 16, d shr 16),
                    average4(a shr 8, b shr 8, c shr 8, d shr 8),
                    average4(a, b, c, d),
                )
        }
    }
    return out
}

private fun average4(
    a: Int,
    b: Int,
    c: Int,
    d: Int,
): Int = ((a and 0xFF) + (b and 0xFF) + (c and 0xFF) + (d and 0xFF) + 2) / 4

/** Every colour pushed away from its luminance by [SATURATION]. */
internal fun saturate(pixels: IntArray): IntArray =
    IntArray(pixels.size) { i ->
        val p = pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
        opaque(
            clamp(luminance + (r - luminance) * SATURATION),
            clamp(luminance + (g - luminance) * SATURATION),
            clamp(luminance + (b - luminance) * SATURATION),
        )
    }

private fun clamp(v: Float): Int = v.roundToInt().coerceIn(0, 255)

private fun opaque(
    r: Int,
    g: Int,
    b: Int,
): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

/** One box blur pass along the rows ([horizontal]) or the columns of [src], [width] by [height], with the edges clamped. */
internal fun boxBlur(
    src: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
): IntArray {
    val out = IntArray(src.size)
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val span = 2 * radius + 1
    for (line in 0 until lines) {
        for (i in 0 until length) {
            var r = 0
            var g = 0
            var b = 0
            for (k in -radius..radius) {
                val j = max(0, min(length - 1, i + k))
                val p = if (horizontal) src[line * width + j] else src[j * width + line]
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            out[if (horizontal) line * width + i else i * width + line] = opaque(r / span, g / span, b / span)
        }
    }
    return out
}
