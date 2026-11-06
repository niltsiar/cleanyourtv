package dev.niltsiar.tvhome.data.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
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
class HabitRuleTest {
    /** Wednesday 4 November, 21:30, after the launches of the same evening hour on Monday, Tuesday and last Friday. */
    private val now = at(day = 4, hour = 21, minute = 30)
    private val fourOnThreeDates =
        listOf(launch("tv.show", 2, 21), launch("tv.show", 2, 22), launch("tv.show", 3, 21, 20), launch("tv.show", 4, 21, 10))

    private fun habit(
        records: List<LaunchRecord>,
        nowMillis: Long = now,
    ) = habitApp(records, nowMillis, Zone)

    @Test
    fun an_app_with_four_launches_on_three_dates_in_the_window_is_the_habit() {
        assertEquals("tv.show", habit(fourOnThreeDates))
    }

    @Test
    fun four_launches_on_two_dates_are_no_habit() {
        val twoDates = listOf(launch("tv.show", 2, 21), launch("tv.show", 2, 22), launch("tv.show", 3, 21), launch("tv.show", 3, 22))

        assertNull(habit(twoDates))
    }

    @Test
    fun three_launches_on_three_dates_are_no_habit() {
        assertNull(habit(fourOnThreeDates.filterIndexed { index, _ -> index != 1 }))
    }

    @Test
    fun an_app_with_less_than_half_of_the_launches_in_the_window_is_no_habit() {
        val other = (0 until 5).map { launch("other.app", 2, 21, it) }

        assertNull(habit(fourOnThreeDates + other))
    }

    @Test
    fun an_app_with_exactly_half_of_the_launches_in_the_window_is_the_habit() {
        val other = (0 until 4).map { launch("other.app", 2, 21, it) }

        assertEquals("tv.show", habit(fourOnThreeDates + other))
    }

    @Test
    fun two_apps_that_both_reach_half_leave_no_habit() {
        val other =
            listOf(
                launch("other.app", 2, 21, 5),
                launch("other.app", 2, 21, 6),
                launch("other.app", 3, 21, 5),
                launch("other.app", 4, 21, 5),
            )

        assertNull(habit(fourOnThreeDates + other))
    }

    @Test
    fun an_app_with_more_than_half_is_the_habit_over_one_with_fewer() {
        val other = listOf(launch("other.app", 3, 21, 5), launch("other.app", 4, 21, 5))

        assertEquals("tv.show", habit(fourOnThreeDates + other))
    }

    @Test
    fun weekday_launches_count_for_a_weekday_and_not_for_a_weekend() {
        assertEquals("tv.show", habit(fourOnThreeDates, nowMillis = at(day = 5, hour = 21, minute = 30)))
        assertNull(habit(fourOnThreeDates, nowMillis = at(day = 7, hour = 21, minute = 30)))
        assertNull(habit(fourOnThreeDates, nowMillis = at(day = 8, hour = 21, minute = 30)))
    }

    @Test
    fun saturday_and_sunday_launches_count_together_for_a_weekend() {
        val weekend = listOf(launch("tv.show", 7, 21), launch("tv.show", 8, 21), launch("tv.show", 14, 21), launch("tv.show", 15, 21))

        assertEquals("tv.show", habit(weekend, nowMillis = at(day = 21, hour = 21, minute = 30)))
        assertNull(habit(weekend, nowMillis = at(day = 18, hour = 21, minute = 30)))
    }

    @Test
    fun a_launch_45_minutes_from_now_counts_and_one_46_minutes_away_does_not() {
        val edge = listOf(launch("tv.show", 2, 20, 45), launch("tv.show", 3, 22, 15), launch("tv.show", 4, 20, 45))

        assertEquals("tv.show", habit(edge + launch("tv.show", 2, 21)))
        assertNull(habit(edge + launch("tv.show", 2, 22, 16)))
    }

    @Test
    fun the_window_wraps_over_midnight_in_both_directions() {
        val late =
            listOf(launch("tv.show", 2, 23, 40), launch("tv.show", 3, 0, 10), launch("tv.show", 4, 0, 20), launch("tv.show", 5, 23, 50))

        assertEquals("tv.show", habit(late, nowMillis = at(day = 5, hour = 23, minute = 55)))
        assertEquals("tv.show", habit(late, nowMillis = at(day = 6, hour = 0, minute = 5)))
        assertNull(habit(late, nowMillis = at(day = 6, hour = 0, minute = 50)))
    }

    @Test
    fun launches_older_than_eight_weeks_are_ignored() {
        val old = LaunchRecord("tv.show", now - HABIT_KEEP_MS)
        val recent = fourOnThreeDates.drop(1)

        assertNull(habit(recent + old))
        assertEquals("tv.show", habit(recent + old.copy(atMillis = old.atMillis + 1)))
    }

    @Test
    fun the_kind_of_day_and_the_time_of_day_are_the_local_ones() {
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

        assertEquals("tv.show", habitApp(records, utc(27, 23, 30), madrid))
        assertNull(habitApp(records, utc(27, 23, 30), ZoneId.of("UTC")))
    }

    @Test
    fun launches_in_the_last_weeks_still_count_and_a_log_with_no_launch_has_no_habit() {
        assertEquals("tv.show", habit(fourOnThreeDates.map { it.copy(atMillis = it.atMillis - 6 * 7 * 24 * 3_600_000L) }, nowMillis = now))
        assertNull(habit(emptyList()))
    }
}
