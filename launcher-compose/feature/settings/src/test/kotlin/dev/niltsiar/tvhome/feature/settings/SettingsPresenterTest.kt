package dev.niltsiar.tvhome.feature.settings

import android.app.Application
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.config.parseTvConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The presenter over the real repository: a pick is stored, and the rows read back what the store holds. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SettingsPresenterTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private var harness: SettingsHarness? = null

    @After
    fun stop() {
        harness?.stop()
    }

    private fun settings(theme: String = "{}") = SettingsHarness(app, theme).also { harness = it }

    private class RowSpec(
        val id: SettingsRowId,
        val values: List<Any?>,
        val read: (TvConfig) -> Any?,
    )

    private val specs =
        listOf(
            RowSpec(SettingsRowId.Columns, listOf(4, 5, 6, 7)) { it.columns },
            RowSpec(SettingsRowId.RowsBelowDock, listOf(0, 1, 2, 3)) { it.rowsBelowDock },
            RowSpec(SettingsRowId.DockOpacity, listOf(null, 100, 70, 50, 30)) { it.dockOpacity },
            RowSpec(SettingsRowId.ClockFormat, listOf(ClockFormat.System, ClockFormat.Hour12, ClockFormat.Hour24)) { it.clockFormat },
            RowSpec(SettingsRowId.Animations, onOff) { it.animations },
            RowSpec(SettingsRowId.AlwaysShowNames, onOff) { it.alwaysShowNames },
            RowSpec(SettingsRowId.ShowGreeting, onOff) { it.showGreeting },
            RowSpec(SettingsRowId.DateBelowClock, onOff) { it.dateBelowClock },
            RowSpec(SettingsRowId.AppLibrary, onOff) { it.appLibrary },
            RowSpec(SettingsRowId.LibraryLeastUsed, onOff) { it.libraryLeastUsed },
            RowSpec(SettingsRowId.UnusedFadeDays, listOf(0, 30, 60, 90)) { it.unusedFadeDays },
            RowSpec(SettingsRowId.HomeIdleClockMinutes, listOf(0, 5, 10, 30, 60)) { it.homeIdleClockMinutes },
            RowSpec(SettingsRowId.BackOpensClock, onOff) { it.backOpensClock },
            RowSpec(SettingsRowId.ClockWallpaper, onOff) { it.clockWallpaper },
            RowSpec(SettingsRowId.ReducedMotion, onOff) { it.reducedMotion },
            RowSpec(SettingsRowId.FocusOvershoot, onOff) { it.focusOvershoot },
            RowSpec(SettingsRowId.HabitHint, onOff) { it.habitHint },
            RowSpec(SettingsRowId.InputArrival, onOff) { it.inputArrival },
        )

    @Test
    fun every_option_of_every_row_stores_its_own_value_in_its_own_choice() {
        val settings = settings(LAST_OPTIONS)
        assertEquals(
            specs.map { it.id },
            settings.state.sections
                .flatMap { it.rows }
                .map { it.id },
        )

        for (spec in specs) {
            for ((index, value) in spec.values.withIndex()) {
                val others = settings.selections() - spec.id
                settings.pick(spec.id, index)
                settings.awaitState { it.row(spec.id).selected == index }

                assertEquals("${spec.id} option $index", value, spec.read(settings.config))
                assertEquals("${spec.id} option $index leaves the other rows", others, settings.selections() - spec.id)
            }
            assertEquals("${spec.id} lists its options", spec.values.size, settings.row(spec.id).options.size)
        }
    }

    @Test
    fun the_rows_come_in_javas_order_then_the_groups() {
        val sections = settings().state.sections

        assertEquals(
            listOf(
                null,
                R.string.settings_group_home,
                R.string.settings_group_library,
                R.string.settings_group_clock,
                R.string.settings_group_motion,
            ),
            sections.map {
                it.heading
            },
        )
        assertEquals(
            listOf(
                listOf(
                    SettingsRowId.Columns,
                    SettingsRowId.RowsBelowDock,
                    SettingsRowId.DockOpacity,
                    SettingsRowId.ClockFormat,
                    SettingsRowId.Animations,
                ),
                listOf(SettingsRowId.AlwaysShowNames, SettingsRowId.ShowGreeting, SettingsRowId.DateBelowClock),
                listOf(SettingsRowId.AppLibrary, SettingsRowId.LibraryLeastUsed, SettingsRowId.UnusedFadeDays),
                listOf(SettingsRowId.HomeIdleClockMinutes, SettingsRowId.BackOpensClock, SettingsRowId.ClockWallpaper),
                listOf(SettingsRowId.ReducedMotion, SettingsRowId.FocusOvershoot, SettingsRowId.HabitHint, SettingsRowId.InputArrival),
            ),
            sections.map { section -> section.rows.map { it.id } },
        )
    }

    @Test
    fun a_row_selects_the_option_the_theme_holds_and_shows_none_for_a_value_it_does_not_offer() {
        val settings =
            settings("""{"columns": 6, "rows_below_dock": 2, "unused_fade_days": 45, "home_idle_clock_minutes": 7, "dock_opacity": 80}""")

        assertEquals(2, settings.row(SettingsRowId.Columns).selected)
        assertEquals(2, settings.row(SettingsRowId.RowsBelowDock).selected)
        assertEquals(-1, settings.row(SettingsRowId.UnusedFadeDays).selected)
        assertEquals(-1, settings.row(SettingsRowId.HomeIdleClockMinutes).selected)
        assertEquals(-1, settings.row(SettingsRowId.DockOpacity).selected)

        settings.pick(SettingsRowId.UnusedFadeDays, 2)
        settings.awaitState { it.row(SettingsRowId.UnusedFadeDays).selected == 2 }
        assertEquals(60, settings.config.unusedFadeDays)
    }

    @Test
    fun columns_outside_the_four_to_seven_of_the_page_show_no_selection() {
        assertEquals(-1, settings("""{"columns": 8}""").row(SettingsRowId.Columns).selected)
    }

    @Test
    fun auto_dock_opacity_is_selected_while_the_theme_sets_no_number() {
        assertEquals(0, settings().row(SettingsRowId.DockOpacity).selected)
    }

    @Test
    fun picking_auto_overrides_the_themes_dock_opacity() {
        val settings = settings("""{"dock_opacity": 80}""")
        assertEquals(-1, settings.row(SettingsRowId.DockOpacity).selected)

        settings.pick(SettingsRowId.DockOpacity, 0)
        settings.awaitState { it.row(SettingsRowId.DockOpacity).selected == 0 }

        assertEquals(null, settings.config.dockOpacity)
    }

    @Test
    fun follow_theme_is_selected_only_while_no_choice_is_stored_and_picking_it_clears_every_choice() {
        val theme = """{"columns": 5}"""
        val settings = settings(theme)
        assertTrue(settings.state.followsTheme)

        settings.pick(SettingsRowId.Columns, 3)
        settings.pick(SettingsRowId.AlwaysShowNames, 0)
        settings.awaitState { !it.followsTheme && it.row(SettingsRowId.AlwaysShowNames).selected == 0 }
        assertEquals(7, settings.config.columns)

        settings.followTheme()
        settings.awaitState { it.followsTheme }

        assertEquals(parseTvConfig(theme).config, settings.config)
        assertEquals(1, settings.row(SettingsRowId.Columns).selected)
    }

    @Test
    fun a_refused_write_is_reported_and_the_row_keeps_what_the_store_holds() {
        val settings = settings()
        settings.pick(SettingsRowId.Columns, 1)
        settings.awaitState { it.row(SettingsRowId.Columns).selected == 1 }
        val folder = File(app.filesDir, "datastore")
        folder.setWritable(false)
        try {
            assumeFalse("a root user writes anyway", folder.canWrite())

            settings.pick(SettingsRowId.Columns, 2)
            runBlocking { withTimeout(10_000) { settings.viewModel.saveFailed.first() } }

            assertEquals(1, settings.row(SettingsRowId.Columns).selected)
        } finally {
            folder.setWritable(true)
        }
    }

    private fun SettingsHarness.selections() = state.sections.flatMap { it.rows }.associate { it.id to it.selected }

    private companion object {
        val onOff = listOf(true, false)

        /** Every key at the last option of its row, so the first pick of each row differs from what the theme holds. */
        const val LAST_OPTIONS =
            """{"columns": 7, "rows_below_dock": 3, "dock_opacity": 30, "clock_format": "24", "animations": false,
            "always_show_names": false, "show_greeting": false, "date_below_clock": false, "app_library": false,
            "library_least_used": false, "unused_fade_days": 90, "home_idle_clock_minutes": 60, "back_opens_clock": false,
            "clock_wallpaper": false, "reduced_motion": false, "focus_overshoot": false, "habit_hint": false, "input_arrival": false}"""
    }
}
