package dev.niltsiar.tvhome.data.wallpaper

/**
 * How big a theme's wallpaper may be and what size it is decoded at. A 1 GB TV cannot afford a picture decoded at
 * full size: a 65535x2159 image is about 283 MB even in RGB_565.
 */
internal object WallpaperSize {
    /** 4K UHD (3840x2160, 8.3 M pixels) and a little more; anything bigger is refused. */
    const val MAX_PIXELS: Long = 8_400_000L

    fun accepted(
        width: Int,
        height: Int,
    ): Boolean = width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS

    /** The largest power-of-two sample size that still covers the screen in both directions. */
    fun sampleSize(
        width: Int,
        height: Int,
        screenW: Int,
        screenH: Int,
    ): Int {
        var sample = 1
        while (width / (sample * 2) >= screenW && height / (sample * 2) >= screenH) sample *= 2
        return sample
    }

    /**
     * {inDensity, inTargetDensity} that scale a (sampled) picture down to what a centre crop shows: the side that
     * limits the cover lands on the screen size. Null when it is already that small.
     */
    fun coverScale(
        width: Int,
        height: Int,
        screenW: Int,
        screenH: Int,
    ): IntArray? =
        if (screenW.toLong() * height >= screenH.toLong() * width) {
            // The width limits the cover.
            if (width > screenW) intArrayOf(width, screenW) else null
        } else {
            if (height > screenH) intArrayOf(height, screenH) else null
        }
}
