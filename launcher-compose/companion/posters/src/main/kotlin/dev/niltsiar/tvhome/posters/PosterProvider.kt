package dev.niltsiar.tvhome.posters

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.FileNotFoundException

/**
 * Serves Home the posters it asks for at `content://dev.niltsiar.tvhome.posters/poster?u=<url>&w=<px>&h=<px>[&lan=1]`, to a
 * caller signed with this app's key only, read-only. A poster not cached yet is a [FileNotFoundException] at once, and a
 * fetch is queued. Nothing else is served.
 */
class PosterProvider : ContentProvider() {
    internal lateinit var posters: Posters
    private lateinit var packages: PackageManager

    override fun onCreate(): Boolean {
        val context = checkNotNull(context)
        packages = context.packageManager
        posters = Posters(context)
        return true
    }

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        // A caller that could choose the URLs would use the helper as its network access.
        val signatures = packages.checkSignatures(Binder.getCallingUid(), Process.myUid())
        if (signatures != PackageManager.SIGNATURE_MATCH) throw SecurityException("caller not signed with this app's key")
        if (mode != "r") throw SecurityException("read-only, not $mode")
        val request = posterRequest(uri) ?: throw FileNotFoundException("not a poster this helper fetches")
        return posters.open(request) ?: throw FileNotFoundException("not fetched yet")
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
    ): Uri = throw UnsupportedOperationException("read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("read-only")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("read-only")
}
