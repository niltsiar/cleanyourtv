package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.toArgb
import dev.niltsiar.tvhome.core.config.HeaderScrim
import dev.niltsiar.tvhome.core.config.TvConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperLayoutTest {
    @Test
    fun the_theme_background_and_sky_reach_the_layout() {
        val layout = TvConfig(backgroundColor = 0xFF102030.toInt(), skyIntensity = 40).wallpaperLayout()
        assertEquals(0xFF102030.toInt(), layout.background.toArgb())
        assertEquals(40, layout.skyIntensity)
    }

    @Test
    fun a_picture_cross_fades_unless_the_theme_turns_motion_down() {
        assertTrue(TvConfig().wallpaperLayout().crossfade)
        assertFalse(TvConfig(animations = false).wallpaperLayout().crossfade)
        assertFalse(TvConfig(reducedMotion = true).wallpaperLayout().crossfade)
    }

    @Test
    fun the_header_scrim_follows_the_wallpaper_unless_the_theme_turns_it_off() {
        assertEquals(HeaderScrim.Auto, TvConfig().wallpaperLayout().headerScrim)
        assertEquals(HeaderScrim.Off, TvConfig(headerScrim = HeaderScrim.Off).wallpaperLayout().headerScrim)
    }
}
