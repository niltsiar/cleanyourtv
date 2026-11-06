package dev.niltsiar.tvhome.core.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/**
 * One launchable app tile. [key] is the activity's component in its short form (`pkg/.Main`): a package can show
 * two tiles, so the key, not the package, names a tile. [image] is the TV banner or the icon, sized for a tile.
 * [accent] is the vivid colour of the art, which the focus highlight and the cinematic shelf take; unspecified while
 * the art is not loaded. [leanback] is true when the package has a LEANBACK_LAUNCHER activity, so its tile launches like
 * the TV's own. [iconArt] is true when [image] is the app's icon drawn in the middle of the tile, which names nothing,
 * rather than a banner. [lightArt] is true when the banner is light enough to need an edge on a light surface.
 */
@Immutable
public data class AppEntry(
    val key: String,
    val packageName: String,
    val label: String,
    val image: ImageBitmap? = null,
    val accent: Color = Color.Unspecified,
    val leanback: Boolean = false,
    val iconArt: Boolean = false,
    val lightArt: Boolean = false,
)

/** The tiles in Home's order, and how many of the leading ones are dock apps (the rest follow in the grid). */
@Immutable
public data class AppList(
    val apps: List<AppEntry> = emptyList(),
    val docked: Int = 0,
)
