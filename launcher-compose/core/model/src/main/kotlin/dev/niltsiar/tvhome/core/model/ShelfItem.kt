package dev.niltsiar.tvhome.core.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Which row of the TV provider a [ShelfItem] comes from: Watch Next by its type, or a preview channel. [NowPlaying] comes from
 * the app's media session instead.
 */
public enum class ShelfKind {
    Continue,
    Next,
    New,
    Watchlist,
    Live,
    Preview,
    NowPlaying,
}

/**
 * A preview channel of an app. [key] is its internal_provider_id, which the app chooses and keeps when it builds the channel
 * again, or its row id when it has none; [name] is its display name.
 */
@Immutable
public data class ShelfChannel(
    val key: String,
    val name: String,
)

/**
 * One item an app publishes in the TV provider, as the shelf shows it. [positionMs] and [durationMs] are null when the app
 * gives none. [intentUri] opens the item and goes through `startShelfItem`. [lastEngagementMs] is null for preview programs,
 * which have no such time. [playing] is what the session plays, set for a [ShelfKind.NowPlaying] item only.
 */
@Immutable
public data class ShelfItem(
    val packageName: String,
    val title: String,
    val kind: ShelfKind,
    val seasonNumber: String? = null,
    val episodeNumber: String? = null,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val posterUri: String? = null,
    val intentUri: String? = null,
    val lastEngagementMs: Long? = null,
    val playing: NowPlayingItem? = null,
)

/** [playing] as the first card of its app's shelf: no intent, so OK opens the app. */
public fun nowPlayingCard(playing: NowPlayingItem): ShelfItem =
    ShelfItem(
        packageName = playing.packageName,
        title = playing.title,
        kind = ShelfKind.NowPlaying,
        posterUri = (playing.art as? NowPlayingArt.Poster)?.uri,
        playing = playing,
    )

/**
 * A shelf item's [picture] as a card draws it. [fill] is unspecified for a picture close enough to the card's shape to cover
 * it, cropped. Otherwise the picture shows whole, centred, and [fill], the average colour of its edge pixels, fills the rest.
 */
@Immutable
public data class ShelfPoster(
    val picture: ImageBitmap,
    val fill: Color = Color.Unspecified,
)

/** A preview channel of a [ShelfPublisher] and how many programs it holds. */
@Immutable
public data class PublishedChannel(
    val channel: ShelfChannel,
    val programs: Int,
)

/** An installed app that publishes rows in the TV provider: [watchNext] Watch Next rows and its preview [channels]. */
@Immutable
public data class ShelfPublisher(
    val packageName: String,
    val label: String,
    val watchNext: Int,
    val channels: List<PublishedChannel>,
)

/** The app_shelf_sources entry that ticks the Watch Next rows of [packageName]. */
public fun watchNextSource(packageName: String): String = packageName

/** The app_shelf_sources entry that ticks one preview channel of [packageName]. */
public fun channelSource(
    packageName: String,
    channelKey: String,
): String = "$packageName/$channelKey"

/** An app_shelf_sources entry read back: the [packageName] it ticks, and the [channelKey] it names, null for Watch Next. */
public data class ShelfSource(
    val packageName: String,
    val channelKey: String?,
)

/** Reads an entry that [watchNextSource] or [channelSource] built. A package name has no slash; a channel key may. */
public fun shelfSourceOf(entry: String): ShelfSource {
    val slash = entry.indexOf('/')
    return if (slash < 0) ShelfSource(entry, null) else ShelfSource(entry.substring(0, slash), entry.substring(slash + 1))
}
