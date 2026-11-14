package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

val TileImagesTest by testSuite {
    test("wide banners are kept") {
        isBannerShaped(320, 180).shouldBeTrue()
        isBannerShaped(1920, 1080).shouldBeTrue()
    }

    test("square and tall pictures are not banners") {
        isBannerShaped(64, 64).shouldBeFalse()
        isBannerShaped(256, 256).shouldBeFalse()
        isBannerShaped(100, 160).shouldBeFalse()
    }

    test("a drawable without a size still fills the tile") {
        isBannerShaped(-1, -1).shouldBeTrue()
        isBannerShaped(0, 0).shouldBeTrue()
    }

    test("the tile width is the cell less room for the focus zoom up to the design size") {
        tileWidthPx(innerWidthPx = 1920, columns = 4, density = 2f) shouldBe 360
        tileWidthPx(innerWidthPx = 1920, columns = 6, density = 2f) shouldBe 269
    }

    test("a tile never gets narrower than the smallest size") {
        tileWidthPx(innerWidthPx = 400, columns = 8, density = 2f) shouldBe 160
    }

    test("an unknown grid width gives the design size") {
        tileWidthPx(innerWidthPx = 0, columns = 6, density = 2f) shouldBe 360
    }
}
