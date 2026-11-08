package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The focus ring's colour from an app's colour, worked in HSL. */
class RingColorTest {
    private val twitch = Color(0xFF9146FF)
    private val spotify = Color(0xFF1DB954)
    private val navy = Color(0xFF1A3C8C)
    private val slateTeal = Color(0xFF4F6A72)

    private class Hsl(
        val h: Float,
        val s: Float,
        val l: Float,
    )

    private fun hsl(color: Color): Hsl {
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

    private class Hsv(
        val h: Float,
        val s: Float,
        val v: Float,
    )

    private fun hsv(color: Color): Hsv {
        val hsl = hsl(color)
        val high = maxOf(color.red, color.green, color.blue)
        val low = minOf(color.red, color.green, color.blue)
        return Hsv(hsl.h, if (high == 0f) 0f else (high - low) / high, high)
    }

    @Test
    fun a_grey_app_colour_gets_a_white_ring() {
        // A dark grey tile and a pale grey one both vanish on cloud, so neither keeps its own tone.
        for (art in listOf(Color(0xFF3A3A3A), Color(0xFF8A8C90), Color(0xFFC4C8CC), Color(0xFF8C8C8C), Color.Black, Color.White)) {
            assertEquals("$art", Color.White, ringColor(art))
        }
    }

    @Test
    fun a_dark_saturated_app_colour_is_lifted_to_a_lightness_of_065_keeping_its_hue_and_saturation() {
        val before = hsl(navy)
        val after = hsl(ringColor(navy))

        assertEquals(0.65f, after.l, 0.005f)
        assertEquals(before.s, after.s, 0.01f)
        assertEquals(before.h, after.h, 1f)
    }

    @Test
    fun every_saturated_app_colour_gets_a_ring_at_least_065_light_in_its_own_hue() {
        for (art in listOf(twitch, spotify, Color(0xFFE50914), Color(0xFF113CCF), Color(0xFFFF8800), Color(0xFF00A0A0))) {
            val before = hsl(art)
            val after = hsl(ringColor(art))

            assertTrue("$art lightness ${after.l}", after.l >= 0.645f)
            assertTrue("$art hue ${before.h} became ${after.h}", abs(before.h - after.h) < 1f)
        }
    }

    @Test
    fun a_saturated_app_colour_that_is_already_light_is_left_as_it_is() {
        val pastel = Color(0xFF6FB6FF)

        assertEquals(pastel, ringColor(pastel))
    }

    @Test
    fun a_muted_app_colour_keeps_its_hue_and_gets_a_saturation_of_045_and_a_value_of_085() {
        val before = hsv(slateTeal)
        val after = hsv(ringColor(slateTeal))

        assertEquals(before.h, after.h, 1f)
        assertEquals(0.45f, after.s, 0.01f)
        assertEquals(0.85f, after.v, 0.01f)
    }

    @Test
    fun a_muted_app_colour_that_is_already_brighter_than_085_keeps_its_value() {
        val after = hsv(ringColor(Color.hsv(30f, 0.2f, 0.95f)))

        assertEquals(0.45f, after.s, 0.01f)
        assertEquals(0.95f, after.v, 0.01f)
    }

    @Test
    fun a_saturation_of_008_is_the_first_to_keep_its_colour() {
        assertEquals(Color.White, ringColor(Color.hsv(200f, 0.07f, 0.5f)))
        assertNotEquals(Color.White, ringColor(Color.hsv(200f, 0.09f, 0.5f)))
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun a_ring_on_a_tile_of_its_own_colour_reads_2_to_1_or_more_against_the_tile_in_its_own_hue() {
        val prime = Color(0xFF288BFF)
        for (tile in listOf(prime, twitch, Color(0xFFE50914), Color(0xFFFF0000))) {
            val plain = ringColor(tile)
            val ring = ringColor(tile, tile)

            assertTrue("$tile: ${contrast(plain, tile)} before", contrast(plain, tile) < 1.5f)
            assertTrue("$tile: ${contrast(ring, tile)} after", contrast(ring, tile) >= 2f)
            assertTrue("$tile hue ${hsl(plain).h} became ${hsl(ring).h}", abs(hsl(plain).h - hsl(ring).h) < 1f)
        }
    }

    @Test
    fun a_ring_on_a_tile_of_another_colour_keeps_its_colour() {
        val charcoal = Color(0xFF1E1E1E)

        assertEquals(ringColor(Color(0xFFFF0000)), ringColor(Color(0xFFFF0000), charcoal))
        assertEquals(ringColor(slateTeal), ringColor(slateTeal, slateTeal))
        assertEquals(ringColor(twitch), ringColor(twitch, Color.Unspecified))
    }

    @Test
    fun a_brand_not_known_yet_keeps_the_white_ring() {
        assertEquals(Color.White, ringColor(Color.Unspecified))
    }
}
