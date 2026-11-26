package dev.niltsiar.tvhome.core.designsystem.expressive

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The house scheme of [seed], dark or light: the Material 3 roles at the M3 tones, each a colour of its palette's hue and
 * chroma whose CIE L* is the role's tone. Contrast between two roles depends only on their luminance, so it follows from the
 * tones whatever the seed. material3 builds a scheme from a seed only through internal code (its `TonalPalette` and HCT
 * solver), and its public dynamic schemes read the system's palette on API 31 and up, so the palettes are built here: hue
 * and chroma from the seed in OKLCH, the tone solved in CIE L*.
 */
internal fun houseScheme(
    seed: Color,
    dark: Boolean,
): ColorScheme {
    val (hue, chroma) = hueAndChroma(seed)
    val primary = Palette(hue, chroma.coerceIn(PRIMARY_CHROMA_FLOOR, PRIMARY_CHROMA_CEILING))
    val secondary = Palette(hue, SECONDARY_CHROMA)
    val tertiary = Palette(hue + TERTIARY_TURN, TERTIARY_CHROMA)
    val neutral = Palette(hue, NEUTRAL_CHROMA)
    val variant = Palette(hue, NEUTRAL_VARIANT_CHROMA)
    val error = Palette(errorHue(hue), ERROR_CHROMA)
    return if (dark) {
        darkColorScheme(
            primary = primary(80),
            onPrimary = primary(20),
            primaryContainer = primary(30),
            onPrimaryContainer = primary(90),
            inversePrimary = primary(40),
            secondary = secondary(80),
            onSecondary = secondary(20),
            secondaryContainer = secondary(30),
            onSecondaryContainer = secondary(90),
            tertiary = tertiary(80),
            onTertiary = tertiary(20),
            tertiaryContainer = tertiary(30),
            onTertiaryContainer = tertiary(90),
            background = neutral(6),
            onBackground = neutral(90),
            surface = neutral(6),
            onSurface = neutral(90),
            surfaceVariant = variant(30),
            onSurfaceVariant = variant(80),
            surfaceTint = primary(80),
            inverseSurface = neutral(90),
            inverseOnSurface = neutral(20),
            error = error(80),
            onError = error(20),
            errorContainer = error(30),
            onErrorContainer = error(90),
            outline = variant(60),
            outlineVariant = variant(30),
            scrim = Color.Black,
            surfaceBright = neutral(24),
            surfaceContainer = neutral(12),
            surfaceContainerHigh = neutral(17),
            surfaceContainerHighest = neutral(22),
            surfaceContainerLow = neutral(10),
            surfaceContainerLowest = neutral(4),
            surfaceDim = neutral(6),
        )
    } else {
        lightColorScheme(
            primary = primary(40),
            onPrimary = primary(100),
            primaryContainer = primary(90),
            onPrimaryContainer = primary(10),
            inversePrimary = primary(80),
            secondary = secondary(40),
            onSecondary = secondary(100),
            secondaryContainer = secondary(90),
            onSecondaryContainer = secondary(10),
            tertiary = tertiary(40),
            onTertiary = tertiary(100),
            tertiaryContainer = tertiary(90),
            onTertiaryContainer = tertiary(10),
            background = neutral(98),
            onBackground = neutral(10),
            surface = neutral(98),
            onSurface = neutral(10),
            surfaceVariant = variant(90),
            onSurfaceVariant = variant(30),
            surfaceTint = primary(40),
            inverseSurface = neutral(20),
            inverseOnSurface = neutral(95),
            error = error(40),
            onError = error(100),
            errorContainer = error(90),
            onErrorContainer = error(10),
            outline = variant(50),
            outlineVariant = variant(80),
            scrim = Color.Black,
            surfaceBright = neutral(98),
            surfaceContainer = neutral(94),
            surfaceContainerHigh = neutral(92),
            surfaceContainerHighest = neutral(90),
            surfaceContainerLow = neutral(96),
            surfaceContainerLowest = neutral(100),
            surfaceDim = neutral(87),
        )
    }
}

/**
 * The focus band of an app whose colour is [accent], at the dark scheme's primary tone of the accent's own palette, so the
 * band keeps the house primary's contrast whatever the art.
 */
internal class AppBand(
    val band: Color,
)

internal fun appBand(accent: Color): AppBand {
    val (hue, chroma) = hueAndChroma(accent)
    return AppBand(band = Palette(hue, chroma.coerceIn(PRIMARY_CHROMA_FLOOR, PRIMARY_CHROMA_CEILING))(BAND_TONE))
}

/**
 * Where the charge of a held OK on a focus [band] starts: the band's OKLCH hue at no more than the secondary role's chroma,
 * its OKLab lightness eased toward the grey of the band's luminance as the chroma drops, so the tone stays close to the
 * band's. Plain arithmetic in [scratch] (3 or more floats), so a band that changes on every frame, as one travelling
 * between two apps' colours does, solves nothing and allocates nothing.
 */
internal fun chargeStart(
    band: Color,
    scratch: FloatArray,
): Color {
    val r = linear(band.red)
    val g = linear(band.green)
    val b = linear(band.blue)
    val lab = oklab(r, g, b, scratch)
    val chroma = sqrt(lab[1] * lab[1] + lab[2] * lab[2])
    if (chroma <= SECONDARY_CHROMA) return band
    val keep = SECONDARY_CHROMA / chroma
    val grey = cbrt(0.2126f * r + 0.7152f * g + 0.0722f * b)
    return encode(linearRgb(grey + (lab[0] - grey) * keep, lab[1] * keep, lab[2] * keep, scratch))
}

/** The tone of the dark scheme's primary role, which the focus band takes. */
private const val BAND_TONE = 80

/** The [AppBand]s of the last 64 colours asked for, so a band read in every frame builds nothing once its colour was seen. */
internal object AppBands {
    private val bands = RecentSlots(arrayOfNulls<AppBand>(64))

    operator fun get(accent: Color): AppBand {
        val key = accent.value.toLong()
        return bands.find(key) ?: bands.keep(key, appBand(accent))
    }
}

/**
 * The status roles the house scheme adds to Material 3's: [success] and [warning] ink, at the error role's tones, and a
 * success container with its text.
 */
internal class HouseStatus(
    val success: Color,
    val warning: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

/** The status roles of the house scheme, [dark] or light; they take no hue from the seed. */
internal fun houseStatus(dark: Boolean): HouseStatus {
    val success = Palette(SUCCESS_HUE, STATUS_CHROMA)
    val warning = Palette(WARNING_HUE, STATUS_CHROMA)
    return if (dark) {
        HouseStatus(success = success(80), warning = warning(80), successContainer = success(30), onSuccessContainer = success(90))
    } else {
        HouseStatus(success = success(40), warning = warning(40), successContainer = success(90), onSuccessContainer = success(10))
    }
}

/** The hues of the success (a green) and warning (an amber) roles, in radians, the same for every seed. */
private const val SUCCESS_HUE = 2.5f
private const val WARNING_HUE = 1.2f
private const val STATUS_CHROMA = 0.14f

/** OKLCH chroma of the palettes, in OKLab units. Expressive keeps primary above [PRIMARY_CHROMA_FLOOR], so a grey seed still gives a coloured band. */
private const val PRIMARY_CHROMA_FLOOR = 0.10f
private const val PRIMARY_CHROMA_CEILING = 0.20f
private const val SECONDARY_CHROMA = 0.045f
private const val TERTIARY_CHROMA = 0.10f
private const val NEUTRAL_CHROMA = 0.012f
private const val NEUTRAL_VARIANT_CHROMA = 0.022f
private const val ERROR_CHROMA = 0.16f

/** Tertiary turns the hue by 60 degrees. */
private const val TERTIARY_TURN = (PI / 3).toFloat()

/** The hue of the error roles, a red, in radians. */
private const val ERROR_HUE = 0.5f

/** The least turn between the error hue and the seed's: closer, a warning reads as the primary. */
private const val ERROR_HUE_GAP = (PI / 3).toFloat()

/**
 * The error hue for a seed of [hue]: red, or for a seed within [ERROR_HUE_GAP] of red, that far from the seed on red's
 * side of it, toward crimson from an orange seed and toward orange from a pink one.
 */
private fun errorHue(hue: Float): Float {
    val off = atan2(sin(hue - ERROR_HUE), cos(hue - ERROR_HUE))
    return when {
        abs(off) >= ERROR_HUE_GAP -> ERROR_HUE
        off >= 0f -> hue - ERROR_HUE_GAP
        else -> hue + ERROR_HUE_GAP
    }
}

private const val SEARCH_STEPS = 24

/** The colours of one hue at one chroma, by tone: the chroma drops where the tone has no colour that bright in sRGB. */
private class Palette(
    private val hue: Float,
    private val chroma: Float,
) {
    operator fun invoke(tone: Int): Color = toneColour(hue, chroma, tone.toFloat())
}

/** The OKLCH hue, in radians, and chroma of [colour]. */
private fun hueAndChroma(colour: Color): Pair<Float, Float> {
    val lab = oklab(linear(colour.red), linear(colour.green), linear(colour.blue))
    return atan2(lab[2], lab[1]) to sqrt(lab[1] * lab[1] + lab[2] * lab[2])
}

/** How many tones [toneColour] has solved in this process, for a test that a path builds no palette. */
internal var toneSolves = 0
    private set

/**
 * The colour of OKLCH [hue] whose CIE L* is [tone], with [chroma] or, where sRGB has no such colour, the most chroma it has
 * at that tone. A grey of any tone is in sRGB, so the search always ends in gamut.
 */
private fun toneColour(
    hue: Float,
    chroma: Float,
    tone: Float,
): Color {
    toneSolves++
    if (tone <= 0f) return Color.Black
    if (tone >= 100f) return Color.White
    val y = luminanceOfTone(tone)
    val a = cos(hue)
    val b = sin(hue)
    val full = solveLightness(a * chroma, b * chroma, y)
    if (inGamut(full)) return encode(full)
    var low = 0f
    var high = chroma
    var best = solveLightness(0f, 0f, y)
    repeat(SEARCH_STEPS) {
        val mid = (low + high) / 2f
        val rgb = solveLightness(a * mid, b * mid, y)
        if (inGamut(rgb)) {
            best = rgb
            low = mid
        } else {
            high = mid
        }
    }
    return encode(best)
}

/** Linear sRGB of the OKLab colour with [a] and [b] whose luminance is [y], by bisection on its lightness. */
private fun solveLightness(
    a: Float,
    b: Float,
    y: Float,
): FloatArray {
    var low = 0f
    var high = 1f
    var rgb = linearRgb(0.5f, a, b)
    repeat(SEARCH_STEPS) {
        val mid = (low + high) / 2f
        rgb = linearRgb(mid, a, b)
        if (luminance(rgb) < y) low = mid else high = mid
    }
    return rgb
}

private const val GAMUT_SLACK = 1e-4f

private fun inGamut(rgb: FloatArray): Boolean = rgb.all { it >= -GAMUT_SLACK && it <= 1f + GAMUT_SLACK }

private fun luminance(rgb: FloatArray): Float = 0.2126f * rgb[0] + 0.7152f * rgb[1] + 0.0722f * rgb[2]

/** The relative luminance of CIE L* [tone]. */
private fun luminanceOfTone(tone: Float): Float {
    val f = (tone + 16f) / 116f
    return if (tone > 8f) f * f * f else tone * 27f / 24389f
}

private fun encode(rgb: FloatArray): Color = Color(gamma(rgb[0]), gamma(rgb[1]), gamma(rgb[2]))

private fun linear(channel: Float): Float = if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

private fun gamma(channel: Float): Float {
    val c = channel.coerceIn(0f, 1f)
    return if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
}

/** OKLab of linear sRGB (Ottosson's matrices). */
private fun oklab(
    r: Float,
    g: Float,
    b: Float,
    out: FloatArray = FloatArray(3),
): FloatArray {
    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
    out[0] = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
    out[1] = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
    out[2] = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    return out
}

/** Linear sRGB of OKLab ([lightness], [a], [b]); out of gamut it may leave 0 to 1. */
private fun linearRgb(
    lightness: Float,
    a: Float,
    b: Float,
    out: FloatArray = FloatArray(3),
): FloatArray {
    val l = (lightness + 0.3963377774f * a + 0.2158037573f * b).let { it * it * it }
    val m = (lightness - 0.1055613458f * a - 0.0638541728f * b).let { it * it * it }
    val s = (lightness - 0.0894841775f * a - 1.2914855480f * b).let { it * it * it }
    out[0] = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
    out[1] = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
    out[2] = -0.0045160939f * l - 0.0782771729f * m + 1.0742872363f * s
    return out
}
