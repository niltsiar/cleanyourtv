package dev.niltsiar.tvhome.core.model

import androidx.compose.runtime.Immutable

/**
 * A picture the Wallpaper picker offers. [fileName] is what a pick stores. [caption] is its line in
 * `wallpapers/captions.txt`, or null. [single] is true for the theme's `wallpaper.jpg`, false for a picture of its
 * `wallpapers/` folder.
 */
@Immutable
public data class WallpaperPicture(
    val fileName: String,
    val caption: String? = null,
    val single: Boolean = false,
)
