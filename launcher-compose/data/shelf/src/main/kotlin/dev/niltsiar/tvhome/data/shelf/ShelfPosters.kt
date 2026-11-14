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
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "TvHome.Shelf"

/**
 * How many bytes of decoded posters stay in memory: the ten cards of two apps at 1080p, 480 x 270 pixels of 4 bytes each, with
 * room to spare. Posters keep ARGB_8888, as RGB_565 bands the dark gradients of key art.
 */
internal const val POSTER_CACHE_BYTES: Int = 12 * 1024 * 1024

/**
 * The most pixels a poster decodes into before its last scale: a 1080p frame, 8 MB at 4 bytes each. A 16:9 or 2:3 poster for a
 * 1080p card stays under it, and so does a 16:9 one for a card of twice that size; a poster of an extreme aspect, which only
 * the short side would shrink, goes over it and shows as a text card.
 */
internal const val POSTER_MAX_PIXELS: Long = 1920L * 1080L

/**
 * Decodes the posters of shelf items for Home's cards, on [io] and one at a time, and keeps them in an LRU of
 * [POSTER_CACHE_BYTES]. A `content://` or `android.resource://` poster is read as it is; Home has no network, so an `https`
 * or `http` one is read through the poster helper app as [WebPosters] allows, else not at all. A poster that cannot be read
 * or decoded is not tried again until the next [onStart], except one the helper has not fetched yet: [changes] rises when
 * it may have.
 */
public class ShelfPosters(
    private val context: Context,
    config: StateFlow<TvConfig>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val web = WebPosters(context, config)

    private val decoding = Mutex()
    private val cache =
        object : LruCache<String, Bitmap>(POSTER_CACHE_BYTES) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Rises each time a poster the helper had not fetched may have arrived; a card without a picture then asks again. While
     * collected, it watches the helper once Home has asked it for a poster, and stops watching when Home stops.
     */
    public val changes: Flow<Int> = web.changes

    /**
     * The poster at [uri], scaled while it decodes to the least size that covers [width] x [height] pixels. Null for a URI of
     * another scheme, a web one the helper does not serve now, one that cannot be opened or decoded, or one over
     * [POSTER_MAX_PIXELS].
     */
    public suspend fun poster(
        uri: String,
        width: Int,
        height: Int,
    ): ImageBitmap? {
        val parsed = Uri.parse(uri)
        val local = parsed.scheme == ContentResolver.SCHEME_CONTENT || parsed.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE
        if (!local && !web.serves(parsed.scheme)) return null
        if (width <= 0 || height <= 0) return null
        val key = "$uri@${width}x$height"
        cache.get(key)?.let { return it.asImageBitmap() }
        if (key in failed) return null
        return decoding
            .withLock {
                cache.get(key) ?: when {
                    key in failed -> null
                    local -> load(key, parsed, width, height, helper = false)
                    else -> load(key, web.request(uri, parsed.scheme == "http", width, height), width, height, helper = true)
                }
            }?.asImageBitmap()
    }

    /** Home came to the front: a poster that failed may be readable now, as after the app granted its URI again. */
    public fun onStart() {
        failed.clear()
        web.onStart()
    }

    /** Home went to the back: it stops watching the helper. */
    public fun onStop() {
        web.onStop()
    }

    /** Drops every decoded poster, for memory another app needs or when app_shelf turns off. */
    public fun trim() {
        cache.evictAll()
    }

    /** A [helper] poster that is not there yet is not counted as failed: the helper fetches it and notifies [changes]. */
    private suspend fun load(
        key: String,
        uri: Uri,
        width: Int,
        height: Int,
        helper: Boolean,
    ): Bitmap? {
        val bitmap =
            try {
                withContext(io) { decode(uri, width, height) }
            } catch (e: FileNotFoundException) {
                if (helper) return null
                Log.w(TAG, "cannot read poster $uri: $e")
                null
            }
        if (bitmap == null) failed += key else cache.put(key, bitmap)
        return bitmap
    }

    /** The app gave the URI, so any failure to read it counts as no poster; a missing file is left to [load]. */
    private fun decode(
        uri: Uri,
        width: Int,
        height: Int,
    ): Bitmap? =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = posterOptions(bounds.outWidth, bounds.outHeight, width, height)
            if (options == null) {
                Log.w(TAG, "poster $uri not decoded at ${bounds.outWidth} x ${bounds.outHeight}")
                null
            } else {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        } catch (e: FileNotFoundException) {
            throw e
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
 * Null for a picture with no size, or one whose sampled size is over [POSTER_MAX_PIXELS].
 */
internal fun posterOptions(
    sourceWidth: Int,
    sourceHeight: Int,
    width: Int,
    height: Int,
): BitmapFactory.Options? {
    if (sourceWidth <= 0 || sourceHeight <= 0) return null
    val cover = max(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
    val options = BitmapFactory.Options()
    if (cover >= 1f) return options.takeIf { sourceWidth.toLong() * sourceHeight <= POSTER_MAX_PIXELS }
    val targetWidth = (sourceWidth * cover).roundToInt()
    val targetHeight = (sourceHeight * cover).roundToInt()
    var sample = 1
    while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) sample *= 2
    // A decoder may round a sampled side up.
    val sampledPixels = ((sourceWidth + sample - 1) / sample).toLong() * ((sourceHeight + sample - 1) / sample)
    if (sampledPixels > POSTER_MAX_PIXELS) return null
    options.inSampleSize = sample
    val sampledWidth = sourceWidth / sample
    if (sampledWidth > targetWidth) {
        options.inScaled = true
        options.inDensity = sampledWidth
        options.inTargetDensity = targetWidth
    }
    return options
}
