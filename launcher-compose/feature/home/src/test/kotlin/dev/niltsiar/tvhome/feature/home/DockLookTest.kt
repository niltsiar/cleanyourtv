package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.config.TvConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the dock panel is tinted with, from the theme and the wallpaper under it. */
class DockLookTest {
    private val dark = Color(0xFF0B0D12)
    private val light = Color(0xFFF4F6FA)

    @Test
    fun the_dark_tint_is_032_and_the_light_one_036_on_a_dim_wallpaper() {
        assertEquals(dark.copy(alpha = 0.32f), DockLook().tint(darkSurfaces = true, lightness = 40f))
        assertEquals(light.copy(alpha = 0.36f), DockLook().tint(darkSurfaces = false, lightness = 40f))
    }

    @Test
    fun above_L_60_the_dark_tint_climbs_to_042_at_L_75_and_the_light_one_to_046() {
        for ((lightness, extra) in listOf(60f to 0f, 67.5f to 0.05f, 75f to 0.10f, 95f to 0.10f)) {
            assertEquals("dark at $lightness", 0.32f + extra, DockLook().tint(true, lightness).alpha, 0.005f)
            assertEquals("light at $lightness", 0.36f + extra, DockLook().tint(false, lightness).alpha, 0.005f)
        }
    }

    @Test
    fun dock_glass_takes_the_light_tint_in_night_mode() {
        assertEquals(light.copy(alpha = 0.36f), DockLook(glass = true).tint(darkSurfaces = true, lightness = 40f))
    }

    @Test
    fun dock_opacity_sets_the_tint_to_085_times_the_percent_and_the_wallpaper_no_longer_matters() {
        assertEquals(dark.copy(alpha = 0.85f * 0.70f), DockLook(opacity = 70).tint(true, lightness = 90f))
        assertEquals(light.copy(alpha = 0.85f * 0.30f), DockLook(opacity = 30, glass = true).tint(true, lightness = 10f))
    }

    @Test
    fun the_edge_light_needs_the_dock_panel_and_motion() {
        assertTrue(TvConfig().dockLook().edgeLight)
        assertFalse(TvConfig(dockBand = false).dockLook().edgeLight)
        assertFalse(TvConfig(animations = false).dockLook().edgeLight)
        assertFalse(TvConfig(reducedMotion = true).dockLook().edgeLight)
        assertFalse(TvConfig(dockEdgeLight = false).dockLook().edgeLight)
    }
}
