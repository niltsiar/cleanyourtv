package dev.niltsiar.tvhome.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HeroSourceTest {
    @Test
    fun with_dock_title_on_the_focused_tile_is_the_line_when_nothing_else_claims_the_band() {
        assertEquals(HeroSource.Tile, heroSource(dockTitle = true, hint = null, pillFocused = false, control = null))
    }

    @Test
    fun with_dock_title_off_the_focused_tile_gets_no_line() {
        assertEquals(HeroSource.Quiet, heroSource(dockTitle = false, hint = null, pillFocused = false, control = null))
    }

    @Test
    fun a_reorder_hint_a_library_pill_and_a_bar_control_keep_their_line_with_dock_title_off() {
        assertEquals(
            HeroSource.Mode(ReorderHint.Reorder),
            heroSource(dockTitle = false, hint = ReorderHint.Reorder, pillFocused = false, control = null),
        )
        assertEquals(HeroSource.Library, heroSource(dockTitle = false, hint = null, pillFocused = true, control = null))
        assertEquals(
            HeroSource.Bar(BarControl.Network),
            heroSource(dockTitle = false, hint = null, pillFocused = false, control = BarControl.Network),
        )
    }

    @Test
    fun a_mode_beats_the_pill_and_the_pill_beats_a_bar_control() {
        assertEquals(
            HeroSource.Mode(ReorderHint.Reorder),
            heroSource(dockTitle = true, hint = ReorderHint.Reorder, pillFocused = true, control = BarControl.Network),
        )
        assertEquals(HeroSource.Library, heroSource(dockTitle = true, hint = null, pillFocused = true, control = BarControl.Network))
    }
}
