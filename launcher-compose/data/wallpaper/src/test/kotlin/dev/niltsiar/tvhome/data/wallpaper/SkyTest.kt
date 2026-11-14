package dev.niltsiar.tvhome.data.wallpaper

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** The sunset palette: anchors, interpolation and clamping. */
val SkyTest by testSuite {
    /** Rough relative brightness of an opaque ARGB colour, 0 to 1. */
    fun luma(color: Int): Double =
        (0.2126 * ((color shr 16) and 0xFF) + 0.7152 * ((color shr 8) and 0xFF) + 0.0722 * (color and 0xFF)) / 255.0

    test("night looks the same at both ends of the day") {
        val first = Sky.at(0)
        val last = Sky.at(1439)
        last.top shouldBe first.top
        last.mid shouldBe first.mid
        last.bottom shouldBe first.bottom
        last.sun shouldBe 0
    }

    test("glow peaks at sunrise and sunset") {
        Sky.at(450).sun shouldBeGreaterThan Sky.at(780).sun // 07:30 sunrise vs 13:00 day
        Sky.at(1140).sun shouldBeGreaterThan Sky.at(1260).sun // 19:00 sunset vs 21:00 dusk
        Sky.at(1140).sun shouldBeGreaterThan Sky.at(450).sun // the evening is the warmest moment
        Sky.at(60).sun shouldBe 0
        Sky.at(1400).sun shouldBe 0
    }

    test("the glow tracks the sun from left to right") {
        Sky.at(390).sunX shouldBeLessThan Sky.at(780).sunX
        Sky.at(780).sunX shouldBeLessThan Sky.at(1140).sunX
        // midday is near the centre
        Sky.at(780).sunX shouldBeGreaterThan 0.4f
        Sky.at(780).sunX shouldBeLessThan 0.6f
    }

    test("colours interpolate between anchors") {
        val dawn = Sky.at(390)
        val sunrise = Sky.at(450)
        val half = Sky.at(420)
        half.top shouldBe Sky.lerpColor(dawn.top, sunrise.top, 0.5f)
        half.top shouldNotBe dawn.top
        half.top shouldNotBe sunrise.top
    }

    test("time is clamped and answered every minute of the day") {
        Sky.at(-30).top shouldBe Sky.at(0).top
        Sky.at(4000).top shouldBe Sky.at(1440).top
        for (minute in 0 until 1440 step 5) {
            val p = Sky.at(minute)
            // The header and the clock sit on the top two thirds; they stay on dark ground all day.
            withClue("top too bright at $minute") { luma(p.top) shouldBeLessThan 0.4 }
            withClue("mid too bright at $minute") { luma(p.mid) shouldBeLessThan 0.4 }
        }
        Sky.minuteOfDay(23, 59) shouldBe 1439
        Sky.minuteOfDay(0, 0) shouldBe 0
    }
}
