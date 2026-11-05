package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/** The frost's blur and saturation on images whose result can be worked out by hand. */
class ChromeFrostTest {
    private fun rgb(
        r: Int,
        g: Int,
        b: Int,
    ): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun channels(pixel: Int): List<Int> = listOf(pixel shr 16 and 0xFF, pixel shr 8 and 0xFF, pixel and 0xFF)

    /**
     * What three box blurs of radius 2 make of a step from [before] to [after] between [EDGE] - 1 and [EDGE], at [at]:
     * the 13-tap kernel 1 3 6 10 15 18 19 18 15 10 6 3 1 over 125 weighs how much of [after] reaches it.
     */
    private fun predicted(
        at: Int,
        before: Int,
        after: Int,
    ): List<Int> {
        val weight = KERNEL.indices.filter { at + it - KERNEL.size / 2 >= EDGE }.sumOf { KERNEL[it] } / KERNEL.sum().toFloat()
        return channels(before).zip(channels(after)) { from, to -> (from + (to - from) * weight).roundToInt() }
    }

    private fun assertSpread(
        actual: (Int) -> Int,
        before: Int,
        after: Int,
    ) {
        for (at in 0 until LENGTH) {
            val expected = predicted(at, before, after)
            val got = channels(actual(at))
            if (at < EDGE - KERNEL.size / 2 || at >= EDGE + KERNEL.size / 2) {
                assertEquals("at $at", expected, got)
            } else {
                // Each pass rounds down.
                assertTrue("at $at: expected $expected, got $got", expected.zip(got).all { (e, g) -> abs(e - g) <= 3 })
            }
        }
    }

    @Test
    fun a_colour_edge_spreads_across_as_three_radius_2_box_blurs_predict() {
        val width = LENGTH
        val height = 5
        val pixels = IntArray(width * height) { if (it % width < EDGE) BEFORE else AFTER }

        blur(pixels, width, height)

        repeat(height) { y -> assertSpread({ x -> pixels[y * width + x] }, BEFORE, AFTER) }
    }

    @Test
    fun a_colour_edge_spreads_down_as_three_radius_2_box_blurs_predict() {
        val width = 5
        val height = LENGTH
        val pixels = IntArray(width * height) { if (it / width < EDGE) BEFORE else AFTER }

        blur(pixels, width, height)

        repeat(width) { x -> assertSpread({ y -> pixels[y * width + x] }, BEFORE, AFTER) }
    }

    @Test
    fun saturation_moves_a_colour_45_percent_further_from_its_grey() {
        // Its grey is 0.2126 * 200 + 0.7152 * 100 + 0.0722 * 50 = 117.65.
        val pixels = intArrayOf(rgb(200, 100, 50))

        saturate(pixels)

        assertEquals(listOf(237, 92, 20), channels(pixels[0]))
    }

    @Test
    fun saturation_leaves_a_grey_alone_and_clamps_a_colour_at_the_ends_of_a_channel() {
        val pixels = intArrayOf(rgb(128, 128, 128), rgb(250, 10, 10))

        saturate(pixels)

        assertEquals(listOf(128, 128, 128), channels(pixels[0]))
        assertEquals(listOf(255, 0, 0), channels(pixels[1]))
    }

    @Test
    fun brightening_lifts_each_channel_8_percent_and_clamps_at_the_top() {
        val pixels = intArrayOf(rgb(100, 50, 0), rgb(250, 128, 240))

        brighten(pixels)

        assertEquals(listOf(108, 54, 0), channels(pixels[0]))
        assertEquals(listOf(255, 138, 255), channels(pixels[1]))
    }

    @Test
    fun the_lightness_under_the_dock_is_the_mean_L_star_of_the_pixels_inside_its_band() {
        // A picture 10 wide: white on its left half, black on its right; L* of white is 100 and of black 0.
        val pixels = IntArray(10 * 4) { if (it % 10 < 5) rgb(255, 255, 255) else rgb(0, 0, 0) }

        assertEquals(100f, meanLightness(pixels, 10, 4, Rect(0.1f, 0.2f, 0.4f, 0.9f)), 0.01f)
        assertEquals(0f, meanLightness(pixels, 10, 4, Rect(0.6f, 0.2f, 0.9f, 0.9f)), 0.01f)
        assertEquals(50f, meanLightness(pixels, 10, 4, Rect(0f, 0f, 1f, 1f)), 0.01f)
    }

    @Test
    fun a_mid_grey_is_L_star_53() {
        assertEquals(53.4f, meanLightness(intArrayOf(rgb(128, 128, 128)), 1, 1, Rect(0f, 0f, 1f, 1f)), 0.2f)
    }

    private companion object {
        val KERNEL = intArrayOf(1, 3, 6, 10, 15, 18, 19, 18, 15, 10, 6, 3, 1)
        const val LENGTH = 24
        const val EDGE = 12
        val BEFORE = (0xFF shl 24) or (0 shl 16) or (200 shl 8) or 40
        val AFTER = (0xFF shl 24) or (240 shl 16) or (20 shl 8) or 100
    }
}
