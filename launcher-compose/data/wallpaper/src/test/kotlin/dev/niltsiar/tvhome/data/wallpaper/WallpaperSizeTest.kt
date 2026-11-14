package dev.niltsiar.tvhome.data.wallpaper

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val WallpaperSizeTest by testSuite {
    test("very wide picture is refused") {
        WallpaperSize.accepted(65535, 2159).shouldBeFalse()
    }

    test("four k picture is accepted") {
        WallpaperSize.accepted(3840, 2160).shouldBeTrue()
        WallpaperSize.accepted(1920, 1080).shouldBeTrue()
    }

    test("unreadable picture is refused") {
        WallpaperSize.accepted(-1, -1).shouldBeFalse()
        WallpaperSize.accepted(0, 1080).shouldBeFalse()
    }

    test("four k on 1080p samples by two") {
        WallpaperSize.sampleSize(3840, 2160, 1920, 1080) shouldBe 2
        WallpaperSize.coverScale(1920, 1080, 1920, 1080).shouldBeNull()
    }

    test("photo between sample sizes is scaled to the screen") {
        // 3000x2000 keeps sample size 1; the cover is limited by the width, so it decodes 1920 wide.
        WallpaperSize.sampleSize(3000, 2000, 1920, 1080) shouldBe 1
        WallpaperSize.coverScale(3000, 2000, 1920, 1080) shouldBe intArrayOf(3000, 1920)
    }

    test("wide picture is scaled by its height") {
        WallpaperSize.coverScale(4000, 2000, 1920, 1080) shouldBe intArrayOf(2000, 1080)
    }

    test("small picture is not scaled") {
        WallpaperSize.sampleSize(1280, 720, 1920, 1080) shouldBe 1
        WallpaperSize.coverScale(1280, 720, 1920, 1080).shouldBeNull()
    }

    test("a phone sized photo is accepted once sampled down") {
        val s = WallpaperSize.sampleSize(4032, 3024, 1920, 1080)
        WallpaperSize.accepted(4032 / s, 3024 / s).shouldBeTrue()
        val t = WallpaperSize.sampleSize(65535, 2159, 1920, 1080)
        WallpaperSize.accepted(65535 / t, 2159 / t).shouldBeFalse()
    }
}
