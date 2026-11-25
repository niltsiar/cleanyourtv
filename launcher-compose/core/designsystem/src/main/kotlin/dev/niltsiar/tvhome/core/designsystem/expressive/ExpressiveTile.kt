package dev.niltsiar.tvhome.core.designsystem.expressive

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.ExpressiveTile
import dev.niltsiar.tvhome.core.designsystem.FocusRingReach
import dev.niltsiar.tvhome.core.designsystem.FocusStyle
import dev.niltsiar.tvhome.core.designsystem.TILE_FOCUSED_SCALE
import dev.niltsiar.tvhome.core.designsystem.addFromTopCentre
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The gap, band and keyline of the focus band, which fill [FocusRingReach] between them. */
private val Gap = 2.dp
private val Band = 5.dp
private val Keyline = 2.dp

/** With glass_style rim: no fill in the gap, and a band as wide as Glass's heavy ring. */
private val RimGap = 3.dp
private val RimBand = 4.dp

/** The wave: its lobes and waists past the tile's edge, its keyline, and how far the rim band reaches in from the wave. */
private val WavePeak = 7.5.dp
private val WaveWaist = 2.dp
private val WaveKeyline = 1.5.dp
private val WaveRimBand = 4.dp
private const val WAVE_LOBES = 12

/** The cinch: how far each side dips in, and the rounding of its corners and waists. */
private val CinchDip = 6.dp
private val CinchCorner = 24.dp
private val CinchWaist = 48.dp

/** The widest a band ring reaches round the tile, for the masks; whole pixels so they land on the pixel grid. */
private fun Density.maskReach(): Float = ceil(FocusRingReach.toPx())

/**
 * What one [dev.niltsiar.tvhome.core.designsystem.FocusSurface] keeps to draw its focus in
 * [dev.niltsiar.tvhome.core.config.Look.Expressive]: the capsule's clip steps for its size, the band geometry of the wave
 * or the cinch for its size and style, taken from [BandCache], and the strokes and tints it draws with. Each is built when
 * the size, style or colour changes, never on a frame that only moves the focus.
 */
internal class TilePainter {
    private var steps: CapsuleSteps? = null
    private var stepsBase: Shape? = null
    private var band: BandGeometry? = null
    private var bandKey: BandKey? = null
    private val strokeWidths = FloatArray(STROKES)
    private val strokes = arrayOfNulls<Stroke>(STROKES)
    private val tintColours = arrayOfNulls<Color>(2)
    private val tints = arrayOfNulls<ColorFilter>(2)

    /** The arc of a hold cue, rewound and refilled each frame it shows. */
    internal val holdArc = Path()

    /** The capsule's hold line, kept while its corner radius and the size stay. */
    private val capsuleLine = Path()
    private val capsuleMeasure = PathMeasure()
    private var capsuleRadius = Float.NaN
    private var capsuleMiddle = Float.NaN
    private var capsuleSize = Size.Unspecified

    /** The line through the middle of a capsule band [middle] past the tile's edge, from the top centre clockwise. */
    fun capsuleHold(
        steps: CapsuleSteps,
        radius: Float,
        middle: Float,
    ): PathMeasure {
        if (radius != capsuleRadius || middle != capsuleMiddle || steps.size != capsuleSize) {
            val size = steps.size
            capsuleLine.rewind()
            capsuleLine.addFromTopCentre(
                RoundRect(-middle, -middle, size.width + middle, size.height + middle, CornerRadius(radius + middle)),
            )
            capsuleMeasure.setPath(capsuleLine, false)
            capsuleRadius = radius
            capsuleMiddle = middle
            capsuleSize = size
        }
        return capsuleMeasure
    }

    /** The capsule steps for [base] at [size]. */
    fun steps(
        base: Shape,
        size: Size,
        density: Density,
    ): CapsuleSteps {
        val kept = steps
        if (kept != null && kept.size == size && stepsBase == base) return kept
        val rest = (base.createOutline(size, LayoutDirection.Ltr, density) as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
        return CapsuleSteps(size, rest).also {
            steps = it
            stepsBase = base
        }
    }

    /** The clip of the tile's content: [base], or with the capsule at [morph] its rounded step. */
    fun clip(
        base: Shape,
        tile: ExpressiveTile,
        morph: Float,
        size: Size,
        density: Density,
    ): Shape {
        if (tile != ExpressiveTile.Capsule || morph <= 0f) return base
        val steps = steps(base, size, density)
        return steps.shape(steps.radius(morph))
    }

    /** The band geometry of the wave or the cinch for this tile, built or found once per size and style. */
    fun band(
        tile: ExpressiveTile,
        rim: Boolean,
        base: Shape,
        size: Size,
        density: Density,
    ): BandGeometry {
        val rest = steps(base, size, density).rest
        val key = BandKey(tile, rim, size.width, size.height, rest, density.density)
        val kept = band
        if (kept != null && bandKey == key) return kept
        return BandCache.get(key) { buildBand(key, density) }.also {
            band = it
            bandKey = key
        }
    }

    /** The band geometry this painter drew last, for the hold cue; null before it drew a wave or a cinch. */
    fun lastBand(): BandGeometry? = band

    /** Whether the painter last drew the band of glass_style rim, for the capsule's hold cue. */
    var lastRim: Boolean = false

    fun stroke(width: Float): Stroke {
        for (i in 0 until STROKES) {
            val kept = strokes[i]
            if (kept != null && strokeWidths[i] == width) return kept
        }
        val slot = strokes.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 0
        return Stroke(width).also {
            strokes[slot] = it
            strokeWidths[slot] = width
        }
    }

    /** A tint of an alpha mask in [colour], kept per [slot] while the colour stays. */
    fun tint(
        slot: Int,
        colour: Color,
    ): ColorFilter {
        val kept = tints[slot]
        if (kept != null && tintColours[slot] == colour) return kept
        return ColorFilter.tint(colour).also {
            tints[slot] = it
            tintColours[slot] = colour
        }
    }

    private companion object {
        const val STROKES = 4
    }
}

/** What a band geometry is built for. */
internal data class BandKey(
    val tile: ExpressiveTile,
    val rim: Boolean,
    val width: Float,
    val height: Float,
    val rest: Float,
    val density: Float,
)

/** The band geometry of the last few tile sizes, shared by every tile of a size, so a grid of equal tiles builds it once. */
internal object BandCache {
    private const val KEEP = 8
    private val kept =
        object : LinkedHashMap<BandKey, BandGeometry>(KEEP, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BandKey, BandGeometry>?): Boolean = size > KEEP
        }

    fun get(
        key: BandKey,
        build: () -> BandGeometry,
    ): BandGeometry = kept.getOrPut(key, build)
}

private fun buildBand(
    key: BandKey,
    density: Density,
): BandGeometry =
    with(density) {
        when (key.tile) {
            ExpressiveTile.Wave -> {
                waveGeometry(
                    width = key.width,
                    height = key.height,
                    radius = key.rest,
                    peak = WavePeak.toPx(),
                    waist = WaveWaist.toPx(),
                    keyline = WaveKeyline.toPx(),
                    inset = if (key.rim) WaveRimBand.toPx() else null,
                    lobes = WAVE_LOBES,
                    reach = maskReach(),
                    maskScale = TILE_FOCUSED_SCALE,
                )
            }

            else -> {
                cinchGeometry(
                    width = key.width,
                    height = key.height,
                    widths = bandWidths(key.rim),
                    dip = CinchDip.toPx(),
                    corner = CinchCorner.toPx(),
                    waist = CinchWaist.toPx(),
                )
            }
        }
    }

private fun Density.bandWidths(rim: Boolean): BandWidths =
    if (rim) {
        BandWidths(gap = RimGap.toPx(), band = RimBand.toPx(), keyline = Keyline.toPx(), gapFilled = false)
    } else {
        BandWidths(gap = Gap.toPx(), band = Band.toPx(), keyline = Keyline.toPx(), gapFilled = true)
    }

/**
 * Draws a focused tile in Expressive, already grown and dipped by the caller, with its content: in [style], a [plate] (when
 * specified, glass_style glass only) behind it, then the band in [band] round it as [tile] says, with the gap in [gap] and
 * the keyline in [keyline]. [progress] fades the plate and [ringAlpha] the band. Nothing here allocates once the painter
 * has the geometry for this size and style.
 */
internal fun ContentDrawScope.drawExpressiveTile(
    painter: TilePainter,
    tile: ExpressiveTile,
    style: FocusStyle,
    base: Shape,
    progress: Float,
    ringAlpha: Float,
    band: Color,
    gap: Color,
    keyline: Color,
    plate: Color,
    plateAlpha: Float,
) {
    val rim = style == FocusStyle.Rim
    painter.lastRim = rim
    val capsule = tile == ExpressiveTile.Capsule
    val steps = painter.steps(base, size, this)
    val radius = if (capsule) steps.radius(progress) else steps.rest
    if (plate.isSpecified && style.slab && progress > 0f) {
        val grow = Gap.toPx()
        drawRoundRect(
            plate,
            topLeft = Offset(-grow, -grow),
            size = Size(size.width + 2f * grow, size.height + 2f * grow),
            cornerRadius = CornerRadius(radius + grow),
            alpha = progress * plateAlpha,
        )
    }
    drawContent()
    if (ringAlpha <= 0f) return
    when (tile) {
        ExpressiveTile.Capsule -> drawCapsuleBand(painter, radius, rim, ringAlpha, band, gap, keyline)
        ExpressiveTile.Wave -> drawWaveBand(painter.band(tile, rim, base, size, this), painter, ringAlpha, band, keyline)
        ExpressiveTile.Cinch -> drawCinchBand(painter.band(tile, rim, base, size, this), ringAlpha, band, gap, keyline)
    }
}

private fun DrawScope.drawCapsuleBand(
    painter: TilePainter,
    radius: Float,
    rim: Boolean,
    alpha: Float,
    band: Color,
    gap: Color,
    keyline: Color,
) {
    val inner = (if (rim) RimGap else Gap).toPx()
    val wide = (if (rim) RimBand else Band).toPx()
    if (!rim) roundRing(painter, radius, 0f, inner, gap, alpha)
    roundRing(painter, radius, inner, wide, band, alpha)
    roundRing(painter, radius, inner + wide, Keyline.toPx(), keyline, alpha)
}

/** A ring [width] wide round the tile, [from] past its edge, with corners concentric to [radius]. */
private fun DrawScope.roundRing(
    painter: TilePainter,
    radius: Float,
    from: Float,
    width: Float,
    colour: Color,
    alpha: Float,
) {
    val middle = from + width / 2f
    drawRoundRect(
        colour,
        topLeft = Offset(-middle, -middle),
        size = Size(size.width + 2f * middle, size.height + 2f * middle),
        cornerRadius = CornerRadius(radius + middle),
        alpha = alpha,
        style = painter.stroke(width),
    )
}

private fun DrawScope.drawWaveBand(
    geometry: BandGeometry,
    painter: TilePainter,
    alpha: Float,
    band: Color,
    keyline: Color,
) {
    val masks = geometry.masks ?: return
    val reach = geometry.maskReach
    val at = IntOffset(-reach.roundToInt(), -reach.roundToInt())
    val drawn = IntSize((size.width + 2f * reach).roundToInt(), (size.height + 2f * reach).roundToInt())
    masks[1]?.let { drawImage(it, dstOffset = at, dstSize = drawn, alpha = alpha, colorFilter = painter.tint(0, band.copy(alpha = 1f))) }
    masks[2]?.let {
        drawImage(
            it,
            dstOffset = at,
            dstSize = drawn,
            alpha = alpha * keyline.alpha,
            colorFilter = painter.tint(1, keyline.copy(alpha = 1f)),
        )
    }
}

private fun DrawScope.drawCinchBand(
    geometry: BandGeometry,
    alpha: Float,
    band: Color,
    gap: Color,
    keyline: Color,
) {
    geometry.rings[0]?.let { drawPath(it, gap, alpha = alpha) }
    geometry.rings[1]?.let { drawPath(it, band, alpha = alpha) }
    geometry.rings[2]?.let { drawPath(it, keyline, alpha = alpha) }
}

/**
 * The hold cue: a thin line in black or white, whichever reads on [band], from the top centre clockwise, [held] of the way
 * round, through the middle of the band of [tile] at focus [progress]. It runs along the band the painter drew, so it
 * follows the capsule's corner, the wave and the cinch.
 */
internal fun DrawScope.drawExpressiveHold(
    painter: TilePainter,
    tile: ExpressiveTile,
    base: Shape,
    progress: Float,
    held: Float,
    band: Color,
) {
    val measure =
        if (tile == ExpressiveTile.Capsule) {
            val steps = painter.steps(base, size, this)
            val rim = painter.lastRim
            val middle = if (rim) RimGap.toPx() + RimBand.toPx() / 2f else Gap.toPx() + Band.toPx() / 2f
            painter.capsuleHold(steps, steps.radius(progress), middle)
        } else {
            painter.lastBand()?.holdMeasure ?: return
        }
    val arc = painter.holdArc
    arc.rewind()
    measure.getSegment(0f, measure.length * held, arc, true)
    drawPath(arc, if (band.luminance() > HOLD_INK_FROM) Color.Black else Color.White, style = painter.stroke(HoldWidth.toPx()))
}

/** The hold line's width, and the band luminance from which it is black: where black and white read equally. */
private val HoldWidth = 2.dp
private const val HOLD_INK_FROM = 0.179f
