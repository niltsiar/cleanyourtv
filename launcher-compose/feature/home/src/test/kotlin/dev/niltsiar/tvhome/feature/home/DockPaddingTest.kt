package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DockPaddingTest {
    @Test
    fun without_even_padding_the_sides_keep_the_rings_room_and_the_top_and_bottom_a_gap() {
        assertEquals(DockPadding(side = 26.8.dp, vertical = 24.dp), dockPadding(26.8.dp, shrunk = false, even = false))
        assertEquals(DockPadding(side = 29.8.dp, vertical = 24.dp), dockPadding(29.8.dp, shrunk = false, even = false))
    }

    @Test
    fun a_shrunk_panel_keeps_at_least_28_dp_at_its_sides() {
        assertEquals(DockPadding(side = 28.dp, vertical = 24.dp), dockPadding(26.8.dp, shrunk = true, even = false))
        assertEquals(DockPadding(side = 29.8.dp, vertical = 24.dp), dockPadding(29.8.dp, shrunk = true, even = false))
    }

    @Test
    fun even_padding_is_the_larger_of_the_side_and_a_gap_on_all_four_sides() {
        assertEquals(DockPadding(side = 26.8.dp, vertical = 26.8.dp), dockPadding(26.8.dp, shrunk = false, even = true))
        assertEquals(DockPadding(side = 29.8.dp, vertical = 29.8.dp), dockPadding(29.8.dp, shrunk = false, even = true))
        assertEquals(DockPadding(side = 28.dp, vertical = 28.dp), dockPadding(26.8.dp, shrunk = true, even = true))
    }
}
