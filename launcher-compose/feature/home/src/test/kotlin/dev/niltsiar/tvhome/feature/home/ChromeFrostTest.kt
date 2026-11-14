package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.geometry.Rect
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.roundToInt

/** The frost's blur and saturation on images whose result can be worked out by hand. */
private val KERNEL = intArrayOf(1, 3, 6, 10, 15, 18, 19, 18, 15, 10, 6, 3, 1)
private const val LENGTH = 24
private const val EDGE = 12
private val BEFORE = (0xFF shl 24) or (0 shl 16) or (200 shl 8) or 40
private val AFTER = (0xFF shl 24) or (240 shl 16) or (20 shl 8) or 100

val ChromeFrostTest by testSuite {
    fun rgb(
        r: Int,
        g: Int,
        b: Int,
    ): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    fun channels(pixel: Int): List<Int> = listOf(pixel shr 16 and 0xFF, pixel shr 8 and 0xFF, pixel and 0xFF)

    /**
     * What three box blurs of radius 2 make of a step from [before] to [after] between [EDGE] - 1 and [EDGE], at [at]:
     * the 13-tap kernel 1 3 6 10 15 18 19 18 15 10 6 3 1 over 125 weighs how much of [after] reaches it.
     */
    fun predicted(
        at: Int,
        before: Int,
        after: Int,
    ): List<Int> {
        val weight = KERNEL.indices.filter { at + it - KERNEL.size / 2 >= EDGE }.sumOf { KERNEL[it] } / KERNEL.sum().toFloat()
        return channels(before).zip(channels(after)) { from, to -> (from + (to - from) * weight).roundToInt() }
    }

    fun assertSpread(
        actual: (Int) -> Int,
        before: Int,
        after: Int,
    ) {
        for (at in 0 until LENGTH) {
            val expected = predicted(at, before, after)
            val got = channels(actual(at))
            if (at < EDGE - KERNEL.size / 2 || at >= EDGE + KERNEL.size / 2) {
                withClue("at $at") { got shouldBe expected }
            } else {
                // Each pass rounds down.
                withClue("at $at: expected $expected, got $got") { (expected.zip(got).all { (e, g) -> abs(e - g) <= 3 }).shouldBeTrue() }
            }
        }
    }

    test("a colour edge spreads across as three radius 2 box blurs predict") {
        val width = LENGTH
        val height = 5
        val pixels = IntArray(width * height) { if (it % width < EDGE) BEFORE else AFTER }

        blur(pixels, width, height)

        repeat(height) { y -> assertSpread({ x -> pixels[y * width + x] }, BEFORE, AFTER) }
    }

    test("a colour edge spreads down as three radius 2 box blurs predict") {
        val width = 5
        val height = LENGTH
        val pixels = IntArray(width * height) { if (it / width < EDGE) BEFORE else AFTER }

        blur(pixels, width, height)

        repeat(width) { x -> assertSpread({ y -> pixels[y * width + x] }, BEFORE, AFTER) }
    }

    test("saturation moves a colour 45 percent further from its grey") {
        // Its grey is 0.2126 * 200 + 0.7152 * 100 + 0.0722 * 50 = 117.65.
        val pixels = intArrayOf(rgb(200, 100, 50))

        saturate(pixels)

        channels(pixels[0]) shouldBe listOf(237, 92, 20)
    }

    test("saturation leaves a grey alone and clamps a colour at the ends of a channel") {
        val pixels = intArrayOf(rgb(128, 128, 128), rgb(250, 10, 10))

        saturate(pixels)

        channels(pixels[0]) shouldBe listOf(128, 128, 128)
        channels(pixels[1]) shouldBe listOf(255, 0, 0)
    }

    test("brightening lifts each channel 8 percent and clamps at the top") {
        val pixels = intArrayOf(rgb(100, 50, 0), rgb(250, 128, 240))

        brighten(pixels)

        channels(pixels[0]) shouldBe listOf(108, 54, 0)
        channels(pixels[1]) shouldBe listOf(255, 138, 255)
    }

    test("the lightness under the dock is the mean L star of the pixels inside its band") {
        // A picture 10 wide: white on its left half, black on its right; L* of white is 100 and of black 0.
        val pixels = IntArray(10 * 4) { if (it % 10 < 5) rgb(255, 255, 255) else rgb(0, 0, 0) }

        meanLightness(pixels, 10, 4, Rect(0.1f, 0.2f, 0.4f, 0.9f)) shouldBe (100f plusOrMinus 0.01f)
        meanLightness(pixels, 10, 4, Rect(0.6f, 0.2f, 0.9f, 0.9f)) shouldBe (0f plusOrMinus 0.01f)
        meanLightness(pixels, 10, 4, Rect(0f, 0f, 1f, 1f)) shouldBe (50f plusOrMinus 0.01f)
    }

    test("the luminance under the hero block is the mean relative luminance of the pixels inside its box") {
        // A picture 10 wide: white on its left half, black on its right; the relative luminance of white is 1 and of black 0.
        val pixels = IntArray(10 * 4) { if (it % 10 < 5) rgb(255, 255, 255) else rgb(0, 0, 0) }

        meanLuminance(pixels, 10, 4, Rect(0.1f, 0.2f, 0.4f, 0.9f)) shouldBe (1f plusOrMinus 0.001f)
        meanLuminance(pixels, 10, 4, Rect(0.6f, 0.2f, 0.9f, 0.9f)) shouldBe (0f plusOrMinus 0.001f)
        meanLuminance(pixels, 10, 4, Rect(0f, 0f, 1f, 1f)) shouldBe (0.5f plusOrMinus 0.001f)
    }

    test("a mid grey has a relative luminance of 021 not 05") {
        meanLuminance(intArrayOf(rgb(128, 128, 128)), 1, 1, Rect(0f, 0f, 1f, 1f)) shouldBe (0.216f plusOrMinus 0.002f)
    }

    test("a mid grey is L star 53") {
        meanLightness(intArrayOf(rgb(128, 128, 128)), 1, 1, Rect(0f, 0f, 1f, 1f)) shouldBe (53.4f plusOrMinus 0.2f)
    }
}
