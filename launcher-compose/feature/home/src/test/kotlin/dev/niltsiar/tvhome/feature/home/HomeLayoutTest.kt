package dev.niltsiar.tvhome.feature.home

import dev.niltsiar.tvhome.core.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    private val gridWidth = 880f
    private val height = 324f
    private val gap = 14f

    /** The height of the dock panel and [rows] rows when the grid uses [share] of its width. */
    private fun needed(
        share: Float,
        rows: Int,
        columns: Int = 6,
    ): Float {
        val inner = gridWidth * share
        val dockTile = (inner - (columns + 1) * gap) / columns * 9 / 16
        val gridTile = (inner - (columns - 1) * gap) / columns * 9 / 16
        return dockTile + 2 * gap + rows * (gridTile + gap)
    }

    @Test
    fun no_rows_below_the_dock_keep_the_full_width() {
        assertEquals(1f, tileWidthShare(gridWidth, height, columns = 6, rowsBelowDock = 0, gap = gap), 0f)
    }

    @Test
    fun rows_that_already_fit_keep_the_full_width() {
        assertEquals(1f, tileWidthShare(gridWidth, height, columns = 6, rowsBelowDock = 1, gap = gap), 0f)
    }

    @Test
    fun rows_that_do_not_fit_shrink_the_tiles_until_they_do() {
        val share = tileWidthShare(gridWidth, height, columns = 6, rowsBelowDock = 3, gap = gap)
        assertTrue("share $share", share < 1f && share > 0.85f)
        assertEquals(height, needed(share, rows = 3), 0.5f)
    }

    @Test
    fun the_tiles_never_shrink_below_the_floor() {
        assertEquals(0.85f, tileWidthShare(gridWidth, 200f, columns = 6, rowsBelowDock = 3, gap = gap), 0f)
    }

    @Test
    fun an_unknown_window_size_keeps_the_full_width() {
        assertEquals(1f, tileWidthShare(gridWidth, 0f, columns = 6, rowsBelowDock = 3, gap = gap), 0f)
    }

    private fun tiles(n: Int) = (1..n).map { AppEntry(key = "t$it", packageName = "t$it", label = "T$it") }

    @Test
    fun down_from_a_tile_with_no_cell_below_goes_to_the_last_tile_so_the_pill_does_not_capture_it() {
        // Seven grid tiles in 3 columns: rows 0 1 2 / 3 4 5 / 6.
        assertEquals(6, gridDownOverride(row = 1, column = 1, gridCount = 7, columns = 3))
        assertEquals(6, gridDownOverride(row = 1, column = 2, gridCount = 7, columns = 3))
        assertEquals(null, gridDownOverride(row = 1, column = 0, gridCount = 7, columns = 3))
        assertEquals(null, gridDownOverride(row = 0, column = 1, gridCount = 7, columns = 3))
        assertEquals(null, gridDownOverride(row = 2, column = 0, gridCount = 7, columns = 3))
    }

    @Test
    fun down_from_a_dock_tile_over_a_short_first_row_goes_to_the_last_grid_tile() {
        assertEquals(1, gridDownOverride(row = -1, column = 2, gridCount = 2, columns = 4))
        assertEquals(null, gridDownOverride(row = -1, column = 1, gridCount = 2, columns = 4))
        assertEquals(null, gridDownOverride(row = -1, column = 0, gridCount = 0, columns = 4))
    }

    @Test
    fun up_from_the_pill_returns_to_the_last_row_under_the_column_that_had_focus() {
        val dock = tiles(3)
        val grid = (4..12).map { AppEntry(key = "t$it", packageName = "t$it", label = "T$it") }

        assertEquals("t11", pillUpKey(dock, grid, columns = 3, singleRow = false, focusedKey = "t5"))
        assertEquals("t12", pillUpKey(dock, grid, columns = 3, singleRow = false, focusedKey = "t9"))
        assertEquals("t12", pillUpKey(dock, grid, columns = 3, singleRow = false, focusedKey = "t3"))
        assertEquals("t10", pillUpKey(dock, grid, columns = 3, singleRow = false, focusedKey = null))
        assertEquals("t10", pillUpKey(dock, grid.take(7), columns = 3, singleRow = false, focusedKey = "t5"))
    }

    @Test
    fun up_from_the_pill_with_no_grid_goes_to_the_dock_and_in_a_single_row_to_the_tile_that_had_focus() {
        val dock = tiles(4)

        assertEquals("t2", pillUpKey(dock, emptyList(), columns = 4, singleRow = false, focusedKey = "t2"))
        assertEquals("t3", pillUpKey(dock, emptyList(), columns = 2, singleRow = true, focusedKey = "t3"))
        assertEquals("t4", pillUpKey(dock, emptyList(), columns = 2, singleRow = true, focusedKey = null))
        assertEquals(null, pillUpKey(emptyList(), emptyList(), columns = 4, singleRow = false, focusedKey = null))
    }
}
