package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

val VividColorTest by testSuite {
    fun art(
        fill: Int,
        vararg at: Pair<Int, Int>,
    ) = IntArray(VIVID_SIDE * VIVID_SIDE) { fill }.also { px -> at.forEach { (i, color) -> px[i] = color } }

    test("the most saturated pixel wins a little more saturated") {
        val pixels = art(0xFF808080.toInt(), 5 to 0xFFC82828.toInt())

        // saturation 0.8 becomes 0.88; the value stays 200/255
        vividColor(pixels) shouldBe 0xFFC81818.toInt()
    }

    test("the edge colour is the average of the border pixels and ignores the middle") {
        val middle = 5 * VIVID_SIDE + 5
        val blueEdge = art(0xFF288BFF.toInt(), middle to 0xFFFF0000.toInt())
        val halfAndHalf =
            IntArray(VIVID_SIDE * VIVID_SIDE) {
                if (it % VIVID_SIDE <
                    VIVID_SIDE / 2
                ) {
                    0xFF000000.toInt()
                } else {
                    0xFFFFFFFF.toInt()
                }
            }

        edgeColor(blueEdge) shouldBe 0xFF288BFF.toInt()
        edgeColor(halfAndHalf) shouldBe 0xFF7F7F7F.toInt()
    }

    test("a dark saturated colour is lifted to half brightness") {
        vividColor(art(0xFF000032.toInt())) shouldBe 0xFF000080.toInt()
    }

    test("art with no vivid pixel gives its average lifted to 55 percent brightness") {
        vividColor(art(0xFF404040.toInt())) shouldBe 0xFF8C8C8C.toInt()
        vividColor(art(0xFFFFFFFF.toInt())) shouldBe 0xFFFFFFFF.toInt()
    }

    test("hue saturation and value round trip through rgb") {
        val hsv = FloatArray(3)
        rgbToHsv(0xFF00FF80.toInt(), hsv)

        hsv[0] shouldBe (150f plusOrMinus 0.2f)
        hsv[1] shouldBe (1f plusOrMinus 0f)
        hsv[2] shouldBe (1f plusOrMinus 0f)
        hsvToRgb(hsv) shouldBe 0xFF00FF80.toInt()
    }

    test("a hue below zero wraps to magenta side of red") {
        val hsv = FloatArray(3)
        rgbToHsv(0xFFFF0040.toInt(), hsv)

        hsv[0] shouldBe (345f plusOrMinus 0.2f)
    }
}
