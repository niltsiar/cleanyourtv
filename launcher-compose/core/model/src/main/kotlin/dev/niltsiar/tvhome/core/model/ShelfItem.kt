package dev.niltsiar.tvhome.core.model

import androidx.compose.runtime.Immutable

/** Which row of the TV provider a [ShelfItem] comes from: Watch Next by its type, or a preview channel. */
public enum class ShelfKind {
    Continue,
    Next,
    New,
    Watchlist,
    Live,
    Preview,
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
 * gives none. [intentUri] opens the item and goes through `shelfIntent` before use. [lastEngagementMs] is null for preview
 * programs, which have no such time. [channel] is the preview channel the item is in, null for a Watch Next row.
 */
@Immutable
public data class ShelfItem(
    val packageName: String,
    val title: String,
    val kind: ShelfKind,
    val seasonNumber: String? = null,
    val episodeNumber: String? = null,
    val episodeTitle: String? = null,
    val description: String? = null,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val posterUri: String? = null,
    val intentUri: String? = null,
    val lastEngagementMs: Long? = null,
    val channel: ShelfChannel? = null,
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
