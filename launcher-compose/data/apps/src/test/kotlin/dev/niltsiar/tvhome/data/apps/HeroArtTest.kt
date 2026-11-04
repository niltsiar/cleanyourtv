package dev.niltsiar.tvhome.data.apps

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HeroArtTest {
    private fun rgb(
        r: Int,
        g: Int,
        b: Int,
    ) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private val source = IntArray(192 * 108)

    @Test
    fun a_192_by_108_picture_comes_out_48_by_27() {
        assertEquals(48 * 27, softenHeroArt(source).size)
    }

    @Test
    fun a_picture_of_another_size_is_refused() {
        assertThrows(IllegalArgumentException::class.java) { softenHeroArt(IntArray(48 * 27)) }
    }

    @Test
    fun halving_averages_each_2_by_2_block_per_channel() {
        val block = intArrayOf(rgb(10, 20, 30), rgb(20, 30, 40), rgb(30, 40, 50), rgb(41, 50, 60))

        // 4 pixels wide, 2 high: two blocks
        val out = halve(intArrayOf(block[0], block[1], block[0], block[1], block[2], block[3], block[2], block[3]), 4)

        assertArrayEquals(intArrayOf(rgb(25, 35, 45), rgb(25, 35, 45)), out)
    }

    @Test
    fun saturating_pushes_each_channel_from_the_luminance_by_1_7() {
        // luminance 121.26: red 255 (clamped), green and blue 85
        assertArrayEquals(intArrayOf(rgb(255, 85, 85)), saturate(intArrayOf(rgb(200, 100, 100))))
    }

    @Test
    fun grey_has_no_hue_to_saturate() {
        assertArrayEquals(intArrayOf(rgb(90, 90, 90)), saturate(intArrayOf(rgb(90, 90, 90))))
    }

    @Test
    fun a_box_blur_spreads_a_pixel_over_its_5_neighbours() {
        val row = IntArray(9) { rgb(0, 0, 0) }
        row[4] = rgb(250, 250, 250)

        val blurred = boxBlur(row, width = 9, height = 1, radius = 2, horizontal = true)

        assertEquals(listOf(0, 0, 50, 50, 50, 50, 50, 0, 0), blurred.map { it and 0xFF })
    }

    @Test
    fun a_box_blur_counts_the_edge_pixel_again_for_the_steps_beyond_it() {
        val row = IntArray(6) { rgb(0, 0, 0) }
        row[0] = rgb(100, 100, 100)

        val blurred = boxBlur(row, width = 6, height = 1, radius = 2, horizontal = true)

        assertEquals(listOf(60, 40, 20, 0, 0, 0), blurred.map { it and 0xFF })
    }

    @Test
    fun a_box_blur_runs_along_columns_too() {
        val column = IntArray(7) { rgb(0, 0, 0) }
        column[3] = rgb(100, 100, 100)

        val blurred = boxBlur(column, width = 1, height = 7, radius = 2, horizontal = false)

        assertEquals(listOf(0, 20, 20, 20, 20, 20, 0), blurred.map { it and 0xFF })
    }

    @Test
    fun a_flat_picture_stays_flat_and_saturated_through_the_whole_pipeline() {
        val flat = IntArray(192 * 108) { rgb(200, 100, 100) }

        assertEquals(setOf(rgb(255, 85, 85)), softenHeroArt(flat).toSet())
    }

    @Test
    fun the_blur_softens_an_edge_but_leaves_far_pixels_alone() {
        val half = IntArray(192 * 108) { if (it % 192 < 96) rgb(200, 100, 100) else rgb(100, 100, 200) }

        val out = softenHeroArt(half)
        val middle = out[13 * 48 + 24]

        assertEquals(rgb(255, 85, 85), out[13 * 48])
        assertEquals(rgb(95, 95, 255), out[13 * 48 + 47])
        assertNotEquals(rgb(255, 85, 85), middle)
        assertNotEquals(rgb(95, 95, 255), middle)
    }
}
