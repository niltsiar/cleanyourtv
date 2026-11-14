package dev.niltsiar.tvhome.data.shelf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "TvHome.Shelf"

/** How many bytes of decoded posters stay in memory, about five cards of two apps at 1080p. */
internal const val POSTER_CACHE_BYTES: Int = 6 * 1024 * 1024

/**
 * Decodes the posters of shelf items for Home's cards, on [io] and one at a time, and keeps them in an LRU of
 * [POSTER_CACHE_BYTES]. Only a `content://` or `android.resource://` poster is read: Home has no network.
 */
public class ShelfPosters(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val decoding = Mutex()
    private val cache =
        object : LruCache<String, Bitmap>(POSTER_CACHE_BYTES) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }

    /**
     * The poster at [uri], scaled while it decodes to the least size that covers [width] x [height] pixels. Null for a URI of
     * another scheme, or one that cannot be opened or decoded.
     */
    public suspend fun poster(
        uri: String,
        width: Int,
        height: Int,
    ): ImageBitmap? {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != ContentResolver.SCHEME_CONTENT && parsed.scheme != ContentResolver.SCHEME_ANDROID_RESOURCE) return null
        if (width <= 0 || height <= 0) return null
        val key = "$uri@${width}x$height"
        cache.get(key)?.let { return it.asImageBitmap() }
        return decoding
            .withLock {
                cache.get(key) ?: withContext(io) { decode(parsed, width, height) }?.also { cache.put(key, it) }
            }?.asImageBitmap()
    }

    /** The app gave the URI, so any failure to read it counts as no poster. */
    private fun decode(
        uri: Uri,
        width: Int,
        height: Int,
    ): Bitmap? =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                null
            } else {
                val options = posterOptions(bounds.outWidth, bounds.outHeight, width, height)
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        } catch (e: IOException) {
            Log.w(TAG, "cannot read poster $uri: $e")
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot read poster $uri: $e")
            null
        }
}

/**
 * Options that decode a [sourceWidth] x [sourceHeight] picture at the least size that covers [width] x [height]: the largest
 * power-of-two sample that keeps it at least that big, then a density scale for the rest. A smaller picture keeps its size.
 */
internal fun posterOptions(
    sourceWidth: Int,
    sourceHeight: Int,
    width: Int,
    height: Int,
): BitmapFactory.Options {
    val cover = max(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
    val options = BitmapFactory.Options()
    if (cover >= 1f) return options
    val targetWidth = (sourceWidth * cover).roundToInt()
    val targetHeight = (sourceHeight * cover).roundToInt()
    var sample = 1
    while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) sample *= 2
    options.inSampleSize = sample
    val sampledWidth = sourceWidth / sample
    if (sampledWidth > targetWidth) {
        options.inScaled = true
        options.inDensity = sampledWidth
        options.inTargetDensity = targetWidth
    }
    return options
}
