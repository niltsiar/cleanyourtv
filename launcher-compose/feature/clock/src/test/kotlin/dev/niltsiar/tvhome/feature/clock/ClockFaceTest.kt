package dev.niltsiar.tvhome.feature.clock

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class ClockFaceTest {
    private val evening = LocalDateTime.of(2026, 11, 4, 21, 5)
    private val datePattern = "EEEE  d MMMM"

    @Test
    fun the_24_hour_clock_pads_the_hour_and_has_no_marker() {
        assertEquals(
            ClockFace("21", "05", "", "WEDNESDAY  4 NOVEMBER"),
            clockFace(evening, hour24 = true, datePattern, Locale.US),
        )
        assertEquals("09", clockFace(evening.withHour(9), hour24 = true, datePattern, Locale.US).hours)
    }

    @Test
    fun the_12_hour_clock_drops_the_pad_and_names_am_or_pm() {
        assertEquals(
            ClockFace("9", "05", "PM", "WEDNESDAY  4 NOVEMBER"),
            clockFace(evening, hour24 = false, datePattern, Locale.US),
        )
        assertEquals("AM", clockFace(evening.withHour(0), hour24 = false, datePattern, Locale.US).amPm)
        assertEquals("12", clockFace(evening.withHour(0), hour24 = false, datePattern, Locale.US).hours)
    }

    @Test
    fun the_date_follows_the_locale() {
        assertEquals("MIÉRCOLES  4 NOVIEMBRE", clockFace(evening, hour24 = true, datePattern, Locale.forLanguageTag("es-ES")).date)
    }
}
