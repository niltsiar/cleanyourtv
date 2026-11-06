package dev.niltsiar.tvhome.data.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightArtTest {
    private fun art(
        colour: Int,
        alpha: Int = 0xFF,
    ) = IntArray(VIVID_SIDE * VIVID_SIDE) { (alpha shl 24) or (colour and 0xFFFFFF) }

    private fun grey(level: Int) = (level shl 16) or (level shl 8) or level

    @Test
    fun a_white_banner_is_light() {
        assertTrue(isLightArt(art(grey(0xF5))))
    }

    @Test
    fun a_light_grey_banner_is_light() {
        assertTrue(isLightArt(art(grey(0xD6))))
    }

    @Test
    fun a_blue_banner_and_a_teal_banner_are_not() {
        assertFalse(isLightArt(art(0x0A7BFF)))
        assertFalse(isLightArt(art(0x4A6A73)))
    }

    @Test
    fun a_grey_of_relative_luminance_035_is_the_darkest_that_counts() {
        // sRGB 160 is 0.3515 relative luminance and 159 is 0.3467.
        assertTrue(isLightArt(art(grey(160))))
        assertFalse(isLightArt(art(grey(159))))
    }

    @Test
    fun a_light_banner_three_quarters_clear_over_the_dark_tile_is_not_light() {
        val sparse = IntArray(VIVID_SIDE * VIVID_SIDE) { if (it % 4 == 0) 0xFFF5F5F5.toInt() else 0 }

        assertFalse(isLightArt(sparse))
    }

    @Test
    fun a_white_logo_on_a_dark_banner_is_not_light() {
        val logo = IntArray(VIVID_SIDE * VIVID_SIDE) { if (it % 6 == 0) 0xFFFFFFFF.toInt() else 0xFF202020.toInt() }

        assertFalse(isLightArt(logo))
    }
}
