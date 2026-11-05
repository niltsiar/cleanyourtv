package dev.niltsiar.tvhome.core.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The picture behind Home, sized to cover the screen. [caption] is its name when the theme shows names, [sky] the
 * time-of-day colours when the theme asks for them. A null [picture] shows the theme's background colour.
 * [thumbnail] is the picture as the screen shows it, averaged down to 120 pixels wide, in CPU memory.
 */
@Immutable
public data class Wallpaper(
    val picture: ImageBitmap? = null,
    val caption: String? = null,
    val sky: SkyPalette? = null,
    val thumbnail: ImageBitmap? = null,
)

/**
 * The sky at one minute of the day, as opaque ARGB colours from the [top] of the screen to its [bottom]. [glow] is the
 * warm colour of the glow of the sun, [sun] is that glow's alpha (0 to 255) and [sunX] its place across the
 * screen, 0 at the left edge and 1 at the right.
 */
@Immutable
public data class SkyPalette(
    val top: Int,
    val mid: Int,
    val bottom: Int,
    val glow: Int,
    val sun: Int,
    val sunX: Float,
)
