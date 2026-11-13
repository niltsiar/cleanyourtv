package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs

private class Hsl(
    val h: Float,
    val s: Float,
    val l: Float,
)

private class Hsv(
    val h: Float,
    val s: Float,
    val v: Float,
)

/** The focus ring's colour from an app's colour, worked in HSL. */
val RingColorTest by testSuite {
    val twitch = Color(0xFF9146FF)
    val spotify = Color(0xFF1DB954)
    val navy = Color(0xFF1A3C8C)
    val slateTeal = Color(0xFF4F6A72)

    fun hsl(color: Color): Hsl {
        val high = maxOf(color.red, color.green, color.blue)
        val low = minOf(color.red, color.green, color.blue)
        val l = (high + low) / 2f
        val c = high - low
        val s = if (c == 0f) 0f else c / (1f - abs(2f * l - 1f))
        val h =
            when {
                c == 0f -> 0f
                high == color.red -> (((color.green - color.blue) / c) % 6f + 6f) % 6f
                high == color.green -> (color.blue - color.red) / c + 2f
                else -> (color.red - color.green) / c + 4f
            } * 60f
        return Hsl(h, s, l)
    }

    fun hsv(color: Color): Hsv {
        val hsl = hsl(color)
        val high = maxOf(color.red, color.green, color.blue)
        val low = minOf(color.red, color.green, color.blue)
        return Hsv(hsl.h, if (high == 0f) 0f else (high - low) / high, high)
    }

    fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }

    test("a grey app colour gets a white ring") {
        // A dark grey tile and a pale grey one both vanish on cloud, so neither keeps its own tone.
        for (art in listOf(Color(0xFF3A3A3A), Color(0xFF8A8C90), Color(0xFFC4C8CC), Color(0xFF8C8C8C), Color.Black, Color.White)) {
            withClue("$art") { ringColor(art) shouldBe Color.White }
        }
    }

    test("a dark saturated app colour is lifted to a lightness of 0.65 keeping its hue and saturation") {
        val before = hsl(navy)
        val after = hsl(ringColor(navy))

        after.l shouldBe (0.65f plusOrMinus 0.005f)
        after.s shouldBe (before.s plusOrMinus 0.01f)
        after.h shouldBe (before.h plusOrMinus 1f)
    }

    test("every saturated app colour gets a ring at least 0.65 light in its own hue") {
        for (art in listOf(twitch, spotify, Color(0xFFE50914), Color(0xFF113CCF), Color(0xFFFF8800), Color(0xFF00A0A0))) {
            val before = hsl(art)
            val after = hsl(ringColor(art))

            withClue("$art lightness ${after.l}") { after.l shouldBeGreaterThanOrEqual 0.645f }
            withClue("$art hue ${before.h} became ${after.h}") { abs(before.h - after.h) shouldBeLessThan 1f }
        }
    }

    test("a saturated app colour that is already light is left as it is") {
        val pastel = Color(0xFF6FB6FF)

        ringColor(pastel) shouldBe pastel
    }

    test("a muted app colour keeps its hue and gets a saturation of 0.45 and a value of 0.85") {
        val before = hsv(slateTeal)
        val after = hsv(ringColor(slateTeal))

        after.h shouldBe (before.h plusOrMinus 1f)
        after.s shouldBe (0.45f plusOrMinus 0.01f)
        after.v shouldBe (0.85f plusOrMinus 0.01f)
    }

    test("a muted app colour that is already brighter than 0.85 keeps its value") {
        val after = hsv(ringColor(Color.hsv(30f, 0.2f, 0.95f)))

        after.s shouldBe (0.45f plusOrMinus 0.01f)
        after.v shouldBe (0.95f plusOrMinus 0.01f)
    }

    test("a saturation of 0.08 is the first to keep its colour") {
        ringColor(Color.hsv(200f, 0.07f, 0.5f)) shouldBe Color.White
        ringColor(Color.hsv(200f, 0.09f, 0.5f)) shouldNotBe Color.White
    }

    test("a ring on a tile of its own colour reads 2 to 1 or more against the tile in its own hue") {
        val prime = Color(0xFF288BFF)
        for (tile in listOf(prime, twitch, Color(0xFFE50914), Color(0xFFFF0000))) {
            val plain = ringColor(tile)
            val ring = ringColor(tile, tile)

            withClue("$tile: ${contrast(plain, tile)} before") { contrast(plain, tile) shouldBeLessThan 1.5f }
            withClue("$tile: ${contrast(ring, tile)} after") { contrast(ring, tile) shouldBeGreaterThanOrEqual 2f }
            withClue("$tile hue ${hsl(plain).h} became ${hsl(ring).h}") { abs(hsl(plain).h - hsl(ring).h) shouldBeLessThan 1f }
        }
    }

    test("a ring on a tile of another colour keeps its colour") {
        val charcoal = Color(0xFF1E1E1E)

        ringColor(Color(0xFFFF0000), charcoal) shouldBe ringColor(Color(0xFFFF0000))
        ringColor(slateTeal, slateTeal) shouldBe ringColor(slateTeal)
        ringColor(twitch, Color.Unspecified) shouldBe ringColor(twitch)
    }

    test("a brand not known yet keeps the white ring") {
        ringColor(Color.Unspecified) shouldBe Color.White
    }
}
