package dev.niltsiar.tvhome.feature.home

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
}
