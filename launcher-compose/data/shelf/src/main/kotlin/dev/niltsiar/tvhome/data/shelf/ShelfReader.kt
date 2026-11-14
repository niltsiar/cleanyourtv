package dev.niltsiar.tvhome.data.shelf

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract
import android.media.tv.TvContract.Channels
import android.media.tv.TvContract.PreviewPrograms
import android.media.tv.TvContract.WatchNextPrograms
import android.net.Uri
import android.util.Log
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.PublishedChannel
import dev.niltsiar.tvhome.core.model.ShelfChannel
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind
import dev.niltsiar.tvhome.core.model.ShelfPublisher
import dev.niltsiar.tvhome.core.model.watchNextSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

private const val TAG = "TvHome.Shelf"

/** The permission that lets Home read other apps' rows of the TV provider; the SDK keeps its Manifest constant hidden. */
public const val READ_TV_LISTINGS: String = "android.permission.READ_TV_LISTINGS"

/** TvProvider's query parameters; TvContract keeps their constants hidden. */
private const val PARAM_PACKAGE = "package"
private const val PARAM_PREVIEW = "preview"

/** Android 10 added the column; an older provider has none, and the title stands for the series. */
private const val COLUMN_SERIES_ID = "series_id"

/**
 * Reads what apps publish in the TV provider (Watch Next rows and preview channels) for the app shelf, on [io]. Nothing is
 * read while app_shelf is off or Home lacks READ_TV_LISTINGS. The provider shows a caller with that permission the rows it
 * owns and the searchable ones, and refuses a selection, so rows are picked by the `package`, `preview` and `channel`
 * parameters of the URI and filtered here. A provider that refuses or fails gives no rows.
 */
public class ShelfReader(
    private val context: Context,
    private val config: StateFlow<TvConfig>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** True when Home holds READ_TV_LISTINGS. */
    public fun hasAccess(): Boolean = context.checkSelfPermission(READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * The shelf of [packageName] from its ticked app_shelf_sources: its Watch Next rows by last engagement, newest first, then
     * the programs of each ticked preview channel in the provider's channel order, each channel's by weight, after the noise
     * rules of [shelf]. Empty when no source of the package is ticked.
     */
    public suspend fun items(packageName: String): List<ShelfItem> {
        val current = config.value
        if (!current.appShelf || !hasAccess()) return emptyList()
        val watchNext = watchNextSource(packageName) in current.appShelfSources
        val prefix = "$packageName/"
        val channelKeys = current.appShelfSources.filter { it.startsWith(prefix) }.mapTo(HashSet()) { it.removePrefix(prefix) }
        return withContext(io) {
            val rows = ArrayList<ShelfRow>()
            if (watchNext) rows += watchNextRows(packageName)
            if (channelKeys.isNotEmpty()) rows += channelRows(packageName, channelKeys)
            shelf(rows, now())
        }
    }

    /**
     * The installed apps with Watch Next rows or preview channels, by label, each with its count of Watch Next rows and its
     * channels with their counts of programs, before any noise rule. Empty while app_shelf is off or without access.
     */
    public suspend fun publishers(): List<ShelfPublisher> {
        if (!config.value.appShelf || !hasAccess()) return emptyList()
        return withContext(io) {
            val watchNext = HashMap<String, Int>()
            forEachRow(WatchNextPrograms.CONTENT_URI, arrayOf(WatchNextPrograms.COLUMN_PACKAGE_NAME)) { row ->
                row.string(WatchNextPrograms.COLUMN_PACKAGE_NAME)?.let { watchNext.merge(it, 1, Int::plus) }
            }
            val programs = HashMap<Long, Int>()
            forEachRow(PreviewPrograms.CONTENT_URI, arrayOf(PreviewPrograms.COLUMN_CHANNEL_ID)) { row ->
                row.long(PreviewPrograms.COLUMN_CHANNEL_ID)?.let { programs.merge(it, 1, Int::plus) }
            }
            val channels = LinkedHashMap<String, MutableList<PublishedChannel>>()
            forEachRow(previewChannels(null), null) { row ->
                val pkg = row.string(Channels.COLUMN_PACKAGE_NAME) ?: return@forEachRow
                val (id, channel) = row.previewChannel() ?: return@forEachRow
                channels.getOrPut(pkg) { ArrayList() } += PublishedChannel(channel, programs[id] ?: 0)
            }
            (watchNext.keys + channels.keys)
                .mapNotNull { pkg -> label(pkg)?.let { ShelfPublisher(pkg, it, watchNext[pkg] ?: 0, channels[pkg].orEmpty()) } }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
    }

    private fun watchNextRows(pkg: String): List<ShelfRow> {
        val rows = ArrayList<ShelfRow>()
        forEachRow(WatchNextPrograms.CONTENT_URI.ofPackage(pkg), null) { row ->
            row.shelfRow(pkg, row.watchNextKind(), null)?.let { rows += it }
        }
        return rows.sortedByDescending { it.item.lastEngagementMs ?: Long.MIN_VALUE }
    }

    private fun channelRows(
        pkg: String,
        keys: Set<String>,
    ): List<ShelfRow> {
        val ticked = ArrayList<Pair<Long, ShelfChannel>>()
        forEachRow(previewChannels(pkg), null) { row ->
            row.previewChannel()?.takeIf { it.second.key in keys }?.let { ticked += it }
        }
        return ticked.flatMap { (id, channel) ->
            val rows = ArrayList<Pair<ShelfRow, Long>>()
            forEachRow(TvContract.buildPreviewProgramsUriForChannel(id).ofPackage(pkg), null) { row ->
                row.shelfRow(pkg, ShelfKind.Preview, channel)?.let { rows += it to (row.long(PreviewPrograms.COLUMN_WEIGHT) ?: 0L) }
            }
            rows.sortedByDescending { it.second }.map { it.first }
        }
    }

    private fun previewChannels(pkg: String?): Uri {
        val uri =
            Channels.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(PARAM_PREVIEW, true.toString())
                .build()
        return if (pkg == null) uri else uri.ofPackage(pkg)
    }

    private fun label(pkg: String): String? =
        try {
            val pm = context.packageManager
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    /** The provider runs in another process and holds what other apps wrote, so any failure there counts as no more rows. */
    private inline fun forEachRow(
        uri: Uri,
        projection: Array<String>?,
        each: (Cursor) -> Unit,
    ) {
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) each(cursor)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot read $uri: $e")
        }
    }
}

private fun Uri.ofPackage(pkg: String): Uri = buildUpon().appendQueryParameter(PARAM_PACKAGE, pkg).build()

/** The row id and the channel of a preview channel row; null without a row id. */
private fun Cursor.previewChannel(): Pair<Long, ShelfChannel>? {
    val id = long(Channels._ID) ?: return null
    val key = string(Channels.COLUMN_INTERNAL_PROVIDER_ID)?.takeIf { it.isNotBlank() } ?: id.toString()
    return id to ShelfChannel(key, string(Channels.COLUMN_DISPLAY_NAME).orEmpty())
}

private fun Cursor.watchNextKind(): ShelfKind =
    when (long(WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE)?.toInt()) {
        WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE -> ShelfKind.Continue
        WatchNextPrograms.WATCH_NEXT_TYPE_NEW -> ShelfKind.New
        WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST -> ShelfKind.Watchlist
        else -> ShelfKind.Next
    }

/** A Watch Next or preview program row, whose columns share their names; null without a title. A live program is [ShelfKind.Live]. */
private fun Cursor.shelfRow(
    pkg: String,
    kind: ShelfKind,
    channel: ShelfChannel?,
): ShelfRow? {
    val title = string(WatchNextPrograms.COLUMN_TITLE)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val item =
        ShelfItem(
            packageName = pkg,
            title = title,
            kind = if (long(WatchNextPrograms.COLUMN_LIVE) == 1L) ShelfKind.Live else kind,
            seasonNumber = string(WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER),
            episodeNumber = string(WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER),
            episodeTitle = string(WatchNextPrograms.COLUMN_EPISODE_TITLE),
            description = string(WatchNextPrograms.COLUMN_SHORT_DESCRIPTION),
            positionMs = long(WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)?.takeIf { it > 0 },
            durationMs = long(WatchNextPrograms.COLUMN_DURATION_MILLIS)?.takeIf { it > 0 },
            posterUri = string(WatchNextPrograms.COLUMN_POSTER_ART_URI) ?: string(WatchNextPrograms.COLUMN_THUMBNAIL_URI),
            intentUri = string(WatchNextPrograms.COLUMN_INTENT_URI),
            lastEngagementMs = long(WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS)?.takeIf { it > 0 },
            channel = channel,
        )
    return ShelfRow(item, long(WatchNextPrograms.COLUMN_TYPE)?.toInt(), string(COLUMN_SERIES_ID) ?: title)
}

/** A blank string is null. */
private fun Cursor.string(column: String): String? {
    val index = getColumnIndex(column)
    return if (index < 0 || isNull(index)) null else getString(index)?.takeIf { it.isNotBlank() }
}

private fun Cursor.long(column: String): Long? {
    val index = getColumnIndex(column)
    return if (index < 0 || isNull(index)) null else getLong(index)
}
