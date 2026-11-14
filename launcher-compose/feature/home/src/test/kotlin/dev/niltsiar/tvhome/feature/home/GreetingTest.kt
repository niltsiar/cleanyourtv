package dev.niltsiar.tvhome.feature.home

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe
import java.util.Locale

val GreetingTest by testSuite {
    /** The part of the day at each hour, as a letter per hour from 0 to 23: n night, m morning, f forenoon, a afternoon, e evening. */
    fun day(locale: Locale): String =
        (0..23).joinToString("") {
            when (dayPart(it, locale)) {
                DayPart.Night -> "n"
                DayPart.Morning -> "m"
                DayPart.Forenoon -> "f"
                DayPart.Afternoon -> "a"
                DayPart.Evening -> "e"
            }
        }

    test("the default hours run night to 5 morning to 12 afternoon to 17 evening to 22") {
        day(Locale.ENGLISH) shouldBe "nnnnn" + "mmmmmmm" + "aaaaa" + "eeeee" + "nn"
        day(Locale.GERMANY) shouldBe day(Locale.ENGLISH)
    }

    test("indonesian splits the day at 10 15 and 18") {
        val indonesian = "mmmmmmmmmm" + "aaaaa" + "eee" + "nnnnnn"
        day(Locale.forLanguageTag("id-ID")) shouldBe indonesian
    }

    test("spain and catalan say good night from 21 and until 6") {
        val spain = "nnnnnn" + "mmmmmm" + "aaaaaaaaa" + "nnn"
        day(Locale.forLanguageTag("es-ES")) shouldBe spain
        day(Locale.forLanguageTag("ca-ES")) shouldBe spain
    }

    test("latin american spanish keeps the default hours") {
        day(Locale.forLanguageTag("es-MX")) shouldBe day(Locale.ENGLISH)
    }

    test("danish has a late morning hour at 11") {
        day(Locale.forLanguageTag("da-DK")) shouldBe "nnnnn" + "mmmmmm" + "f" + "aaaaaa" + "eeee" + "nn"
    }
}
