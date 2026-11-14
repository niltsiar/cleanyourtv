package dev.niltsiar.tvhome.data.shelf

import android.media.tv.TvContract.WatchNextPrograms
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind

/** The most items one app's shelf shows. */
internal const val SHELF_MAX: Int = 5

/** An item engaged with longer ago than this is dropped. */
internal const val SHELF_MAX_AGE_MS: Long = 30L * 24 * 60 * 60 * 1000

/** A "continue" item this far through is finished in all but name. */
internal const val SHELF_DONE_FRACTION: Double = 0.95

/** A row read from the provider: the item, its program [type] (null when the app gives none) and the key of its [series]. */
internal class ShelfRow(
    val item: ShelfItem,
    val type: Int?,
    val series: String,
)

/**
 * The noise rules over [rows] in the order the app gives them: live programs, channel and event programs and watchlist entries
 * go, so do "continue" items at [SHELF_DONE_FRACTION] or more and items engaged with more than [SHELF_MAX_AGE_MS] before
 * [now]. Of the rest, the first of each series stays, up to [SHELF_MAX].
 */
internal fun shelf(
    rows: List<ShelfRow>,
    now: Long,
): List<ShelfItem> {
    val series = HashSet<String>()
    return rows
        .asSequence()
        .filterNot { it.item.kind == ShelfKind.Live || it.item.kind == ShelfKind.Watchlist }
        .filterNot { it.type == WatchNextPrograms.TYPE_CHANNEL || it.type == WatchNextPrograms.TYPE_EVENT }
        .filterNot { it.item.kind == ShelfKind.Continue && it.item.done() }
        .filterNot { row -> row.item.lastEngagementMs?.let { now - it > SHELF_MAX_AGE_MS } == true }
        .filter { series.add(it.series) }
        .map { it.item }
        .take(SHELF_MAX)
        .toList()
}

private fun ShelfItem.done(): Boolean {
    val position = positionMs ?: return false
    val duration = durationMs ?: return false
    return position.toDouble() / duration >= SHELF_DONE_FRACTION
}
