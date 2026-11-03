package dev.niltsiar.tvhome.core.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** One launchable app as the Home screen lists it. [image] is the TV banner or the icon, sized for a tile. */
@Immutable
public data class AppEntry(
    val packageName: String,
    val label: String,
    val image: ImageBitmap? = null,
)
