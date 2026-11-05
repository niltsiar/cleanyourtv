package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.config.HeaderScrim
import dev.niltsiar.tvhome.core.config.TvConfig

/**
 * The theme's say over what is behind Home: the [background] colour that shows where there is no picture, the
 * [skyIntensity] (percent) of the time-of-day sky over the picture, whether a new picture or caption [crossfade]s in,
 * and whether the [headerScrim] follows the picture's brightness.
 */
@Immutable
public data class WallpaperLayout(
    val background: Color = Color.Black,
    val skyIntensity: Int = 0,
    val crossfade: Boolean = true,
    val headerScrim: HeaderScrim = HeaderScrim.Auto,
)

public fun TvConfig.wallpaperLayout(): WallpaperLayout =
    WallpaperLayout(
        background = Color(backgroundColor),
        skyIntensity = skyIntensity,
        crossfade = animations && !reducedMotion,
        headerScrim = headerScrim,
    )
