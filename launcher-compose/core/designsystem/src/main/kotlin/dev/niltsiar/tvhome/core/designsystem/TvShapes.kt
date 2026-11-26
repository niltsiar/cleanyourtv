package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shapes of the design system's elements, named for the element; [TvTheme.shapes] gives them. A focused or pressed
 * element keeps its shape: it grows or dips with it.
 */
@Immutable
public class TvShapes internal constructor(
    /** A [FocusSurface] whose caller gives no shape. */
    public val tile: Shape,
    /** A [FocusPill] whose caller gives no shape. */
    public val pill: Shape,
    /** A [GlassSurface] whose caller gives no shape. */
    public val chromePanel: RoundedCornerShape,
    /** A [GlassIconButton]: its glass and its fill. */
    public val chromeButton: RoundedCornerShape,
    /** A chrome pill with text, as the clock's. */
    public val chromePill: RoundedCornerShape,
    /** The corner of an overlay panel. */
    public val overlayCorner: Dp,
    /** A [KeyChip]'s backing and rim. */
    public val chip: Shape,
    /** The fill under each key of a [KeyChip]. */
    public val chipKey: Shape,
    /** The corner of a card in the App library's grid. */
    public val libraryCardCorner: Dp,
    /** The art on a library card. */
    public val libraryArt: Shape,
) {
    /** A card in the App library's grid. */
    public val libraryCard: Shape = RoundedCornerShape(libraryCardCorner)

    /** An overlay panel: [OverlaySurface] when its caller gives no shape. */
    public val overlay: Shape = RoundedCornerShape(overlayCorner)

    /** A row of a panel padded by [padding], its corner concentric with the panel's. */
    public fun menuRow(padding: Dp): Shape = RoundedCornerShape(overlayCorner - padding)
}

internal val GlassShapes: TvShapes =
    TvShapes(
        tile = RoundedCornerShape(16.dp),
        pill = CircleShape,
        chromePanel = RoundedCornerShape(20.dp),
        chromeButton = CircleShape,
        chromePill = CircleShape,
        overlayCorner = 28.dp,
        chip = CircleShape,
        chipKey = CircleShape,
        libraryCardCorner = 16.dp,
        libraryArt = RoundedCornerShape(8.dp),
    )
