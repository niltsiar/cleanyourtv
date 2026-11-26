package dev.niltsiar.tvhome.core.designsystem.expressive

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.HOLD_OK_MS
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The first part of a held OK shows nothing, so a click never shows the charge. */
private const val CHARGE_DEAD_MS = 120f

/** What a charged tile tightens toward from its focus scale, and how much of its focused corner it gives back at most. */
private const val CHARGED_SCALE = 1.02f
private const val CHARGE_PINCH = 0.4f

/** How far the charge of a press is, 0 to 1, when the press has run [held] of [HOLD_OK_MS]: 0 through the dead zone. */
internal fun chargeOf(held: Float): Float = ((held * HOLD_OK_MS - CHARGE_DEAD_MS) / (HOLD_OK_MS - CHARGE_DEAD_MS)).coerceIn(0f, 1f)

/** The scale of a tile grown to [grown] at [charge]: from [grown] toward [CHARGED_SCALE], never above [grown]. */
internal fun chargedScale(
    grown: Float,
    charge: Float,
): Float = if (grown <= CHARGED_SCALE) grown else grown + (CHARGED_SCALE - grown) * FastOutSlowInEasing.transform(charge)

/** The share of its focused corner a capsule keeps at [charge]. */
internal fun pinchOf(charge: Float): Float = 1f - CHARGE_PINCH * FastOutSlowInEasing.transform(charge)

/** The points the wave is drawn through round a whole loop. */
internal const val CHARGE_POINTS = 56

/** The wave's lobes round a loop, and how many lobes it travels forward over a whole charge. */
private const val CHARGE_LOBES = 8
private const val CHARGE_TRAVEL = 2f
private val ChargeAmplitude = 1.dp

/** The wave's stroke. */
internal val ChargeWidth = 2.dp

/**
 * The line a charge runs along, from the middle of its top clockwise: [CHARGE_POINTS] points evenly spaced along it, each
 * with its outward normal, as x, y, normal x, normal y. A tile's line is sampled when its outline is built; a capsule's is
 * sampled again in each charged frame, as its corner pinches, into the same array.
 */
internal class ChargeLine {
    val points = FloatArray(CHARGE_POINTS * 4)

    init {
        built++
    }

    /** Samples the rounded rectangle from [left], [top] to [right], [bottom] with corners of [radius]; it allocates nothing. */
    fun sampleRoundRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
    ) {
        val width = right - left
        val height = bottom - top
        val r = radius.coerceIn(0f, min(width, height) / 2f)
        val arc = (PI / 2.0).toFloat() * r
        val halfTop = width / 2f - r
        val side = height - 2f * r
        val across = width - 2f * r
        val length = 2f * (across + side) + 4f * arc
        for (i in 0 until CHARGE_POINTS) {
            var d = length * i / CHARGE_POINTS
            val n = i * 4
            if (d < halfTop) {
                set(n, left + width / 2f + d, top, 0f, -1f)
                continue
            }
            d -= halfTop
            if (d < arc) {
                corner(n, right - r, top + r, r, -90f + 90f * d / arc)
                continue
            }
            d -= arc
            if (d < side) {
                set(n, right, top + r + d, 1f, 0f)
                continue
            }
            d -= side
            if (d < arc) {
                corner(n, right - r, bottom - r, r, 90f * d / arc)
                continue
            }
            d -= arc
            if (d < across) {
                set(n, right - r - d, bottom, 0f, 1f)
                continue
            }
            d -= across
            if (d < arc) {
                corner(n, left + r, bottom - r, r, 90f + 90f * d / arc)
                continue
            }
            d -= arc
            if (d < side) {
                set(n, left, bottom - r - d, -1f, 0f)
                continue
            }
            d -= side
            if (d < arc) {
                corner(n, left + r, top + r, r, 180f + 90f * d / arc)
                continue
            }
            set(n, left + r + d - arc, top, 0f, -1f)
        }
    }

    /**
     * Samples the closed clockwise outline [measure] measures, starting at the point of its upper half nearest [centreX], so
     * a line whose own start lies elsewhere still runs from the middle of its top.
     */
    fun sample(
        measure: PathMeasure,
        centreX: Float,
        centreY: Float,
    ) {
        val length = measure.length
        var start = 0f
        var nearest = Float.MAX_VALUE
        for (k in 0 until START_SEARCH) {
            val d = length * k / START_SEARCH
            val at = measure.getPosition(d)
            val off = abs(at.x - centreX)
            if (at.y < centreY && off < nearest) {
                nearest = off
                start = d
            }
        }
        for (i in 0 until CHARGE_POINTS) {
            val d = (start + length * i / CHARGE_POINTS) % length
            val at = measure.getPosition(d)
            val tangent = measure.getTangent(d)
            set(i * 4, at.x, at.y, tangent.y, -tangent.x)
        }
    }

    private fun corner(
        n: Int,
        cx: Float,
        cy: Float,
        r: Float,
        degrees: Float,
    ) {
        val angle = Math.toRadians(degrees.toDouble())
        val nx = cos(angle).toFloat()
        val ny = sin(angle).toFloat()
        set(n, cx + nx * r, cy + ny * r, nx, ny)
    }

    private fun set(
        n: Int,
        x: Float,
        y: Float,
        nx: Float,
        ny: Float,
    ) {
        points[n] = x
        points[n + 1] = y
        points[n + 2] = nx
        points[n + 3] = ny
    }

    /** Where the wave at [s] points along the line lies, at [charge], into [out] at [at]. */
    fun waveAt(
        s: Float,
        charge: Float,
        amplitude: Float,
        out: FloatArray,
        at: Int,
    ) {
        val whole = s.toInt()
        val f = s - whole
        val a = (whole % CHARGE_POINTS) * 4
        val b = ((whole + 1) % CHARGE_POINTS) * 4
        val offset = amplitude * sin(2f * PI.toFloat() * (CHARGE_LOBES * s / CHARGE_POINTS - CHARGE_TRAVEL * charge))
        out[at] = points[a] + (points[b] - points[a]) * f + (points[a + 2] + (points[b + 2] - points[a + 2]) * f) * offset
        out[at + 1] = points[a + 1] + (points[b + 1] - points[a + 1]) * f + (points[a + 3] + (points[b + 3] - points[a + 3]) * f) * offset
    }

    internal companion object {
        /** Lines sampled so far, for a test that a hold samples none per frame. */
        var built = 0

        /** The steps of the search for an outline's top middle. */
        private const val START_SEARCH = 256
    }
}

/**
 * The charge of a held OK: a wave in [colour] along [line], from the middle of its top clockwise, [charge] of the way round,
 * closing the loop at 1; without [wavy], the line itself. Its points are written into [path], rewound first, and joined by
 * quadratic curves through their midpoints; [stroke] has round caps. [scratch] holds two points. Nothing here allocates.
 */
internal fun DrawScope.drawCharge(
    line: ChargeLine,
    charge: Float,
    colour: Color,
    path: Path,
    stroke: Stroke,
    scratch: FloatArray,
    wavy: Boolean = true,
) {
    if (charge <= 0f) return
    val amplitude = if (wavy) ChargeAmplitude.toPx() else 0f
    val reach = charge * CHARGE_POINTS
    val whole = reach.toInt()
    // Points 0 to whole sit on samples; a head between two samples ends the wave where the charge is.
    val last = if (reach > whole) whole + 1 else whole
    if (last < 1) return
    path.rewind()
    line.waveAt(0f, charge, amplitude, scratch, 0)
    path.moveTo(scratch[0], scratch[1])
    for (j in 1 until last) {
        line.waveAt(j.toFloat(), charge, amplitude, scratch, 0)
        line.waveAt(if (j + 1 > whole) reach else (j + 1).toFloat(), charge, amplitude, scratch, 2)
        path.quadraticTo(scratch[0], scratch[1], (scratch[0] + scratch[2]) / 2f, (scratch[1] + scratch[3]) / 2f)
    }
    line.waveAt(if (last > whole) reach else last.toFloat(), charge, amplitude, scratch, 0)
    path.lineTo(scratch[0], scratch[1])
    drawPath(path, colour, style = stroke)
}
