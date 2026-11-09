package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** A choice stored on the TV reaches Home's state through the flow, with the one presenter that was there before it. */
class HomeConfigFollowTest {
    private val events = Channel<HomeEvent>(Channel.UNLIMITED)
    private val config = MutableStateFlow(TvConfig(columns = 7))
    private val memory = FocusMemory()
    private val saves = mutableListOf<Pair<List<String>, Int>>()

    private fun presenter(libraryCount: Int? = null): Flow<HomeUiState> {
        val sources =
            sources(
                focusMemory = memory,
                saveOrder = { keys, docked -> saves.add(keys to docked) },
                libraryCount = libraryCount?.let(::flowOf) ?: emptyFlow(),
            )
        return moleculeFlow(RecompositionMode.Immediate) {
            homePresenter(events.receiveAsFlow(), sources, config, {}, Locale.ENGLISH, "EEEE d MMMM")
        }
    }

    @Test
    fun the_columns_resize_the_dock_and_the_grid() =
        runTest {
            presenter().test {
                assertEquals(7, awaitMatching { it.dock.isNotEmpty() }.dock.size)

                config.value = config.value.copy(columns = 4)

                val state = awaitMatching { it.dock.size == 4 }
                assertEquals(5, state.grid.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_clock_format_changes_the_clock_text() =
        runTest {
            presenter().test {
                assertEquals("20:15", awaitMatching { it.clock.isNotEmpty() }.clock)

                config.value = config.value.copy(clockFormat = ClockFormat.Hour12)

                val state = awaitMatching { it.amPm.isNotEmpty() }
                assertEquals("8:15", state.clock)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_greeting_follows_show_greeting() =
        runTest {
            presenter().test {
                assertNotNull(awaitMatching { it.greeting != null }.greeting)

                config.value = config.value.copy(showGreeting = false)

                assertNull(awaitMatching { it.greeting == null }.greeting)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_library_count_shows_only_while_the_app_library_is_on() =
        runTest {
            config.value = config.value.copy(appLibrary = true)
            presenter(libraryCount = 12).test {
                assertEquals(12, awaitMatching { it.libraryCount != null }.libraryCount)

                config.value = config.value.copy(appLibrary = false)

                assertNull(awaitMatching { it.libraryCount == null }.libraryCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun an_unrelated_choice_asks_for_no_focus_and_keeps_the_lists() =
        runTest {
            presenter().test {
                events.send(HomeEvent.Focus("pkg.7"))
                val before = awaitMatching { it.focusedKey == "pkg.7" }

                config.value = config.value.copy(clockFormat = ClockFormat.Hour12)

                val after = awaitMatching { it.amPm.isNotEmpty() }
                assertNull(after.focusRequest)
                assertEquals(before.dock, after.dock)
                assertEquals(before.grid, after.grid)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun fewer_columns_with_focus_on_the_seventh_tile_land_focus_on_that_tile_in_the_grid() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.7"))
                awaitMatching { it.focusedKey == "pkg.7" }
                assertEquals(6, memory.slot)

                config.value = config.value.copy(columns = 4)

                val state = awaitMatching { it.dock.size == 4 && it.focusRequest != null }
                assertEquals("pkg.7", state.focusedKey)
                assertTrue(state.grid.any { it.key == "pkg.7" })
                assertEquals("pkg.7", state.focusRequest?.target)
                assertTrue(memory.slot in NineApps.indices)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun fewer_columns_end_a_reorder_that_is_on_and_keep_its_order_and_focus() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.7"))
                awaitMatching { it.focusedKey == "pkg.7" }
                events.send(HomeEvent.ToggleReorder)
                awaitMatching { it.reorder != null }

                config.value = config.value.copy(columns = 4)

                val state = awaitMatching { it.dock.size == 4 && it.reorder == null }
                assertEquals("pkg.7", state.focusedKey)
                assertEquals("pkg.7", state.focusRequest?.target)
                assertEquals(emptyList<Pair<List<String>, Int>>(), saves)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun fewer_columns_end_an_edit_of_the_dock_that_is_on() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.7"))
                awaitMatching { it.focusedKey == "pkg.7" }
                events.send(HomeEvent.SettingsMenu)
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.EditDock))
                awaitMatching { it.reorder?.hint == ReorderHint.DockChoosePlace }

                config.value = config.value.copy(columns = 4)

                val state = awaitMatching { it.dock.size == 4 && it.reorder == null }
                assertEquals("pkg.7", state.focusedKey)
                assertEquals("pkg.7", state.focusRequest?.target)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun an_open_menu_closes_when_the_columns_change() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.7", held = true))
                awaitMatching { it.menu != null }

                config.value = config.value.copy(columns = 4)

                assertEquals(4, awaitMatching { it.dock.size == 4 && it.menu == null }.dock.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_choice_made_while_home_is_stopped_applies_when_it_starts() =
        runTest {
            presenter().test {
                assertEquals(7, awaitMatching { it.dock.isNotEmpty() }.dock.size)
                events.send(HomeEvent.Stopped)
                events.send(HomeEvent.Focus("pkg.2"))
                awaitMatching { it.focusedKey == "pkg.2" }

                config.value = config.value.copy(columns = 4)
                expectNoEvents()

                events.send(HomeEvent.Started)
                assertEquals(4, awaitMatching { it.dock.size == 4 }.dock.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun every_state_carries_the_config_its_dock_and_grid_are_split_for() =
        runTest {
            presenter().test {
                val states = mutableListOf(awaitMatching { it.dock.isNotEmpty() })
                events.send(HomeEvent.Stopped)
                events.send(HomeEvent.Focus("pkg.2"))
                states += awaitMatching { it.focusedKey == "pkg.2" }
                config.value = config.value.copy(columns = 4)
                events.send(HomeEvent.Focus("pkg.3"))
                states += awaitMatching { it.focusedKey == "pkg.3" }
                events.send(HomeEvent.Started)
                while (states.last().dock.size != 4) states += awaitItem()

                assertEquals(7, states.first().config.columns)
                assertEquals(7, states[2].config.columns)
                assertEquals(4, states.last().config.columns)
                states.forEach { assertEquals(it.config.columns, it.dock.size) }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun closing_launcher_settings_with_other_columns_focuses_the_settings_button_in_the_new_layout() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.7"))
                awaitMatching { it.focusedKey == "pkg.7" }
                events.send(HomeEvent.Stopped)
                config.value = config.value.copy(columns = 4)
                events.send(HomeEvent.Started)
                events.send(HomeEvent.SettingsClosed(reorder = false))

                val state = awaitMatching { it.focusRequest?.target == SETTINGS_FOCUS }

                assertEquals(4, state.dock.size)
                assertEquals(4, state.config.columns)
                assertNull(state.reorder)
                events.send(HomeEvent.Focus("pkg.2"))
                assertEquals(SETTINGS_FOCUS, awaitMatching { it.focusedKey == "pkg.2" }.focusRequest?.target)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun closing_launcher_settings_with_names_toggled_focuses_the_settings_button() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.3"))
                awaitMatching { it.focusedKey == "pkg.3" }
                events.send(HomeEvent.Stopped)
                config.value = config.value.copy(alwaysShowNames = true)
                events.send(HomeEvent.Started)
                events.send(HomeEvent.SettingsClosed(reorder = false))

                val state = awaitMatching { it.focusRequest?.target == SETTINGS_FOCUS }

                assertTrue(state.config.alwaysShowNames)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_return_from_launcher_settings_waits_for_home_to_start() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Stopped)
                config.value = config.value.copy(columns = 4)
                events.send(HomeEvent.SettingsClosed(reorder = false))
                events.send(HomeEvent.Focus("pkg.2"))

                val waiting = awaitMatching { it.focusedKey == "pkg.2" }
                assertNull(waiting.focusRequest)
                assertEquals(7, waiting.dock.size)

                events.send(HomeEvent.Started)

                val state = awaitMatching { it.focusRequest?.target == SETTINGS_FOCUS }
                assertEquals(4, state.dock.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_reorder_row_of_launcher_settings_starts_reorder_mode_in_the_new_layout_with_the_usual_reorder_focus() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.7"))
                awaitMatching { it.focusedKey == "pkg.7" }
                events.send(HomeEvent.Stopped)
                config.value = config.value.copy(columns = 4)
                events.send(HomeEvent.Started)
                events.send(HomeEvent.SettingsClosed(reorder = true))

                val state = awaitMatching { it.reorder != null }

                assertEquals(4, state.dock.size)
                assertEquals("pkg.7", state.focusRequest?.target)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
