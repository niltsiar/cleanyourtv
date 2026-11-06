package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.graphics.Color
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

    @Test
    fun a_grey_app_colour_gets_a_white_ring() {
        // A dark grey tile and a pale blue-grey one both vanish on cloud, so neither keeps its own tone.
        for (art in listOf(Color(0xFF3A3A3A), Color(0xFF8A8F99), Color(0xFFB8C4D4), Color.Black, Color.White)) {
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
        val pastel = Color(0xFFA6D8FF)

        assertEquals(pastel, ringColor(pastel))
    }

    /** A colour of HSL lightness 0.4 and the given [saturation], red-leaning. */
    private fun hslColor(saturation: Float): Color {
        val l = 0.4f
        val c = (1f - abs(2f * l - 1f)) * saturation
        return Color(l + c / 2f, l, l - c / 2f)
    }

    @Test
    fun a_saturation_of_025_is_the_first_to_keep_its_colour() {
        assertEquals(Color.White, ringColor(hslColor(0.24f)))
        assertNotEquals(Color.White, ringColor(hslColor(0.26f)))
    }

    @Test
    fun a_brand_not_known_yet_keeps_the_white_ring() {
        assertEquals(Color.White, ringColor(Color.Unspecified))
    }
}
