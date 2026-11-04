package dev.niltsiar.tvhome.core.config

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Lets tools/theme.sh write the theme files into the theme directory on firmware where the adb shell is
 * locked out of Android/data. Only the files [ThemeFiles] allows, and only for callers holding
 * WRITE_SECURE_SETTINGS, which the adb shell has and ordinary apps cannot get.
 *
 *   content write --uri content://<applicationId>.theme/config.json < /data/local/tmp/config.json
 *   content write --uri content://<applicationId>.theme/wallpapers/dusk.jpg < /data/local/tmp/dusk.jpg
 *   content delete --uri content://<applicationId>.theme/config.json
 *   content delete --uri content://<applicationId>.theme/wallpapers      (every image in the collection)
 */
public class ThemeProvider : ContentProvider() {
    private fun themeDir(): File? = context?.getExternalFilesDir(null)

    @Throws(FileNotFoundException::class)
    private fun file(uri: Uri): File {
        val path = ThemeFiles.path(uri.pathSegments)
        val dir = themeDir()
        if (dir == null || path == null) throw FileNotFoundException(uri.toString())
        val file = File(dir, path)
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) throw FileNotFoundException(uri.toString())
        return file
    }

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        val file = file(uri)
        val fileMode =
            if (mode.contains('w')) {
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            } else {
                ParcelFileDescriptor.MODE_READ_ONLY
            }
        return ParcelFileDescriptor.open(file, fileMode)
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        val dir = themeDir()
        if (dir != null && ThemeFiles.isCollection(uri.pathSegments)) {
            val images = File(dir, ThemeFiles.WALLPAPERS).listFiles { _, name -> ThemeFiles.collectionImage(name) }
            return images?.count { it.delete() } ?: 0
        }
        return try {
            if (file(uri).delete()) 1 else 0
        } catch (_: FileNotFoundException) {
            0
        }
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
