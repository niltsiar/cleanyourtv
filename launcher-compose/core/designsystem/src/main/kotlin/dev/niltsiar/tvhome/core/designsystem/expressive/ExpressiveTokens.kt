package dev.niltsiar.tvhome.core.designsystem.expressive

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.TvElementColors
import dev.niltsiar.tvhome.core.designsystem.TvShapes

/** The shapes of [dev.niltsiar.tvhome.core.config.Look.Expressive]: tighter tiles at rest, rounder chrome, keys on soft squares. */
internal val ExpressiveShapes: TvShapes =
    TvShapes(
        tile = RoundedCornerShape(12.dp),
        pill = CircleShape,
        chromePanel = RoundedCornerShape(24.dp),
        chromeButton = CircleShape,
        chromePill = CircleShape,
        overlayCorner = 28.dp,
        chip = CircleShape,
        chipKey = RoundedCornerShape(6.dp),
        libraryCardCorner = 20.dp,
        libraryArt = RoundedCornerShape(12.dp),
    )

/**
 * The Expressive set for a ground whose text is [ink], as [dev.niltsiar.tvhome.core.designsystem.glassElementColors] takes
 * it. Surfaces are flat: no sheen, rim, gloss or shadow. A colour that comes from the house scheme is
 * [Color.Unspecified] here, and the drawing takes it from the scheme in the draw phase.
 */
internal fun expressiveElementColors(
    ink: Color,
    tileInk: Color,
    menuTitleInk: Color,
): TvElementColors =
    TvElementColors(
        ring = Color.Unspecified,
        ringPressed = Color.White,
        ringPressedMix = 0.35f,
        ringKeyline = Color.Black.copy(alpha = 0.6f),
        ringOuterStroke = Color.Transparent,
        ringBlendStroke = Color.Transparent,
        tileGloss = Color.Transparent,
        plateTopAlpha = 0.35f,
        plateBottomAlpha = 0.35f,
        plateEdgeAlpha = 0f,
        haloUnderAlpha = 0.5f,
        haloSideAlpha = 0.25f,
        pillShadow = Color.Transparent,
        chromeRimTop = Color.Transparent,
        chromeRimBottom = Color.Transparent,
        chromeSheenTop = Color.Transparent,
        chromeSheenBottom = Color.Transparent,
        chromeShadow = Color.Transparent,
        brightBarFill = 0f,
        buttonActiveFill = 1f,
        offerPulse = Color.Unspecified,
        offerPulsePeak = 1f,
        overlaySheen = Color.Transparent,
        overlayShadow = Color.Transparent,
        menuDividerAlpha = 0.12f,
        menuWarnDividerAlpha = 0.18f,
        menuTitle = menuTitleInk.copy(alpha = 0.7f),
        dockRimTop = Color.Transparent,
        dockRimBottom = Color.Transparent,
        dockTopLine = Color.Transparent,
        dockSheen = Color.Transparent,
        dockBottomEdge = Color.Transparent,
        dockOuterHairline = Color.Black.copy(alpha = 0.30f),
        dockShadow = Color.Transparent,
        dockShade = Color.Black,
        dockLightAlpha = 1f,
        dockGlintAlpha = 0.24f,
        keyChipBacking = Color.Unspecified,
        keyChipRim = Color.Transparent,
        keyChipKeyFill = Color.Unspecified,
        scrim = Color.Black,
        scrimAlpha = 0.6f,
        greeting = ink.copy(alpha = 0.95f),
        tileLabel = tileInk,
        tileCaption = Color.White,
        caption = ink.copy(alpha = 0.85f),
        screensaverClock = Color.White.copy(alpha = 0.92f),
        screensaverAmPm = Color.White.copy(alpha = 0.6f),
        screensaverDate = Color.White.copy(alpha = 0.7f),
    )
