package dev.niltsiar.tvhome.feature.clock

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.random.Random

val ClockMotionTest by testSuite {
    val screen = IntSize(1920, 1080)
    val clock = IntSize(700, 320)

    test("every spot keeps the whole clock on screen and off the edges") {
        val random = Random(1)
        repeat(500) {
            val spot = Spot.random(random)
            spot.x shouldBeIn 0.1f..0.9f
            spot.y shouldBeIn 0.1f..0.9f
            val corner = screen.cornerAt(clock, spot)
            corner.x shouldBeGreaterThan 0
            corner.y shouldBeGreaterThan 0
            corner.x + clock.width shouldBeLessThan screen.width
            corner.y + clock.height shouldBeLessThan screen.height
        }
    }

    test("the extreme spots sit a tenth of the free room from each edge") {
        screen.cornerAt(clock, Spot(0.1f, 0.1f)) shouldBe IntOffset(122, 76)
        screen.cornerAt(clock, Spot(0.9f, 0.9f)) shouldBe IntOffset(1098, 684)
    }

    test("a spots x places the clock across and its y down") {
        screen.cornerAt(clock, Spot(0.25f, 0.75f)) shouldBe IntOffset(305, 570)
    }

    test("a clock wider than the screen stays at the origin") {
        IntSize(500, 300).cornerAt(IntSize(600, 400), Spot(0.8f, 0.8f)) shouldBe IntOffset(0, 0)
    }

    test("the glow is centred behind the clock") {
        val spot = Spot(0.5f, 0.5f)
        val corner = screen.cornerAt(clock, spot)
        screen.centerOf(clock, spot) shouldBe Offset(corner.x + 350f, corner.y + 160f)
    }

    test("the glow takes 16 steps from one colour to the next") {
        val from = GlowPalette[0]
        val to = GlowPalette[1]
        glowStep(from, to, 0) shouldBe from
        glowStep(from, to, GLOW_STEPS) shouldBe to
        glowStep(from, to, GLOW_STEPS / 2) shouldNotBe from
        glowStep(from, to, GLOW_STEPS / 2) shouldNotBe to
    }

    test("the glow palette is the reference launchers five colours") {
        GlowPalette shouldBe listOf(0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C).map { Color(it) }
    }
}
