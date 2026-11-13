package dev.niltsiar.tvhome.data.wallpaper

import androidx.compose.ui.graphics.ImageBitmap
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.model.WallpaperPicture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The pictures a theme offers to the on-TV wallpaper choice. */
public class WallpaperPictures(
    private val theme: ThemeDirectory,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * The pictures in the order the picker lists them: the theme's wallpaper.jpg, then its wallpapers folder, each folder
     * picture with its caption from captions.txt whether wallpaper_captions is on or not.
     */
    public suspend fun pictures(): List<WallpaperPicture> =
        withContext(io) {
            val folder = theme.wallpapersFolder
            val single = WallpaperRotation.usable(theme.wallpaperFile)
            val captions = WallpaperCaptions.read(folder)
            WallpaperRotation.offered(folder, single).map { file ->
                if (file == single) WallpaperPicture(file.name, single = true) else WallpaperPicture(file.name, captions[file.name])
            }
        }

    /** The centre of [picture] at [width] x [height] pixels, or null when its file cannot be decoded. */
    public suspend fun thumbnail(
        picture: WallpaperPicture,
        width: Int,
        height: Int,
    ): ImageBitmap? =
        withContext(io) {
            val file = if (picture.single) theme.wallpaperFile else theme.wallpapersFolder?.resolve(picture.fileName)
            file?.let { WallpaperThumbnail.decode(it, width, height) }
        }
}
