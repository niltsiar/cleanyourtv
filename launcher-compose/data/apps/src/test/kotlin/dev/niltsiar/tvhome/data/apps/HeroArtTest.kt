package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

val HeroArtTest by testSuite {
    fun rgb(
        r: Int,
        g: Int,
        b: Int,
    ) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    test("a 192 by 108 picture comes out 48 by 27") {
        val source = IntArray(192 * 108)

        softenHeroArt(source).size shouldBe 48 * 27
    }

    test("a picture of another size is refused") {
        shouldThrow<IllegalArgumentException> { softenHeroArt(IntArray(48 * 27)) }
    }

    test("halving averages each 2 by 2 block per channel") {
        val block = intArrayOf(rgb(10, 20, 30), rgb(20, 30, 40), rgb(30, 40, 50), rgb(41, 50, 60))

        // 4 pixels wide, 2 high: two blocks
        val out = halve(intArrayOf(block[0], block[1], block[0], block[1], block[2], block[3], block[2], block[3]), 4)

        out shouldBe intArrayOf(rgb(25, 35, 45), rgb(25, 35, 45))
    }

    test("saturating pushes each channel from the luminance by 1 7") {
        // luminance 121.26: red 255 (clamped), green and blue 85
        saturate(intArrayOf(rgb(200, 100, 100))) shouldBe intArrayOf(rgb(255, 85, 85))
    }

    test("grey has no hue to saturate") {
        saturate(intArrayOf(rgb(90, 90, 90))) shouldBe intArrayOf(rgb(90, 90, 90))
    }

    test("a box blur spreads a pixel over its 5 neighbours") {
        val row = IntArray(9) { rgb(0, 0, 0) }
        row[4] = rgb(250, 250, 250)

        val blurred = boxBlur(row, width = 9, height = 1, radius = 2, horizontal = true)

        blurred.map { it and 0xFF } shouldBe listOf(0, 0, 50, 50, 50, 50, 50, 0, 0)
    }

    test("a box blur counts the edge pixel again for the steps beyond it") {
        val row = IntArray(6) { rgb(0, 0, 0) }
        row[0] = rgb(100, 100, 100)

        val blurred = boxBlur(row, width = 6, height = 1, radius = 2, horizontal = true)

        blurred.map { it and 0xFF } shouldBe listOf(60, 40, 20, 0, 0, 0)
    }

    test("a box blur runs along columns too") {
        val column = IntArray(7) { rgb(0, 0, 0) }
        column[3] = rgb(100, 100, 100)

        val blurred = boxBlur(column, width = 1, height = 7, radius = 2, horizontal = false)

        blurred.map { it and 0xFF } shouldBe listOf(0, 20, 20, 20, 20, 20, 0)
    }

    test("a flat picture stays flat and saturated through the whole pipeline") {
        val flat = IntArray(192 * 108) { rgb(200, 100, 100) }

        softenHeroArt(flat).toSet() shouldBe setOf(rgb(255, 85, 85))
    }

    test("the blur softens an edge but leaves far pixels alone") {
        val half = IntArray(192 * 108) { if (it % 192 < 96) rgb(200, 100, 100) else rgb(100, 100, 200) }

        val out = softenHeroArt(half)
        val middle = out[13 * 48 + 24]

        out[13 * 48] shouldBe rgb(255, 85, 85)
        out[13 * 48 + 47] shouldBe rgb(95, 95, 255)
        middle shouldNotBe rgb(255, 85, 85)
        middle shouldNotBe rgb(95, 95, 255)
    }
}
