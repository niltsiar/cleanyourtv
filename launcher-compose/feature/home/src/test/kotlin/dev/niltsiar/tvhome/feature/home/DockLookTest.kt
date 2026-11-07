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
    fun the_light_tray_keeps_a_relative_luminance_under_056_over_any_frost() {
        for (lightness in listOf(40f, 75f, 85f, 90f, 95f, 100f)) {
            val tint = DockLook(glass = true).tint(darkSurfaces = true, lightness = lightness)

            assertTrue("L* $lightness gives ${trayLuminance(tint, lightness)}", trayLuminance(tint, lightness) <= 0.56f)
        }
    }

    @Test
    fun on_a_dark_picture_the_light_tray_lifts_the_picture_by_at_most_002() {
        for (lightness in listOf(1f, 5f, 10f, 15f, 20f, 25f, 30f)) {
            val tint = DockLook(glass = true).tint(darkSurfaces = true, lightness = lightness)
            val lift = trayLuminance(tint, lightness) - ((lightness + 16f) / 116f).pow(3)

            assertTrue("L* $lightness lifts the picture by $lift", lift <= 0.021f)
        }
    }

    @Test
    fun with_no_frost_under_the_dock_a_lightness_of_0_keeps_the_base_light_tint() {
        assertEquals(light.copy(alpha = 0.20f), DockLook(glass = true).tint(darkSurfaces = true, lightness = 0f))
    }

    @Test
    fun on_a_bright_picture_the_light_tint_keeps_its_alpha_and_colour() {
        val kept =
            listOf(
                Triple(40f, 0.20f, 0.95686275f),
                Triple(60f, 0.20f, 0.95686275f),
                Triple(70f, 0.23921569f, 0.95686275f),
                Triple(75f, 0.25882354f, 0.88235295f),
                Triple(85f, 0.25882354f, 0.5764706f),
                Triple(90f, 0.25882354f, 0.41960785f),
                Triple(100f, 0.25882354f, 0.105882354f),
            )
        for ((lightness, alpha, red) in kept) {
            val tint = DockLook(glass = true).tint(darkSurfaces = true, lightness = lightness)

            assertEquals("alpha at L* $lightness", alpha, tint.alpha, 0.001f)
            assertEquals("red at L* $lightness", red, tint.red, 0.001f)
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
