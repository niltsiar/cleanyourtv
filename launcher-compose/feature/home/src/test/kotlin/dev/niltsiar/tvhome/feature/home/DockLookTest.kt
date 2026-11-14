package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.Color
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.pow

/** What the dock panel is tinted with, from the theme and the wallpaper under it. */
val DockLookTest by testSuite {
    val dark = Color(0xFF0B0D12)

    val light = Color(0xFFF4F6FA)

    fun encode(luminance: Float) = 1.055f * luminance.pow(1f / 2.4f) - 0.055f

    fun decode(value: Float) = ((value + 0.055f) / 1.055f).pow(2.4f)

    /** The relative luminance of a grey frost of CIE L* [lightness] under [tint], mixed on the encoded values. */
    fun trayLuminance(
        tint: Color,
        lightness: Float,
    ): Float {
        val frost = encode(((lightness + 16f) / 116f).pow(3))

        fun mixed(channel: Float) = decode(frost * (1f - tint.alpha) + channel * tint.alpha)
        return 0.2126f * mixed(tint.red) + 0.7152f * mixed(tint.green) + 0.0722f * mixed(tint.blue)
    }

    test("the dark tint is 016 and the light one 020 on a dim wallpaper") {
        DockLook().tint(darkSurfaces = true, lightness = 60f) shouldBe dark.copy(alpha = 0.16f)
        DockLook().tint(darkSurfaces = false, lightness = 60f) shouldBe light.copy(alpha = 0.20f)
    }

    test("above L 60 the dark tint climbs to 022 at L 75") {
        for ((lightness, extra) in listOf(60f to 0f, 67.5f to 0.03f, 75f to 0.06f, 95f to 0.06f)) {
            withClue("dark at $lightness") { DockLook().tint(true, lightness).alpha shouldBe (0.16f + extra plusOrMinus 0.005f) }
        }
    }

    test("dock glass takes the light tint in night mode") {
        DockLook(glass = true).tint(darkSurfaces = true, lightness = 60f) shouldBe light.copy(alpha = 0.20f)
    }

    test("dock opacity sets the tint to 085 times the percent and the wallpaper no longer matters") {
        DockLook(opacity = 70).tint(true, lightness = 90f) shouldBe dark.copy(alpha = 0.85f * 0.70f)
        DockLook(opacity = 30, glass = true).tint(true, lightness = 90f) shouldBe light.copy(alpha = 0.85f * 0.30f)
    }

    test("the light tray keeps a relative luminance under 056 over any frost") {
        for (lightness in listOf(40f, 75f, 85f, 90f, 95f, 100f)) {
            val tint = DockLook(glass = true).tint(darkSurfaces = true, lightness = lightness)

            withClue("L* $lightness gives ${trayLuminance(tint, lightness)}") {
                trayLuminance(tint, lightness) shouldBeLessThanOrEqualTo
                    0.56f
            }
        }
    }

    test("on a dark picture the light tray lifts the picture by at most 002") {
        for (lightness in listOf(1f, 5f, 10f, 15f, 20f, 25f, 30f)) {
            val tint = DockLook(glass = true).tint(darkSurfaces = true, lightness = lightness)
            val lift = trayLuminance(tint, lightness) - ((lightness + 16f) / 116f).pow(3)

            withClue("L* $lightness lifts the picture by $lift") { lift shouldBeLessThanOrEqualTo 0.021f }
        }
    }

    test("over a frost from L 31 to 38 the light tray lifts it to at most 1 6 times its luminance") {
        for (lightness in listOf(31f, 33f, 35f, 36.5f, 38f)) {
            val tint = DockLook(glass = true).tint(darkSurfaces = true, lightness = lightness)
            val frost = ((lightness + 16f) / 116f).pow(3)

            withClue("L* $lightness gives ${trayLuminance(tint, lightness) / frost} times") {
                trayLuminance(tint, lightness) shouldBeLessThanOrEqualTo
                    1.6f * frost
            }
        }
    }

    test("over a frost of L 35 to 50 the light tray lifts it by at most 004") {
        for (lightness in listOf(35f, 40f, 45f, 50f)) {
            val tint = DockLook(glass = true).tint(darkSurfaces = true, lightness = lightness)
            val lift = trayLuminance(tint, lightness) - ((lightness + 16f) / 116f).pow(3)

            withClue("L* $lightness lifts the picture by $lift") { lift shouldBeLessThanOrEqualTo 0.041f }
        }
    }

    test("over a frost of L 65 or more the light tray is the base tint or at the 055 cap") {
        for (lightness in listOf(65f, 70f, 75f, 80f, 90f, 100f)) {
            val tint = DockLook(glass = true).tint(darkSurfaces = true, lightness = lightness)
            val base = light.copy(alpha = 0.20f + 0.06f * ((lightness - 60f) / 15f).coerceIn(0f, 1f))

            withClue("L* $lightness") {
                trayLuminance(tint, lightness) shouldBe
                    (minOf(trayLuminance(base, lightness), 0.55f) plusOrMinus 0.005f)
            }
        }
    }

    test("the light tray never gets darker as the frost gets lighter from L 30 to 80") {
        var before = 0f
        for (lightness in 30..80) {
            val tint = DockLook(glass = true).tint(darkSurfaces = true, lightness = lightness.toFloat())
            val tray = trayLuminance(tint, lightness.toFloat())

            withClue("L* $lightness gives $tray after $before") { tray shouldBeGreaterThanOrEqualTo before - 0.0005f }
            before = tray
        }
    }

    test("a light tray keeps all its gloss on a frost of L 60 or more or with no frost and a dark one always") {
        for (lightness in listOf(0f, 60f, 80f, 100f)) {
            withClue("L* $lightness") {
                DockLook(glass = true).gloss(darkSurfaces = true, lightness = lightness) shouldBe
                    (1f plusOrMinus 0f)
            }
        }
        for (lightness in listOf(1f, 30f, 40f, 100f)) {
            withClue("dark tray at L* $lightness") {
                DockLook().gloss(darkSurfaces = true, lightness = lightness) shouldBe
                    (1f plusOrMinus 0f)
            }
        }
    }

    test("a light tray over a frost of L 30 or less keeps a quarter of its gloss and more up to L 60") {
        DockLook(glass = true).gloss(darkSurfaces = true, lightness = 30f) shouldBe (0.25f plusOrMinus 0.001f)
        DockLook(glass = true).gloss(darkSurfaces = true, lightness = 10f) shouldBe (0.25f plusOrMinus 0.001f)
        DockLook(glass = true).gloss(darkSurfaces = true, lightness = 45f) shouldBe (0.625f plusOrMinus 0.001f)
        DockLook(glass = true).gloss(darkSurfaces = true, lightness = 45f) shouldBeLessThan 0.75f
    }

    test("with no frost under the dock a lightness of 0 keeps the base light tint") {
        DockLook(glass = true).tint(darkSurfaces = true, lightness = 0f) shouldBe light.copy(alpha = 0.20f)
    }

    test("on a bright picture the light tint keeps its alpha and colour") {
        val kept =
            listOf(
                Triple(60f, 0.20f, 0.95686275f),
                Triple(65f, 0.22f, 0.95686275f),
                Triple(70f, 0.23921569f, 0.95686275f),
                Triple(75f, 0.25882354f, 0.88235295f),
                Triple(85f, 0.25882354f, 0.5764706f),
                Triple(90f, 0.25882354f, 0.41960785f),
                Triple(100f, 0.25882354f, 0.105882354f),
            )
        for ((lightness, alpha, red) in kept) {
            val tint = DockLook(glass = true).tint(darkSurfaces = true, lightness = lightness)

            withClue("alpha at L* $lightness") { tint.alpha shouldBe (alpha plusOrMinus 0.001f) }
            withClue("red at L* $lightness") { tint.red shouldBe (red plusOrMinus 0.001f) }
        }
    }

    test("a light tint darkens only over a frost bright enough to need it") {
        DockLook(glass = true).tint(true, lightness = 60f) shouldBe light.copy(alpha = 0.20f)
        DockLook(glass = true).tint(true, lightness = 100f).red shouldBeLessThan light.red - 0.05f
    }

    test("the edge light needs the dock panel and motion") {
        TvConfig().dockLook().edgeLight.shouldBeTrue()
        TvConfig(dockBand = false).dockLook().edgeLight.shouldBeFalse()
        TvConfig(animations = false).dockLook().edgeLight.shouldBeFalse()
        TvConfig(reducedMotion = true).dockLook().edgeLight.shouldBeFalse()
        TvConfig(dockEdgeLight = false).dockLook().edgeLight.shouldBeFalse()
    }

    test("even padding follows the themes dock even padding") {
        TvConfig().dockLook().evenPadding.shouldBeFalse()
        TvConfig(dockEvenPadding = true).dockLook().evenPadding.shouldBeTrue()
    }
}
