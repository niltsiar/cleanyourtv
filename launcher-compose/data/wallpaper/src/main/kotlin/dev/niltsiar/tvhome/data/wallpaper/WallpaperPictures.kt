package dev.niltsiar.tvhome.data.wallpaper

import dev.niltsiar.tvhome.core.config.ThemeDirectory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The pictures a theme offers to the on-TV wallpaper choice. */
public class WallpaperPictures(
    private val theme: ThemeDirectory,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** The file names in the order the picker lists them: the theme's wallpaper.jpg, then its wallpapers folder. */
    public suspend fun names(): List<String> =
        withContext(io) { WallpaperRotation.offered(theme.wallpapersFolder, theme.wallpaperFile).map { it.name } }
}
