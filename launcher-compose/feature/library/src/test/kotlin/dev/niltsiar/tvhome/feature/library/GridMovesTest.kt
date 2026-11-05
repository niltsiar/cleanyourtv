package dev.niltsiar.tvhome.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridMovesTest {
    // 23 cards in 6 columns: three full rows and a last row of five, cards 18 to 22.
    private fun move(
        index: Int,
        move: GridMove,
    ) = gridNeighbour(index, move, count = 23, columns = 6)

    @Test
    fun a_move_inside_the_grid_goes_to_the_next_card_in_that_direction() {
        assertEquals(8, move(7, GridMove.Right))
        assertEquals(6, move(7, GridMove.Left))
        assertEquals(13, move(7, GridMove.Down))
        assertEquals(1, move(7, GridMove.Up))
    }

    @Test
    fun the_first_row_the_last_row_and_the_sides_are_edges() {
        assertNull(move(3, GridMove.Up))
        assertNull(move(6, GridMove.Left))
        assertNull(move(11, GridMove.Right))
        assertNull(move(20, GridMove.Down))
    }

    @Test
    fun the_last_card_ends_its_short_row() {
        assertNull(move(22, GridMove.Right))
        assertEquals(21, move(22, GridMove.Left))
    }

    @Test
    fun down_into_the_short_last_row_keeps_the_column_it_has_and_otherwise_goes_to_the_last_card() {
        assertEquals(21, move(15, GridMove.Down))
        assertEquals(22, move(17, GridMove.Down))
    }
}
