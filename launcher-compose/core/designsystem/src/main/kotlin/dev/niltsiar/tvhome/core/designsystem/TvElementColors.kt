package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colours each element of the design system draws with, and the alphas it lays over a colour its caller gives, named
 * for the element. [TvTheme.elementColors] gives the set for the ground the element sits on: the dark set over the
 * wallpaper and on a dark surface, the on-light set for text on a light one; an element whose colour does not depend on
 * the ground has the same value in both.
 */
@Immutable
public class TvElementColors internal constructor(
    /** The ring of a focused [FocusSurface], its halo and its hold arc, when the caller gives no colour. */
    public val ring: Color,
    /** The colour a pressed ring moves toward, [ringPressedMix] of the way at the bottom of the press. */
    public val ringPressed: Color,
    public val ringPressedMix: Float,
    /** The hairline outside the ring. */
    public val ringKeyline: Color,
    /** The stroke outside the keyline, so the ring holds on a bright wallpaper. */
    public val ringOuterStroke: Color,
    /** The stroke across the gap inside a ring that blends with its tile. */
    public val ringBlendStroke: Color,
    /** The top of the lit-top gradient over a focused tile, which fades to nothing at its bottom. */
    public val tileGloss: Color,
    /** The alphas of a focused tile's plate colour at its top and bottom, and of its edge. */
    public val plateTopAlpha: Float,
    public val plateBottomAlpha: Float,
    public val plateEdgeAlpha: Float,
    /** The alphas of the halo glow under a focused tile and at its sides and top. */
    public val haloUnderAlpha: Float,
    public val haloSideAlpha: Float,
    /** The shadow of a [FocusPill] that casts one. */
    public val pillShadow: Color,
    /** The rim of a chrome surface and of an overlay, from its top to its bottom. */
    public val chromeRimTop: Color,
    public val chromeRimBottom: Color,
    /** The sheen over a chrome surface, from its top to its bottom. */
    public val chromeSheenTop: Color,
    public val chromeSheenBottom: Color,
    /** The shadow of a chrome surface that casts one. */
    public val chromeShadow: Color,
    /** The total alpha a top-bar button's tint reaches over a bright wallpaper. */
    public val brightBarFill: Float,
    /** The alpha of the pill fill of a button whose panel is open. */
    public val buttonActiveFill: Float,
    /** The rim of an offered button at the peak of its pulse, at [offerPulsePeak]. */
    public val offerPulse: Color,
    public val offerPulsePeak: Float,
    /** The sheen over the top of an overlay, which fades out 40 % of the way down. */
    public val overlaySheen: Color,
    /** The shadow under an overlay. */
    public val overlayShadow: Color,
    /** The alpha of the text colour in a menu's dividers and under its title, and above a warning row. */
    public val menuDividerAlpha: Float,
    public val menuWarnDividerAlpha: Float,
    /** The title of a menu. */
    public val menuTitle: Color,
    /** The rim of the dock, from its top to its bottom, and the line that takes over its top as its gloss fades. */
    public val dockRimTop: Color,
    public val dockRimBottom: Color,
    public val dockTopLine: Color,
    /** The sheen over the top of the dock, which fades out 35 % of the way down. */
    public val dockSheen: Color,
    /** The edge that darkens the dock from its middle to its bottom. */
    public val dockBottomEdge: Color,
    /** The line round the outside of a dock that asks for one. */
    public val dockOuterHairline: Color,
    /** The shadow under the dock. */
    public val dockShadow: Color,
    /** The layer that shades the whole dock, at the alpha its caller gives. */
    public val dockShade: Color,
    /** The alphas of the focused dock tile's colour in its light on the rim and in its glint on the floor. */
    public val dockLightAlpha: Float,
    public val dockGlintAlpha: Float,
    /** The backing, rim and key fill of a [KeyChip] over the wallpaper. */
    public val keyChipBacking: Color,
    public val keyChipRim: Color,
    public val keyChipKeyFill: Color,
    /** The scrim of an [OverlayHost], and its alpha when the caller gives none. */
    public val scrim: Color,
    public val scrimAlpha: Float,
    /** The text of the roles of [TvTypography] whose colour is not the plain text colour. */
    public val greeting: Color,
    public val tileLabel: Color,
    public val tileCaption: Color,
    public val caption: Color,
    public val screensaverClock: Color,
    public val screensaverAmPm: Color,
    public val screensaverDate: Color,
)

/**
 * The Glass set for a ground whose text is [ink]: the dark set's or the on-light set's. A tile's label takes [tileInk], as
 * the tile fill is dark in every set, and a menu's title [menuTitleInk].
 */
internal fun glassElementColors(
    ink: Color,
    tileInk: Color,
    menuTitleInk: Color,
): TvElementColors =
    TvElementColors(
        ring = Color.White,
        ringPressed = Color.White,
        ringPressedMix = 0.5f,
        ringKeyline = Color(0x73000000),
        ringOuterStroke = Color.Black.copy(alpha = 0.55f),
        ringBlendStroke = Color.White.copy(alpha = 0.90f),
        tileGloss = Color.White.copy(alpha = 0.14f),
        plateTopAlpha = 0.22f,
        plateBottomAlpha = 0.06f,
        plateEdgeAlpha = 0.3f,
        haloUnderAlpha = 0.5f,
        haloSideAlpha = 0.25f,
        pillShadow = Color.Black.copy(alpha = 0.30f),
        chromeRimTop = Color.White.copy(alpha = 0.32f),
        chromeRimBottom = Color.White.copy(alpha = 0.08f),
        chromeSheenTop = Color.White.copy(alpha = 0.10f),
        chromeSheenBottom = Color.White.copy(alpha = 0.03f),
        chromeShadow = Color.Black.copy(alpha = 0.30f),
        brightBarFill = 0.75f,
        buttonActiveFill = 0.60f,
        offerPulse = Color.White,
        offerPulsePeak = 0.9f,
        overlaySheen = Color.White.copy(alpha = 0.10f),
        overlayShadow = Color.Black.copy(alpha = 0.50f),
        menuDividerAlpha = 0.12f,
        menuWarnDividerAlpha = 0.18f,
        menuTitle = menuTitleInk.copy(alpha = 0.65f),
        dockRimTop = Color.White.copy(alpha = 0.40f),
        dockRimBottom = Color.White.copy(alpha = 0.08f),
        dockTopLine = Color.White.copy(alpha = 0.30f),
        dockSheen = Color.White.copy(alpha = 0.16f),
        dockBottomEdge = Color.Black.copy(alpha = 0.25f),
        dockOuterHairline = Color.Black.copy(alpha = 0.30f),
        dockShadow = Color.Black.copy(alpha = 0.22f),
        dockShade = Color.Black,
        dockLightAlpha = 0.85f,
        dockGlintAlpha = 0.20f,
        keyChipBacking = Color(0xFF07080C).copy(alpha = 0.55f),
        keyChipRim = Color.White.copy(alpha = 0.20f),
        keyChipKeyFill = Color.White.copy(alpha = 0.16f),
        scrim = Color(0xFF07080C),
        scrimAlpha = 0.5f,
        greeting = ink.copy(alpha = 0.95f),
        tileLabel = tileInk,
        tileCaption = Color.White,
        caption = ink.copy(alpha = 0.85f),
        screensaverClock = Color.White.copy(alpha = 0.92f),
        screensaverAmPm = Color.White.copy(alpha = 0.6f),
        screensaverDate = Color.White.copy(alpha = 0.55f),
    )
