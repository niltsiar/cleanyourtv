package dev.niltsiar.tvhome.data.wallpaper

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val WallpaperHueTest by testSuite {
    /** A 120 x 68 thumbnail: [share] of its pixels in [colour] (ARGB), the rest in [rest]. */
    fun picture(
        colour: Int,
        share: Float = 1f,
        rest: Int = 0xFF808080.toInt(),
    ): IntArray {
        val size = 120 * 68
        val coloured = (size * share).toInt()
        return IntArray(size) { if (it < coloured) colour else rest }
    }

    test("a vivid picture gives its own hue") {
        // #2060E0: red 32, green 96, blue 224, hue 220.
        WallpaperHue.dominant(picture(0xFF2060E0.toInt()))!! shouldBe (220f plusOrMinus 0.5f)
        // #E05A1E: hue 18.6.
        WallpaperHue.dominant(picture(0xFFE05A1E.toInt()))!! shouldBe (18.6f plusOrMinus 0.5f)
    }

    test("a grey picture with faint colour noise and black gives no hue") {
        val pixels = picture(0xFF8A8682.toInt(), share = 0.5f)
        for (i in 0 until pixels.size / 4) pixels[i] = 0xFF000000.toInt()
        pixels[pixels.size - 1] = 0xFFFF0000.toInt()
        WallpaperHue.dominant(pixels).shouldBeNull()
    }

    test("a picture with no pixels gives no hue") {
        WallpaperHue.dominant(IntArray(0)).shouldBeNull()
    }

    test("a mixed picture gives the hue that weighs most and ignores its grey") {
        val pixels = picture(0xFF808080.toInt())
        val orange = 0xFFE08A20.toInt()
        val blue = 0xFF2040C0.toInt()
        pixels.indices.forEach { i ->
            when (i % 10) {
                0, 1, 2 -> pixels[i] = orange
                3 -> pixels[i] = blue
            }
        }
        // The orange #E08A20 has hue 33.
        WallpaperHue.dominant(pixels)!! shouldBe (33.1f plusOrMinus 0.5f)
    }

    test("a picture dimmed toward black keeps its hue") {
        // #2060E0 at 15 percent brightness.
        WallpaperHue.dominant(picture(0xFF050E22.toInt()))!! shouldBe (220f plusOrMinus 6f)
    }
}
