package dev.niltsiar.tvhome.core.config

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

val ClockFormatTest by testSuite {
    test("a chosen format wins without asking the tv") {
        val neverAsked = { error("the TV is not asked") }

        ClockFormat.Hour24.is24Hour(neverAsked).shouldBeTrue()
        ClockFormat.Hour12.is24Hour(neverAsked).shouldBeFalse()
    }

    test("system follows the tv") {
        ClockFormat.System.is24Hour { true }.shouldBeTrue()
        ClockFormat.System.is24Hour { false }.shouldBeFalse()
    }
}
