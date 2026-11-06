package dev.niltsiar.tvhome.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApartRowTest {
    private val gap = 24f
    private val reach = 45f
    private val floor = 4f

    private fun row(
        left: Int,
        right: Int,
        reach: Float = this.reach,
    ) = ApartRow(left, right, gap, reach, floor)

    /** The widths of the gaps of [row] from the first tile to the last, read from where its tiles end up. */
    private fun widths(
        row: ApartRow,
        left: Int,
        right: Int,
    ): List<Float> = (-left until right).map { gap + row.shift(it + 1) - row.shift(it) }

    @Test
    fun in_a_six_tile_row_the_gaps_beside_the_carried_tile_are_as_wide_as_the_chevrons_need_and_the_row_keeps_its_length() {
        assertEquals(listOf(45f, 45f, 10f, 10f, 10f), widths(row(1, 4), 1, 4))
        assertEquals(listOf(45f, 18.75f, 18.75f, 18.75f, 18.75f), widths(row(0, 5), 0, 5))
        assertEquals(listOf(18.75f, 18.75f, 18.75f, 18.75f, 45f), widths(row(5, 0), 5, 0))
        assertEquals(0f, row(1, 4).shift(-1), 0f)
        assertEquals(0f, row(1, 4).shift(4), 0f)
    }

    @Test
    fun a_five_tile_row_leaves_4_dp_between_the_other_tiles_and_grows_1_dp_at_each_end() {
        val row = row(2, 2)

        assertEquals(listOf(4f, 45f, 45f, 4f), widths(row, 2, 2))
        assertEquals(-1f, row.shift(-2), 0f)
        assertEquals(1f, row.shift(2), 0f)
    }

    @Test
    fun a_four_tile_row_cannot_hold_two_wide_gaps_so_it_grows_11_dp_at_each_end() {
        val row = row(1, 2)

        assertEquals(listOf(45f, 45f, 4f), widths(row, 1, 2))
        assertEquals(-11f, row.shift(-1), 0f)
        assertEquals(11f, row.shift(2), 0f)
    }

    @Test
    fun a_gap_beside_the_carried_tile_is_never_narrower_than_the_chevrons_need_and_the_row_grows_evenly_or_not_at_all() {
        for (left in 0..8) {
            for (right in 0..8) {
                if (left + right == 0) continue
                val row = row(left, right)
                val label = "$left before and $right after"
                val widths = widths(row, left, right)
                val beside = listOfNotNull(widths.getOrNull(left - 1).takeIf { left > 0 }, widths.getOrNull(left).takeIf { right > 0 })
                assertTrue(label, beside.all { it >= reach - 0.001f })
                assertTrue(label, widths.all { it >= floor - 0.001f })
                assertEquals(label, -row.shift(-left), row.shift(right), 0.001f)
                assertTrue(label, row.shift(right) >= -0.001f)
            }
        }
    }

    @Test
    fun a_gap_that_is_wide_enough_at_rest_moves_nothing() {
        val row = row(2, 3, reach = 20f)

        assertTrue((-2..3).all { row.shift(it) == 0f })
    }
}
