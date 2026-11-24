package dev.niltsiar.tvhome.data.wallpaper

private const val BINS = 36
private const val BIN_DEGREES = 360f / BINS
private const val CHANNEL = 0xFF

/** A pixel this close to grey (HSV saturation) has no hue worth counting. */
private const val MIN_SATURATION = 0.2f

/** A pixel this dark (its brightest channel, of 255) is noise: a dimmed picture keeps its hues well above it. */
private const val MIN_VALUE = 16

/** The heaviest bin must weigh this share of the pixel count, so the colour noise of a grey photo gives no hue. */
private const val MIN_SHARE = 0.01f

/** The hue a picture shows most, for the house seed. */
internal object WallpaperHue {
    /**
     * The dominant hue of [pixels] (ARGB), in degrees from 0 to 360: a histogram of 10 degree bins over the chromatic pixels,
     * each weighted by its saturation, and the mean hue of the heaviest bin. Null when too few pixels have a hue.
     */
    fun dominant(pixels: IntArray): Float? {
        val weights = FloatArray(BINS)
        val hueSums = FloatArray(BINS)
        for (pixel in pixels) {
            val r = pixel shr 16 and CHANNEL
            val g = pixel shr 8 and CHANNEL
            val b = pixel and CHANNEL
            val max = maxOf(r, g, b)
            val chroma = max - minOf(r, g, b)
            if (max < MIN_VALUE) continue
            val saturation = chroma / max.toFloat()
            if (saturation < MIN_SATURATION) continue
            val hue = hue(r, g, b, max, chroma)
            val bin = (hue / BIN_DEGREES).toInt().coerceAtMost(BINS - 1)
            weights[bin] += saturation
            hueSums[bin] += hue * saturation
        }
        val heaviest = weights.indices.maxBy { weights[it] }
        if (weights[heaviest] < pixels.size * MIN_SHARE) return null
        return hueSums[heaviest] / weights[heaviest]
    }

    private fun hue(
        r: Int,
        g: Int,
        b: Int,
        max: Int,
        chroma: Int,
    ): Float {
        val sector =
            when (max) {
                r -> ((g - b) / chroma.toFloat()).mod(6f)
                g -> (b - r) / chroma.toFloat() + 2f
                else -> (r - g) / chroma.toFloat() + 4f
            }
        return sector * 60f
    }
}
