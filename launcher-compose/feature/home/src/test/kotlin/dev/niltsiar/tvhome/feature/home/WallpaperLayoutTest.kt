package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.toArgb
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.HeaderScrim
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

val WallpaperLayoutTest by testSuite {
    test("the theme background and sky reach the layout") {
        val layout = TvConfig(backgroundColor = 0xFF102030.toInt(), skyIntensity = 40).wallpaperLayout()
        layout.background.toArgb() shouldBe 0xFF102030.toInt()
        layout.skyIntensity shouldBe 40
    }

    test("a picture cross fades unless the theme turns motion down") {
        TvConfig().wallpaperLayout().crossfade.shouldBeTrue()
        TvConfig(animations = false).wallpaperLayout().crossfade.shouldBeFalse()
        TvConfig(reducedMotion = true).wallpaperLayout().crossfade.shouldBeFalse()
    }

    test("the header scrim follows the wallpaper unless the theme turns it off") {
        TvConfig().wallpaperLayout().headerScrim shouldBe HeaderScrim.Auto
        TvConfig(headerScrim = HeaderScrim.Off).wallpaperLayout().headerScrim shouldBe HeaderScrim.Off
    }
}
