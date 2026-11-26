package dev.niltsiar.tvhome.core.designsystem.expressive

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.LookState
import dev.niltsiar.tvhome.core.designsystem.lookColorSets
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

val HousePaletteTest by testSuite {
    test("every text role reads at 4.5:1 and the focus band at 3:1 against its neighbours, for every seed, dark and light") {
        val failures =
            seeds().flatMap { (name, seed) ->
                listOf(true, false).flatMap { dark ->
                    val scheme = houseScheme(seed, dark)
                    textPairs(scheme).mapNotNull { (pair, fg, bg) ->
                        contrast(fg, bg).takeIf { it < 4.5 }?.let { "$name ${if (dark) "dark" else "light"} $pair: $it" }
                    } +
                        bandPairs(scheme).mapNotNull { (pair, fg, bg) ->
                            contrast(fg, bg).takeIf { it < 3.0 }?.let { "$name ${if (dark) "dark" else "light"} $pair: $it" }
                        }
                }
            }
        failures.shouldBeEmpty()
    }

    test(
        "an app's band and its charge's start read at 3:1 against the tile fill, the dock, the gap and the keyline, for every accent and seed",
    ) {
        val bands = seeds().map { (name, accent) -> name to appBand(accent) }
        val failures =
            seeds().flatMap { (seedName, seed) ->
                val scheme = houseScheme(seed, dark = true)
                val grounds =
                    listOf(
                        "tile fill" to scheme.surfaceContainerHigh,
                        "dock" to scheme.surfaceContainer,
                        "surface gap" to scheme.surface,
                        "scrim keyline" to scheme.scrim,
                    )
                bands.flatMap { (accentName, band) ->
                    grounds.flatMap { (groundName, ground) ->
                        listOf("band" to band.band, "charge start" to band.chargeStart).mapNotNull { (role, colour) ->
                            contrast(colour, ground).takeIf { it < 3.0 }?.let { "$accentName $role on $seedName $groundName: $it" }
                        }
                    }
                }
            }
        failures.shouldBeEmpty()
    }

    test("an app's band keeps the hue of its colour, and two app colours give two bands") {
        val red = appBand(Color(0xFFE0453A)).band
        val blue = appBand(Color(0xFF2F9FD8)).band
        withClue("$red") { (red.red > red.blue).shouldBeTrue() }
        withClue("$blue") { (blue.blue > blue.red).shouldBeTrue() }
        AppBands[Color(0xFFE0453A)].band shouldBe red
    }

    test("the error role stays apart from the primary for every seed, dark and light") {
        val failures =
            seeds().flatMap { (name, seed) ->
                listOf(true, false).mapNotNull { dark ->
                    val scheme = houseScheme(seed, dark)
                    deltaE(scheme.error, scheme.primary).takeIf { it < MIN_ERROR_DISTANCE }?.let {
                        "$name ${if (dark) "dark" else "light"}: error ${scheme.error} primary ${scheme.primary}, delta E $it"
                    }
                }
            }
        failures.shouldBeEmpty()
    }

    test("the status roles read at 4.5:1 on every container, dark and light") {
        val failures =
            listOf(true, false).flatMap { dark ->
                val scheme = houseScheme(Ember, dark)
                val status = houseStatus(dark)
                val grounds =
                    listOf(
                        scheme.surface,
                        scheme.surfaceContainerLowest,
                        scheme.surfaceContainerLow,
                        scheme.surfaceContainer,
                        scheme.surfaceContainerHigh,
                        scheme.surfaceContainerHighest,
                    )
                val pairs =
                    grounds.flatMap { listOf(Triple("success", status.success, it), Triple("warning", status.warning, it)) } +
                        Triple("onSuccessContainer", status.onSuccessContainer, status.successContainer)
                pairs.mapNotNull { (name, fg, bg) ->
                    contrast(fg, bg).takeIf { it < 4.5 }?.let { "${if (dark) "dark" else "light"} $name on $bg: $it" }
                }
            }
        failures.shouldBeEmpty()
    }

    test("a seed far from red keeps a red error role") {
        val error = houseScheme(Color(0xFF3050D0), dark = true).error

        withClue("error $error") { (error.red > error.green && error.red > error.blue) shouldBe true }
    }

    test("each role sits at its tone in CIE L*") {
        val dark = houseScheme(Ember, dark = true)
        val light = houseScheme(Ember, dark = false)

        lightness(dark.primary) shouldBe (80.0 plusOrMinus 0.6)
        lightness(dark.primaryContainer) shouldBe (30.0 plusOrMinus 0.6)
        lightness(dark.surface) shouldBe (6.0 plusOrMinus 0.6)
        lightness(dark.surfaceContainerHigh) shouldBe (17.0 plusOrMinus 0.6)
        lightness(dark.onSurface) shouldBe (90.0 plusOrMinus 0.6)
        lightness(light.primary) shouldBe (40.0 plusOrMinus 0.6)
        lightness(light.surfaceContainer) shouldBe (94.0 plusOrMinus 0.6)
    }

    test("the primary keeps the seed's hue, and two seeds give two schemes") {
        val red = houseScheme(Color(0xFFD03030), dark = true).primary
        val blue = houseScheme(Color(0xFF3050D0), dark = true).primary

        withClue("red primary $red") { (red.red > red.blue) shouldBe true }
        withClue("blue primary $blue") { (blue.blue > blue.red) shouldBe true }
        red shouldNotBe blue
    }

    test("a grey seed still gives a coloured primary and grey containers") {
        val scheme = houseScheme(Color(0xFF808080), dark = true)

        withClue("primary ${scheme.primary}") { spread(scheme.primary) shouldNotBe 0f }
        withClue("container ${scheme.surfaceContainer}") { (spread(scheme.surfaceContainer) < 0.05f) shouldBe true }
    }

    test("a carried tile's ring is its app's band, and the plate it lifts off is the name pills' container whatever the app") {
        val red = Color(0xFFE0453A)
        val blue = Color(0xFF2F9FD8)
        for (seed in listOf(Ember, NightTeal)) {
            val look = LookState(Look.Expressive) { seed }
            val sets = lookColorSets(look)
            for ((dark, colours) in listOf(true to sets.dark, false to sets.light)) {
                withClue("seed $seed, ${if (dark) "dark" else "light"} ground") {
                    val rings = listOf(red, blue).map { colours.carriedRing(it, neutral = false) }
                    rings shouldBe listOf(AppBands[red].band, AppBands[blue].band)
                    rings[0] shouldNotBe rings[1]
                    colours.liftPlate() shouldBe look.scheme(dark).surfaceContainerHighest
                }
            }
        }
    }

    test("the lift plate stands off the dock under it by the bound of its ground, for every seed") {
        val failures =
            listOf(true to MIN_PLATE_CONTRAST_DARK, false to MIN_PLATE_CONTRAST_LIGHT).flatMap { (dark, bound) ->
                seeds().mapNotNull { (name, seed) ->
                    val look = LookState(Look.Expressive) { seed }
                    val sets = lookColorSets(look)
                    val plate = (if (dark) sets.dark else sets.light).liftPlate()
                    contrast(plate, look.scheme(dark).surfaceContainer).takeIf { it < bound }?.let {
                        "${if (dark) "dark" else "light"} $name: $it"
                    }
                }
            }
        failures.shouldBeEmpty()
    }
}

private val Ember = Color(0xFFFF6A3D)
private val NightTeal = Color(0xFF1F8A8A)

/**
 * The least contrast of the lift plate against the dock: the step from the dock's container (tone 12 dark, 94 light) to
 * the name pills' (22 dark, 90 light) that the house tones give, 1.33:1 dark and 1.11:1 light, less the 0.6 L* the tone
 * solver may miss by. The plate only marks the place a carried tile left: the tile itself shows by its band at 3:1.
 */
private const val MIN_PLATE_CONTRAST_DARK = 1.3
private const val MIN_PLATE_CONTRAST_LIGHT = 1.09

/**
 * The least CIE76 distance between the error and the primary roles. At the same tone two roles differ only in hue and
 * chroma; at 20 a warning row's text and the focused pill beside it read as two colours from the sofa, where the Ember
 * seed's 3 made a warning look like the selected pill.
 */
private const val MIN_ERROR_DISTANCE = 20.0

/** The seeds: hue by chroma by tone, in HSL, then the named ones the house seed chain gives. */
private fun seeds(): List<Pair<String, Color>> {
    val swept =
        (0 until 360 step 15).flatMap { hue ->
            listOf(0f, 0.2f, 0.55f, 1f).flatMap { saturation ->
                listOf(0.08f, 0.3f, 0.5f, 0.7f, 0.92f).map { tone ->
                    "hsl($hue, $saturation, $tone)" to hsl(hue.toFloat(), saturation, tone)
                }
            }
        }
    val glowWarmth = Color(0xFFFFD9A0)
    val named =
        listOf(
            // The day-part seeds of :data:wallpaper's HouseSeed.kt.
            "night teal" to Color(0xFF1F8A8A),
            "morning amber" to Color(0xFFE8A33D),
            "forenoon sky blue" to Color(0xFF4A9FE0),
            "afternoon periwinkle" to Color(0xFF8B93E8),
            "evening magenta" to Color(0xFFC03A8C),
            // SkyPalette.glow: a horizon colour of Sky.kt lerped 0.65 toward its glow warmth.
            "night sky glow" to lerp(Color(0xFF0B1326), glowWarmth, 0.65f),
            "sunset sky glow" to lerp(Color(0xFFE2704A), glowWarmth, 0.65f),
            "dawn sky glow" to lerp(Color(0xFF8A4A6B), glowWarmth, 0.65f),
            "near-black accent" to Color(0xFF050505),
            "near-white accent" to Color(0xFFFAFAFA),
            "pure blue accent" to Color(0xFF0000FF),
            "pure yellow accent" to Color(0xFFFFFF00),
            "default seed" to Color(0xFF7CB7FF),
            "ember" to Ember,
            "transparent seed" to Color(0x00FF0000),
            "unspecified seed" to Color.Unspecified,
        )
    return swept + named
}

private fun textPairs(s: ColorScheme): List<Triple<String, Color, Color>> =
    listOf(
        Triple("onPrimary on primary", s.onPrimary, s.primary),
        Triple("onPrimaryContainer on primaryContainer", s.onPrimaryContainer, s.primaryContainer),
        Triple("onSecondary on secondary", s.onSecondary, s.secondary),
        Triple("onSecondaryContainer on secondaryContainer", s.onSecondaryContainer, s.secondaryContainer),
        Triple("onTertiary on tertiary", s.onTertiary, s.tertiary),
        Triple("onTertiaryContainer on tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer),
        Triple("onError on error", s.onError, s.error),
        Triple("onErrorContainer on errorContainer", s.onErrorContainer, s.errorContainer),
        Triple("inverseOnSurface on inverseSurface", s.inverseOnSurface, s.inverseSurface),
    ) +
        listOf(
            "surface" to s.surface,
            "surfaceContainerLowest" to s.surfaceContainerLowest,
            "surfaceContainerLow" to s.surfaceContainerLow,
            "surfaceContainer" to s.surfaceContainer,
            "surfaceContainerHigh" to s.surfaceContainerHigh,
            "surfaceContainerHighest" to s.surfaceContainerHighest,
        ).flatMap { (name, ground) ->
            listOf(
                Triple("onSurface on $name", s.onSurface, ground),
                Triple("onSurfaceVariant on $name", s.onSurfaceVariant, ground),
                Triple("primary on $name", s.primary, ground),
                Triple("error on $name", s.error, ground),
            )
        }

/** The focus band and what lies beside it: the gap in the surface colour, the dark keyline, a tile at rest. */
private fun bandPairs(s: ColorScheme): List<Triple<String, Color, Color>> =
    listOf(
        Triple("primary band beside the surface gap", s.primary, s.surface),
        Triple("primary band beside the lowest container", s.primary, s.surfaceContainerLowest),
        Triple("primary band beside the scrim keyline", s.primary, s.scrim),
        Triple("tertiary band beside the surface gap", s.tertiary, s.surface),
    )

/** WCAG 2 contrast ratio, from the 8-bit sRGB channels. */
private fun contrast(
    a: Color,
    b: Color,
): Double {
    val la = wcagLuminance(a)
    val lb = wcagLuminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

private fun wcagLuminance(colour: Color): Double {
    val argb = colour.toArgb()

    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/** CIE76 distance between [a] and [b] in CIELAB (D65), from their 8-bit sRGB channels. */
private fun deltaE(
    a: Color,
    b: Color,
): Double {
    val la = cieLab(a)
    val lb = cieLab(b)
    return sqrt((la[0] - lb[0]).pow(2) + (la[1] - lb[1]).pow(2) + (la[2] - lb[2]).pow(2))
}

private fun cieLab(colour: Color): DoubleArray {
    val argb = colour.toArgb()

    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    val r = channel(16)
    val g = channel(8)
    val b = channel(0)

    fun f(t: Double): Double = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0
    val x = f((0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047)
    val y = f(0.2126 * r + 0.7152 * g + 0.0722 * b)
    val z = f((0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883)
    return doubleArrayOf(116.0 * y - 16.0, 500.0 * (x - y), 200.0 * (y - z))
}

/** CIE L* of [colour]. */
private fun lightness(colour: Color): Double {
    val y = wcagLuminance(colour)
    return if (y > 216.0 / 24389.0) 116.0 * cbrt(y) - 16.0 else y * 24389.0 / 27.0
}

private fun spread(colour: Color): Float = max(colour.red, max(colour.green, colour.blue)) - min(colour.red, min(colour.green, colour.blue))

private fun hsl(
    hue: Float,
    saturation: Float,
    lightness: Float,
): Color {
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation
    val sector = hue / 60f
    val x = chroma * (1f - abs(sector.mod(2f) - 1f))
    val m = lightness - chroma / 2f
    val (r, g, b) =
        when (sector.toInt()) {
            0 -> Triple(chroma, x, 0f)
            1 -> Triple(x, chroma, 0f)
            2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma)
            4 -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
    return Color(r + m, g + m, b + m)
}
