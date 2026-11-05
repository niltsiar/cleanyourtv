package dev.niltsiar.tvhome.core.config

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

private const val TAG = "TvHome.Config"

/**
 * The folder tools/theme.sh pushes a theme into (the app's external files directory), and the files in it:
 * config.json, wallpaper.jpg, a wallpapers/ collection with its captions.txt, and theme_revision.
 * Accessors return the paths; a file may not exist.
 */
public class ThemeDirectory(
    private val context: Context,
) {
    /** Null while external storage is unavailable. */
    public val dir: File? get() = context.getExternalFilesDir(null)
    public val wallpaperFile: File? get() = dir?.resolve(ThemeFiles.WALLPAPER_JPG)
    public val wallpapersFolder: File? get() = dir?.resolve(ThemeFiles.WALLPAPERS)

    /**
     * config.json parsed over the defaults. Home can start before external storage is mounted, so then the
     * copy kept in filesDir at the last read stands in; on mounted storage a file that is missing, too large or
     * not a JSON object deletes the copy, as the reference launcher does.
     */
    public fun loadConfig(): ParsedConfig {
        val mounted = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        val copy = context.filesDir.resolve(ThemeFiles.CONFIG_JSON)
        val parsed = readConfig(dir?.resolve(ThemeFiles.CONFIG_JSON), copy, mounted)
        if (parsed.unknownKeys.isNotEmpty()) Log.w(TAG, "unknown keys in config.json, ignored: ${parsed.unknownKeys.joinToString()}")
        if (parsed.unsupportedKeys.isNotEmpty()) Log.i(TAG, "unused keys in config.json: ${parsed.unsupportedKeys.joinToString()}")
        if (parsed.rejectedKeys.isNotEmpty()) Log.w(TAG, "bad values in config.json, defaults kept: ${parsed.rejectedKeys.joinToString()}")
        return parsed
    }
}

internal fun readConfig(
    file: File?,
    copy: File,
    mounted: Boolean,
): ParsedConfig {
    if (!mounted) return parseTvConfig(readText(copy).orEmpty())
    val text = readText(file)
    val parsed = parseTvConfig(text.orEmpty())
    if (text == null || !parsed.overlayApplied) {
        copy.delete()
    } else {
        runCatching { copy.writeText(text) }.onFailure { Log.w(TAG, "could not keep a copy of $file: ${it.message}") }
    }
    return parsed
}

/** The file's text, or null when it is missing, too large or unreadable. */
private fun readText(file: File?): String? {
    if (file == null || !file.isFile) return null
    if (file.length() > MAX_CONFIG_BYTES) {
        Log.w(TAG, "ignoring $file: larger than ${MAX_CONFIG_BYTES / 1024} KB")
        return null
    }
    return runCatching { file.readText() }.onFailure { Log.w(TAG, "ignoring $file: ${it.message}") }.getOrNull()
}
