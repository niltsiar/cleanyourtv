package dev.niltsiar.tvhome.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class DropdownTopTest {
    private val screen = 1080
    private val gap = 20
    private val topLimit = 160

    private fun top(
        anchorTop: Int,
        anchorBottom: Int,
        panel: Int = 400,
    ) = dropdownTop(anchorTop, anchorBottom, panel, screen, gap, topLimit)

    @Test
    fun the_panel_hangs_below_an_anchor_with_room_under_it() {
        assertEquals(320, top(anchorTop = 200, anchorBottom = 300))
    }

    @Test
    fun the_panel_goes_above_an_anchor_with_no_room_under_it_but_room_over_it() {
        assertEquals(520, top(anchorTop = 940, anchorBottom = 1040))
    }

    @Test
    fun a_panel_with_room_on_neither_side_stays_under_the_top_limit_and_overlaps_the_anchor() {
        assertEquals(topLimit, top(anchorTop = 575, anchorBottom = 710))
    }

    @Test
    fun a_panel_taller_than_the_room_under_the_top_limit_starts_at_the_limit() {
        assertEquals(topLimit, top(anchorTop = 500, anchorBottom = 600, panel = 1000))
    }
}
