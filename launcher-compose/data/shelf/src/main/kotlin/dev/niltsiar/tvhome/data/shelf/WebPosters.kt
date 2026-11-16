package dev.niltsiar.tvhome.data.shelf

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.util.Log
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.PosterHelper
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "TvHome.Shelf"

/** The poster helper app's package; it must own [POSTER_HELPER_AUTHORITY]. */
internal const val POSTER_HELPER_PACKAGE = "dev.niltsiar.tvhome.posters"

/** The authority of the poster helper's read-only provider. The helper declares the same value. */
internal const val POSTER_HELPER_AUTHORITY = "dev.niltsiar.tvhome.posters"

/** The path of a poster under [POSTER_HELPER_AUTHORITY]; the helper notifies changes on that URI with no query. */
internal const val POSTER_HELPER_PATH = "poster"

/** The query parameter that carries the poster's URL. */
internal const val POSTER_PARAM_URL = "u"

/** The query parameters of the card's width and height in pixels. */
internal const val POSTER_PARAM_WIDTH = "w"
internal const val POSTER_PARAM_HEIGHT = "h"

/** The query parameter, set to 1, that lets the helper fetch an `http` poster from the home network. */
internal const val POSTER_PARAM_LAN = "lan"

private val helperPosters: Uri =
    Uri
        .Builder()
        .scheme("content")
        .authority(POSTER_HELPER_AUTHORITY)
        .path(POSTER_HELPER_PATH)
        .build()

/**
 * Whether the poster helper can serve Home: its package owns [POSTER_HELPER_AUTHORITY] and is signed with Home's key. Home
 * sends a poster URL only to such a helper, as the URLs tell what the household watches.
 */
public fun posterHelper(context: Context): PosterHelper {
    val packages = context.packageManager
    val provider = packages.resolveContentProvider(POSTER_HELPER_AUTHORITY, 0)
    return when {
        provider == null || provider.packageName != POSTER_HELPER_PACKAGE -> PosterHelper.Missing
        packages.checkSignatures(context.packageName, provider.packageName) != PackageManager.SIGNATURE_MATCH -> PosterHelper.OtherKey
        else -> PosterHelper.Ready
    }
}

/** The helper's URI for the picture at [poster], to cover [width] x [height] pixels; [lan] for an `http` poster. */
internal fun helperPosterUri(
    poster: String,
    width: Int,
    height: Int,
    lan: Boolean,
): Uri =
    helperPosters
        .buildUpon()
        .appendQueryParameter(POSTER_PARAM_URL, poster)
        .appendQueryParameter(POSTER_PARAM_WIDTH, width.toString())
        .appendQueryParameter(POSTER_PARAM_HEIGHT, height.toString())
        .apply { if (lan) appendQueryParameter(POSTER_PARAM_LAN, "1") }
        .build()

/**
 * The posters Home asks the poster helper for: an `https` one with app_shelf_web_posters, an `http` one with
 * app_shelf_lan_posters too, and only while the helper is [PosterHelper.Ready], which is checked once per [onStart].
 */
internal class WebPosters(
    private val context: Context,
    private val config: StateFlow<TvConfig>,
) {
    @Volatile private var helper: PosterHelper? = null
    private val generation = MutableStateFlow(0)
    private val asked = MutableStateFlow(false)
    private val started = MutableStateFlow(false)

    /**
     * Rises each time the helper may have new pictures, and on an [onStart] to retry. While collected, and once Home asked
     * the helper for a poster, it watches the helper's notifications until Home stops.
     */
    val changes: Flow<Int> =
        channelFlow {
            launch { combine(started, asked) { on, any -> on && any }.collectLatest { if (it) watch() } }
            generation.collect { send(it) }
        }

    /** True when a poster of [scheme] goes to the helper now. */
    fun serves(scheme: String?): Boolean {
        val on = config.value
        val allowed =
            when (scheme) {
                "https" -> on.appShelfWebPosters
                "http" -> on.appShelfWebPosters && on.appShelfLanPosters
                else -> false
            }
        return allowed && (helper ?: posterHelper(context).also { helper = it }) == PosterHelper.Ready
    }

    /** The helper's URI for [poster], whose scheme [serves] allowed; [lan] for an `http` one. */
    fun request(
        poster: String,
        lan: Boolean,
        width: Int,
        height: Int,
    ): Uri {
        asked.value = true
        return helperPosterUri(poster, width, height, lan)
    }

    /**
     * Home came to the front: the helper may have been installed, removed, or have fetched pictures meanwhile. [retry], for a
     * poster that failed and may be readable now, raises [changes] even when the helper was never asked.
     */
    fun onStart(retry: Boolean) {
        helper = null
        if (asked.value || retry) generation.update { it + 1 }
        started.value = true
    }

    /** Raises [changes], for a poster that failed and may be readable now. */
    fun raise() {
        generation.update { it + 1 }
    }

    fun onStop() {
        started.value = false
    }

    private suspend fun watch() {
        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    generation.update { it + 1 }
                }
            }
        try {
            context.contentResolver.registerContentObserver(helperPosters, false, observer)
        } catch (e: SecurityException) {
            // The helper was removed after Home asked it for a poster: its authority no longer resolves.
            Log.w(TAG, "cannot watch the poster helper: $e")
            return
        }
        try {
            awaitCancellation()
        } finally {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
}
