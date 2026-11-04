package dev.niltsiar.tvhome.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class GreetingTest {
    /** The part of the day at each hour, as a letter per hour from 0 to 23: n night, m morning, f forenoon, a afternoon, e evening. */
    private fun day(locale: Locale): String =
        (0..23).joinToString("") {
            when (dayPart(it, locale)) {
                DayPart.Night -> "n"
                DayPart.Morning -> "m"
                DayPart.Forenoon -> "f"
                DayPart.Afternoon -> "a"
                DayPart.Evening -> "e"
            }
        }

    @Test
    fun the_default_hours_run_night_to_5_morning_to_12_afternoon_to_17_evening_to_22() {
        assertEquals("nnnnn" + "mmmmmmm" + "aaaaa" + "eeeee" + "nn", day(Locale.ENGLISH))
        assertEquals(day(Locale.ENGLISH), day(Locale.GERMANY))
    }

    @Test
    fun indonesian_splits_the_day_at_10_15_and_18() {
        val indonesian = "mmmmmmmmmm" + "aaaaa" + "eee" + "nnnnnn"
        assertEquals(indonesian, day(Locale.forLanguageTag("id-ID")))
    }

    @Test
    fun spain_and_catalan_say_good_night_from_21_and_until_6() {
        val spain = "nnnnnn" + "mmmmmm" + "aaaaaaaaa" + "nnn"
        assertEquals(spain, day(Locale.forLanguageTag("es-ES")))
        assertEquals(spain, day(Locale.forLanguageTag("ca-ES")))
    }

    @Test
    fun latin_american_spanish_keeps_the_default_hours() {
        assertEquals(day(Locale.ENGLISH), day(Locale.forLanguageTag("es-MX")))
    }

    @Test
    fun danish_has_a_late_morning_hour_at_11() {
        assertEquals("nnnnn" + "mmmmmm" + "f" + "aaaaaa" + "eeee" + "nn", day(Locale.forLanguageTag("da-DK")))
    }
}
