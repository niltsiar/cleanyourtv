package dev.niltsiar.tvhome

import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.config.parseTvConfig
import dev.niltsiar.tvhome.core.designsystem.FocusStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigFocusStyleTest {
    private fun styleOf(json: String): FocusStyle = parseTvConfig(json).config.focusStyle()

    @Test
    fun a_theme_without_glass_style_or_with_glass_gets_the_full_glass_look() {
        assertEquals(FocusStyle.Glass, TvConfig().focusStyle())
        assertEquals(FocusStyle.Glass, styleOf("""{"glass_style": "glass"}"""))
    }

    @Test
    fun rim_and_halo_reach_the_focus_look() {
        assertEquals(FocusStyle.Rim, styleOf("""{"glass_style": "rim"}"""))
        assertEquals(FocusStyle.Halo, styleOf("""{"glass_style": "halo"}"""))
    }
}
