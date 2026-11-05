package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

class BesideAnchorTest {
    private val screenWidth = 1920
    private val gap = 32
    private val top = 512
    private val fallbackTop = 184
    private val bottom = 984

    private fun place(
        anchor: IntRect,
        width: Int = 400,
        height: Int = 300,
    ) = besideAnchor(anchor, width, height, screenWidth, gap, top, fallbackTop, bottom)

    @Test
    fun the_panel_sits_right_of_its_tile_centred_on_it() {
        assertEquals(IntOffset(612, 610), place(IntRect(left = 280, top = 680, right = 580, bottom = 840)))
    }

    @Test
    fun a_tile_with_no_room_on_its_right_gets_the_panel_on_its_left() {
        assertEquals(IntOffset(1048, 610), place(IntRect(left = 1480, top = 680, right = 1780, bottom = 840)))
    }

    @Test
    fun a_panel_centred_above_the_top_limit_comes_down_to_it() {
        assertEquals(top, place(IntRect(left = 80, top = 560, right = 380, bottom = 720)).y)
    }

    @Test
    fun a_panel_centred_past_the_bottom_limit_goes_up_to_end_on_it() {
        assertEquals(bottom - 300, place(IntRect(left = 80, top = 900, right = 380, bottom = 1060)).y)
    }

    @Test
    fun a_panel_taller_than_the_room_under_the_top_limit_may_rise_to_the_fallback() {
        assertEquals(bottom - 600, place(IntRect(left = 80, top = 700, right = 380, bottom = 860), height = 600).y)
    }

    @Test
    fun a_panel_too_tall_for_the_screen_starts_at_the_fallback() {
        assertEquals(fallbackTop, place(IntRect(left = 80, top = 700, right = 380, bottom = 860), height = 900).y)
    }
}
