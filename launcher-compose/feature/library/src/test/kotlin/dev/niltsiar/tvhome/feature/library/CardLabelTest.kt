package dev.niltsiar.tvhome.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class CardLabelTest {
    @Test
    fun a_label_is_hidden_below_half_the_card_and_whole_from_three_quarters() {
        assertEquals(0f, labelAlpha(0.31f), 0f)
        assertEquals(0f, labelAlpha(0.5f), 0f)
        assertEquals(0.5f, labelAlpha(0.625f), 0.001f)
        assertEquals(1f, labelAlpha(0.75f), 0f)
        assertEquals(1f, labelAlpha(1f), 0f)
    }

    @Test
    fun the_share_of_a_card_in_the_row_is_the_part_between_the_viewport_edges() {
        assertEquals(0.25f, visibleShare(offset = 360, size = 160, start = 0, end = 400), 0.001f)
        assertEquals(0.5f, visibleShare(offset = -80, size = 160, start = 0, end = 400), 0.001f)
        assertEquals(1f, visibleShare(offset = 100, size = 160, start = 0, end = 400), 0.001f)
        assertEquals(0f, visibleShare(offset = 500, size = 160, start = 0, end = 400), 0f)
    }
}
