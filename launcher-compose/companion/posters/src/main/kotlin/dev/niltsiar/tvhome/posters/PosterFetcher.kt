package dev.niltsiar.tvhome.posters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

internal const val MAX_BODY_BYTES = 2L * 1024 * 1024
internal const val MIN_SIDE = 16
internal const val MAX_SIDE = 4096

private const val USER_AGENT = "Mozilla/5.0"
private val IMAGE_TYPES = setOf("jpeg", "png", "webp")

/**
 * Fetches a poster with a GET that sends no cookies, no Referer and a generic User-Agent, follows no redirect, and goes
 * through no proxy, so [PosterDns] over [resolver] checks every address it connects to. [newClient] gives the base client,
 * built on the first fetch.
 */
internal class PosterFetcher(
    private val resolver: Dns = Dns.SYSTEM,
    newClient: () -> OkHttpClient = ::OkHttpClient,
) {
    private val base by lazy(newClient)
    private val web by lazy { client(lan = false) }
    private val home by lazy { client(lan = true) }

    private fun client(lan: Boolean): OkHttpClient =
        base
            .newBuilder()
            .dns(PosterDns(resolver, lan))
            .proxy(Proxy.NO_PROXY)
            .cookieJar(CookieJar.NO_COOKIES)
            .cache(null)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

    /** The poster of [request], re-encoded as a picture of at most its size; an [IOException] for any refusal. */
    fun picture(request: PosterRequest): Bitmap = posterPicture(download(request), request.width, request.height)

    private fun download(request: PosterRequest): ByteArray {
        val call =
            Request
                .Builder()
                .url(request.url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/jpeg, image/png, image/webp")
                .build()
        (if (request.lan) home else web).newCall(call).execute().use { response ->
            if (response.code != 200) throw IOException("status ${response.code}")
            val type = response.body.contentType()
            if (type?.type != "image" || type.subtype !in IMAGE_TYPES) throw IOException("content type $type")
            val source = response.body.source()
            if (source.request(MAX_BODY_BYTES + 1)) throw IOException("body over $MAX_BODY_BYTES bytes")
            return source.buffer.readByteArray()
        }
    }
}

/**
 * [bytes] decoded and cropped at the centre to the aspect of [width] x [height], then scaled down to that size, never up.
 * A picture with a side under [MIN_SIDE] or over [MAX_SIDE] pixels is refused before its pixels are decoded.
 */
internal fun posterPicture(
    bytes: ByteArray,
    width: Int,
    height: Int,
): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sourceWidth = bounds.outWidth
    val sourceHeight = bounds.outHeight
    if (sourceWidth !in MIN_SIDE..MAX_SIDE || sourceHeight !in MIN_SIDE..MAX_SIDE) {
        throw IOException("picture of $sourceWidth x $sourceHeight")
    }
    val (cropWidth, cropHeight) = crop(sourceWidth, sourceHeight, width, height)
    var sample = 1
    while (cropWidth / (sample * 2) >= width && cropHeight / (sample * 2) >= height) sample *= 2
    val sampled =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("picture does not decode")
    val (sampledWidth, sampledHeight) = crop(sampled.width, sampled.height, width, height)
    val scale = minOf(1f, width / sampledWidth.toFloat(), height / sampledHeight.toFloat())
    return Bitmap
        .createBitmap(
            sampled,
            (sampled.width - sampledWidth) / 2,
            (sampled.height - sampledHeight) / 2,
            sampledWidth,
            sampledHeight,
            Matrix().apply { setScale(scale, scale) },
            true,
        ).also { if (it !== sampled) sampled.recycle() }
}

/** The largest region of a [sourceWidth] x [sourceHeight] picture with the aspect of [width] x [height]. */
private fun crop(
    sourceWidth: Int,
    sourceHeight: Int,
    width: Int,
    height: Int,
): Pair<Int, Int> =
    if (sourceWidth.toLong() * height > sourceHeight.toLong() * width) {
        (sourceHeight.toLong() * width / height).toInt().coerceAtLeast(1) to sourceHeight
    } else {
        sourceWidth to (sourceWidth.toLong() * height / width).toInt().coerceAtLeast(1)
    }
