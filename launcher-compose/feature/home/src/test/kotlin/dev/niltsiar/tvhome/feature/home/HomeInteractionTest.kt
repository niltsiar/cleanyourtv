package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class HomeInteractionTest {
    private val events = Channel<HomeEvent>(Channel.UNLIMITED)
    private val eventFlow = events.receiveAsFlow()
    private val effects = Channel<HomeEffect>(Channel.UNLIMITED)
    private val saves = mutableListOf<Pair<List<String>, Int>>()
    private var saveWorks = true
    private val removed = mutableListOf<String>()
    private var removeWorks = true

    private fun removeFromHome(pkg: String): Boolean {
        removed += pkg
        return removeWorks
    }

    private fun saveOrder(
        keys: List<String>,
        docked: Int,
    ): Boolean {
        saves += keys to docked
        return saveWorks
    }

    private fun presenter(
        config: TvConfig = SixColumns,
        apps: MutableStateFlow<AppList> = MutableStateFlow(AppList(NineApps)),
        appInfo: Boolean = true,
        nativeSettings: Boolean = false,
        focusMemory: FocusMemory = FocusMemory(),
    ): Flow<HomeUiState> {
        val sources =
            sources(
                apps = apps,
                hasAppInfo = { appInfo },
                hasNativeSettings = { nativeSettings },
                saveOrder = ::saveOrder,
                removeFromHome = ::removeFromHome,
                focusMemory = focusMemory,
            )
        return moleculeFlow(RecompositionMode.Immediate) {
            homePresenter(eventFlow, sources, config, effects::trySend, Locale.ENGLISH, "EEEE d MMMM")
        }
    }

    private fun keys(vararg n: Int) = n.map { "pkg.$it" }

    @Test
    fun a_dock_tile_menu_replaces_and_a_grid_tile_menu_adds() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.2", held = true))
                val dock = awaitMatching { it.menu != null }.menu
                assertEquals(HomeMenu("pkg.2", listOf(MenuRow.ReplaceInDock, MenuRow.Move, MenuRow.AppInfo)), dock)

                events.send(HomeEvent.Back)
                awaitMatching { it.menu == null }

                events.send(HomeEvent.TileMenu("pkg.8", held = false))
                val grid = awaitMatching { it.menu != null }.menu
                assertEquals(HomeMenu("pkg.8", listOf(MenuRow.AddToDock, MenuRow.Move, MenuRow.AppInfo)), grid)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun with_the_app_library_the_tile_menu_removes_the_app_from_home_and_asks_for_focus_back_on_its_tile() =
        runTest {
            presenter(SixColumns.copy(appLibrary = true)).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.8", held = true))
                val menu = awaitMatching { it.menu != null }.menu
                assertEquals(listOf(MenuRow.AddToDock, MenuRow.Move, MenuRow.RemoveFromHome, MenuRow.AppInfo), menu?.rows)

                events.send(HomeEvent.MenuPick(MenuRow.RemoveFromHome))
                val closed = awaitMatching { it.menu == null }

                assertEquals(listOf("pkg.8"), removed)
                assertEquals("pkg.8", closed.focusRequest?.target)
                assertEquals(true, effects.tryReceive().isFailure)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_removal_that_was_not_saved_says_so() =
        runTest {
            removeWorks = false
            presenter(SixColumns.copy(appLibrary = true)).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.2", held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.RemoveFromHome))

                assertEquals(HomeEffect.LibrarySaveFailed, effects.receive())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun without_the_app_library_the_tile_menu_has_no_remove_row() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.8", held = true))

                assertEquals(false, awaitMatching { it.menu != null }.menu?.rows?.contains(MenuRow.RemoveFromHome))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_rows_follow_dock_row_and_the_apps_info_page() =
        runTest {
            presenter(SixColumns.copy(dockRow = false), appInfo = false).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.2", held = true))
                assertEquals(listOf(MenuRow.Move), awaitMatching { it.menu != null }.menu?.rows)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun without_tile_menu_a_held_ok_opens_app_info_and_the_menu_key_does_nothing() =
        runTest {
            presenter(SixColumns.copy(tileMenu = false)).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.2", held = false))
                events.send(HomeEvent.TileMenu("pkg.3", held = true))
                assertEquals(HomeEffect.OpenAppInfo("pkg.3"), effects.receive())
                expectNoEvents()
                assertEquals(true, effects.tryReceive().isFailure)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_settings_menu_lists_the_dock_and_reorder_rows_and_native_settings_when_installed() =
        runTest {
            presenter(nativeSettings = true).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.SettingsMenu)
                val menu = awaitMatching { it.menu != null }.menu
                assertEquals(HomeMenu(null, listOf(MenuRow.NativeSettings, MenuRow.EditDock, MenuRow.Reorder)), menu)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun back_closes_the_menu_and_asks_for_focus_on_its_tile() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.8", held = true))
                awaitMatching { it.menu != null }

                events.send(HomeEvent.Back)

                val closed = awaitMatching { it.menu == null && it.focusRequest != null }
                assertEquals("pkg.8", closed.focusRequest?.target)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun picking_app_info_opens_it_and_hands_focus_back_to_the_tile() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.8", held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.AppInfo))

                val state = awaitMatching { it.menu == null && it.focusRequest != null }

                assertEquals(HomeEffect.OpenAppInfo("pkg.8"), effects.receive())
                assertEquals("pkg.8", state.focusRequest?.target)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun move_picks_the_tile_up_and_the_arrows_carry_it_into_the_dock_and_the_order_is_saved_on_back() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.8", held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.Move))
                val started = awaitMatching { it.reorder?.carried == "pkg.8" }
                assertEquals(keys(1, 2, 3, 4, 5, 6), started.dock.map { it.key })

                events.send(HomeEvent.ReorderMove(Arrow.Up))
                val moved = awaitMatching { it.dock.getOrNull(1)?.key == "pkg.8" }
                assertEquals(keys(1, 8, 2, 3, 4, 5), moved.dock.map { it.key })
                assertEquals("pkg.8", moved.focusRequest?.target)
                assertEquals(emptyList<Pair<List<String>, Int>>(), saves)

                events.send(HomeEvent.ReorderOk)
                events.send(HomeEvent.Back)
                val done = awaitMatching { it.reorder == null }

                assertEquals(listOf(keys(1, 8, 2, 3, 4, 5, 6, 7, 9) to -1), saves)
                assertEquals(keys(1, 8, 2, 3, 4, 5), done.dock.map { it.key })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_session_that_moved_nothing_saves_nothing() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.3"))
                events.send(HomeEvent.ToggleReorder)
                awaitMatching { it.reorder != null }
                events.send(HomeEvent.ReorderOk)
                awaitMatching { it.reorder?.carried == "pkg.3" }
                events.send(HomeEvent.ReorderOk)
                awaitMatching { it.reorder?.carried == null }
                events.send(HomeEvent.Back)
                awaitMatching { it.reorder == null }

                assertEquals(emptyList<Pair<List<String>, Int>>(), saves)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_reorder_button_toggles_the_mode_and_back_ends_it_at_once_when_nothing_is_carried() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.2"))
                events.send(HomeEvent.ToggleReorder)
                val on = awaitMatching { it.reorder != null }
                assertEquals(ReorderHint.Reorder, on.reorder?.hint)
                assertEquals("pkg.2", on.focusRequest?.target)

                events.send(HomeEvent.ToggleReorder)
                assertNull(awaitMatching { it.reorder == null }.reorder)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun home_stopping_ends_the_mode_and_saves_the_order_so_far() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.2", held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.Move))
                awaitMatching { it.reorder?.carried == "pkg.2" }
                events.send(HomeEvent.ReorderMove(Arrow.Right))
                awaitMatching { it.dock.getOrNull(2)?.key == "pkg.2" }

                events.send(HomeEvent.Stopped)
                awaitMatching { it.reorder == null }

                assertEquals(listOf(keys(1, 3, 2, 4, 5, 6, 7, 8, 9) to -1), saves)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_home_key_closes_an_open_tile_menu() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.2", held = true))
                awaitMatching { it.menu != null }

                events.send(HomeEvent.HomePressed)

                assertNull(awaitMatching { it.menu == null }.menu)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_home_key_ends_a_reorder_that_moved_nothing_without_saving() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.2", held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.Move))
                awaitMatching { it.reorder?.carried == "pkg.2" }

                events.send(HomeEvent.HomePressed)

                assertNull(awaitMatching { it.reorder == null }.reorder)
                assertEquals(emptyList<Pair<List<String>, Int>>(), saves)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_home_key_ends_a_reorder_and_keeps_the_order_so_far() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.2", held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.Move))
                awaitMatching { it.reorder?.carried == "pkg.2" }
                events.send(HomeEvent.ReorderMove(Arrow.Right))
                awaitMatching { it.dock.getOrNull(2)?.key == "pkg.2" }

                events.send(HomeEvent.HomePressed)

                assertNull(awaitMatching { it.reorder == null }.reorder)
                assertEquals(listOf(keys(1, 3, 2, 4, 5, 6, 7, 8, 9) to -1), saves)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun an_app_installed_during_the_mode_ends_it_and_keeps_the_order_so_far() =
        runTest {
            val apps = MutableStateFlow(AppList(NineApps))
            presenter(apps = apps).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.2", held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.Move))
                awaitMatching { it.reorder?.carried == "pkg.2" }
                events.send(HomeEvent.ReorderMove(Arrow.Right))
                awaitMatching { it.dock.getOrNull(2)?.key == "pkg.2" }

                apps.value = AppList(NineApps.drop(1) + NineApps.first().copy(key = "pkg.10", packageName = "pkg.10"))
                awaitMatching { it.reorder == null }

                assertEquals(listOf(keys(1, 3, 2, 4, 5, 6, 7, 8, 9) to -1), saves)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_failed_write_is_reported_and_the_new_order_stays_on_screen() =
        runTest {
            saveWorks = false
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.2", held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.Move))
                awaitMatching { it.reorder?.carried == "pkg.2" }
                events.send(HomeEvent.ReorderMove(Arrow.Right))
                events.send(HomeEvent.ReorderOk)
                events.send(HomeEvent.Back)

                val done = awaitMatching { it.reorder == null && it.dock.getOrNull(2)?.key == "pkg.2" }

                assertEquals(HomeEffect.OrderNotSaved, effects.receive())
                assertNotNull(done)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun with_fill_dock_off_the_saved_dock_size_leaves_the_free_slots_after_the_dock() =
        runTest {
            val config = SixColumns.copy(fillDock = false)
            presenter(config, MutableStateFlow(AppList(NineApps, docked = 3))).test {
                val before = awaitMatching { it.dock.isNotEmpty() }
                assertEquals(keys(1, 2, 3), before.dock.map { it.key })

                events.send(HomeEvent.TileMenu("pkg.7", held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.Move))
                val session = awaitMatching { it.reorder != null }
                assertEquals(keys(1, 2, 3, 4, 5, 6), session.dock.map { it.key })

                events.send(HomeEvent.ReorderMove(Arrow.Up))
                awaitMatching { it.dock.contains(NineApps[6]) }
                events.send(HomeEvent.ReorderOk)
                events.send(HomeEvent.Back)
                val done = awaitMatching { it.reorder == null }

                assertEquals(listOf(keys(7, 1, 2, 3, 4, 5, 6, 8, 9) to 4), saves)
                assertEquals(keys(7, 1, 2, 3), done.dock.map { it.key })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun add_to_dock_asks_for_a_place_then_swaps_the_app_into_it_and_saves() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.8", held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.AddToDock))
                val choosing = awaitMatching { it.reorder != null }
                assertEquals(ReorderHint.DockPlaceForApp, choosing.reorder?.hint)
                assertEquals("pkg.1", choosing.focusRequest?.target)

                events.send(HomeEvent.Focus("pkg.3"))
                events.send(HomeEvent.ReorderOk)
                val done = awaitMatching { it.reorder == null }

                assertEquals(listOf(keys(1, 2, 8, 4, 5, 6, 7, 3, 9) to -1), saves)
                assertEquals(keys(1, 2, 8, 4, 5, 6), done.dock.map { it.key })
                assertEquals("pkg.8", done.focusRequest?.target)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun replace_in_dock_keeps_the_place_then_takes_the_app_chosen_in_the_grid() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.2", held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.ReplaceInDock))
                val choosing = awaitMatching { it.reorder != null }
                assertEquals(ReorderHint.DockChooseApp, choosing.reorder?.hint)
                assertEquals(setOf("pkg.1", "pkg.3", "pkg.4", "pkg.5", "pkg.6"), choosing.reorder?.dimmed)

                events.send(HomeEvent.Focus("pkg.9"))
                events.send(HomeEvent.ReorderOk)
                val done = awaitMatching { it.reorder == null }

                assertEquals(listOf(keys(1, 9, 3, 4, 5, 6, 7, 8, 2) to -1), saves)
                assertEquals(keys(1, 9, 3, 4, 5, 6), done.dock.map { it.key })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun back_in_edit_dock_steps_back_from_the_app_to_the_place_and_then_leaves_without_saving() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.SettingsMenu)
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.EditDock))
                awaitMatching { it.reorder?.hint == ReorderHint.DockChoosePlace }

                events.send(HomeEvent.Focus("pkg.2"))
                events.send(HomeEvent.ReorderOk)
                awaitMatching { it.reorder?.hint == ReorderHint.DockChooseApp }
                events.send(HomeEvent.Back)
                val back = awaitMatching { it.reorder?.hint == ReorderHint.DockChoosePlace }
                assertEquals("pkg.2", back.focusRequest?.target)
                events.send(HomeEvent.Back)
                awaitMatching { it.reorder == null }

                assertEquals(emptyList<Pair<List<String>, Int>>(), saves)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun with_fewer_than_two_tiles_move_starts_nothing_and_focus_goes_back_to_the_tile() =
        runTest {
            presenter(apps = MutableStateFlow(AppList(NineApps.take(1)))).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("pkg.1", held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.Move))

                val state = awaitMatching { it.menu == null && it.focusRequest != null }

                assertNull(state.reorder)
                assertEquals("pkg.1", state.focusRequest?.target)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun no_menu_opens_for_an_app_that_is_not_a_tile() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.TileMenu("gone", held = true))
                events.send(HomeEvent.Focus("pkg.4"))
                assertNull(awaitMatching { it.focusedLabel == "App 4" }.menu)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_home_created_again_returns_to_the_tile_that_had_focus() =
        runTest {
            val memory = FocusMemory()
            presenter(focusMemory = memory).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.8"))
                awaitMatching { it.focusedKey == "pkg.8" }
                cancelAndIgnoreRemainingEvents()
            }

            presenter(focusMemory = memory).test {
                assertEquals("pkg.8", awaitMatching { it.dock.isNotEmpty() }.focusedKey)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_home_created_again_without_that_app_takes_the_tile_at_its_place_or_the_last_one() =
        runTest {
            val memory = FocusMemory()
            presenter(focusMemory = memory).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.8"))
                awaitMatching { it.focusedKey == "pkg.8" }
                cancelAndIgnoreRemainingEvents()
            }

            presenter(apps = MutableStateFlow(AppList(NineApps.filterNot { it.key == "pkg.8" })), focusMemory = memory).test {
                assertEquals("pkg.9", awaitMatching { it.dock.isNotEmpty() }.focusedKey)
                cancelAndIgnoreRemainingEvents()
            }
            presenter(apps = MutableStateFlow(AppList(NineApps.take(3))), focusMemory = memory).test {
                assertEquals("pkg.3", awaitMatching { it.dock.isNotEmpty() }.focusedKey)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_home_key_sends_the_memory_back_to_the_first_tile() =
        runTest {
            val memory = FocusMemory()
            presenter(focusMemory = memory).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.8"))
                awaitMatching { it.focusedKey == "pkg.8" }

                events.send(HomeEvent.HomePressed)

                assertEquals("pkg.1", awaitMatching { it.focusedKey == "pkg.1" }.focusedKey)
                cancelAndIgnoreRemainingEvents()
            }

            presenter(focusMemory = memory).test {
                assertEquals("pkg.1", awaitMatching { it.dock.isNotEmpty() }.focusedKey)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun home_stopping_keeps_the_memory() =
        runTest {
            val memory = FocusMemory()
            presenter(focusMemory = memory).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.8"))
                awaitMatching { it.focusedKey == "pkg.8" }
                events.send(HomeEvent.Stopped)
                cancelAndIgnoreRemainingEvents()
            }

            presenter(focusMemory = memory).test {
                assertEquals("pkg.8", awaitMatching { it.dock.isNotEmpty() }.focusedKey)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
