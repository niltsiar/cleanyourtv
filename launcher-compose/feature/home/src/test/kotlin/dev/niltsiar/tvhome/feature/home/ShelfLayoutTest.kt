package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.ringColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfLayoutTest {
    private val art = Color(0xFFC81818)
    private val plain = Color.Unspecified

    @Test
    fun without_the_cinematic_shelf_tiles_keep_the_theme_accent() {
        val layout = ShelfLayout()

        assertEquals(plain, layout.focusAccent(art, inDock = true))
        assertEquals(plain, layout.focusAccent(art, inDock = false))
    }

    @Test
    fun the_cinematic_shelf_colours_the_dock_and_not_the_grid() {
        val layout = ShelfLayout(cinematic = true)

        assertEquals(art, layout.focusAccent(art, inDock = true))
        assertEquals(plain, layout.focusAccent(art, inDock = false))
    }

    @Test
    fun cinematic_white_focus_keeps_the_dock_white() {
        val layout = ShelfLayout(cinematic = true, whiteFocus = true)

        assertEquals(Color.White, layout.focusAccent(art, inDock = true))
        assertEquals(plain, layout.focusAccent(art, inDock = false))
    }

    @Test
    fun app_glow_colours_every_tile_even_with_white_focus_or_without_the_shelf() {
        assertEquals(art, ShelfLayout(appGlow = true).focusAccent(art, inDock = false))
        assertEquals(art, ShelfLayout(appGlow = true).focusAccent(art, inDock = true))
        assertEquals(art, ShelfLayout(cinematic = true, whiteFocus = true, appGlow = true).focusAccent(art, inDock = true))
    }

    @Test
    fun the_ring_takes_the_app_colour_in_the_dock_and_in_the_grid_only_with_app_glow() {
        val ring = ringColor(art)

        assertEquals(ring, ShelfLayout().ringColour(art, inDock = true))
        assertEquals(Color.White, ShelfLayout().ringColour(art, inDock = false))
        assertEquals(ring, ShelfLayout(appGlow = true).ringColour(art, inDock = false))
    }

    @Test
    fun cinematic_white_focus_keeps_every_ring_white() {
        val layout = ShelfLayout(whiteFocus = true, appGlow = true)

        assertEquals(Color.White, layout.ringColour(art, inDock = true))
        assertEquals(Color.White, layout.ringColour(art, inDock = false))
    }

    @Test
    fun the_theme_percentages_become_fractions_and_motion_follows_the_switches() {
        val layout = TvConfig(cinematicHome = true, cinematicArt = 20, cinematicScrim = 40, cinematicDots = false).shelfLayout()

        assertTrue(layout.cinematic)
        assertEquals(0.2f, layout.artAlpha, 0f)
        assertEquals(0.4f, layout.scrimAlpha, 0f)
        assertFalse(layout.dots)
        assertTrue(layout.animated)
        assertFalse(TvConfig(animations = false).shelfLayout().animated)
        assertFalse(TvConfig(reducedMotion = true).shelfLayout().animated)
    }

    @Test
    fun the_glow_switches_reach_the_layout() {
        val layout = TvConfig(ambientGlow = false, glowSnap = true, appGlow = true, cinematicWhiteFocus = true).shelfLayout()

        assertFalse(layout.ambientGlow)
        assertTrue(layout.glowSnap && layout.appGlow && layout.whiteFocus)
    }
}
