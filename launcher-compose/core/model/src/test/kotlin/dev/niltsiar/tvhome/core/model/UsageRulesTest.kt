package dev.niltsiar.tvhome.core.model

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe
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

val UsageRulesTest by testSuite {
    val english = Collator.getInstance(Locale.ENGLISH)
    val today = LocalDate.of(2026, 11, 6)

    fun unused(
        record: OpenedRecord,
        days: Int,
        vararg names: String,
    ) = record.unusedKeys(names.map(::key), days, today, zone)

    test("an app opened more than n days ago is unused and one opened exactly n days ago is not") {
        val record =
            OpenedRecord(
                times = mapOf(key("Old") to millis(today.minusDays(61)), key("Edge") to millis(today.minusDays(60))),
                since = millis(today.minusDays(100)),
            )

        unused(record, 60, "Old", "Edge") shouldBe setOf(key("Old"))
    }

    test("the hour of the last open does not move the day it counts from") {
        val late = OpenedRecord(times = mapOf(key("A") to millis(today.minusDays(60), hour = 23)), since = 0L)
        val early = OpenedRecord(times = mapOf(key("A") to millis(today.minusDays(61), hour = 0)), since = 0L)

        unused(late, 60, "A") shouldBe emptySet()
        unused(early, 60, "A") shouldBe setOf(key("A"))
    }

    test("an app never opened counts from the start of the record") {
        val young = OpenedRecord(since = millis(today.minusDays(10)))
        val old = OpenedRecord(since = millis(today.minusDays(61)))

        unused(young, 60, "Never") shouldBe emptySet()
        unused(old, 60, "Never") shouldBe setOf(key("Never"))
    }

    test("a record that has not started calls nothing unused") {
        unused(OpenedRecord(), 60, "Never") shouldBe emptySet()
    }

    test("zero days fades nothing") {
        val record = OpenedRecord(since = millis(today.minusDays(500)))

        unused(record, 0, "Never") shouldBe emptySet()
    }

    test("the wait for the next day ends at local midnight also on the day the clocks change") {
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

        millisUntilNextDay(evening, zone) shouldBe 90 * 60_000L
        millisUntilNextDay(longDay, zone) shouldBe (23 * 60 + 30) * 60_000L
        millisUntilNextDay(shortDay, zone) shouldBe (22 * 60 + 30) * 60_000L
    }

    test("the least used order lists apps never opened first then the oldest open and breaks ties by label") {
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

        sorted shouldBe listOf("Alpha", "Zeta", "Oldest", "Tie A", "Tie B", "Recent")
    }

    test("an app opened at the very start of the record still follows the apps never opened") {
        val record = OpenedRecord(times = mapOf(key("Opened") to 1000L), since = 1000L)

        val sorted = sortLeastUsed(listOf(app("Opened"), app("Zzz")), record, english)

        sorted.map { it.app.label } shouldBe listOf("Zzz", "Opened")
    }
}
