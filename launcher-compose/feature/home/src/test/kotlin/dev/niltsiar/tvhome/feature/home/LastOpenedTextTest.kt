package dev.niltsiar.tvhome.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LastOpenedTextTest {
    private val zone = ZoneId.systemDefault()
    private val now = LocalDateTime.of(2026, 11, 3, 20, 15)

    private fun describe(
        opened: LocalDateTime,
        hour24: Boolean = false,
        at: LocalDateTime = now,
        locale: Locale = Locale.ENGLISH,
    ) = describeLastOpened(RuntimeEnvironment.getApplication(), OpenedAt(millis(opened), millis(at), hour24), locale, zone)

    private fun millis(time: LocalDateTime) = time.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun within_the_hour_it_counts_minutes() {
        assertEquals("5 minutes ago", describe(now.minusMinutes(5)))
        assertEquals("59 minutes ago", describe(now.minusMinutes(59).minusSeconds(30)))
    }

    @Test
    fun under_a_minute_and_the_future_read_as_one_minute() {
        assertEquals("1 minute ago", describe(now.minusSeconds(20)))
        assertEquals("1 minute ago", describe(now.plusMinutes(10)))
    }

    @Test
    fun from_the_hour_on_it_names_the_day_and_the_time() {
        assertEquals("today, 6:15 PM", describe(now.minusHours(2)))
        assertEquals("yesterday, 9:40 PM", describe(LocalDateTime.of(2026, 11, 2, 21, 40)))
        assertEquals("3 days ago, 9:05 AM", describe(LocalDateTime.of(2026, 10, 31, 9, 5)))
    }

    @Test
    fun the_day_follows_the_calendar_not_the_24_hour_count() {
        val justAfterMidnight = LocalDateTime.of(2026, 11, 4, 0, 30)

        assertEquals("yesterday, 1:30 AM", describe(LocalDateTime.of(2026, 11, 3, 1, 30), at = justAfterMidnight))
    }

    @Test
    fun a_week_or_more_gives_the_date_and_a_year_only_when_it_is_another_one() {
        assertEquals("Oct 12, 9:05 AM", describe(LocalDateTime.of(2026, 10, 12, 9, 5)))
        assertEquals("Dec 30, 2025, 11:59 PM", describe(LocalDateTime.of(2025, 12, 30, 23, 59)))
    }

    @Test
    fun the_time_follows_the_12_or_24_hour_clock() {
        val noon = LocalDateTime.of(2026, 11, 3, 12, 0)
        val midnight = LocalDateTime.of(2026, 11, 3, 0, 10)

        assertEquals("today, 12:00 PM", describe(noon))
        assertEquals("today, 12:10 AM", describe(midnight))
        assertEquals("today, 12:00", describe(noon, hour24 = true))
        assertEquals("today, 00:10", describe(midnight, hour24 = true))
    }

    @Test
    @Config(qualifiers = "es-rES")
    fun spanish_gets_the_words_of_the_tvs_language() {
        val spanish = Locale.forLanguageTag("es-ES")
        Locale.setDefault(spanish)

        assertEquals("hace 5 minutos", describe(now.minusMinutes(5), locale = spanish))
        assertEquals("ayer, 21:40", describe(LocalDateTime.of(2026, 11, 2, 21, 40), hour24 = true, locale = spanish))
    }
}
