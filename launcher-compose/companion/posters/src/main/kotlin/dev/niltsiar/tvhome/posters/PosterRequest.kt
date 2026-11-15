package dev.niltsiar.tvhome.posters

import android.net.Uri
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.security.MessageDigest
import kotlin.math.roundToInt

/** The provider's authority, which Home's `POSTER_HELPER_AUTHORITY` names; it is also the helper's package. */
internal const val POSTER_AUTHORITY = "dev.niltsiar.tvhome.posters"

/** The path of a poster; the helper notifies changes on [POSTER_CHANGES], this path with no query. */
internal const val POSTER_PATH = "poster"

/** The query parameter that carries the poster's URL. */
internal const val PARAM_URL = "u"

/** The query parameters of the size Home wants, in pixels. */
internal const val PARAM_WIDTH = "w"
internal const val PARAM_HEIGHT = "h"

/** The query parameter that, set to 1, lets the poster be on the home network, and only there. */
internal const val PARAM_LAN = "lan"

internal const val MAX_URL_LENGTH = 2048

/** The largest size a stored poster is scaled to cover: Home's size is scaled down to fit in it. */
internal const val MAX_WIDTH = 640
internal const val MAX_HEIGHT = 360

/** A poster Home asked for, whose [url] passed the rules of its mode, to cover [width] x [height] pixels. */
internal data class PosterRequest(
    val url: HttpUrl,
    val lan: Boolean,
    val width: Int,
    val height: Int,
) {
    /** The cache key of the URL and the size, for the picture kept whole. */
    val key: String =
        MessageDigest
            .getInstance("SHA-256")
            .digest("whole $url ${width}x$height".toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** The request in [uri], or null when it is not a poster URI, its size is not positive, or its URL breaks the rules. */
internal fun posterRequest(uri: Uri): PosterRequest? {
    if (uri.pathSegments != listOf(POSTER_PATH)) return null
    val lan = uri.getQueryParameter(PARAM_LAN) == "1"
    val url = uri.getQueryParameter(PARAM_URL)?.let { posterUrl(it, lan) } ?: return null
    val width = uri.getQueryParameter(PARAM_WIDTH)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val height = uri.getQueryParameter(PARAM_HEIGHT)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val scale = minOf(1f, MAX_WIDTH / width.toFloat(), MAX_HEIGHT / height.toFloat())
    return PosterRequest(
        url,
        lan,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
    )
}

/**
 * [raw] as a URL the helper may fetch. Without [lan]: `https` on port 443 to a DNS host name. With [lan]: `http` or `https` on
 * any port, to a host name or to a private IP literal. Never user info, never longer than [MAX_URL_LENGTH]. The addresses a
 * host name resolves to are checked by [PosterDns] when the fetch connects.
 */
internal fun posterUrl(
    raw: String,
    lan: Boolean,
): HttpUrl? {
    if (raw.length > MAX_URL_LENGTH) return null
    val url = raw.toHttpUrlOrNull() ?: return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    val schemeAllowed =
        when (url.scheme) {
            "https" -> lan || url.port == 443
            "http" -> lan
            else -> false
        }
    if (!schemeAllowed) return null
    // OkHttp connects to an IP literal without asking its Dns, so the literal's range is checked here.
    val literal = ipLiteral(url.host)
    val hostAllowed = if (literal != null) lan && addressRange(literal) == AddressRange.Private else isHostName(url.host)
    return url.takeIf { hostAllowed }
}

private val IPV4 = Regex("""((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)""")

/** The address of [host] when it is an IPv6 literal (OkHttp drops the brackets) or a dotted-quad IPv4 literal. */
private fun ipLiteral(host: String): InetAddress? = if (':' in host || IPV4.matches(host)) InetAddress.getByName(host) else null

/** Whether [host] is a DNS name: its last label starts with a letter, which no numeric form of an IPv4 address does. */
private fun isHostName(host: String): Boolean =
    host
        .removeSuffix(".")
        .substringAfterLast('.')
        .firstOrNull() in 'a'..'z'
