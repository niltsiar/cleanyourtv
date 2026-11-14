package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.time.ZoneId

private val Zone = ZoneId.of("UTC")

private fun at(
    day: Int,
    hour: Int,
    minute: Int = 0,
): Long =
    LocalDateTime
        .of(2026, 11, day, hour, minute)
        .atZone(Zone)
        .toInstant()
        .toEpochMilli()

private fun launch(
    app: String,
    day: Int,
    hour: Int,
    minute: Int = 0,
) = LaunchRecord(app, at(day, hour, minute))

/** 2026-11-02 is a Monday; 2026-11-07 a Saturday and 2026-11-08 a Sunday. */
val HabitRuleTest by testSuite {
    /** Wednesday 4 November, 21:30, after the launches of the same evening hour on Monday, Tuesday and last Friday. */
    val now = at(day = 4, hour = 21, minute = 30)
    val fourOnThreeDates =
        listOf(launch("tv.show", 2, 21), launch("tv.show", 2, 22), launch("tv.show", 3, 21, 20), launch("tv.show", 4, 21, 10))

    fun habit(
        records: List<LaunchRecord>,
        nowMillis: Long = now,
    ) = habitApp(records, nowMillis, Zone)

    test("an app with four launches on three dates in the window is the habit") {
        habit(fourOnThreeDates) shouldBe "tv.show"
    }

    test("four launches on two dates are no habit") {
        val twoDates = listOf(launch("tv.show", 2, 21), launch("tv.show", 2, 22), launch("tv.show", 3, 21), launch("tv.show", 3, 22))

        habit(twoDates).shouldBeNull()
    }

    test("three launches on three dates are no habit") {
        habit(fourOnThreeDates.filterIndexed { index, _ -> index != 1 }).shouldBeNull()
    }

    test("an app with less than half of the launches in the window is no habit") {
        val other = (0 until 5).map { launch("other.app", 2, 21, it) }

        habit(fourOnThreeDates + other).shouldBeNull()
    }

    test("an app with exactly half of the launches in the window is the habit") {
        val other = (0 until 4).map { launch("other.app", 2, 21, it) }

        habit(fourOnThreeDates + other) shouldBe "tv.show"
    }

    test("two apps that both reach half leave no habit") {
        val other =
            listOf(
                launch("other.app", 2, 21, 5),
                launch("other.app", 2, 21, 6),
                launch("other.app", 3, 21, 5),
                launch("other.app", 4, 21, 5),
            )

        habit(fourOnThreeDates + other).shouldBeNull()
    }

    test("an app with more than half is the habit over one with fewer") {
        val other = listOf(launch("other.app", 3, 21, 5), launch("other.app", 4, 21, 5))

        habit(fourOnThreeDates + other) shouldBe "tv.show"
    }

    test("weekday launches count for a weekday and not for a weekend") {
        habit(fourOnThreeDates, nowMillis = at(day = 5, hour = 21, minute = 30)) shouldBe "tv.show"
        habit(fourOnThreeDates, nowMillis = at(day = 7, hour = 21, minute = 30)).shouldBeNull()
        habit(fourOnThreeDates, nowMillis = at(day = 8, hour = 21, minute = 30)).shouldBeNull()
    }

    test("saturday and sunday launches count together for a weekend") {
        val weekend = listOf(launch("tv.show", 7, 21), launch("tv.show", 8, 21), launch("tv.show", 14, 21), launch("tv.show", 15, 21))

        habit(weekend, nowMillis = at(day = 21, hour = 21, minute = 30)) shouldBe "tv.show"
        habit(weekend, nowMillis = at(day = 18, hour = 21, minute = 30)).shouldBeNull()
    }

    test("a launch 45 minutes from now counts and one 46 minutes away does not") {
        val edge = listOf(launch("tv.show", 2, 20, 45), launch("tv.show", 3, 22, 15), launch("tv.show", 4, 20, 45))

        habit(edge + launch("tv.show", 2, 21)) shouldBe "tv.show"
        habit(edge + launch("tv.show", 2, 22, 16)).shouldBeNull()
    }

    test("the window wraps over midnight in both directions") {
        val late =
            listOf(launch("tv.show", 2, 23, 40), launch("tv.show", 3, 0, 10), launch("tv.show", 4, 0, 20), launch("tv.show", 5, 23, 50))

        habit(late, nowMillis = at(day = 5, hour = 23, minute = 55)) shouldBe "tv.show"
        habit(late, nowMillis = at(day = 6, hour = 0, minute = 5)) shouldBe "tv.show"
        habit(late, nowMillis = at(day = 6, hour = 0, minute = 50)).shouldBeNull()
    }

    test("launches older than eight weeks are ignored") {
        val old = LaunchRecord("tv.show", now - HABIT_KEEP_MS)
        val recent = fourOnThreeDates.drop(1)

        habit(recent + old).shouldBeNull()
        habit(recent + old.copy(atMillis = old.atMillis + 1)) shouldBe "tv.show"
    }

    test("the kind of day and the time of day are the local ones") {
        val madrid = ZoneId.of("Europe/Madrid")

        fun utc(
            day: Int,
            hour: Int,
            minute: Int,
        ) = LocalDateTime
            .of(2026, 11, day, hour, minute)
            .atZone(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        // Friday and Saturday 23:30 UTC are Saturday and Sunday 00:30 in Madrid.
        val records = listOf(13, 14, 20, 21).map { LaunchRecord("tv.show", utc(it, 23, 30)) }

        habitApp(records, utc(27, 23, 30), madrid) shouldBe "tv.show"
        habitApp(records, utc(27, 23, 30), ZoneId.of("UTC")).shouldBeNull()
    }

    test("launches in the last weeks still count and a log with no launch has no habit") {
        habit(fourOnThreeDates.map { it.copy(atMillis = it.atMillis - 6 * 7 * 24 * 3_600_000L) }, nowMillis = now) shouldBe "tv.show"
        habit(emptyList()).shouldBeNull()
    }
}
