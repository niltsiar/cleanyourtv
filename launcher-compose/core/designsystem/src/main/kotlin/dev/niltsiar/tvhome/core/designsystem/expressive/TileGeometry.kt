package dev.niltsiar.tvhome.core.designsystem.expressive

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
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
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

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
 * The band of a [dev.niltsiar.tvhome.core.config.ExpressiveTile.Wave] or
 * [dev.niltsiar.tvhome.core.config.ExpressiveTile.Cinch] tile of one size: each ring of the band as a path built once, or,
 * for the wave, as an alpha mask rendered once at [maskScale] times the size so the grown tile draws it sharp; and the
 * line the hold cue runs along, from the top centre clockwise. Everything is in the tile's own pixels, its top left at 0.
 */
internal class BandGeometry(
    /** The rings from the tile outwards: gap, band, keyline. A null ring is not drawn. */
    val rings: Array<Path?>,
    /** The rings as masks, for the wave; null for the cinch, which draws [rings]. */
    val masks: Array<ImageBitmap?>?,
    /** How far the masks reach past the tile on every side, in pixels. */
    val maskReach: Float,
    /** The line through the middle of the band, from the top centre clockwise, for the hold cue. */
    val hold: Path,
    val holdMeasure: PathMeasure,
)

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

/**
 * The wave round a [width] by [height] tile with corners of [radius], every point pushed out along the edge's normal:
 * [lobes] lobes [peak] pixels out, one on each corner and the rest spread along the sides by their length, with a waist
 * [waist] pixels out between each two, on a cosine between. It starts at the middle of the top and runs clockwise.
 */
private fun wave(
    width: Float,
    height: Float,
    radius: Float,
    peak: Float,
    waist: Float,
    lobes: Int,
): Vertices {
    val edge = RoundedEdge(width, height, radius)
    val alongTop = round((lobes - 4) / 2f * width / (width + height)).toInt().let { it.coerceIn(1, (lobes - 4) / 2 - 1) }
    val alongSide = (lobes - 4) / 2 - alongTop
    // Where each lobe sits, as a distance round the edge from the middle of the top.
    val corners = FloatArray(4) { edge.cornerMiddle(it) }
    val at = ArrayList<Float>(lobes)

    fun spread(
        from: Float,
        to: Float,
        count: Int,
    ) {
        for (i in 1..count) at += from + (to - from) * i / (count + 1)
    }
    val topStart = corners[3] - edge.length
    spread(topStart, corners[0], alongTop)
    at += corners[0]
    spread(corners[0], corners[1], alongSide)
    at += corners[1]
    spread(corners[1], corners[2], alongTop)
    at += corners[2]
    spread(corners[2], corners[3], alongSide)
    at += corners[3]
    val sorted = at.map { (it + edge.length) % edge.length }.sorted()
    val points = FloatArray(sorted.size * SAMPLES_PER_LOBE * 2)
    val rounding = ArrayList<CornerRounding>(sorted.size * SAMPLES_PER_LOBE)
    val point = FloatArray(4)
    for (i in sorted.indices) {
        val lobe = sorted[i]
        val next = sorted[(i + 1) % sorted.size].let { if (it <= lobe) it + edge.length else it }
        val step = (next - lobe) / SAMPLES_PER_LOBE
        val smooth = CornerRounding(step * SAMPLE_ROUNDING)
        for (k in 0 until SAMPLES_PER_LOBE) {
            val out = waist + (peak - waist) * (1f + cos(2.0 * PI * k / SAMPLES_PER_LOBE).toFloat()) / 2f
            edge.pointAt((lobe + step * k) % edge.length, point)
            val n = (i * SAMPLES_PER_LOBE + k) * 2
            points[n] = point[0] + point[2] * out
            points[n + 1] = point[1] + point[3] * out
            rounding += smooth
        }
    }
    return Vertices(points, rounding)
}

/** Points per lobe, and the share of the distance between two that rounds each, so the wave reads as one curve. */
private const val SAMPLES_PER_LOBE = 8
private const val SAMPLE_ROUNDING = 0.5f

/** The edge of a rounded rectangle, walked by distance from the middle of its top, clockwise. */
private class RoundedEdge(
    private val width: Float,
    private val height: Float,
    radius: Float,
) {
    private val r = radius.coerceIn(0f, min(width, height) / 2f)
    private val arc = (PI / 2.0).toFloat() * r
    private val halfTop = width / 2f - r
    private val side = height - 2f * r
    private val across = width - 2f * r

    /** The parts in order: half the top, an arc, the right side, an arc, the bottom, an arc, the left side, an arc, half the top. */
    private val parts = floatArrayOf(halfTop, arc, side, arc, across, arc, side, arc, halfTop)
    val length: Float = parts.sum()

    /** Where the middle of corner [index] (0 top right, then clockwise) lies. */
    fun cornerMiddle(index: Int): Float {
        var at = 0f
        for (i in 0 until 1 + 2 * index) at += parts[i]
        return at + arc / 2f
    }

    /** The point [distance] round the edge, and its outward normal, into [out] as x, y, normal x, normal y. */
    fun pointAt(
        distance: Float,
        out: FloatArray,
    ) {
        var d = distance
        var part = 0
        while (part < parts.size - 1 && d > parts[part]) {
            d -= parts[part]
            part++
        }
        when (part) {
            0 -> set(out, width / 2f + d, 0f, 0f, -1f)
            1 -> corner(out, width - r, r, -90f + 90f * fraction(d))
            2 -> set(out, width, r + d, 1f, 0f)
            3 -> corner(out, width - r, height - r, 90f * fraction(d))
            4 -> set(out, width - r - d, height, 0f, 1f)
            5 -> corner(out, r, height - r, 90f + 90f * fraction(d))
            6 -> set(out, 0f, height - r - d, -1f, 0f)
            7 -> corner(out, r, r, 180f + 90f * fraction(d))
            else -> set(out, r + d, 0f, 0f, -1f)
        }
    }

    private fun fraction(d: Float): Float = if (arc == 0f) 0f else d / arc

    private fun corner(
        out: FloatArray,
        cx: Float,
        cy: Float,
        degrees: Float,
    ) {
        val angle = Math.toRadians(degrees.toDouble())
        val nx = cos(angle).toFloat()
        val ny = sin(angle).toFloat()
        set(out, cx + nx * r, cy + ny * r, nx, ny)
    }

    private fun set(
        out: FloatArray,
        x: Float,
        y: Float,
        nx: Float,
        ny: Float,
    ) {
        out[0] = x
        out[1] = y
        out[2] = nx
        out[3] = ny
    }
}

/** The ring between two closed outlines. */
private fun ring(
    outer: Path,
    inner: Path,
): Path = Path().apply { op(outer, inner, PathOperation.Difference) }

private fun roundRectPath(
    width: Float,
    height: Float,
    radius: Float,
    offset: Float,
): Path =
    Path().apply {
        addRoundRect(RoundRect(-offset, -offset, width + offset, height + offset, CornerRadius(max(0f, radius + offset))))
    }

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
        masks = null,
        maskReach = 0f,
        hold = hold,
        holdMeasure = PathMeasure().apply { setPath(hold, false) },
    )
}

/**
 * The wave band round a [width] by [height] tile with corners of [radius]: the band from the tile's edge, or from [inset]
 * inside the wave with an inset, out to a wave whose lobes reach [peak] and waists [waist], then a keyline [keyline] wide
 * outside the wave. The rings are rendered into alpha masks [reach] past the tile on each side, at [maskScale].
 */
internal fun waveGeometry(
    width: Float,
    height: Float,
    radius: Float,
    peak: Float,
    waist: Float,
    keyline: Float,
    inset: Float?,
    lobes: Int,
    reach: Float,
    maskScale: Float,
): BandGeometry {
    fun at(
        lobe: Float,
        between: Float,
    ) = wave(width, height, radius, lobe, between, lobes).path()
    val edge = at(peak, waist)
    val outer = at(peak + keyline, waist + keyline)
    val inner = if (inset == null) roundRectPath(width, height, radius, 0f) else at(peak - inset, waist - inset)
    val rings = arrayOf<Path?>(null, ring(edge, inner), ring(outer, edge))
    val hold = if (inset == null) at(peak / 2f, waist / 2f) else at(peak - inset / 2f, waist - inset / 2f)
    return BandGeometry(
        rings = rings,
        masks = Array(rings.size) { index -> rings[index]?.let { mask(it, width, height, reach, maskScale) } },
        maskReach = reach,
        hold = hold,
        holdMeasure = PathMeasure().apply { setPath(hold, false) },
    )
}

/** [ring] rendered into an alpha mask [reach] past a [width] by [height] tile on every side, at [scale] times its size. */
private fun mask(
    ring: Path,
    width: Float,
    height: Float,
    reach: Float,
    scale: Float,
): ImageBitmap {
    val image =
        ImageBitmap(
            ceil((width + 2f * reach) * scale).toInt(),
            ceil((height + 2f * reach) * scale).toInt(),
            ImageBitmapConfig.Alpha8,
        )
    val canvas = Canvas(image)
    canvas.scale(scale, scale)
    canvas.translate(reach, reach)
    canvas.drawPath(ring, Paint().apply { isAntiAlias = true })
    return image
}
