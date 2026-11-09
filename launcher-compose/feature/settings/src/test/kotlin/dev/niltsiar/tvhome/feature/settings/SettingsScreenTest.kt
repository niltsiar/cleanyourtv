package dev.niltsiar.tvhome.feature.settings

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The screen over the real view model and repository: keys go in at the root, the choices come out of the store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class SettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: Application = RuntimeEnvironment.getApplication()
    private lateinit var settings: SettingsHarness
    private var tvSettingsOpened = 0
    private var reordered = 0

    @After
    fun stop() = settings.stop()

    private val installed = (1..12).map { OpenOnStartApp("App %02d".format(it), "com.app$it") }

    private fun show(
        theme: String = "{}",
        apps: List<OpenOnStartApp> = installed,
    ) {
        settings = SettingsHarness(app, theme, uiThread = true, apps = apps, labels = installed.associate { it.packageName to it.label })
        compose.setContent {
            TvTheme(motion = Motion(animations = false)) {
                val state = settings.viewModel.uiState.collectAsState()
                SettingsScreen(
                    { state.value },
                    settings.viewModel.actions,
                    onOpenTvSettings = { tvSettingsOpened++ },
                    onReorder = { reordered++ },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** A store write comes back from another thread to the UI thread's queue, so each check first runs what the queue holds. */
    private fun waitUntilIdle(condition: () -> Boolean) =
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun startRow() = compose.onNodeWithTag("settings:open_on_start")

    private fun entry(index: Int) = compose.onNodeWithTag("picker:$index")

    /** The page with the Open on start row, its picker open. */
    private fun showPicker(
        pick: String? = null,
        apps: List<OpenOnStartApp> = installed,
    ) {
        show(WITH_CHOICE, apps)
        pick?.let {
            settings.storePick(it)
            waitUntilIdle { settings.state.openOnStart?.value is OptionLabel.Text }
        }
        repeat(5) { press(Key.DirectionDown) }
        startRow().assertIsFocused()
        press(Key.DirectionCenter)
        waitUntilIdle { settings.state.picker != null }
        compose.waitForIdle()
    }

    private fun option(
        row: SettingsRowId,
        index: Int,
    ) = compose.onNodeWithTag("option:${row.name}:$index")

    private fun rowCount() = settings.state.sections.sumOf { it.rows.size }

    /** Down from the first row to [row]. */
    private fun goTo(row: SettingsRowId) {
        val order =
            settings.state.sections
                .flatMap { it.rows }
                .map { it.id }
        repeat(order.indexOf(row)) { press(Key.DirectionDown) }
    }

    @Test
    fun focus_starts_on_the_selected_option_of_the_first_row() {
        show("""{"columns": 6}""")

        option(SettingsRowId.Columns, 2).assertIsFocused()
        option(SettingsRowId.Columns, 2).assertIsSelected()
        option(SettingsRowId.Columns, 0).assertIsNotSelected()
    }

    @Test
    fun up_on_the_first_row_stays_on_the_screen() {
        show()

        press(Key.DirectionUp)

        option(SettingsRowId.Columns, 0).assertIsFocused()
    }

    @Test
    fun down_visits_every_row_on_its_selected_option_and_the_list_scrolls_to_keep_it_in_view() {
        show("""{"columns": 6, "dock_opacity": 50, "clock_format": "24", "show_greeting": false}""")
        val rows = settings.state.sections.flatMap { it.rows }

        for (row in rows.drop(1)) {
            press(Key.DirectionDown)

            val focused = option(row.id, row.selected.coerceAtLeast(0))
            focused.assertIsFocused()
            focused.assertIsDisplayed()
        }
        press(Key.DirectionDown)
        compose.onNodeWithTag("settings:follow_theme").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("settings:tv_settings").assertIsFocused()
        compose.onNodeWithTag("settings:tv_settings").assertIsDisplayed()
    }

    @Test
    fun down_on_the_last_row_and_up_on_the_first_stay_on_the_screen() {
        show()
        repeat(rowCount() + 1) { press(Key.DirectionDown) }
        compose.onNodeWithTag("settings:tv_settings").assertIsFocused()

        repeat(3) { press(Key.DirectionDown) }
        compose.onNodeWithTag("settings:tv_settings").assertIsFocused()
        compose.onNodeWithTag("settings:tv_settings").assertIsDisplayed()

        repeat(rowCount() + 3) { press(Key.DirectionUp) }
        option(SettingsRowId.Columns, 0).assertIsFocused()
    }

    @Test
    fun left_on_a_rows_first_option_and_right_on_its_last_stay_on_the_row() {
        show()
        val rows =
            listOf(
                SettingsRowId.Columns,
                SettingsRowId.RowsBelowDock,
                SettingsRowId.DockOpacity,
                SettingsRowId.ClockFormat,
                SettingsRowId.Animations,
            )

        rows.forEachIndexed { index, row ->
            if (index > 0) press(Key.DirectionDown)
            val options = settings.row(row).options.size

            repeat(options) { press(Key.DirectionLeft) }
            option(row, 0).assertIsFocused()
            repeat(options) { press(Key.DirectionRight) }
            option(row, options - 1).assertIsFocused()
        }
    }

    @Test
    fun right_then_left_returns_to_the_same_option() {
        show()

        press(Key.DirectionRight)
        option(SettingsRowId.Columns, 1).assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionLeft)
        option(SettingsRowId.Columns, 1).assertIsFocused()
        press(Key.DirectionLeft)
        option(SettingsRowId.Columns, 0).assertIsFocused()
    }

    @Test
    fun down_and_up_land_on_the_selected_option_of_the_next_row_whatever_the_option_focused() {
        show("""{"rows_below_dock": 2}""")
        repeat(3) { press(Key.DirectionRight) }
        option(SettingsRowId.Columns, 3).assertIsFocused()

        press(Key.DirectionDown)
        option(SettingsRowId.RowsBelowDock, 2).assertIsFocused()
        press(Key.DirectionDown)
        option(SettingsRowId.DockOpacity, 0).assertIsFocused()
        press(Key.DirectionUp)
        option(SettingsRowId.RowsBelowDock, 2).assertIsFocused()
    }

    @Test
    @Config(qualifiers = "ar-rSA-w960dp-h540dp-xhdpi")
    fun in_a_right_to_left_language_the_first_option_is_on_the_right_and_the_keys_follow_the_screen() {
        show()
        assertTrue(option(SettingsRowId.Columns, 0).getBoundsInRoot().left > option(SettingsRowId.Columns, 1).getBoundsInRoot().left)

        press(Key.DirectionRight)
        option(SettingsRowId.Columns, 0).assertIsFocused()
        press(Key.DirectionLeft)
        option(SettingsRowId.Columns, 1).assertIsFocused()
        press(Key.DirectionRight)
        option(SettingsRowId.Columns, 0).assertIsFocused()
        repeat(4) { press(Key.DirectionLeft) }
        option(SettingsRowId.Columns, 3).assertIsFocused()
    }

    @Test
    fun ok_on_an_option_stores_it_and_marks_it_selected() {
        show()

        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        waitUntilIdle { settings.row(SettingsRowId.Columns).selected == 1 }
        compose.waitForIdle()

        option(SettingsRowId.Columns, 1).assertIsSelected()
        option(SettingsRowId.Columns, 0).assertIsNotSelected()
        assertEquals(1, settings.row(SettingsRowId.Columns).selected)
    }

    @Test
    fun the_follow_theme_button_is_marked_while_nothing_is_stored_and_ok_on_it_clears_the_choices() {
        show("""{"columns": 5}""")
        compose.onNodeWithTag("settings:follow_theme").assertIsSelected()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        waitUntilIdle { !settings.state.followsTheme }
        compose.waitForIdle()
        compose.onNodeWithTag("settings:follow_theme").assertIsNotSelected()

        goTo(SettingsRowId.InputArrival)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        waitUntilIdle { settings.state.followsTheme }
        compose.waitForIdle()

        assertEquals(5, settings.config.columns)
        compose.onNodeWithTag("settings:follow_theme").assertIsSelected()
    }

    @Test
    fun ok_on_tv_system_settings_opens_them() {
        show()
        repeat(rowCount() + 1) { press(Key.DirectionDown) }

        press(Key.DirectionCenter)

        assertEquals(1, tvSettingsOpened)
    }

    @Test
    fun with_launcher_settings_button_the_reorder_row_comes_first_has_focus_at_open_and_ok_on_it_reports() {
        show("""{"launcher_settings_button": true}""")

        compose.onNodeWithTag("settings:reorder").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("settings:reorder").assertIsFocused()
        press(Key.DirectionDown)
        option(SettingsRowId.Columns, 0).assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("settings:reorder").assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(1, reordered)
    }

    @Test
    fun the_last_rows_stay_reachable_with_the_reorder_row() {
        show("""{"launcher_settings_button": true}""")

        repeat(rowCount() + 3) { press(Key.DirectionDown) }
        compose.onNodeWithTag("settings:tv_settings").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("settings:follow_theme").assertIsFocused()
    }

    @Test
    fun without_launcher_settings_button_there_is_no_reorder_row_and_focus_starts_on_the_first_option() {
        show()

        assertEquals(0, compose.onAllNodesWithTag("settings:reorder").fetchSemanticsNodes().size)
        option(SettingsRowId.Columns, 0).assertIsFocused()
    }

    @Test
    fun the_open_on_start_row_follows_animations_and_down_goes_on_to_the_next_group() {
        show(WITH_CHOICE)

        repeat(4) { press(Key.DirectionDown) }
        option(SettingsRowId.Animations, 0).assertIsFocused()
        press(Key.DirectionDown)
        startRow().assertIsFocused()
        compose.onNodeWithText("Open on start: Off").assertIsDisplayed()
        press(Key.DirectionDown)
        option(SettingsRowId.AlwaysShowNames, settings.row(SettingsRowId.AlwaysShowNames).selected).assertIsFocused()
        press(Key.DirectionUp)
        startRow().assertIsFocused()
        press(Key.DirectionUp)
        option(SettingsRowId.Animations, 0).assertIsFocused()
    }

    @Test
    fun without_the_choice_key_there_is_no_open_on_start_row() {
        show()

        assertEquals(0, compose.onAllNodesWithTag("settings:open_on_start").fetchSemanticsNodes().size)
    }

    @Test
    fun ok_on_the_row_opens_the_picker_with_off_first_and_focus_on_the_current_pick() {
        showPicker(pick = "com.app5")

        compose.onNodeWithTag("settings:picker").assertIsDisplayed()
        compose.onNodeWithText("Open on start").assertIsDisplayed()
        entry(5).assertIsFocused()
        entry(5).assertIsSelected()
        entry(5).assertIsDisplayed()
        compose.onNodeWithText("App 05").assertIsDisplayed()
        repeat(5) { press(Key.DirectionUp) }
        entry(0).assertIsFocused()
        entry(0).assertIsNotSelected()
        entry(0).assertTextEquals("Off")
    }

    @Test
    fun with_no_pick_the_picker_focuses_off() {
        showPicker()

        entry(0).assertIsFocused()
        entry(0).assertIsSelected()
        entry(0).assertTextEquals("Off")
        assertTrue(entry(0).getBoundsInRoot().top < entry(1).getBoundsInRoot().top)
        assertTrue(entry(1).getBoundsInRoot().top < entry(2).getBoundsInRoot().top)
    }

    @Test
    fun keys_from_every_edge_of_the_picker_stay_inside_it() {
        showPicker()

        press(Key.DirectionUp)
        entry(0).assertIsFocused()
        press(Key.DirectionLeft)
        entry(0).assertIsFocused()
        press(Key.DirectionRight)
        entry(0).assertIsFocused()

        repeat(installed.size) { press(Key.DirectionDown) }
        entry(installed.size).assertIsFocused()
        entry(installed.size).assertIsDisplayed()
        press(Key.DirectionDown)
        entry(installed.size).assertIsFocused()
        press(Key.DirectionRight)
        entry(installed.size).assertIsFocused()
        press(Key.DirectionLeft)
        entry(installed.size).assertIsFocused()
        startRow().assertIsNotFocused()
    }

    @Test
    fun down_on_the_last_entry_of_a_short_list_does_not_reach_the_page_behind() {
        showPicker(apps = installed.take(2))

        repeat(3) { press(Key.DirectionDown) }

        entry(2).assertIsFocused()
        compose.onNodeWithTag("settings:follow_theme").assertIsNotFocused()
        compose.onNodeWithTag("settings:tv_settings").assertIsNotFocused()
    }

    @Test
    fun back_closes_the_picker_changes_nothing_and_returns_focus_to_the_row() {
        showPicker(pick = "com.app5")
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        entry(7).assertIsFocused()

        back()

        assertEquals(0, compose.onAllNodesWithTag("settings:picker").fetchSemanticsNodes().size)
        assertEquals("com.app5", settings.pickedApp)
        assertEquals(emptyList<String>(), settings.skippedAt)
        startRow().assertIsFocused()
    }

    @Test
    fun ok_on_an_app_stores_it_closes_the_picker_and_the_row_names_it() {
        showPicker()
        repeat(3) { press(Key.DirectionDown) }

        press(Key.DirectionCenter)
        waitUntilIdle { settings.pickedApp == "com.app3" && settings.state.picker == null }
        compose.waitForIdle()

        assertEquals(0, compose.onAllNodesWithTag("settings:picker").fetchSemanticsNodes().size)
        compose.onNodeWithText("Open on start: App 03").assertIsDisplayed()
        assertEquals(listOf(""), settings.skippedAt)
        startRow().assertIsFocused()
    }

    @Test
    fun ok_on_off_removes_the_pick() {
        showPicker(pick = "com.app2")
        repeat(2) { press(Key.DirectionUp) }
        entry(0).assertIsFocused()

        press(Key.DirectionCenter)
        waitUntilIdle { settings.pickedApp.isEmpty() && settings.state.picker == null }
        compose.waitForIdle()

        compose.onNodeWithText("Open on start: Off").assertIsDisplayed()
        assertEquals(emptyList<String>(), settings.skippedAt)
    }

    private companion object {
        const val WITH_CHOICE = """{"open_on_start_choice": true}"""
    }
}
