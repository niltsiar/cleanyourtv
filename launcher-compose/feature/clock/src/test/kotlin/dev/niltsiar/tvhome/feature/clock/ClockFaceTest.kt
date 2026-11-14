package dev.niltsiar.tvhome.feature.clock

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.Locale

val ClockFaceTest by testSuite {
    val evening = LocalDateTime.of(2026, 11, 4, 21, 5)
    val datePattern = "EEEE  d MMMM"

    test("the 24 hour clock pads the hour and has no marker") {
        clockFace(evening, hour24 = true, datePattern, Locale.US) shouldBe ClockFace("21", "05", "", "WEDNESDAY  4 NOVEMBER")
        clockFace(evening.withHour(9), hour24 = true, datePattern, Locale.US).hours shouldBe "09"
    }

    test("the 12 hour clock drops the pad and names am or pm") {
        clockFace(evening, hour24 = false, datePattern, Locale.US) shouldBe ClockFace("9", "05", "PM", "WEDNESDAY  4 NOVEMBER")
        clockFace(evening.withHour(0), hour24 = false, datePattern, Locale.US).amPm shouldBe "AM"
        clockFace(evening.withHour(0), hour24 = false, datePattern, Locale.US).hours shouldBe "12"
    }

    test("the date follows the locale") {
        clockFace(evening, hour24 = true, datePattern, Locale.forLanguageTag("es-ES")).date shouldBe "MIÉRCOLES  4 NOVIEMBRE"
    }
}
