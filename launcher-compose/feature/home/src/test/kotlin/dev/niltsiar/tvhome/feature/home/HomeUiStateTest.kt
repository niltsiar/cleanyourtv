package dev.niltsiar.tvhome.feature.home

import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class HomeUiStateTest {
    private fun state(
        apps: List<AppEntry> = NineApps,
        now: LocalDateTime? = Evening,
        focusedPackage: String? = null,
        header: HeaderStyle = HeaderStyle(),
    ) = homeUiState(homeApps(apps), now, focusedPackage, Locale.ENGLISH, header)

    @Test
    fun the_time_formats_as_clock_and_date() {
        val state = state()
        assertEquals("20:15", state.clock)
        assertEquals("Tuesday 3 November", state.date)
    }

    @Test
    fun no_time_yet_gives_an_empty_clock_and_date() {
        val state = state(now = null)
        assertEquals("", state.clock)
        assertEquals("", state.date)
    }

    @Test
    fun a_focused_package_gives_its_label() {
        assertEquals("App 4", state(focusedPackage = "pkg.4").focusedLabel)
    }

    @Test
    fun an_unknown_focused_package_falls_back_to_the_first_app() {
        assertEquals("App 1", state(focusedPackage = "pkg.missing").focusedLabel)
    }

    @Test
    fun nine_apps_split_into_six_in_the_dock_and_three_in_the_grid() {
        val state = state()
        assertEquals(NineApps.take(6), state.dock)
        assertEquals(NineApps.drop(6), state.grid)
    }

    @Test
    fun fewer_apps_than_the_dock_holds_all_go_to_the_dock() {
        val state = state(apps = NineApps.take(4))
        assertEquals(NineApps.take(4), state.dock)
        assertEquals(emptyList<Any>(), state.grid)
    }

    @Test
    fun no_apps_gives_an_empty_dock_grid_and_label() {
        val state = state(apps = emptyList(), focusedPackage = "pkg.1")
        assertEquals(emptyList<Any>(), state.dock)
        assertEquals(emptyList<Any>(), state.grid)
        assertEquals("", state.focusedLabel)
    }

    @Test
    fun dock_apps_found_come_first_and_the_rest_of_the_row_fills_up() {
        val apps = homeApps(docked = 2)
        assertEquals(NineApps.take(6), apps.dock)
        assertEquals(NineApps.drop(6), apps.grid)
    }

    @Test
    fun fill_dock_off_leaves_the_free_dock_slots_empty() {
        val apps = homeApps(docked = 2, config = TvConfig(columns = 6, fillDock = false))
        assertEquals(NineApps.take(2), apps.dock)
        assertEquals(NineApps.drop(2), apps.grid)
    }

    @Test
    fun fill_dock_off_with_no_dock_app_installed_still_fills_the_first_row() {
        val apps = homeApps(docked = 0, config = TvConfig(columns = 6, fillDock = false))
        assertEquals(NineApps.take(6), apps.dock)
    }

    @Test
    fun the_columns_are_the_dock_size() {
        val apps = homeApps(config = TvConfig(columns = 4))
        assertEquals(NineApps.take(4), apps.dock)
        assertEquals(NineApps.drop(4), apps.grid)
    }

    @Test
    fun a_single_row_holds_every_app_in_the_dock() {
        val apps = homeApps(config = TvConfig(columns = 4, singleRow = true))
        assertEquals(NineApps, apps.dock)
        assertEquals(emptyList<Any>(), apps.grid)
    }

    @Test
    fun empty_dock_slots_follow_the_reference_rules() {
        assertEquals(1, emptyDockSlots(docked = 3, dockSize = 4, tiles = 20, fill = false))
        assertEquals(0, emptyDockSlots(docked = 3, dockSize = 4, tiles = 20, fill = true))
        assertEquals(0, emptyDockSlots(docked = 4, dockSize = 4, tiles = 20, fill = false))
        assertEquals(0, emptyDockSlots(docked = 0, dockSize = 4, tiles = 20, fill = false))
        assertEquals(0, emptyDockSlots(docked = 2, dockSize = 4, tiles = 2, fill = false))
    }

    @Test
    fun the_twelve_hour_clock_has_no_leading_zero_and_an_am_pm_marker() {
        val state = state(header = HeaderStyle(hour24 = false))
        assertEquals("8:15" to "PM", state.clock to state.amPm)
        val midnight = state(now = Evening.withHour(0).withMinute(5), header = HeaderStyle(hour24 = false))
        assertEquals("12:05" to "AM", midnight.clock to midnight.amPm)
        assertEquals("", state().amPm)
    }

    @Test
    fun the_date_follows_the_pattern_from_the_resources() {
        assertEquals("Tuesday  3 November", state(header = HeaderStyle(datePattern = "EEEE  d MMMM")).date)
    }

    @Test
    fun the_greeting_names_the_part_of_the_day_and_the_configured_name() {
        assertEquals(Greeting(DayPart.Evening, "Sam"), state(header = HeaderStyle(greetingName = "Sam")).greeting)
        assertEquals(Greeting(DayPart.Evening, null), state().greeting)
    }

    @Test
    fun show_greeting_false_and_no_time_both_leave_no_greeting() {
        assertEquals(null, state(header = HeaderStyle(showGreeting = false)).greeting)
        assertEquals(null, state(now = null).greeting)
    }
}
