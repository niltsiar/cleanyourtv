package dev.niltsiar.tvhome.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class DropdownLeftTest {
    private val screen = 1920
    private val margin = 80

    private fun left(
        anchorLeft: Int,
        anchorRight: Int = anchorLeft + 250,
        panel: Int = 550,
        alignEnd: Boolean = false,
    ) = dropdownLeft(anchorLeft, anchorRight, panel, screen, margin, alignEnd)

    @Test
    fun the_panel_starts_on_the_anchors_left_edge_when_it_fits_before_the_right_margin() {
        assertEquals(700, left(anchorLeft = 700))
        assertEquals(1290, left(anchorLeft = 1290))
    }

    @Test
    fun a_panel_that_would_pass_the_right_margin_ends_on_it_with_its_left_edge_as_near_the_anchor_as_that_allows() {
        assertEquals(screen - margin - 550, left(anchorLeft = 1500))
    }

    @Test
    fun a_panel_with_the_end_alignment_ends_on_the_anchors_right_edge() {
        assertEquals(950, left(anchorLeft = 1000, anchorRight = 1500, alignEnd = true))
    }

    @Test
    fun a_panel_never_starts_before_the_left_margin() {
        assertEquals(margin, left(anchorLeft = 20))
        assertEquals(margin, left(anchorLeft = 0, anchorRight = 300, alignEnd = true))
    }
}
