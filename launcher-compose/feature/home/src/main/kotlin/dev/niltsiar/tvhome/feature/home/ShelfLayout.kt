package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.focusRingColour

/**
 * The theme's cinematic shelf: whether it is [cinematic], the [artAlpha] of the focused app's art and the [scrimAlpha]
 * of the scrim (0 to 1, 0 leaves them out), the position [dots], the [whiteFocus] highlight on the dock, [appGlow] (the
 * app's colour on every tile), the [ambientGlow] behind the shelf and whether it takes a new colour in one frame
 * ([glowSnap]). [animated] is false for a theme without animations or with reduced motion.
 */
@Immutable
public data class ShelfLayout(
    val cinematic: Boolean = false,
    val artAlpha: Float = 0.5f,
    val scrimAlpha: Float = 1f,
    val dots: Boolean = true,
    val whiteFocus: Boolean = false,
    val appGlow: Boolean = false,
    val ambientGlow: Boolean = true,
    val glowSnap: Boolean = false,
    val animated: Boolean = true,
)

public fun TvConfig.shelfLayout(): ShelfLayout =
    ShelfLayout(
        cinematic = cinematicHome,
        artAlpha = cinematicArt / PERCENT,
        scrimAlpha = cinematicScrim / PERCENT,
        dots = cinematicDots,
        whiteFocus = cinematicWhiteFocus,
        appGlow = appGlow,
        ambientGlow = ambientGlow,
        glowSnap = glowSnap,
        animated = animations && !reducedMotion,
    )

private const val PERCENT = 100f

/**
 * The colour of a tile's focus highlight given the [art] colour of its app: app_glow gives every tile its app's colour,
 * the cinematic shelf the dock's, unless cinematic_white_focus keeps the dock white. Unspecified is the theme's accent.
 */
internal fun ShelfLayout.focusAccent(
    art: Color,
    inDock: Boolean,
): Color =
    when {
        appGlow -> art
        cinematic && inDock -> if (whiteFocus) Color.White else art
        else -> Color.Unspecified
    }

/**
 * The colour of a tile's focus ring given the [art] colour of its app: the app's [ringColor] in the dock, and in the grid
 * too with app_glow. cinematic_white_focus keeps every ring white.
 */
internal fun ShelfLayout.ringColour(
    art: Color,
    inDock: Boolean,
): Color = focusRingColour(art, coloured = inDock || appGlow, whiteFocus = whiteFocus)
