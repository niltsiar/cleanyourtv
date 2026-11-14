package dev.niltsiar.tvhome.data.wallpaper

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.ZoneId

/** Sunrise/sunset maths and the sky's stretch onto a real day. */
val SunTest by testSuite {
    val vilnius = ZoneId.of("Europe/Vilnius")
    val svalbard = ZoneId.of("Arctic/Longyearbyen")

    test("vilnius winter day is short and late") {
        val t = Sun.riseAndSet(LocalDate.of(2026, 12, 21), 54.69, 25.28, vilnius).shouldNotBeNull()
        withClue("sunrise ${t.sunrise}") { t.sunrise shouldBeInRange (8 * 60 + 31)..(9 * 60 - 1) } // about 08:40
        withClue("sunset ${t.sunset}") { t.sunset shouldBeInRange (15 * 60 + 41)..(16 * 60 + 4) } // about 15:55
    }

    test("vilnius summer day is long in local summer time") {
        val t = Sun.riseAndSet(LocalDate.of(2026, 6, 21), 54.69, 25.28, vilnius).shouldNotBeNull()
        withClue("sunrise ${t.sunrise}") { t.sunrise shouldBeInRange (4 * 60 + 31)..(4 * 60 + 54) } // about 04:42
        withClue("sunset ${t.sunset}") { t.sunset shouldBeInRange (21 * 60 + 51)..(22 * 60 + 9) } // about 22:00
    }

    test("no sunrise or sunset at the poles") {
        Sun.riseAndSet(LocalDate.of(2026, 12, 21), 78.2, 15.6, svalbard).shouldBeNull()
        Sun.riseAndSet(LocalDate.of(2026, 6, 21), 78.2, 15.6, svalbard).shouldBeNull()
    }

    test("sky glows land on the real sunrise and sunset") {
        val sunrise = 8 * 60 + 40
        val sunset = 15 * 60 + 55
        Sky.at(sunrise, sunrise, sunset).sun shouldBe Sky.at(Sky.REF_SUNRISE).sun
        Sky.at(sunset, sunrise, sunset).bottom shouldBe Sky.at(Sky.REF_SUNSET).bottom
        Sky.at(0, sunrise, sunset).top shouldBe Sky.at(0).top
        Sky.at(1440, sunrise, sunset).top shouldBe Sky.at(1440).top
        // Five o'clock on that winter day is past sunset: the glow has gone down on the right,
        // where the reference day still has the sun in the afternoon sky.
        Sky.at(17 * 60, sunrise, sunset).sunX shouldBeGreaterThan Sky.at(17 * 60).sunX + 0.1f
    }

    test("the reference day maps onto itself") {
        for (m in 0..1440 step 30) {
            Sky.at(m, Sky.REF_SUNRISE, Sky.REF_SUNSET).bottom shouldBe Sky.at(m).bottom
        }
    }

    test("nonsense sun times fall back to the reference day") {
        Sky.at(600, 900, 800).bottom shouldBe Sky.at(600).bottom
        Sky.at(600, 0, 1440).bottom shouldBe Sky.at(600).bottom
    }
}
