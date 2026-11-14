package dev.niltsiar.tvhome.data.shelf

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.media.tv.TvContract.Channels
import android.media.tv.TvContract.PreviewPrograms
import android.media.tv.TvContract.WatchNextPrograms
import android.net.Uri

/**
 * The TV provider as TvProvider answers a caller with READ_TV_LISTINGS and without ACCESS_ALL_EPG_DATA: a selection is refused,
 * and the `package`, `preview` and `channel` parameters of the URI pick the rows. Every query is recorded in [queries];
 * [refuse] makes each one throw, as the provider does without the permission.
 */
internal class FakeTvProvider : ContentProvider() {
    val watchNext = ArrayList<Map<String, Any?>>()
    val channels = ArrayList<Map<String, Any?>>()
    val programs = ArrayList<Map<String, Any?>>()
    val queries = ArrayList<Uri>()
    var refuse = false

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (refuse) throw SecurityException("Access denied to $uri")
        if (selection != null) throw SecurityException("Selection not allowed for $uri")
        queries += uri
        val table =
            when (uri.pathSegments.first()) {
                "watch_next_program" -> {
                    watchNext
                }

                "channel" -> {
                    val preview = uri.getQueryParameter("preview") == "true"
                    channels.filter { !preview || it[Channels.COLUMN_TYPE] == Channels.TYPE_PREVIEW }
                }

                "preview_program" -> {
                    val channel = uri.getQueryParameter("channel")
                    if (channel == null) programs else programs.filter { it[PreviewPrograms.COLUMN_CHANNEL_ID].toString() == channel }
                }

                else -> {
                    error("not a TV provider table: $uri")
                }
            }
        val pkg = uri.getQueryParameter("package")
        val rows = table.filter { pkg == null || it[WatchNextPrograms.COLUMN_PACKAGE_NAME] == pkg }
        val columns = projection?.toList() ?: rows.flatMap { it.keys }.distinct()
        return MatrixCursor(columns.toTypedArray()).apply { rows.forEach { row -> addRow(columns.map { row[it] }) } }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
