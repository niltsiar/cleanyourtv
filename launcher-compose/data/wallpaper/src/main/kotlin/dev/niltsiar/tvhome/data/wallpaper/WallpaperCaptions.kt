package dev.niltsiar.tvhome.data.wallpaper

import java.io.File
import java.nio.file.Files

/**
 * Names for a wallpaper collection (wallpaper_captions): `wallpapers/captions.txt`, one "filename<TAB>name" per line.
 * Blank lines, lines starting with # and lines without a tab are skipped; a name is cut to [MAX_LENGTH] characters.
 */
internal object WallpaperCaptions {
    const val FILE_NAME: String = "captions.txt"
    const val MAX_LENGTH: Int = 80
    private const val MAX_FILE_BYTES = 256 * 1024

    fun parse(lines: List<String?>): Map<String, String> {
        val names = HashMap<String, String>()
        for (line in lines) {
            if (line == null || line.isBlank() || line.trim().startsWith("#")) continue
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val file = line.substring(0, tab).trim()
            var name = line.substring(tab + 1).trim()
            if (file.isEmpty() || name.isEmpty()) continue
            if (name.length > MAX_LENGTH) name = name.substring(0, MAX_LENGTH).trim()
            names[file] = name
        }
        return names
    }

    /** The names in [folder]/captions.txt, or none when the file is missing, too large or unreadable. */
    fun read(folder: File?): Map<String, String> {
        val file = folder?.let { File(it, FILE_NAME) }
        if (file == null || !file.isFile || file.length() > MAX_FILE_BYTES) return emptyMap()
        return try {
            parse(Files.readAllLines(file.toPath(), Charsets.UTF_8))
        } catch (_: java.io.IOException) {
            emptyMap()
        }
    }
}
