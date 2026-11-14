package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

val LightArtTest by testSuite {
    fun art(
        colour: Int,
        alpha: Int = 0xFF,
    ) = IntArray(VIVID_SIDE * VIVID_SIDE) { (alpha shl 24) or (colour and 0xFFFFFF) }

    fun grey(level: Int) = (level shl 16) or (level shl 8) or level

    test("a white banner is light") {
        isLightArt(art(grey(0xF5))).shouldBeTrue()
    }

    test("a light grey banner is light") {
        isLightArt(art(grey(0xD6))).shouldBeTrue()
    }

    test("a blue banner and a teal banner are not") {
        isLightArt(art(0x0A7BFF)).shouldBeFalse()
        isLightArt(art(0x4A6A73)).shouldBeFalse()
    }

    test("a grey of relative luminance 035 is the darkest that counts") {
        // sRGB 160 is 0.3515 relative luminance and 159 is 0.3467.
        isLightArt(art(grey(160))).shouldBeTrue()
        isLightArt(art(grey(159))).shouldBeFalse()
    }

    test("a light banner three quarters clear over the dark tile is not light") {
        val sparse = IntArray(VIVID_SIDE * VIVID_SIDE) { if (it % 4 == 0) 0xFFF5F5F5.toInt() else 0 }

        isLightArt(sparse).shouldBeFalse()
    }

    test("a white logo on a dark banner is not light") {
        val logo = IntArray(VIVID_SIDE * VIVID_SIDE) { if (it % 6 == 0) 0xFFFFFFFF.toInt() else 0xFF202020.toInt() }

        isLightArt(logo).shouldBeFalse()
    }
}
