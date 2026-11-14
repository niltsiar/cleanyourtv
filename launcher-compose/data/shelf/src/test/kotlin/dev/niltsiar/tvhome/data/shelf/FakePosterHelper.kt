package dev.niltsiar.tvhome.data.shelf

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * The poster helper's provider as Home sees it: [pictures] maps a poster URL to the bytes it has fetched, and a URL it has not
 * fetched is a [FileNotFoundException], as the helper answers a miss. Every URI opened is recorded in [opened].
 */
internal class FakePosterHelper : ContentProvider() {
    val pictures = HashMap<String, ByteArray>()
    val opened = ArrayList<Uri>()

    /** The poster URLs asked for, each once, in the order of their first ask. */
    fun urls(): List<String?> = opened.map { it.getQueryParameter(POSTER_PARAM_URL) }.distinct()

    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        opened += uri
        val bytes = pictures[uri.getQueryParameter(POSTER_PARAM_URL)] ?: throw FileNotFoundException("not fetched yet: $uri")
        val file = File.createTempFile("poster", ".png").apply { writeBytes(bytes) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

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
