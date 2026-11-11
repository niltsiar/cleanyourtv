package dev.niltsiar.tvhome.feature.settings

import android.app.Application
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.config.parseTvConfig
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

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

    private fun settings(
        theme: String = "{}",
        apps: List<OpenOnStartApp> = emptyList(),
        appsGate: Deferred<Unit>? = null,
        wallpapers: List<String> = emptyList(),
    ) = SettingsHarness(app, theme, apps = apps, appsGate = appsGate, wallpapers = wallpapers).also { harness = it }

    private val withChoice = """{"open_on_start_choice": true}"""
    private val apps =
        listOf(OpenOnStartApp("Alpha", "com.alpha"), OpenOnStartApp("Beta", "com.beta"), OpenOnStartApp("Gamma", "com.gamma"))

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
        settings.awaitState { it.followsTheme && it.row(SettingsRowId.Columns).selected == 1 }

        assertEquals(parseTvConfig(theme).config, settings.config)
    }

    @Test
    fun picking_the_option_a_row_shows_stores_nothing_in_any_row() {
        val settings = settings("""{"columns": 5}""")
        val shown = settings.selections()
        assertEquals(specs.size, shown.values.count { it >= 0 })

        for ((id, option) in shown) settings.pick(id, option)

        assertTrue(settings.staysFollowingTheme())
        assertTrue(settings.state.followsTheme)
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
            settings.awaitSaveFailed()

            assertEquals(1, settings.row(SettingsRowId.Columns).selected)
        } finally {
            folder.setWritable(true)
        }
    }

    @Test
    fun the_reorder_row_is_offered_with_launcher_settings_button() {
        assertEquals(true, settings("""{"launcher_settings_button": true}""").state.reorder)
    }

    @Test
    fun the_reorder_row_is_not_offered_without_it() {
        assertEquals(false, settings().state.reorder)
    }

    @Test
    fun the_open_on_start_row_is_not_offered_without_its_choice_key() {
        assertNull(settings().state.openOnStart)
    }

    @Test
    fun the_open_on_start_row_reads_off_while_nothing_is_picked() {
        assertEquals(OptionLabel.Res(R.string.settings_off), settings(withChoice).state.openOnStart)
    }

    @Test
    fun the_open_on_start_row_is_empty_until_the_label_of_the_picked_app_has_loaded() {
        val gate = CompletableDeferred<Unit>()
        val settings = settings(withChoice, apps, appsGate = gate)

        settings.storePick("com.beta")
        settings.awaitState { it.openOnStart == OptionLabel.Text("") }
        assertEquals(OptionLabel.Text(""), settings.state.openOnStart)

        gate.complete(Unit)
        settings.awaitState { it.openOnStart == OptionLabel.Text("Beta") }
    }

    @Test
    fun the_open_on_start_row_shows_the_label_of_the_picked_app_its_package_when_it_is_gone_and_off_once_cleared() {
        val settings = settings(withChoice, apps)

        settings.storePick("com.beta")
        settings.awaitState { it.openOnStart == OptionLabel.Text("Beta") }

        settings.storePick("com.gone")
        settings.awaitState { it.openOnStart == OptionLabel.Text("com.gone") }

        runBlocking { settings.repository.clear(Choices.OpenOnStart) }
        settings.awaitState { it.openOnStart == OptionLabel.Res(R.string.settings_off) }
    }

    @Test
    fun the_picker_opens_on_the_apps_with_the_current_pick_selected_and_off_when_the_pick_is_not_among_them() {
        val settings = settings(withChoice, apps)
        assertNull(settings.state.picker)

        settings.viewModel.actions.onOpenPicker()
        assertEquals(OpenOnStartPicker(apps, 0), settings.awaitState { it.picker != null }.picker)

        settings.viewModel.actions.onClosePicker()
        settings.awaitState { it.picker == null }
        settings.storePick("com.beta")
        settings.viewModel.actions.onOpenPicker()
        assertEquals(OpenOnStartPicker(apps, 2), settings.awaitState { it.picker != null }.picker)

        settings.viewModel.actions.onClosePicker()
        settings.awaitState { it.picker == null }
        settings.storePick("com.gone")
        settings.viewModel.actions.onOpenPicker()
        assertEquals(OpenOnStartPicker(apps, 0), settings.awaitState { it.picker != null }.picker)
    }

    @Test
    fun closing_the_picker_changes_nothing() {
        val settings = settings(withChoice, apps)
        settings.storePick("com.alpha")
        settings.viewModel.actions.onOpenPicker()
        settings.awaitState { it.picker != null }

        settings.viewModel.actions.onClosePicker()
        settings.awaitState { it.picker == null }

        assertEquals("com.alpha", settings.pickedApp)
        assertEquals(emptyList<String?>(), settings.skippedAt)
    }

    @Test
    fun picking_an_app_skips_this_boot_before_it_stores_the_pick_and_closes_the_picker() {
        val settings = settings(withChoice, apps)
        settings.viewModel.actions.onOpenPicker()
        settings.awaitState { it.picker != null }

        settings.viewModel.actions.onPickStartApp(3)
        val shown =
            settings.awaitState { it.picker == null && !it.followsTheme && it.openOnStart == OptionLabel.Text("Gamma") }

        assertEquals(OptionLabel.Text("Gamma"), shown.openOnStart)
        assertEquals("com.gamma", settings.pickedApp)
        assertEquals(listOf<String?>(null), settings.skippedAt)
    }

    @Test
    fun picking_off_removes_the_pick_and_skips_nothing() {
        val settings = settings(withChoice, apps)
        settings.storePick("com.beta")
        settings.awaitState { !it.followsTheme }
        settings.viewModel.actions.onOpenPicker()
        settings.awaitState { it.picker != null }

        settings.viewModel.actions.onPickStartApp(0)
        settings.awaitState { it.picker == null && it.followsTheme }

        assertNull(settings.pickedApp)
        assertEquals(OptionLabel.Res(R.string.settings_off), settings.state.openOnStart)
        assertEquals(emptyList<String?>(), settings.skippedAt)
    }

    @Test
    fun a_pick_with_no_picker_open_changes_nothing() {
        val settings = settings(withChoice, apps)
        settings.storePick("com.beta")
        settings.awaitState { !it.followsTheme }

        settings.viewModel.actions.onPickStartApp(0)
        settings.pick(SettingsRowId.Columns, 0)
        settings.awaitState { it.row(SettingsRowId.Columns).selected == 0 }

        assertEquals("com.beta", settings.pickedApp)
    }

    @Test
    fun follow_theme_clears_the_open_on_start_pick() {
        val settings = settings(withChoice, apps)
        settings.storePick("com.beta")
        settings.awaitState { it.openOnStart == OptionLabel.Text("Beta") }

        settings.followTheme()
        settings.awaitState { it.followsTheme && it.openOnStart == OptionLabel.Res(R.string.settings_off) }

        assertNull(settings.pickedApp)
    }

    private fun SettingsHarness.openWallpaperPicker(): WallpaperPicker {
        viewModel.actions.onOpenWallpaperPicker()
        return awaitState { it.wallpaperPicker != null }.wallpaperPicker!!
    }

    @Test
    fun the_wallpaper_row_is_offered_for_a_theme_with_pictures_and_reads_theme_default_until_a_choice_is_stored() {
        val settings = settings(wallpapers = pictures)

        assertEquals(
            OptionLabel.Res(R.string.wallpaper_theme_default),
            settings.awaitState { it.wallpaper != null }.wallpaper,
        )
    }

    @Test
    fun the_wallpaper_row_is_not_offered_for_a_theme_without_pictures() {
        assertNull(settings().state.wallpaper)
    }

    @Test
    fun the_wallpaper_picker_does_not_open_for_a_theme_without_pictures() {
        val settings = settings()
        settings.pick(SettingsRowId.Columns, 0)
        settings.viewModel.actions.onOpenWallpaperPicker()
        settings.awaitState { it.row(SettingsRowId.Columns).selected == 0 }

        assertNull(settings.state.wallpaperPicker)
    }

    @Test
    fun the_wallpaper_row_names_the_picture_without_its_extension_none_or_theme_default() {
        val settings = settings(wallpapers = pictures)
        settings.awaitState { it.wallpaper != null }

        settings.storeWallpaper("dusk.jpg")
        settings.awaitState { it.wallpaper == OptionLabel.Text("Dusk") }

        settings.storeWallpaper("none")
        settings.awaitState { it.wallpaper == OptionLabel.Res(R.string.wallpaper_none) }

        settings.storeWallpaper("gone.jpg")
        settings.awaitState { it.wallpaper == OptionLabel.Res(R.string.wallpaper_theme_default) }
    }

    @Test
    fun the_wallpaper_picker_lists_theme_default_the_pictures_and_none_with_the_current_choice_selected() {
        val settings = settings(wallpapers = pictures)
        settings.awaitState { it.wallpaper != null }
        val entries =
            listOf(
                OptionLabel.Res(R.string.wallpaper_theme_default),
                OptionLabel.Text("Wallpaper"),
                OptionLabel.Text("Dusk"),
                OptionLabel.Text("Dawn"),
                OptionLabel.Res(R.string.wallpaper_none),
            )

        assertEquals(WallpaperPicker(entries, 0), settings.openWallpaperPicker())

        settings.viewModel.actions.onClosePicker()
        settings.awaitState { it.wallpaperPicker == null }
        settings.storeWallpaper("dusk.jpg")
        settings.awaitState { it.wallpaper == OptionLabel.Text("Dusk") }
        assertEquals(WallpaperPicker(entries, 2), settings.openWallpaperPicker())

        settings.viewModel.actions.onClosePicker()
        settings.awaitState { it.wallpaperPicker == null }
        settings.storeWallpaper("none")
        settings.awaitState { it.wallpaper == OptionLabel.Res(R.string.wallpaper_none) }
        assertEquals(WallpaperPicker(entries, 4), settings.openWallpaperPicker())

        settings.viewModel.actions.onClosePicker()
        settings.awaitState { it.wallpaperPicker == null }
        settings.storeWallpaper("gone.jpg")
        settings.awaitState { it.wallpaper == OptionLabel.Res(R.string.wallpaper_theme_default) }
        assertEquals(WallpaperPicker(entries, 0), settings.openWallpaperPicker())
    }

    @Test
    fun a_wallpaper_pick_stores_the_file_name_or_none_closes_the_picker_and_reports_it_applied() {
        val settings = settings(wallpapers = pictures)
        settings.awaitState { it.wallpaper != null }
        settings.openWallpaperPicker()

        settings.viewModel.actions.onPickWallpaper(3)
        settings.awaitState { it.wallpaperPicker == null && !it.followsTheme && it.wallpaper == OptionLabel.Text("Dawn") }
        assertEquals("dawn.jpg", settings.wallpaperChoice)
        settings.awaitApplied()

        settings.openWallpaperPicker()
        settings.viewModel.actions.onPickWallpaper(4)
        settings.awaitState { it.wallpaperPicker == null && it.wallpaper == OptionLabel.Res(R.string.wallpaper_none) }
        assertEquals("none", settings.wallpaperChoice)
        settings.awaitApplied()
        assertTrue(settings.noMoreApplied())
    }

    @Test
    fun theme_default_removes_the_choice_and_reports_nothing() {
        val settings = settings(wallpapers = pictures)
        settings.storeWallpaper("dusk.jpg")
        settings.awaitState { it.wallpaper == OptionLabel.Text("Dusk") }
        settings.openWallpaperPicker()

        settings.viewModel.actions.onPickWallpaper(0)
        settings.awaitState { it.wallpaperPicker == null && it.followsTheme }

        assertNull(settings.wallpaperChoice)
        assertTrue(settings.noMoreApplied())
    }

    @Test
    fun closing_the_wallpaper_picker_and_a_pick_with_no_picker_open_change_nothing() {
        val settings = settings(wallpapers = pictures)
        settings.storeWallpaper("dusk.jpg")
        settings.awaitState { it.wallpaper == OptionLabel.Text("Dusk") }
        settings.openWallpaperPicker()

        settings.viewModel.actions.onClosePicker()
        settings.awaitState { it.wallpaperPicker == null }
        settings.viewModel.actions.onPickWallpaper(4)
        settings.pick(SettingsRowId.Columns, 0)
        settings.awaitState { it.row(SettingsRowId.Columns).selected == 0 }

        assertEquals("dusk.jpg", settings.wallpaperChoice)
        assertTrue(settings.noMoreApplied())
    }

    @Test
    fun follow_theme_clears_the_wallpaper_choice() {
        val settings = settings(wallpapers = pictures)
        settings.storeWallpaper("none")
        settings.awaitState { it.wallpaper == OptionLabel.Res(R.string.wallpaper_none) }

        settings.followTheme()
        settings.awaitState { it.followsTheme && it.wallpaper == OptionLabel.Res(R.string.wallpaper_theme_default) }

        assertNull(settings.wallpaperChoice)
    }

    @Test
    fun a_refused_wallpaper_write_is_reported_as_a_failed_save_and_not_as_applied() {
        val settings = settings(wallpapers = pictures)
        settings.pick(SettingsRowId.Columns, 1)
        settings.awaitState { it.row(SettingsRowId.Columns).selected == 1 }
        settings.openWallpaperPicker()
        val folder = File(app.filesDir, "datastore")
        folder.setWritable(false)
        try {
            assumeFalse("a root user writes anyway", folder.canWrite())

            settings.viewModel.actions.onPickWallpaper(2)
            settings.awaitSaveFailed()

            assertNull(settings.wallpaperChoice)
            assertTrue(settings.noMoreApplied())
        } finally {
            folder.setWritable(true)
        }
    }

    @Test
    fun a_picture_shows_as_a_name_and_its_file_name_is_what_a_pick_stores() {
        val settings = settings(wallpapers = listOf("moon_lake.jpg", "golden-hour.png"))
        settings.awaitState { it.wallpaper != null }
        val picker = settings.openWallpaperPicker()
        assertEquals(listOf(OptionLabel.Text("Moon lake"), OptionLabel.Text("Golden hour")), picker.entries.subList(1, 3))

        settings.viewModel.actions.onPickWallpaper(1)
        settings.awaitState { it.wallpaperPicker == null && !it.followsTheme }

        assertEquals("moon_lake.jpg", settings.wallpaperChoice)
        settings.awaitState { it.wallpaper == OptionLabel.Text("Moon lake") }
    }

    @Test
    fun picture_names_lose_the_extension_and_separators_and_start_upper_case_unless_two_come_out_the_same_or_blank() {
        assertEquals(listOf("A", "B"), wallpaperLabels(listOf("a.jpg", "b.png")))
        assertEquals(listOf("Moon lake", "Golden hour"), wallpaperLabels(listOf("moon_lake.jpg", "golden-hour.png")))
        assertEquals(listOf("A", "B"), wallpaperLabels(listOf("a.jpg", "b")))
        assertEquals(listOf("My.dusk"), wallpaperLabels(listOf("my.dusk.jpg")))
        assertEquals(listOf("İzmir"), wallpaperLabels(listOf("izmir.jpg"), Locale.forLanguageTag("tr")))
        assertEquals(listOf("a.jpg", "a.png"), wallpaperLabels(listOf("a.jpg", "a.png")))
        assertEquals(listOf("moon_lake.jpg", "moon-lake.jpg"), wallpaperLabels(listOf("moon_lake.jpg", "moon-lake.jpg")))
        assertEquals(listOf("a.jpg", ".png"), wallpaperLabels(listOf("a.jpg", ".png")))
    }

    private fun SettingsHarness.storeWallpaper(value: String) = runBlocking { repository.set(Choices.WallpaperChoice, value) }

    private fun SettingsHarness.selections() = state.sections.flatMap { it.rows }.associate { it.id to it.selected }

    private companion object {
        val onOff = listOf(true, false)

        /** What the theme's source lists: its single wallpaper, then its folder. */
        val pictures = listOf("wallpaper.jpg", "dusk.jpg", "dawn.jpg")

        /** Every key at the last option of its row, so the first pick of each row differs from what the theme holds. */
        const val LAST_OPTIONS =
            """{"columns": 7, "rows_below_dock": 3, "dock_opacity": 30, "clock_format": "24", "animations": false,
            "always_show_names": false, "show_greeting": false, "date_below_clock": false, "app_library": false,
            "library_least_used": false, "unused_fade_days": 90, "home_idle_clock_minutes": 60, "back_opens_clock": false,
            "clock_wallpaper": false, "reduced_motion": false, "focus_overshoot": false, "habit_hint": false, "input_arrival": false}"""
    }
}
