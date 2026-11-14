package dev.niltsiar.tvhome.data.wallpaper

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe

val WallpaperCaptionsTest by testSuite {
    test("reads filename tab name") {
        val names =
            WallpaperCaptions.parse(
                listOf("001.jpg\tSunrise and aurora over northern Europe · NASA, Expedition 43", "002.jpg\t  Falcon 9 at sunset  "),
            )
        names["001.jpg"] shouldBe "Sunrise and aurora over northern Europe · NASA, Expedition 43"
        names["002.jpg"] shouldBe "Falcon 9 at sunset"
    }

    test("skips comments blank and malformed lines") {
        val names =
            WallpaperCaptions.parse(
                listOf("# captions for the collection", "", "   ", "no-tab-here.jpg Dusk", "\tno file", "003.jpg\t", null),
            )
        names.shouldBeEmpty()
    }

    test("cuts very long names") {
        val longName = "word ".repeat(30)
        val names = WallpaperCaptions.parse(listOf("004.jpg\t$longName"))
        names["004.jpg"]!!.length shouldBeLessThanOrEqual WallpaperCaptions.MAX_LENGTH
    }

    test("later line wins for the same file") {
        val names = WallpaperCaptions.parse(listOf("005.jpg\tOld", "005.jpg\tNew"))
        names["005.jpg"] shouldBe "New"
        names shouldNotContainKey "006.jpg"
    }

    test("missing folder has no names") {
        WallpaperCaptions.read(null).shouldBeEmpty()
    }
}
