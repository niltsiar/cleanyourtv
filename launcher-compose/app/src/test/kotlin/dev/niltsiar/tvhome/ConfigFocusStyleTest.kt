package dev.niltsiar.tvhome

import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.config.parseTvConfig
import dev.niltsiar.tvhome.core.designsystem.FocusStyle
import io.kotest.matchers.shouldBe

val ConfigFocusStyleTest by testSuite {
    fun styleOf(json: String): FocusStyle = parseTvConfig(json).config.focusStyle()

    test("a theme without glass_style or with glass gets the full glass look") {
        TvConfig().focusStyle() shouldBe FocusStyle.Glass
        styleOf("""{"glass_style": "glass"}""") shouldBe FocusStyle.Glass
    }

    test("rim and halo reach the focus look") {
        styleOf("""{"glass_style": "rim"}""") shouldBe FocusStyle.Rim
        styleOf("""{"glass_style": "halo"}""") shouldBe FocusStyle.Halo
    }
}
