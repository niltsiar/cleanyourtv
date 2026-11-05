package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.lerp
import dev.niltsiar.tvhome.core.designsystem.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** A colour as OKLCH: lightness [l] 0 to 1, chroma [c] and hue [h] in degrees. */
internal data class Oklch(
    val l: Float,
    val c: Float,
    val h: Float,
)

private const val RING_MIN_L = 0.80f
private const val RING_MAX_L = 0.92f
private const val RING_MAX_C = 0.14f
private const val NEUTRAL_BELOW_C = 0.04f
private val NeutralRing = Color(0xFFF2F4F8)

/**
 * The focus ring of an app whose art gives [brand]: the brand's hue at a lightness of 0.80 to 0.92 and a chroma of at
 * most 0.14, so it reads on dark and on white tiles. A brand with almost no chroma (white, black, grey) gets the neutral
 * ring; one not known yet keeps white. Where the hue cannot hold that chroma on a screen, the chroma gives way.
 */
internal fun ringColor(brand: Color): Color {
    if (brand.isUnspecified) return Color.White
    val lch = oklch(brand)
    if (lch.c < NEUTRAL_BELOW_C) return NeutralRing
    return fromOklch(lch.l.coerceIn(RING_MIN_L, RING_MAX_L), min(lch.c, RING_MAX_C), lch.h)
}

internal fun oklch(color: Color): Oklch {
    val r = toLinear(color.red)
    val g = toLinear(color.green)
    val b = toLinear(color.blue)
    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
    val lightness = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
    val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
    val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    val hue = Math.toDegrees(atan2(bb, a).toDouble()).toFloat()
    return Oklch(lightness, hypot(a, bb), if (hue < 0f) hue + 360f else hue)
}

private const val GAMUT_STEPS = 16

/** The colour at [l], [c] and [h], its chroma lowered until all three channels fit a screen. */
private fun fromOklch(
    l: Float,
    c: Float,
    h: Float,
): Color {
    var rgb = linearRgb(l, c, h)
    if (!rgb.fits()) {
        var low = 0f
        var high = c
        repeat(GAMUT_STEPS) {
            val mid = (low + high) / 2f
            if (linearRgb(l, mid, h).fits()) low = mid else high = mid
        }
        rgb = linearRgb(l, low, h)
    }
    return Color(fromLinear(rgb[0]), fromLinear(rgb[1]), fromLinear(rgb[2]))
}

private fun FloatArray.fits(): Boolean = all { it in -0.0005f..1.0005f }

private fun linearRgb(
    lightness: Float,
    chroma: Float,
    hue: Float,
): FloatArray {
    val radians = Math.toRadians(hue.toDouble())
    val a = chroma * cos(radians).toFloat()
    val b = chroma * sin(radians).toFloat()
    val l = (lightness + 0.3963377774f * a + 0.2158037573f * b).pow(3)
    val m = (lightness - 0.1055613458f * a - 0.0638541728f * b).pow(3)
    val s = (lightness - 0.0894841775f * a - 1.2914855480f * b).pow(3)
    return floatArrayOf(
        4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
        -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
        -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
    )
}

private fun toLinear(channel: Float): Float = if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

private fun fromLinear(channel: Float): Float {
    val c = channel.coerceIn(0f, 1f)
    return if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
}

private const val RING_TRAVEL_MS = 220

/**
 * The colour the focus ring shows while it moves from one tile to the next. A tile that takes focus straight from
 * another one starts at the colour the old ring had and blends to its own in OKLab over [RING_TRAVEL_MS]; one that takes
 * it from anywhere else ([reset]: the top bar, a menu, the App library, another app) starts at its own. Only the
 * ring's draw reads [colourFor], so a move recomposes nothing.
 */
@Stable
internal class RingTravel {
    private var key: String? = null
    private var from = Color.White
    private var to = Color.White
    private var job: Job? = null
    private val progress = mutableFloatStateOf(1f)

    /** The ring of tile [key], whose own colour is [own]: its colour now, blended while it travels. */
    fun colourFor(
        key: String,
        own: Color,
    ): Color = if (key == this.key) blend(own) else own

    private fun blend(own: Color): Color = progress.floatValue.let { if (it >= 1f) own else lerp(from, own, it) }

    /** Tile [key] took focus; its ring is [own]. The blend runs in [scope]. */
    fun arrive(
        key: String,
        own: Color,
        motion: Motion,
        scope: CoroutineScope,
    ) {
        val start = if (this.key != null) blend(to) else own
        job?.cancel()
        this.key = key
        from = start
        to = own
        if (start == own) {
            progress.floatValue = 1f
            return
        }
        progress.floatValue = 0f
        job =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                animate(0f, 1f, animationSpec = motion.tweenSpec(RING_TRAVEL_MS)) { value, _ -> progress.floatValue = value }
            }
    }

    /** Focus left the tiles: the next ring starts at its own colour. */
    fun reset() {
        job?.cancel()
        key = null
        progress.floatValue = 1f
    }
}
