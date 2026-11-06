package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.config.TvConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** What the dock panel is tinted with, from the theme and the wallpaper under it. */
class DockLookTest {
    private val dark = Color(0xFF0B0D12)
    private val light = Color(0xFFF4F6FA)

    @Test
    fun the_dark_tint_is_016_and_the_light_one_020_on_a_dim_wallpaper() {
        assertEquals(dark.copy(alpha = 0.16f), DockLook().tint(darkSurfaces = true, lightness = 40f))
        assertEquals(light.copy(alpha = 0.20f), DockLook().tint(darkSurfaces = false, lightness = 40f))
    }

    @Test
    fun above_L_60_the_dark_tint_climbs_to_022_at_L_75() {
        for ((lightness, extra) in listOf(60f to 0f, 67.5f to 0.03f, 75f to 0.06f, 95f to 0.06f)) {
            assertEquals("dark at $lightness", 0.16f + extra, DockLook().tint(true, lightness).alpha, 0.005f)
        }
    }

    @Test
    fun dock_glass_takes_the_light_tint_in_night_mode() {
        assertEquals(light.copy(alpha = 0.20f), DockLook(glass = true).tint(darkSurfaces = true, lightness = 40f))
    }

    @Test
    fun dock_opacity_sets_the_tint_to_085_times_the_percent_and_the_wallpaper_no_longer_matters() {
        assertEquals(dark.copy(alpha = 0.85f * 0.70f), DockLook(opacity = 70).tint(true, lightness = 90f))
        assertEquals(light.copy(alpha = 0.85f * 0.30f), DockLook(opacity = 30, glass = true).tint(true, lightness = 90f))
    }

    private fun encode(luminance: Float) = 1.055f * luminance.pow(1f / 2.4f) - 0.055f

    private fun decode(value: Float) = ((value + 0.055f) / 1.055f).pow(2.4f)

    /** The relative luminance of a grey frost of CIE L* [lightness] under [tint], mixed on the encoded values. */
    private fun trayLuminance(
        tint: Color,
        lightness: Float,
    ): Float {
        val frost = encode(((lightness + 16f) / 116f).pow(3))

        fun mixed(channel: Float) = decode(frost * (1f - tint.alpha) + channel * tint.alpha)
        return 0.2126f * mixed(tint.red) + 0.7152f * mixed(tint.green) + 0.0722f * mixed(tint.blue)
    }

    @Test
    fun the_light_tray_keeps_a_relative_luminance_of_at_most_075_over_any_frost() {
        for (lightness in listOf(40f, 75f, 85f, 90f, 95f, 100f)) {
            val tint = DockLook(glass = true).tint(darkSurfaces = true, lightness = lightness)

            assertTrue("L* $lightness gives ${trayLuminance(tint, lightness)}", trayLuminance(tint, lightness) <= 0.751f)
        }
    }

    @Test
    fun a_light_tint_darkens_only_over_a_frost_bright_enough_to_need_it() {
        assertEquals(light.copy(alpha = 0.20f), DockLook(glass = true).tint(true, lightness = 60f))
        assertTrue(DockLook(glass = true).tint(true, lightness = 100f).red < light.red - 0.05f)
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
