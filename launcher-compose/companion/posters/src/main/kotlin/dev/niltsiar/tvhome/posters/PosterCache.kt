package dev.niltsiar.tvhome.posters

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

internal const val CACHE_CAP_BYTES = 24L * 1024 * 1024
internal val STALE_AFTER_MILLIS = TimeUnit.DAYS.toMillis(7)

private const val JPEG_QUALITY = 85
private val NAME = Regex("""([0-9a-f]{64})-(\d+)-whole\.jpg""")

/** A cached poster opened for reading, and whether it was fetched more than [STALE_AFTER_MILLIS] ago. */
internal class CachedPoster(
    val file: ParcelFileDescriptor,
    val stale: Boolean,
)

/**
 * The posters in [dir], one JPEG per request key, named `<key>-<fetch time>-whole.jpg` as each keeps the whole picture; a
 * file of any other name, such as a stray temp file or a cropped picture, is deleted when the index is first read. A file's
 * modification time is its last use; the least recently used go first when the files pass [capBytes]. The system may delete
 * any file at any time.
 */
internal class PosterCache(
    private val dir: File,
    private val capBytes: Long = CACHE_CAP_BYTES,
) {
    private class Entry(
        val file: File,
        val fetchedAt: Long,
        var usedAt: Long,
    )

    private var loaded: HashMap<String, Entry>? = null

    /** The poster of [key], marked as used at [now], or null when the cache has none. */
    @Synchronized
    fun open(
        key: String,
        now: Long,
    ): CachedPoster? {
        val entries = entries()
        val entry = entries[key] ?: return null
        val file =
            try {
                ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (_: FileNotFoundException) {
                entries.remove(key)
                return null
            }
        entry.usedAt = now
        entry.file.setLastModified(now)
        return CachedPoster(file, stale = now - entry.fetchedAt > STALE_AFTER_MILLIS)
    }

    /** Stores [picture] as the poster of [key], fetched at [now]; a reader sees the old file or the whole new one. */
    fun write(
        key: String,
        picture: Bitmap,
        now: Long,
    ) {
        // The first read of the index deletes temp files, so it happens before this one exists.
        synchronized(this) { entries() }
        dir.mkdirs()
        val temp = File.createTempFile(key, ".tmp", dir)
        val file = File(dir, "$key-$now-whole.jpg")
        try {
            FileOutputStream(temp).use { out ->
                if (!picture.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("JPEG encode failed")
            }
            if (!temp.renameTo(file)) throw IOException("cannot rename $temp")
        } finally {
            temp.delete()
        }
        file.setLastModified(now)
        synchronized(this) {
            val entries = entries()
            entries.put(key, Entry(file, now, now))?.file?.delete()
            trim(entries)
        }
    }

    private fun trim(entries: HashMap<String, Entry>) {
        var total = entries.values.sumOf { it.file.length() }
        for ((key, entry) in entries.entries.sortedBy { it.value.usedAt }) {
            if (total <= capBytes) break
            total -= entry.file.length()
            entry.file.delete()
            entries.remove(key)
        }
    }

    /** The index, read from [dir] on first use; files of another name and older copies of a key are deleted. */
    private fun entries(): HashMap<String, Entry> =
        loaded ?: HashMap<String, Entry>().also { entries ->
            for (file in dir.listFiles().orEmpty()) {
                val match = NAME.matchEntire(file.name)
                if (match == null) {
                    file.delete()
                    continue
                }
                val (key, fetchedAt) = match.destructured
                val entry = Entry(file, fetchedAt.toLong(), file.lastModified())
                val kept = entries[key]
                when {
                    kept == null -> entries[key] = entry
                    kept.fetchedAt < entry.fetchedAt -> entries.put(key, entry)?.file?.delete()
                    else -> file.delete()
                }
            }
            loaded = entries
        }
}
