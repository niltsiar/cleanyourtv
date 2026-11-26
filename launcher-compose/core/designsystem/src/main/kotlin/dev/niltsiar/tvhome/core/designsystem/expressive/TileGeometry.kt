package dev.niltsiar.tvhome.core.designsystem.expressive

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
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
import kotlin.math.min

/**
 * A rounded rectangle of a size known in advance, as a [Shape]: the layer that clips to it takes its outline as is, so a
 * corner that steps through [CapsuleSteps] allocates nothing once each step was built.
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

/** The clip shapes of a capsule tile of one size, one per whole pixel of corner radius, each built the first time it is drawn. */
internal class CapsuleSteps(
    val size: Size,
    /** The corner radius at rest, in pixels. */
    val rest: Float,
) {
    /** Half the tile's shorter side: the radius of a full capsule. */
    val full: Float = min(size.width, size.height) / 2f
    private val steps = arrayOfNulls<FixedRoundRect>(ceil(full).toInt() + 1)

    /** The corner radius, on a whole pixel, at [morph] 0 (at rest) to 1 (a capsule). */
    fun radius(morph: Float): Float = (rest + (full - rest) * morph.coerceIn(0f, 1f)).toInt().toFloat().coerceAtMost(full)

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
): Path = cushion(width, height, offset, dip, corner, waist).path()

/** The vertices of a closed outline, x and y in turn, and the rounding of each. */
private class Vertices(
    val points: FloatArray,
    val rounding: List<CornerRounding>,
) {
    fun path(): Path = RoundedPolygon(points, perVertexRounding = rounding).toPath().asComposePath()
}

/**
 * The cinch-waist cushion round a [width] by [height] tile, pushed out by [offset] pixels: eight vertices, the four corners
 * of the tile's box and the middle of each side, which dips [dip] pixels in. Its corners are rounded by [corner] plus the
 * offset and its waists by [waist], so the offset outlines run parallel. It starts at the middle of the top and runs clockwise.
 */
private fun cushion(
    width: Float,
    height: Float,
    offset: Float,
    dip: Float,
    corner: Float,
    waist: Float,
): Vertices {
    val left = -offset
    val top = -offset
    val right = width + offset
    val bottom = height + offset
    val points =
        floatArrayOf(
            width / 2f,
            top + dip,
            right,
            top,
            right - dip,
            height / 2f,
            right,
            bottom,
            width / 2f,
            bottom - dip,
            left,
            bottom,
            left + dip,
            height / 2f,
            left,
            top,
        )
    val cornerRounding = CornerRounding(corner + offset)
    val waistRounding = CornerRounding(waist)
    return Vertices(points, List(8) { if (it % 2 == 0) waistRounding else cornerRounding })
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
    fun at(offset: Float) = cushion(width, height, offset, dip, corner, waist).path()
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
