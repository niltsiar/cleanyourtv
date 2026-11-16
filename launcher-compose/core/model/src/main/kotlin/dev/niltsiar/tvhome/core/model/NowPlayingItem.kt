package dev.niltsiar.tvhome.core.model

import androidx.compose.runtime.Immutable

/** What an app's media session plays, as Home shows it. [artist] is null when the session gives none. */
@Immutable
public data class NowPlayingItem(
    val packageName: String,
    val title: String,
    val artist: String?,
    val paused: Boolean,
    val art: NowPlayingArt?,
)

/** The picture of a [NowPlayingItem]. */
@Immutable
public sealed interface NowPlayingArt {
    /** A picture the session carries, already scaled for the card. */
    public data class Picture(
        val poster: ShelfPoster,
    ) : NowPlayingArt

    /** A `content://`, `android.resource://` or `https` picture, read as a shelf item's poster is. */
    public data class Poster(
        val uri: String,
    ) : NowPlayingArt
}
