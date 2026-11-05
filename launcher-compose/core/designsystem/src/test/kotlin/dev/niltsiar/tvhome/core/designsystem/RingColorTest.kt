package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The focus ring's colour from an app's brand colour, worked in OKLCH. */
class RingColorTest {
    private val twitch = Color(0xFF9146FF)
    private val spotify = Color(0xFF1DB954)
    private val youtube = Color(0xFFFF0000)

    private fun hueGap(
        a: Float,
        b: Float,
    ): Float = abs(((a - b + 540f) % 360f) - 180f)

    private fun assertLiftedKeepingTheHue(brand: Color) {
        val before = oklch(brand)
        val after = oklch(ringColor(brand))

        assertTrue("L ${after.l}", after.l in 0.795f..0.925f)
        assertTrue("C ${after.c}", after.c <= 0.145f)
        assertTrue("hue ${before.h} became ${after.h}", hueGap(before.h, after.h) < 3f)
    }

    @Test
    fun vivid_brand_colours_are_lifted_to_a_light_tone_with_at_most_0_14_of_chroma_and_keep_their_hue() {
        assertLiftedKeepingTheHue(twitch)
        assertLiftedKeepingTheHue(spotify)
        assertLiftedKeepingTheHue(youtube)
        // Their light, chromatic versions fall outside what a screen shows: the chroma gives way, never the hue.
        for (brand in listOf(Color(0xFF00FFFF), Color(0xFFFFFF00), Color(0xFF0000FF), Color(0xFF00FF00))) assertLiftedKeepingTheHue(brand)
    }

    @Test
    fun a_brand_colour_already_inside_the_band_is_left_as_it_is() {
        val pastel = Color(0xFFA6D8FF)
        val before = oklch(pastel)
        assertTrue(before.l in 0.8f..0.92f && before.c in 0.04f..0.14f)

        val after = oklch(ringColor(pastel))

        assertEquals(before.l, after.l, 0.01f)
        assertEquals(before.c, after.c, 0.01f)
    }

    @Test
    fun a_dark_brand_colour_rises_to_the_floor_of_the_band() {
        val navy = Color(0xFF1A3C8C)

        assertEquals(0.80f, oklch(ringColor(navy)).l, 0.01f)
    }

    @Test
    fun white_black_and_grey_brands_use_the_neutral_ring() {
        val neutral = Color(0xFFF2F4F8)

        for (brand in listOf(Color.White, Color.Black, Color(0xFF808080), Color(0xFFF4F4EC))) {
            assertEquals("$brand", neutral, ringColor(brand))
        }
    }

    @Test
    fun a_brand_not_known_yet_keeps_the_white_ring() {
        assertEquals(Color.White, ringColor(Color.Unspecified))
    }
}
