package dev.niltsiar.tvhome.feature.home

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
    ) = homeUiState(HomeApps(apps), now, focusedPackage, Locale.ENGLISH)

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
}
