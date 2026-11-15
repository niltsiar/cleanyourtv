package dev.niltsiar.tvhome.data.shelf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.ShelfPoster
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
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "TvHome.Shelf"

/**
 * How many bytes of decoded posters stay in memory: the ten cards of two apps at 1080p, 480 x 270 pixels of 4 bytes each, with
 * room to spare. Posters keep ARGB_8888, as RGB_565 bands the dark gradients of key art.
 */
internal const val POSTER_CACHE_BYTES: Int = 12 * 1024 * 1024

/**
 * The most pixels a poster decodes into before its last scale: a 1080p frame, 8 MB at 4 bytes each. A poster for a 1080p card
 * stays under it, and so does a 16:9 one for a card of twice that size; a poster near the card's shape for a card that big
 * can go over it and shows as a text card.
 */
internal const val POSTER_MAX_PIXELS: Long = 1920L * 1080L

/**
 * Decodes the posters of shelf items for Home's cards, on [io] and one at a time, and keeps them in an LRU of
 * [POSTER_CACHE_BYTES]. A `content://` or `android.resource://` poster is read as it is; Home has no network, so an `https`
 * or `http` one is read through the poster helper app as [WebPosters] allows, else not at all. A poster that cannot be read
 * or decoded is not tried again until the next [onStart], which raises [changes] so its card asks again; one the helper
 * has not fetched yet is tried again once [changes] rises for it.
 */
public class ShelfPosters(
    private val context: Context,
    config: StateFlow<TvConfig>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val web = WebPosters(context, config)

    private val decoding = Mutex()
    private val cache =
        object : LruCache<String, ShelfPoster>(POSTER_CACHE_BYTES) {
            override fun sizeOf(
                key: String,
                value: ShelfPoster,
            ): Int = value.picture.asAndroidBitmap().allocationByteCount
        }
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Rises each time a poster the helper had not fetched may have arrived, and on an [onStart] after a poster failed; a card
     * without a picture then asks again. While collected, it watches the helper once Home has asked it for a poster, and stops
     * watching when Home stops.
     */
    public val changes: Flow<Int> = web.changes

    /**
     * The poster at [uri] for a card of [width] x [height] pixels, scaled while it decodes as [posterOptions] says; a poster
     * the card [drawsWhole] comes with its [posterFill]. Null for a URI of another scheme, a web one the helper does not serve
     * now, one that cannot be opened or decoded, or one over [POSTER_MAX_PIXELS].
     */
    public suspend fun poster(
        uri: String,
        width: Int,
        height: Int,
    ): ShelfPoster? {
        val parsed = Uri.parse(uri)
        val local = parsed.scheme == ContentResolver.SCHEME_CONTENT || parsed.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE
        if (!local && !web.serves(parsed.scheme)) return null
        if (width <= 0 || height <= 0) return null
        val key = "$uri@${width}x$height"
        cache.get(key)?.let { return it }
        if (key in failed) return null
        return decoding.withLock {
            cache.get(key) ?: when {
                key in failed -> null
                local -> load(key, parsed, width, height, helper = false)
                else -> load(key, web.request(uri, parsed.scheme == "http", width, height), width, height, helper = true)
            }
        }
    }

    /** Home came to the front: a poster that failed may be readable now, as after the app granted its URI again. */
    public fun onStart() {
        val retry = failed.isNotEmpty()
        failed.clear()
        web.onStart(retry)
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
    ): ShelfPoster? {
        val poster =
            try {
                withContext(io) { decode(uri, width, height) }
            } catch (e: FileNotFoundException) {
                if (helper) return null
                Log.w(TAG, "cannot read poster $uri: $e")
                null
            }
        if (poster == null) failed += key else cache.put(key, poster)
        return poster
    }

    /** The app gave the URI, so any failure to read it counts as no poster; a missing file is left to [load]. */
    private fun decode(
        uri: Uri,
        width: Int,
        height: Int,
    ): ShelfPoster? =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = posterOptions(bounds.outWidth, bounds.outHeight, width, height)
            if (options == null) {
                Log.w(TAG, "poster $uri not decoded at ${bounds.outWidth} x ${bounds.outHeight}")
                null
            } else {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.let { bitmap ->
                    val whole = drawsWhole(bounds.outWidth, bounds.outHeight, width, height)
                    ShelfPoster(bitmap.asImageBitmap(), if (whole) posterFill(bitmap) else Color.Unspecified)
                }
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
 * Whether a card of [width] x [height] draws a [sourceWidth] x [sourceHeight] poster whole rather than cropped to cover it:
 * when the poster's aspect ratio differs from the card's by more than a factor of 1.2, which for a 16:9 card is a ratio under
 * about 1.48 or over about 2.13.
 */
internal fun drawsWhole(
    sourceWidth: Int,
    sourceHeight: Int,
    width: Int,
    height: Int,
): Boolean {
    val poster = sourceWidth.toLong() * height
    val card = sourceHeight.toLong() * width
    return max(poster, card) * 5 > min(poster, card) * 6
}

/**
 * Options that decode a [sourceWidth] x [sourceHeight] picture at the least size that covers [width] x [height], or for one
 * the card [drawsWhole], the greatest size that fits in it: the largest power-of-two sample that keeps it at least that big,
 * then a density scale for the rest. A smaller picture keeps its size. Null for a picture with no size, or one whose sampled
 * size is over [POSTER_MAX_PIXELS].
 */
internal fun posterOptions(
    sourceWidth: Int,
    sourceHeight: Int,
    width: Int,
    height: Int,
): BitmapFactory.Options? {
    if (sourceWidth <= 0 || sourceHeight <= 0) return null
    val widthScale = width.toFloat() / sourceWidth
    val heightScale = height.toFloat() / sourceHeight
    val scale =
        if (drawsWhole(sourceWidth, sourceHeight, width, height)) min(widthScale, heightScale) else max(widthScale, heightScale)
    val options = BitmapFactory.Options()
    if (scale >= 1f) return options.takeIf { sourceWidth.toLong() * sourceHeight <= POSTER_MAX_PIXELS }
    val targetWidth = (sourceWidth * scale).roundToInt()
    val targetHeight = (sourceHeight * scale).roundToInt()
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

/**
 * The colour round a poster drawn whole: the average of the pixels on the outer edge of [bitmap], weighted by their alpha so a
 * transparent edge pixel adds no colour, at their average alpha.
 */
internal fun posterFill(bitmap: Bitmap): Color {
    val width = bitmap.width
    val height = bitmap.height
    val rows = if (height > 1) intArrayOf(0, height - 1) else intArrayOf(0)
    val columns = if (width > 1) intArrayOf(0, width - 1) else intArrayOf(0)
    val inner = max(0, height - 2)
    val pixels = IntArray(rows.size * width + columns.size * inner)
    rows.forEachIndexed { i, y -> bitmap.getPixels(pixels, i * width, width, 0, y, width, 1) }
    columns.forEachIndexed { i, x -> bitmap.getPixels(pixels, rows.size * width + i * inner, 1, x, 1, 1, inner) }
    var alpha = 0L
    var red = 0L
    var green = 0L
    var blue = 0L
    for (pixel in pixels) {
        val a = (pixel ushr 24).toLong()
        alpha += a
        red += a * ((pixel shr 16) and 0xFF)
        green += a * ((pixel shr 8) and 0xFF)
        blue += a * (pixel and 0xFF)
    }
    if (alpha == 0L) return Color.Transparent
    return Color(
        red = (red / alpha).toInt(),
        green = (green / alpha).toInt(),
        blue = (blue / alpha).toInt(),
        alpha = (alpha / pixels.size).toInt(),
    )
}
