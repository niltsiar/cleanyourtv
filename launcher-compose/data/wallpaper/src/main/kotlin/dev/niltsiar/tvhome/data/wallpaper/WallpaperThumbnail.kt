package dev.niltsiar.tvhome.data.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

private const val TAG = "TvHome.Wallpaper"

/** A wallpaper as a small picture for a list. */
internal object WallpaperThumbnail {
    /**
     * The centre of [file] cropped to the shape of [width] x [height] and scaled to that size, in RGB_565 unless the file has
     * transparency. Only the cropped part is decoded, sampled down to under twice the size, so a large
     * picture never comes into memory whole. Null when the file cannot be decoded. Blocks: call it off the main thread.
     */
    fun decode(
        file: File,
        width: Int,
        height: Int,
    ): ImageBitmap? =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val crop = centreCrop(bounds.outWidth, bounds.outHeight, width, height)
            crop?.let { decodeRegion(file, it, width, height) }?.asImageBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "no thumbnail for ${file.name}: $e")
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "no memory for the thumbnail of ${file.name}: $e")
            null
        }

    /** The largest centred part of a [width] x [height] picture with the shape of [targetWidth] x [targetHeight]. */
    private fun centreCrop(
        width: Int,
        height: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Rect? {
        if (width <= 0 || height <= 0) return null
        val wide = width.toLong() * targetHeight > height.toLong() * targetWidth
        val cropWidth = if (wide) (height.toLong() * targetWidth / targetHeight).toInt().coerceAtLeast(1) else width
        val cropHeight = if (wide) height else (width.toLong() * targetHeight / targetWidth).toInt().coerceAtLeast(1)
        val left = (width - cropWidth) / 2
        val top = (height - cropHeight) / 2
        return Rect(left, top, left + cropWidth, top + cropHeight)
    }

    private fun decodeRegion(
        file: File,
        crop: Rect,
        width: Int,
        height: Int,
    ): Bitmap? {
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = WallpaperSize.sampleSize(crop.width(), crop.height(), width, height)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        val decoder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(file.path)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(file.path, false)
            } ?: return null
        val region =
            try {
                decoder.decodeRegion(crop, options)
            } finally {
                decoder.recycle()
            } ?: return null
        val scaled = Bitmap.createScaledBitmap(region, width, height, true)
        if (scaled !== region) region.recycle()
        return scaled
    }
}
