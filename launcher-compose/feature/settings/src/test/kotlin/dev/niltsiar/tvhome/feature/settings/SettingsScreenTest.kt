package dev.niltsiar.tvhome.feature.settings

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The screen over the real view model and repository: keys go in at the root, the choices come out of the store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w960dp-h540dp-xhdpi")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = RuntimeEnvironment.getApplication()
    private lateinit var settings: SettingsHarness
    private var tvSettingsOpened = 0

    @After
    fun stop() = settings.stop()

    private fun show(theme: String = "{}") {
        settings = SettingsHarness(app, theme)
        compose.setContent {
            TvTheme(motion = Motion(animations = false)) {
                val state = settings.viewModel.uiState.collectAsState()
                SettingsScreen({ state.value }, settings.viewModel.actions, onOpenTvSettings = { tvSettingsOpened++ })
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
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
        compose.waitUntil(10_000) { settings.row(SettingsRowId.Columns).selected == 1 }
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
        compose.waitUntil(10_000) { !settings.state.followsTheme }
        compose.waitForIdle()
        compose.onNodeWithTag("settings:follow_theme").assertIsNotSelected()

        goTo(SettingsRowId.InputArrival)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) { settings.state.followsTheme }
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
}
