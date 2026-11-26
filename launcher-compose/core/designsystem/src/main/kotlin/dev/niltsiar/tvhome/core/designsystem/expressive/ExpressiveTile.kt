package dev.niltsiar.tvhome.core.designsystem.expressive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.ExpressiveTile
import dev.niltsiar.tvhome.core.designsystem.FocusStyle
import dev.niltsiar.tvhome.core.designsystem.addFromTopCentre
import kotlin.math.ceil
import kotlin.math.min

/** The gap, band and keyline of the focus band, which fill [dev.niltsiar.tvhome.core.designsystem.FocusRingReach] between them. */
private val Gap = 2.dp
private val Band = 5.dp
private val Keyline = 2.dp

/** With glass_style rim: no fill in the gap, and a band as wide as Glass's heavy ring. */
private val RimGap = 3.dp
private val RimBand = 4.dp

/** How far past the tile the band's outer edge lies, inside its keyline, which lets what is under it show; the same in rim. */
internal val BandReach = Gap + Band

/** The cinch: how far each side dips in, and the rounding of its corners and waists. */
private val CinchDip = 6.dp
private val CinchCorner = 24.dp
private val CinchWaist = 48.dp

/** The cinch's cushion round a [width] by [height] tile, [offset] pixels out: at 0 the tile as it shows, at the band's reach its outer edge. */
internal fun Density.cinchOutline(
    width: Float,
    height: Float,
    offset: Float,
): Path = cushionPath(width, height, offset, CinchDip.toPx(), CinchCorner.toPx(), CinchWaist.toPx())

/**
 * The corner radius of a focused cinch tile's art, [width] by [height] with corners of [rest] at rest: the first whole pixel
 * from [rest] up at which the art, half a pixel grown, stays inside [BandReach], so no art shows past the band or through
 * its keyline. The band's sides dip into the art; its corners round more than the art's.
 */
internal fun Density.cinchArtRadius(
    width: Float,
    height: Float,
    rest: Float,
): Float {
    val measure = PathMeasure().apply { setPath(cinchOutline(width, height, BandReach.toPx()), true) }
    val count = ceil(measure.length).toInt()
    val xs = FloatArray(count)
    val ys = FloatArray(count)
    for (i in 0 until count) {
        val at = measure.getPosition(measure.length * i / count)
        xs[i] = at.x
        ys[i] = at.y
    }
    val full = min(width, height) / 2f
    var radius = ceil(rest)
    while (radius < full && (0 until count).any { insideRoundRect(xs[it], ys[it], width, height, radius, ART_CLEARANCE) }) radius++
    return min(radius, full)
}

/** The top-left corner radius of [base] at [size], 0 for a shape without round corners: the tile's corner at rest. */
internal fun Density.restRadius(
    base: Shape,
    size: Size,
): Float = (base.createOutline(size, LayoutDirection.Ltr, this) as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f

/** Whether ([x], [y]) lies inside a [width] by [height] rectangle with corners of [radius], grown by [grow] on every side. */
private fun insideRoundRect(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float,
    grow: Float,
): Boolean {
    if (x <= -grow || y <= -grow || x >= width + grow || y >= height + grow) return false
    val cx = x.coerceIn(radius, width - radius)
    val cy = y.coerceIn(radius, height - radius)
    val dx = x - cx
    val dy = y - cy
    return dx * dx + dy * dy < (radius + grow) * (radius + grow)
}

/** How far, in pixels, the cinch's art keeps inside its band's outer edge. */
private const val ART_CLEARANCE = 0.5f

/**
 * The halo bands of cinch tiles, from the outer edge of the band, [reach] past the tile, out by a whole number of
 * [quantum]s on each side, kept for the last 16 tile sizes and widths, so a frame that draws a halo already seen builds
 * nothing.
 */
internal object CinchHalos {
    private val halos = RecentSlots(arrayOfNulls<Array<Path>>(16))

    /** The [steps] bands of the halo of a [width] by [height] tile, [left], [top], [right] and [bottom] quanta wide. */
    fun get(
        density: Density,
        width: Float,
        height: Float,
        reach: Float,
        quantum: Float,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        steps: Int,
    ): Array<Path> {
        val key =
            (width.toLong() and 0xFFFF) or
                ((height.toLong() and 0xFFFF) shl 16) or
                (((density.density * 100f).toLong() and 0xFFF) shl 32) or
                ((left.toLong() and 0xF) shl 44) or
                ((top.toLong() and 0xF) shl 48) or
                ((right.toLong() and 0xF) shl 52) or
                ((bottom.toLong() and 0xF) shl 56)
        halos.find(key)?.let { return it }
        val built =
            with(density) {
                cinchHalo(
                    width,
                    height,
                    reach,
                    left * quantum,
                    top * quantum,
                    right * quantum,
                    bottom * quantum,
                    steps,
                    CinchDip.toPx(),
                    CinchCorner.toPx(),
                    CinchWaist.toPx(),
                )
            }
        return halos.keep(key, built)
    }
}

/**
 * What one [dev.niltsiar.tvhome.core.designsystem.FocusSurface] keeps to draw its focus in
 * [dev.niltsiar.tvhome.core.config.Look.Expressive]: the art's clip steps for its size and tile, the band geometry of the
 * cinch for its size and style, taken from [BandCache], and the strokes it draws with. Each is built when
 * the size, style or colour changes, never on a frame that only moves the focus.
 */
internal class TilePainter(
    /** How far past the surface the capsule's focused shape grows, in place of the surface growing with it. */
    private val outset: Dp = 0.dp,
) {
    private var steps: CornerSteps? = null
    private var stepsBase: Shape? = null
    private var stepsTile: ExpressiveTile? = null
    private var band: BandGeometry? = null
    private var bandKey: BandKey? = null
    private val strokeWidths = FloatArray(STROKES)
    private val strokes = arrayOfNulls<Stroke>(STROKES)

    /** The arc of a hold cue, rewound and refilled each frame it shows. */
    internal val holdArc = Path()

    /**
     * The progress of the held OK on this surface, from the holdOk before it, while the look shows the charge; null
     * otherwise. The hold cue sets it, so the clip, the band and the halo read the same press.
     */
    var held: (() -> Float)? by mutableStateOf(null)

    /** How far the charge of the press on this surface is, 0 to 1 ([chargeOf]). */
    fun charge(): Float = held?.let { chargeOf(it()) } ?: 0f

    /** The capsule's morph at focus [progress]: the progress, less the corner a charge pinches back ([pinchOf]). */
    fun morph(progress: Float): Float = if (progress > 0f) progress * pinchOf(charge()) else progress

    /** The capsule's charge line, refilled for its corner each frame the charge shows; built on the first charge. */
    private var capsuleCharge: ChargeLine? = null

    fun capsuleCharge(): ChargeLine = capsuleCharge ?: ChargeLine().also { capsuleCharge = it }

    /** The wave of a charge, rewound and refilled each frame it shows, with its round-capped stroke; built on the first charge. */
    private var chargePath: Path? = null
    private var chargeStroke: Stroke? = null
    internal val chargeScratch = FloatArray(4)

    fun chargePath(): Path = chargePath ?: Path().also { chargePath = it }

    fun chargeStroke(width: Float): Stroke =
        chargeStroke?.takeIf { it.width == width }
            ?: Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round).also { chargeStroke = it }

    /** The capsule's hold line, kept while its corner radius and the size stay. */
    private val capsuleLine = Path()
    private val capsuleMeasure = PathMeasure()
    private var capsuleRadius = Float.NaN
    private var capsuleMiddle = Float.NaN
    private var capsuleSize = Size.Unspecified

    /** The line through the middle of a capsule band [middle] past the tile's edge, from the top centre clockwise. */
    fun capsuleHold(
        steps: CornerSteps,
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

    /** The corner steps of a [tile] of [base] at [size]. */
    fun steps(
        base: Shape,
        tile: ExpressiveTile,
        size: Size,
        density: Density,
    ): CornerSteps {
        val kept = steps
        if (kept != null && kept.size == size && stepsBase == base && stepsTile == tile) return kept
        val rest = density.restRadius(base, size)
        val steps =
            when (tile) {
                ExpressiveTile.Capsule -> CornerSteps(size, rest)
                ExpressiveTile.Cinch -> CornerSteps(size, rest, density.cinchArtRadius(size.width, size.height, rest))
            }
        return steps.also {
            this.steps = it
            stepsBase = base
            stepsTile = tile
        }
    }

    /** The corner of a [tile]'s art at focus [progress]: the capsule's [morph], the cinch's progress. */
    fun corner(
        tile: ExpressiveTile,
        progress: Float,
    ): Float = if (tile == ExpressiveTile.Capsule) morph(progress) else progress

    /** Whether a [tile]'s focused shape grows by the outset while the surface and its content keep their rest size. */
    fun grows(tile: ExpressiveTile): Boolean = outset > 0.dp && tile == ExpressiveTile.Capsule

    /** How far past the surface a [tile]'s focused shape reaches at focus [progress], in pixels. */
    fun grow(
        tile: ExpressiveTile,
        progress: Float,
        density: Density,
    ): Float = if (grows(tile)) with(density) { outset.toPx() } * progress else 0f

    /**
     * The clip of the tile's content: [base] at rest, or at focus [progress] its rounded step toward the focused art; [base]
     * while the focused shape [grows] round content that keeps its rest size.
     */
    fun clip(
        base: Shape,
        tile: ExpressiveTile,
        progress: Float,
        size: Size,
        density: Density,
    ): Shape {
        if (progress <= 0f || grows(tile)) return base
        val steps = steps(base, tile, size, density)
        return steps.shape(steps.radius(corner(tile, progress)))
    }

    /** The band geometry of the cinch for this tile, built or found once per size and style. */
    fun band(
        tile: ExpressiveTile,
        rim: Boolean,
        base: Shape,
        size: Size,
        density: Density,
    ): BandGeometry {
        val rest = steps(base, tile, size, density).rest
        val kept = band
        if (kept != null && bandKey?.matches(tile, rim, size, rest, density.density) == true) return kept
        val key = BandKey(tile, rim, size.width, size.height, rest, density.density)
        return BandCache.get(key) { buildBand(key, density) }.also {
            band = it
            bandKey = key
        }
    }

    /** The band geometry this painter drew last, for the hold cue; null before it drew a cinch. */
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
) {
    /** Whether this key is the one these values make, without building it. */
    fun matches(
        tile: ExpressiveTile,
        rim: Boolean,
        size: Size,
        rest: Float,
        density: Float,
    ): Boolean =
        tile == this.tile && rim == this.rim && size.width == width && size.height == height && rest == this.rest &&
            density == this.density
}

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
        cinchGeometry(
            width = key.width,
            height = key.height,
            widths = bandWidths(key.rim),
            dip = CinchDip.toPx(),
            corner = CinchCorner.toPx(),
            waist = CinchWaist.toPx(),
        )
    }

private fun Density.bandWidths(rim: Boolean): BandWidths =
    if (rim) {
        BandWidths(gap = RimGap.toPx(), band = RimBand.toPx(), keyline = Keyline.toPx(), gapFilled = false)
    } else {
        BandWidths(gap = Gap.toPx(), band = Band.toPx(), keyline = Keyline.toPx(), gapFilled = true)
    }

/**
 * Draws a focused tile in Expressive, already grown and dipped by the caller, with its content: a [fill] (when specified,
 * in every style) or else a [plate] (when specified, glass_style glass only) behind it, then the band in [band] round it as
 * [tile] says, with the gap in [gap] and the keyline in [keyline]. [progress] fades the fill or plate and [ringAlpha] the
 * band; a held OK's charge pinches the capsule's corner ([TilePainter.morph]). Where the painter's focused shape
 * [TilePainter.grows], the fill and the band grow past the tile with [progress] and the content is drawn over them, at its
 * rest size. Nothing here allocates once the painter has the geometry for this size and style.
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
    fill: Color,
) {
    val rim = style == FocusStyle.Rim
    painter.lastRim = rim
    val radius = painter.steps(base, tile, size, this).radius(painter.corner(tile, progress))
    if (painter.grows(tile)) {
        val grow = painter.grow(tile, progress, this)
        inset(-grow) {
            drawBehindTile(style, progress, radius + grow, plate, plateAlpha, fill)
            if (ringAlpha > 0f) drawCapsuleBand(painter, radius + grow, rim, ringAlpha, band, gap, keyline)
        }
        drawContent()
        return
    }
    drawBehindTile(style, progress, radius, plate, plateAlpha, fill)
    drawContent()
    if (ringAlpha <= 0f) return
    when (tile) {
        ExpressiveTile.Capsule -> drawCapsuleBand(painter, radius, rim, ringAlpha, band, gap, keyline)
        ExpressiveTile.Cinch -> drawCinchBand(painter.band(tile, rim, base, size, this), ringAlpha, band, gap, keyline)
    }
}

/** The [fill], or else the [plate] of a style with a slab, out to the band's inner edge, corners concentric to [radius]. */
private fun DrawScope.drawBehindTile(
    style: FocusStyle,
    progress: Float,
    radius: Float,
    plate: Color,
    plateAlpha: Float,
    fill: Color,
) {
    if (progress <= 0f) return
    val colour = if (fill.isSpecified) fill else plate.takeIf { it.isSpecified && style.slab } ?: return
    val grow = Gap.toPx()
    drawRoundRect(
        colour,
        topLeft = Offset(-grow, -grow),
        size = Size(size.width + 2f * grow, size.height + 2f * grow),
        cornerRadius = CornerRadius(radius + grow),
        alpha = if (fill.isSpecified) progress else progress * plateAlpha,
    )
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
 * follows the capsule's corner and the cinch.
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
            val steps = painter.steps(base, tile, size, this)
            val rim = painter.lastRim
            val middle = if (rim) RimGap.toPx() + RimBand.toPx() / 2f else Gap.toPx() + Band.toPx() / 2f
            painter.capsuleHold(steps, steps.radius(progress), middle + painter.grow(tile, progress, this))
        } else {
            painter.lastBand()?.holdMeasure ?: return
        }
    val arc = painter.holdArc
    arc.rewind()
    measure.getSegment(0f, measure.length * held, arc, true)
    drawPath(arc, if (band.luminance() > HOLD_INK_FROM) Color.Black else Color.White, style = painter.stroke(HoldWidth.toPx()))
}

/**
 * The charge of a held OK on a tile of [tile] at focus [progress]: a wave along the outer edge of the band the painter drew,
 * whose colour moves from [band]'s muted tone to [band] as the [charge] closes the loop. The capsule's edge
 * follows its pinched corner; the cinch's line was sampled with its band.
 */
internal fun DrawScope.drawTileCharge(
    painter: TilePainter,
    tile: ExpressiveTile,
    base: Shape,
    progress: Float,
    charge: Float,
    band: Color,
) {
    val line =
        if (tile == ExpressiveTile.Capsule) {
            val steps = painter.steps(base, tile, size, this)
            val edge = bandEdge(painter.lastRim).toPx() + painter.grow(tile, progress, this)
            val radius = steps.radius(progress * pinchOf(charge))
            painter.capsuleCharge().apply { sampleRoundRect(-edge, -edge, size.width + edge, size.height + edge, radius + edge) }
        } else {
            painter.lastBand()?.charge ?: return
        }
    val colour = lerp(chargeStart(band, painter.chargeScratch), band, charge)
    drawCharge(line, charge, colour, painter.chargePath(), painter.chargeStroke(ChargeWidth.toPx()), painter.chargeScratch)
}

/** How far past the tile the band's outer edge lies, with glass_style rim or without. */
private fun bandEdge(rim: Boolean) = if (rim) RimGap + RimBand else Gap + Band

/** The hold line's width, and the band luminance from which it is black: where black and white read equally. */
private val HoldWidth = 2.dp
private const val HOLD_INK_FROM = 0.179f
