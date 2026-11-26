package dev.niltsiar.tvhome.core.designsystem.expressive

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A rounded rectangle of a size known in advance, as a [Shape]: the layer that clips to it takes its outline as is, so a
 * corner that steps through [CornerSteps] allocates nothing once each step was built.
 */
internal class FixedRoundRect(
    private val outline: Outline.Rounded,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = outline.roundRect
        return if (r.width == size.width && r.height == size.height) {
            outline
        } else {
            Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, r.topLeftCornerRadius))
        }
    }
}

/**
 * The clip shapes of a focused tile's art of one size, one per whole pixel of corner radius from [rest] to [end], each built
 * the first time it is drawn: for the capsule [end] is half the tile's shorter side, for the cinch the radius that keeps
 * the art inside its band ([cinchArtRadius]).
 */
internal class CornerSteps(
    val size: Size,
    /** The corner radius at rest, in pixels. */
    val rest: Float,
    /** The corner radius of the focused art, in pixels. */
    val end: Float = min(size.width, size.height) / 2f,
) {
    private val steps = arrayOfNulls<FixedRoundRect>(ceil(max(rest, end)).toInt() + 1)

    /** The corner radius, on a whole pixel, at [morph] 0 (at rest) to 1 (focused). */
    fun radius(morph: Float): Float = (rest + (end - rest) * morph.coerceIn(0f, 1f)).toInt().toFloat().coerceAtMost(max(rest, end))

    fun shape(radius: Float): Shape {
        val step = radius.toInt().coerceIn(0, steps.size - 1)
        return steps[step] ?: FixedRoundRect(
            Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(step.toFloat()))),
        ).also { steps[step] = it }
    }
}

/**
 * The band of a [dev.niltsiar.tvhome.core.config.ExpressiveTile.Cinch] tile of one size: each ring of the band as a path
 * built once; the line the hold cue runs along, from the top centre clockwise; and the band's outer edge for the charge.
 * Everything is in the tile's own pixels, its top left at 0.
 */
internal class BandGeometry(
    /** The rings from the tile outwards: gap, band, keyline. A null ring is not drawn. */
    val rings: Array<Path?>,
    /** The line through the middle of the band, from the top centre clockwise, for the hold cue. */
    val holdMeasure: PathMeasure,
    /** The band's outer edge, which the charge of a held OK runs along. */
    val charge: ChargeLine,
)

/** The outline of the cinch's cushion round a [width] by [height] tile, [offset] pixels out; see [cushion]. */
internal fun cushionPath(
    width: Float,
    height: Float,
    offset: Float,
    dip: Float,
    corner: Float,
    waist: Float,
): Path = cushion(width, height, offset, offset, offset, offset, dip, corner, waist).path()

/** The vertices of a closed outline, x and y in turn, and the rounding of each. */
private class Vertices(
    val points: FloatArray,
    val rounding: List<CornerRounding>,
) {
    fun path(): Path = RoundedPolygon(points, perVertexRounding = rounding).toPath().asComposePath()
}

/**
 * The cinch-waist cushion round a [width] by [height] tile, pushed out [left], [top], [right] and [bottom] pixels: eight
 * vertices, the box's corners and side middles, each middle dipped [dip] in. Corners round by [corner] plus the mean push
 * of their two sides, waists by [waist], so even pushes run parallel. It starts at the middle of the top, clockwise.
 */
private fun cushion(
    width: Float,
    height: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    dip: Float,
    corner: Float,
    waist: Float,
): Vertices {
    val x0 = -left
    val y0 = -top
    val x1 = width + right
    val y1 = height + bottom
    val points =
        floatArrayOf(
            width / 2f,
            y0 + dip,
            x1,
            y0,
            x1 - dip,
            height / 2f,
            x1,
            y1,
            width / 2f,
            y1 - dip,
            x0,
            y1,
            x0 + dip,
            height / 2f,
            x0,
            y0,
        )
    val waistRounding = CornerRounding(waist)
    val pushes = floatArrayOf(top, right, bottom, left)
    // Vertex 2i + 1 is the corner after side i, clockwise from the top.
    return Vertices(
        points,
        List(8) { if (it % 2 == 0) waistRounding else CornerRounding(corner + (pushes[it / 2] + pushes[(it / 2 + 1) % 4]) / 2f) },
    )
}

/** The ring between two closed outlines. */
private fun ring(
    outer: Path,
    inner: Path,
): Path = Path().apply { op(outer, inner, PathOperation.Difference) }

/** The widths of an Expressive focus band, in pixels: the gap by the tile, filled only when [gapFilled], the band and the keyline. */
internal class BandWidths(
    val gap: Float,
    val band: Float,
    val keyline: Float,
    val gapFilled: Boolean,
)

/**
 * The cinch band round a [width] by [height] tile: the gap, band and keyline of [widths] laid over the cushion, whose sides
 * dip [dip] pixels into the tile, so the band covers the art there.
 */
internal fun cinchGeometry(
    width: Float,
    height: Float,
    widths: BandWidths,
    dip: Float,
    corner: Float,
    waist: Float,
): BandGeometry {
    fun at(offset: Float) = cushionPath(width, height, offset, dip, corner, waist)
    val base = at(0f)
    val gapEdge = at(widths.gap)
    val bandEdge = at(widths.gap + widths.band)
    val outer = at(widths.gap + widths.band + widths.keyline)
    val hold = at(widths.gap + widths.band / 2f)
    return BandGeometry(
        rings = arrayOf(if (widths.gapFilled) ring(gapEdge, base) else null, ring(bandEdge, gapEdge), ring(outer, bandEdge)),
        holdMeasure = PathMeasure().apply { setPath(hold, false) },
        charge = chargeLine(bandEdge, width, height),
    )
}

/** The charge line along [edge], the outline round a [width] by [height] tile, from the middle of its top. */
private fun chargeLine(
    edge: Path,
    width: Float,
    height: Float,
): ChargeLine = ChargeLine().apply { sample(PathMeasure().apply { setPath(edge, false) }, width / 2f, height / 2f) }

/**
 * The halo round a cinch tile [width] by [height]: [steps] bands from [reach] past the tile, its band's outer edge, out to
 * [left], [top], [right] and [bottom] more on each side, each band the cushion between two of those pushes, filled even-odd.
 */
internal fun cinchHalo(
    width: Float,
    height: Float,
    reach: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    steps: Int,
    dip: Float,
    corner: Float,
    waist: Float,
): Array<Path> {
    val edges =
        Array(steps + 1) {
            val out = it / steps.toFloat()
            cushion(
                width,
                height,
                reach + left * out,
                reach + top * out,
                reach + right * out,
                reach + bottom * out,
                dip,
                corner,
                waist,
            ).path()
        }
    return Array(steps) {
        Path().apply {
            fillType = PathFillType.EvenOdd
            addPath(edges[it + 1])
            addPath(edges[it])
        }
    }
}
