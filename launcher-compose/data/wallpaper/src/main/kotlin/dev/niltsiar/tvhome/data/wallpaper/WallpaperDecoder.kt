package dev.niltsiar.tvhome.data.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

private const val TAG = "TvHome.Wallpaper"

/**
 * Decodes wallpaper files at the size a centre crop of the screen shows: sampled, then scaled, and refused when still
 * over [WallpaperSize.MAX_PIXELS]. RGB_565 halves the memory unless [trueColor]. [dim] (percent, 0 to 90) is painted
 * into each picture, so no full-screen shade is drawn on every frame. Decoding blocks: call it off the main thread.
 */
internal class WallpaperDecoder(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val trueColor: Boolean,
    dim: Int,
) {
    private val dimColor = if (dim > 0) (dim * 255 / 100) shl 24 else 0

    /** The picture in GPU memory, or null when the file is unreadable or too big. */
    fun decode(file: File): ImageBitmap? {
        val decoded = decodeCover(file) ?: return null
        // One copy in GPU memory instead of a software bitmap plus its texture.
        val gpu = decoded.copy(Bitmap.Config.HARDWARE, false) ?: return decoded.asImageBitmap()
        decoded.recycle()
        return gpu.asImageBitmap()
    }

    /** The software bitmap, with the dim painted in. */
    private fun decodeCover(file: File): Bitmap? =
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return null
            val sample = WallpaperSize.sampleSize(width, height, screenWidth, screenHeight)
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null
            options.inJustDecodeBounds = false
            options.inSampleSize = sample
            WallpaperSize.coverScale(width / sample, height / sample, screenWidth, screenHeight)?.let { (density, target) ->
                options.inScaled = true
                options.inDensity = density
                options.inTargetDensity = target
            }
            options.inPreferredConfig = if (trueColor) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            options.inMutable = dimColor != 0
            BitmapFactory.decodeFile(file.path, options)?.also { if (dimColor != 0) Canvas(it).drawColor(dimColor) }
        } catch (e: Exception) {
            Log.w(TAG, "cannot decode ${file.name}: $e")
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "no memory to decode ${file.name}: $e")
            null
        }
}
