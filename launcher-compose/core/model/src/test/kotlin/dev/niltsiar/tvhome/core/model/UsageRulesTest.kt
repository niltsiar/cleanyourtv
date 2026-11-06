package dev.niltsiar.tvhome.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Collator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

private val zone = ZoneId.of("Europe/Madrid")

private fun millis(
    date: LocalDate,
    hour: Int = 12,
) = date
    .atTime(hour, 0)
    .atZone(zone)
    .toInstant()
    .toEpochMilli()

private fun app(
    name: String,
    pkg: String = "pkg.$name",
) = LibraryApp(AppEntry(key = "$pkg/.Main", packageName = pkg, label = name), onHome = false)

private fun key(name: String) = "pkg.$name/.Main"

class UsageRulesTest {
    private val english = Collator.getInstance(Locale.ENGLISH)
    private val today = LocalDate.of(2026, 11, 6)

    private fun unused(
        record: OpenedRecord,
        days: Int,
        vararg names: String,
    ) = record.unusedKeys(names.map(::key), days, today, zone)

    @Test
    fun an_app_opened_more_than_n_days_ago_is_unused_and_one_opened_exactly_n_days_ago_is_not() {
        val record =
            OpenedRecord(
                times = mapOf(key("Old") to millis(today.minusDays(61)), key("Edge") to millis(today.minusDays(60))),
                since = millis(today.minusDays(100)),
            )

        assertEquals(setOf(key("Old")), unused(record, 60, "Old", "Edge"))
    }

    @Test
    fun the_hour_of_the_last_open_does_not_move_the_day_it_counts_from() {
        val late = OpenedRecord(times = mapOf(key("A") to millis(today.minusDays(60), hour = 23)), since = 0L)
        val early = OpenedRecord(times = mapOf(key("A") to millis(today.minusDays(61), hour = 0)), since = 0L)

        assertEquals(emptySet<String>(), unused(late, 60, "A"))
        assertEquals(setOf(key("A")), unused(early, 60, "A"))
    }

    @Test
    fun an_app_never_opened_counts_from_the_start_of_the_record() {
        val young = OpenedRecord(since = millis(today.minusDays(10)))
        val old = OpenedRecord(since = millis(today.minusDays(61)))

        assertEquals(emptySet<String>(), unused(young, 60, "Never"))
        assertEquals(setOf(key("Never")), unused(old, 60, "Never"))
    }

    @Test
    fun a_record_that_has_not_started_calls_nothing_unused() {
        assertEquals(emptySet<String>(), unused(OpenedRecord(), 60, "Never"))
    }

    @Test
    fun zero_days_fades_nothing() {
        val record = OpenedRecord(since = millis(today.minusDays(500)))

        assertEquals(emptySet<String>(), unused(record, 0, "Never"))
    }

    @Test
    fun the_wait_for_the_next_day_ends_at_local_midnight_also_on_the_day_the_clocks_change() {
        val evening =
            LocalDateTime
                .of(2026, 11, 6, 22, 30)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val longDay =
            LocalDateTime
                .of(2026, 10, 25, 1, 30)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val shortDay =
            LocalDateTime
                .of(2026, 3, 29, 0, 30)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()

        assertEquals(90 * 60_000L, millisUntilNextDay(evening, zone))
        assertEquals((23 * 60 + 30) * 60_000L, millisUntilNextDay(longDay, zone))
        assertEquals((22 * 60 + 30) * 60_000L, millisUntilNextDay(shortDay, zone))
    }

    @Test
    fun the_least_used_order_lists_apps_never_opened_first_then_the_oldest_open_and_breaks_ties_by_label() {
        val day = 24L * 60 * 60 * 1000
        val record =
            OpenedRecord(
                times =
                    mapOf(
                        key("Recent") to 90 * day,
                        key("Oldest") to 10 * day,
                        key("Tie B") to 50 * day,
                        key("Tie A") to 50 * day,
                    ),
                since = 5 * day,
            )
        val apps = listOf(app("Recent"), app("Tie B"), app("Zeta"), app("Oldest"), app("Alpha"), app("Tie A"))

        val sorted = sortLeastUsed(apps, record, english).map { it.app.label }

        assertEquals(listOf("Alpha", "Zeta", "Oldest", "Tie A", "Tie B", "Recent"), sorted)
    }

    @Test
    fun an_app_opened_at_the_very_start_of_the_record_still_follows_the_apps_never_opened() {
        val record = OpenedRecord(times = mapOf(key("Opened") to 1000L), since = 1000L)

        val sorted = sortLeastUsed(listOf(app("Opened"), app("Zzz")), record, english)

        assertEquals(listOf("Zzz", "Opened"), sorted.map { it.app.label })
    }
}
