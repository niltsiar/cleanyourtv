package dev.niltsiar.tvhome.data.shelf

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import dev.niltsiar.tvhome.core.model.NowPlayingArt
import dev.niltsiar.tvhome.core.model.NowPlayingItem
import dev.niltsiar.tvhome.core.model.ShelfPoster
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "TvHome.NowPlaying"

/** How long a paused session stays on Home after its last state update. */
internal const val PAUSED_SHOWN_MS: Long = 60 * 60 * 1000L

private val HIDDEN_STATES = setOf(PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR)
private val LOCAL_SCHEMES = setOf(ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_ANDROID_RESOURCE)
private val PICTURE_KEYS =
    arrayOf(MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)
private val PICTURE_URI_KEYS =
    arrayOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_ART_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
    )

/**
 * Reads what an app's media session plays, through the notification access of [listener]. Without that access, which
 * `MediaSessionManager` reports with a SecurityException, there is no item. Session text is never logged.
 */
public class NowPlaying(
    context: Context,
    private val listener: ComponentName,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    /**
     * The session of [packageName] while collected, or null when it has none to show: no session, state none, stopped or
     * error, no title, or paused for over [PAUSED_SHOWN_MS]. The sessions are read once when collection starts, then
     * followed through session and controller callbacks, which are removed when it stops; collect it only while Home is
     * started. A picture the session carries is scaled to [artWidth] x [artHeight], on the io dispatcher.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun item(
        packageName: String,
        artWidth: Int,
        artHeight: Int,
    ): Flow<NowPlayingItem?> {
        require(artWidth > 0 && artHeight > 0) { "art size $artWidth x $artHeight" }
        return sessionOf(packageName)
            .transformLatest { session ->
                val item = session?.let { nowPlayingItem(it, clock(), artWidth, artHeight) }
                emit(item)
                val state = session?.state
                if (item != null && item.paused && state != null) {
                    delay(state.lastPositionUpdateTime + PAUSED_SHOWN_MS - clock())
                    emit(null)
                }
            }.flowOn(io)
    }

    private fun sessionOf(packageName: String): Flow<Session?> =
        callbackFlow {
            val follower = Follower(packageName, channel)
            main.post(follower::start)
            awaitClose { main.post(follower::stop) }
        }

    /** Follows the first session of [packageName]; every call runs on the main thread. */
    private inner class Follower(
        private val packageName: String,
        private val out: SendChannel<Session?>,
    ) : MediaController.Callback(),
        MediaSessionManager.OnActiveSessionsChangedListener {
        private var controller: MediaController? = null

        fun start() {
            try {
                sessions.addOnActiveSessionsChangedListener(this, listener, main)
                follow(sessions.getActiveSessions(listener))
            } catch (e: SecurityException) {
                Log.w(TAG, "no notification access for $listener: $e")
                out.trySend(null)
            }
        }

        fun stop() {
            sessions.removeOnActiveSessionsChangedListener(this)
            controller?.unregisterCallback(this)
            controller = null
        }

        override fun onActiveSessionsChanged(controllers: List<MediaController>?) = follow(controllers)

        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()

        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()

        override fun onSessionDestroyed() = follow(null)

        private fun follow(controllers: List<MediaController>?) {
            val next = controllers?.firstOrNull { it.packageName == packageName }
            if (next?.sessionToken != controller?.sessionToken) {
                controller?.unregisterCallback(this)
                next?.registerCallback(this, main)
                controller = next
            }
            publish()
        }

        private fun publish() {
            out.trySend(controller?.let { Session(it.packageName, it.playbackState, it.metadata) })
        }
    }
}

/** A session as one callback left it. */
internal class Session(
    val packageName: String,
    val state: PlaybackState?,
    val metadata: MediaMetadata?,
)

/** The item [session] shows at [now] on the elapsed-realtime clock, or null when it shows nothing. */
internal fun nowPlayingItem(
    session: Session,
    now: Long,
    artWidth: Int,
    artHeight: Int,
): NowPlayingItem? {
    val state = session.state ?: return null
    if (state.state in HIDDEN_STATES) return null
    val paused = state.state == PlaybackState.STATE_PAUSED
    if (paused && now - state.lastPositionUpdateTime > PAUSED_SHOWN_MS) return null
    val metadata = session.metadata ?: return null
    val title =
        metadata.text(MediaMetadata.METADATA_KEY_TITLE) ?: metadata.description.title.textOrNull() ?: return null
    val artist = metadata.text(MediaMetadata.METADATA_KEY_ARTIST) ?: metadata.description.subtitle.textOrNull()
    return NowPlayingItem(session.packageName, title, artist, paused, nowPlayingArt(metadata, artWidth, artHeight))
}

/** First match: a picture in the metadata, a local picture URI, an `https` one. */
private fun nowPlayingArt(
    metadata: MediaMetadata,
    width: Int,
    height: Int,
): NowPlayingArt? {
    PICTURE_KEYS.firstNotNullOfOrNull(metadata::getBitmap)?.let { return NowPlayingArt.Picture(scaledArt(it, width, height)) }
    val uris = PICTURE_URI_KEYS.mapNotNull(metadata::getString)

    fun scheme(uri: String) = Uri.parse(uri).scheme?.lowercase()
    val uri = uris.firstOrNull { scheme(it) in LOCAL_SCHEMES } ?: uris.firstOrNull { scheme(it) == "https" } ?: return null
    return NowPlayingArt.Poster(uri)
}

/**
 * [bitmap] at the size a card of [width] x [height] draws it, as a decoded poster is: the least size that covers the card, or
 * for a picture the card [drawsWhole], the greatest that fits in it. A smaller picture keeps its size.
 */
internal fun scaledArt(
    bitmap: Bitmap,
    width: Int,
    height: Int,
): ShelfPoster {
    val whole = drawsWhole(bitmap.width, bitmap.height, width, height)
    val widthScale = width.toFloat() / bitmap.width
    val heightScale = height.toFloat() / bitmap.height
    val scale = if (whole) min(widthScale, heightScale) else max(widthScale, heightScale)
    val picture =
        if (scale >= 1f) {
            bitmap
        } else {
            val scaledWidth = max(1, (bitmap.width * scale).roundToInt())
            val scaledHeight = max(1, (bitmap.height * scale).roundToInt())
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        }
    return ShelfPoster(picture.asImageBitmap(), if (whole) posterFill(picture) else Color.Unspecified)
}

private fun MediaMetadata.text(key: String): String? = getText(key).textOrNull()

private fun CharSequence?.textOrNull(): String? = this?.toString()?.trim()?.takeIf { it.isNotEmpty() }
