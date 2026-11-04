package dev.niltsiar.tvhome.data.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class VividColorTest {
    private fun art(
        fill: Int,
        vararg at: Pair<Int, Int>,
    ) = IntArray(VIVID_SIDE * VIVID_SIDE) { fill }.also { px -> at.forEach { (i, color) -> px[i] = color } }

    @Test
    fun the_most_saturated_pixel_wins_a_little_more_saturated() {
        val pixels = art(0xFF808080.toInt(), 5 to 0xFFC82828.toInt())

        // saturation 0.8 becomes 0.88; the value stays 200/255
        assertEquals(0xFFC81818.toInt(), vividColor(pixels))
    }

    @Test
    fun a_dark_saturated_colour_is_lifted_to_half_brightness() {
        assertEquals(0xFF000080.toInt(), vividColor(art(0xFF000032.toInt())))
    }

    @Test
    fun art_with_no_vivid_pixel_gives_its_average_lifted_to_55_percent_brightness() {
        assertEquals(0xFF8C8C8C.toInt(), vividColor(art(0xFF404040.toInt())))
        assertEquals(0xFFFFFFFF.toInt(), vividColor(art(0xFFFFFFFF.toInt())))
    }

    @Test
    fun hue_saturation_and_value_round_trip_through_rgb() {
        val hsv = FloatArray(3)
        rgbToHsv(0xFF00FF80.toInt(), hsv)

        assertEquals(150f, hsv[0], 0.2f)
        assertEquals(1f, hsv[1], 0f)
        assertEquals(1f, hsv[2], 0f)
        assertEquals(0xFF00FF80.toInt(), hsvToRgb(hsv))
    }

    @Test
    fun a_hue_below_zero_wraps_to_magenta_side_of_red() {
        val hsv = FloatArray(3)
        rgbToHsv(0xFFFF0040.toInt(), hsv)

        assertEquals(345f, hsv[0], 0.2f)
    }
}
