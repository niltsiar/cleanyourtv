package dev.niltsiar.tvhome.data.apps

import android.util.AtomicFile
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

private const val TAG = "TvHome.Library"

internal const val MAX_ADDED: Int = 128
private const val MAX_BYTES = MAX_ADDED * 256
private const val MAX_PACKAGE_LENGTH = 255

/** The apps the owner added to Home from the library, one package per line. */
internal const val LIBRARY_ADDED_FILE = "library_apps.txt"

/** The apps the owner took off Home, whether the theme or the TV had put them there. */
internal const val LIBRARY_REMOVED_FILE = "library_home_removed.txt"

/**
 * What the owner chose in the library: [added] packages show on Home though they are not TV apps or are hidden, and
 * [removed] packages never show. A removal wins over everything else.
 */
internal data class LibraryChoices(
    val added: Set<String> = emptySet(),
    val removed: Set<String> = emptySet(),
) {
    /** A package that has a tile on Home: not removed, and either added or a TV app the theme does not hide. */
    fun showsOnHome(
        packageName: String,
        tvApp: Boolean,
        hidden: Boolean,
    ): Boolean = packageName !in removed && (packageName in added || (tvApp && !hidden))
}

/** [saved] with [pkg] added or taken out. Throws for a name that is not a package, or when the list would pass [MAX_ADDED]. */
internal fun changeChoices(
    saved: Set<String>,
    pkg: String,
    added: Boolean,
): Set<String> {
    require(isValidPackage(pkg)) { "Invalid package" }
    val next = if (added) saved + pkg else saved - pkg
    require(next.size <= MAX_ADDED) { "Too many shortcuts" }
    return next
}

/** One package per line in sorted order. */
internal fun encodeChoices(packages: Set<String>): String {
    require(packages.size <= MAX_ADDED) { "Too many shortcuts" }
    return packages.sorted().joinToString("") { pkg ->
        require(isValidPackage(pkg)) { "Invalid package" }
        "$pkg\n"
    }
}

/** The packages in [text]. Throws for a blank, malformed or repeated line, or too many lines. */
internal fun decodeChoices(text: String): Set<String> {
    require(text.length <= MAX_BYTES) { "Shortcut file too large" }
    val result = LinkedHashSet<String>()
    val lines = text.split('\n').let { if (it.last().isEmpty()) it.dropLast(1) else it }
    for (line in lines) {
        require(isValidPackage(line) && result.add(line) && result.size <= MAX_ADDED) { "Invalid shortcut file" }
    }
    return result
}

/** A Java package name: dot-separated words of letters, digits and underscores, none starting with a digit. */
internal fun isValidPackage(pkg: String?): Boolean {
    if (pkg.isNullOrEmpty() || pkg.length > MAX_PACKAGE_LENGTH) return false
    var first = true
    for (c in pkg) {
        if (c == '.') {
            if (first) return false
            first = true
        } else {
            val letter = c in 'a'..'z' || c in 'A'..'Z' || c == '_'
            if (!letter && (first || c !in '0'..'9')) return false
            first = false
        }
    }
    return !first
}

/** The two choice files in the app's private files. Reads and writes run on one thread at a time. */
internal class LibraryStore(
    private val dir: File,
) {
    private val addedFile get() = AtomicFile(File(dir, LIBRARY_ADDED_FILE))
    private val removedFile get() = AtomicFile(File(dir, LIBRARY_REMOVED_FILE))

    /** Both files, each empty when missing or unreadable. */
    fun load(): LibraryChoices = LibraryChoices(readLenient(addedFile), readLenient(removedFile))

    /**
     * Puts [pkg] on Home ([onHome]) or takes it off. Adding writes the added file and then clears a removal; removing
     * writes the removed file and then drops an earlier addition. False when a file could not be read or written; an
     * unreadable file is left as it is, so the owner's choices are never replaced by an empty list.
     */
    fun save(
        pkg: String,
        onHome: Boolean,
    ): Boolean {
        val added: Set<String>
        val removed: Set<String>
        try {
            added = readStrict(addedFile)
            removed = readStrict(removedFile)
        } catch (e: IOException) {
            Log.w(TAG, "keeping unreadable shortcut choices unchanged: $e")
            return false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "keeping unreadable shortcut choices unchanged: $e")
            return false
        }
        if (onHome) {
            if (!write(addedFile, added, pkg, true)) return false
            return pkg !in removed || write(removedFile, removed, pkg, false)
        }
        if (!write(removedFile, removed, pkg, true)) return false
        if (pkg in added) write(addedFile, added, pkg, false)
        return true
    }

    private fun readLenient(file: AtomicFile): Set<String> =
        try {
            readStrict(file)
        } catch (e: IOException) {
            Log.w(TAG, "cannot read shortcuts: $e")
            emptySet()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "cannot read shortcuts: $e")
            emptySet()
        }

    private fun readStrict(file: AtomicFile): Set<String> {
        val base = file.baseFile
        if (!base.exists() && !File("$base.bak").exists()) return emptySet()
        file.openRead().use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (bytes.size() + count > MAX_BYTES) throw IOException("Shortcut file too large")
                bytes.write(buffer, 0, count)
            }
            return decodeChoices(String(bytes.toByteArray(), Charsets.UTF_8))
        }
    }

    private fun write(
        file: AtomicFile,
        saved: Set<String>,
        pkg: String,
        added: Boolean,
    ): Boolean {
        val bytes =
            try {
                encodeChoices(changeChoices(saved, pkg, added)).toByteArray(Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "cannot save shortcut choice: $e")
                return false
            }
        val out =
            try {
                file.startWrite()
            } catch (e: IOException) {
                Log.w(TAG, "cannot save shortcut choice: $e")
                return false
            }
        return try {
            out.write(bytes)
            file.finishWrite(out)
            true
        } catch (e: IOException) {
            file.failWrite(out)
            Log.w(TAG, "cannot save shortcut choice: $e")
            false
        }
    }
}
