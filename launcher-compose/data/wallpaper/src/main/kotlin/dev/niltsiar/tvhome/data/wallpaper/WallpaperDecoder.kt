package dev.niltsiar.tvhome.data.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

private const val TAG = "TvHome.Wallpaper"
private const val THUMBNAIL_WIDTH = 80
private const val OPAQUE = 0xFF shl 24
private const val CHANNEL = 0xFF

/** A decoded wallpaper: the [picture] for the screen and, when asked for, its 80 pixel wide [thumbnail]. */
internal class DecodedPicture(
    val picture: ImageBitmap,
    val thumbnail: ImageBitmap?,
)

/**
 * Decodes wallpaper files at the size a centre crop of the screen shows: sampled, then scaled, and refused when still
 * over [WallpaperSize.MAX_PIXELS]. RGB_565 halves the memory unless [trueColor]. [dim] (percent, 0 to 90) is painted
 * into each picture, so no full-screen shade is drawn on every frame. With [thumbnails] each picture also gets an
 * 80 pixel wide thumbnail. Decoding blocks: call it off the main thread.
 */
internal class WallpaperDecoder(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val trueColor: Boolean,
    dim: Int,
    private val thumbnails: Boolean,
) {
    private val dimColor = if (dim > 0) (dim * 255 / 100) shl 24 else 0

    /** The picture in GPU memory, or null when the file is unreadable or too big. */
    fun decode(file: File): DecodedPicture? {
        val decoded = decodeCover(file) ?: return null
        val thumbnail = if (thumbnails) thumbnailOf(decoded) else null
        // One copy in GPU memory instead of a software bitmap plus its texture.
        val gpu = decoded.copy(Bitmap.Config.HARDWARE, false) ?: return DecodedPicture(decoded.asImageBitmap(), thumbnail)
        decoded.recycle()
        return DecodedPicture(gpu.asImageBitmap(), thumbnail)
    }

    /**
     * The centred part of [cover] that the screen shows, each block of pixels averaged into one, so no single bright
     * pixel decides a thumbnail pixel. One row is read at a time: the full picture is never copied.
     */
    private fun thumbnailOf(cover: Bitmap): ImageBitmap {
        val wide = cover.width.toLong() * screenHeight > cover.height.toLong() * screenWidth
        val cropWidth = if (wide) (cover.height.toLong() * screenWidth / screenHeight).toInt() else cover.width
        val cropHeight = if (wide) cover.height else (cover.width.toLong() * screenHeight / screenWidth).toInt()
        val left = (cover.width - cropWidth) / 2
        val top = (cover.height - cropHeight) / 2
        val width = THUMBNAIL_WIDTH
        val height = (THUMBNAIL_WIDTH * screenHeight / screenWidth).coerceAtLeast(1)
        val sums = IntArray(width * height * 3)
        val counts = IntArray(width * height)
        val row = IntArray(cropWidth)
        for (y in 0 until cropHeight) {
            cover.getPixels(row, 0, cropWidth, left, top + y, cropWidth, 1)
            val base = y * height / cropHeight * width
            for (x in 0 until cropWidth) {
                val cell = base + x * width / cropWidth
                val pixel = row[x]
                sums[cell * 3] += pixel shr 16 and CHANNEL
                sums[cell * 3 + 1] += pixel shr 8 and CHANNEL
                sums[cell * 3 + 2] += pixel and CHANNEL
                counts[cell]++
            }
        }
        val pixels =
            IntArray(width * height) { cell ->
                val n = counts[cell].coerceAtLeast(1)
                OPAQUE or (sums[cell * 3] / n shl 16) or (sums[cell * 3 + 1] / n shl 8) or (sums[cell * 3 + 2] / n)
            }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
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
