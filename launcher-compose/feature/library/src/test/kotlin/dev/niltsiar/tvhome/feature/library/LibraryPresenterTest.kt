package dev.niltsiar.tvhome.feature.library

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LibraryPresenterTest {
    private val events = Channel<LibraryEvent>(Channel.UNLIMITED)
    private val effects = Channel<LibraryEffect>(Channel.UNLIMITED)

    private val catalogue =
        listOf(
            libraryApp("Zeta"),
            libraryApp("Home", onHome = true),
            libraryApp("Alpha"),
            libraryApp("Fresh", installedAt = NOW - DAY),
            libraryApp("Beta"),
        )

    private fun presenter(
        library: FakeLibrary,
        config: TvConfig = TvConfig(appLibrary = true),
    ): Flow<LibraryUiState> =
        moleculeFlow(RecompositionMode.Immediate) {
            libraryPresenter(events.receiveAsFlow(), library.sources, config, effects::trySend, Locale.ENGLISH)
        }

    private fun LibraryUiState.labels() = cards.map { it.app.label }

    @Test
    fun nothing_shows_until_the_library_is_asked_to_open_and_its_catalogue_loaded() =
        runTest {
            val library = FakeLibrary(catalogue).apply { gate = CompletableDeferred() }
            presenter(library).test {
                assertEquals(LibraryUiState(), awaitItem())
                events.send(LibraryEvent.Open)
                val opening = awaitMatching { it.open }
                assertFalse(opening.shown)
                assertEquals(emptyList<LibraryCard>(), opening.cards)

                library.gate?.complete(Unit)
                val shown = awaitMatching { it.shown }

                assertEquals(listOf("Fresh", "Alpha", "Beta", "Zeta", "Home"), shown.labels())
                assertEquals(listOf(true, false, false, false, false), shown.cards.map { it.recent })
                assertEquals(listOf(false, false, false, false, true), shown.cards.map { it.onHome })
                assertEquals(key("Fresh"), shown.focusedKey)
                assertEquals(key("Fresh"), shown.focusRequest?.key)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_package_change_while_the_catalogue_loads_still_shows_the_panel_once() =
        runTest {
            val library = FakeLibrary(catalogue).apply { gate = CompletableDeferred() }
            presenter(library).test {
                events.send(LibraryEvent.Open)
                awaitMatching { it.open }
                runCurrent()
                library.apps = catalogue + libraryApp("Gamma")
                library.changes.emit(1)
                runCurrent()
                library.gate?.complete(Unit)

                val first = awaitMatching { it.shown }
                library.apps = catalogue
                library.changes.emit(2)
                val later = awaitMatching { it.cards.size == 5 }

                assertEquals(6, first.cards.size)
                assertTrue(later.shown)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_refresh_after_the_panel_shows_keeps_focus_on_the_same_app_and_the_menu_closes() =
        runTest {
            val library = FakeLibrary(catalogue)
            library.appInfoFor = setOf("pkg.Beta")
            presenter(library).test {
                events.send(LibraryEvent.Open)
                awaitMatching { it.shown }
                events.send(LibraryEvent.Focus(key("Beta")))
                events.send(LibraryEvent.Hold(key("Beta")))
                awaitMatching { it.menu != null }
                runCurrent()

                library.apps = catalogue + libraryApp("Aaa")
                library.changes.emit(1)
                val refreshed = awaitMatching { it.cards.size == 6 }

                assertEquals(null, refreshed.menu)
                assertEquals(key("Beta"), refreshed.focusedKey)
                assertEquals(key("Beta"), refreshed.focusRequest?.key)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun ok_opens_an_app_on_home_and_closes_the_library() =
        runTest {
            val library = FakeLibrary(catalogue)
            presenter(library).test {
                events.send(LibraryEvent.Open)
                awaitMatching { it.shown }

                events.send(LibraryEvent.Click(key("Home")))
                val closed = awaitMatching { !it.open }

                assertEquals(LibraryEffect.LaunchApp(catalogue[1].app), effects.receive())
                assertEquals(emptyList<LibraryCard>(), closed.cards)
                assertEquals(1, library.released)
                assertEquals(emptyList<Pair<String, Boolean>>(), library.saved)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun ok_on_an_app_not_on_home_adds_it_and_focus_moves_to_the_next_app_that_is_not() =
        runTest {
            val library = FakeLibrary(catalogue)
            presenter(library).test {
                events.send(LibraryEvent.Open)
                awaitMatching { it.shown }
                events.send(LibraryEvent.Focus(key("Alpha")))

                events.send(LibraryEvent.Click(key("Alpha")))
                val added = awaitMatching { it.cards.firstOrNull { card -> card.app.label == "Alpha" }?.onHome == true }

                assertEquals(listOf("pkg.Alpha" to true), library.saved)
                assertEquals(listOf("Fresh", "Beta", "Zeta", "Alpha", "Home"), added.labels())
                assertEquals(key("Beta"), added.focusedKey)
                assertEquals(key("Beta"), added.focusRequest?.key)
                assertEquals(true, effects.tryReceive().isFailure)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_choice_that_was_not_saved_says_so_and_leaves_the_apps_as_they_were() =
        runTest {
            val library = FakeLibrary(catalogue).apply { saveWorks = false }
            presenter(library).test {
                events.send(LibraryEvent.Open)
                val shown = awaitMatching { it.shown }

                events.send(LibraryEvent.Click(key("Fresh")))

                assertEquals(LibraryEffect.SaveFailed, effects.receive())
                val after = awaitMatching { it.focusRequest?.token != shown.focusRequest?.token }
                assertEquals(listOf("pkg.Fresh" to true), library.saved)
                assertEquals(shown.cards, after.cards)
                assertEquals(key("Fresh"), after.focusRequest?.key)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun library_ok_opens_makes_ok_open_every_app() =
        runTest {
            val library = FakeLibrary(catalogue)
            presenter(library, TvConfig(appLibrary = true, libraryOkOpens = true)).test {
                events.send(LibraryEvent.Open)
                val shown = awaitMatching { it.shown }
                assertEquals(LibraryHint.Open, shown.hint)

                events.send(LibraryEvent.Click(key("Zeta")))

                assertEquals(LibraryEffect.LaunchApp(catalogue[0].app), effects.receive())
                assertEquals(emptyList<Pair<String, Boolean>>(), library.saved)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun selection_mode_lists_apps_on_home_first_toggles_with_ok_and_has_no_menu() =
        runTest {
            val library = FakeLibrary(catalogue)
            presenter(library, TvConfig(appLibrary = true, appLibrarySelection = true, libraryOkOpens = true)).test {
                events.send(LibraryEvent.Open)
                val shown = awaitMatching { it.shown }
                assertEquals(listOf("Home", "Alpha", "Beta", "Fresh", "Zeta"), shown.labels())
                assertEquals(listOf(false, false, false, false, false), shown.cards.map { it.recent })
                assertEquals(LibraryHint.SelectRemove, shown.hint)

                events.send(LibraryEvent.Hold(key("Home")))
                events.send(LibraryEvent.Click(key("Home")))
                val removed =
                    awaitMatching {
                        it.cards.firstOrNull { card -> card.app.label == "Home" } == null ||
                            it.focusedKey == key("Alpha")
                    }

                assertEquals(listOf("pkg.Home" to false), library.saved)
                assertEquals(null, removed.menu)
                assertEquals(key("Alpha"), removed.focusRequest?.key)
                assertEquals(true, effects.tryReceive().isFailure)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun selection_mode_keeps_focus_on_an_app_it_adds_as_the_app_moves_to_the_front() =
        runTest {
            val library = FakeLibrary(catalogue)
            presenter(library, TvConfig(appLibrary = true, appLibrarySelection = true)).test {
                events.send(LibraryEvent.Open)
                awaitMatching { it.shown }

                events.send(LibraryEvent.Click(key("Zeta")))
                val added = awaitMatching { it.cards.firstOrNull { card -> card.app.label == "Zeta" }?.onHome == true }

                assertEquals(listOf("Home", "Zeta", "Alpha", "Beta", "Fresh"), added.labels())
                assertEquals(key("Zeta"), added.focusRequest?.key)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_held_ok_opens_a_menu_by_whether_the_app_is_on_home_and_whether_the_tv_has_an_info_page() =
        runTest {
            val library = FakeLibrary(catalogue).apply { appInfoFor = setOf("pkg.Home") }
            presenter(library).test {
                events.send(LibraryEvent.Open)
                awaitMatching { it.shown }

                events.send(LibraryEvent.Hold(key("Home")))
                assertEquals(
                    LibraryMenu(key("Home"), listOf(LibraryRow.Open, LibraryRow.RemoveFromHome, LibraryRow.AppInfo)),
                    awaitMatching { it.menu != null }.menu,
                )
                events.send(LibraryEvent.Back)
                awaitMatching { it.menu == null }

                events.send(LibraryEvent.Hold(key("Alpha")))
                assertEquals(
                    LibraryMenu(key("Alpha"), listOf(LibraryRow.Open, LibraryRow.AddToHome)),
                    awaitMatching { it.menu != null }.menu,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun back_closes_the_menu_and_asks_for_focus_on_its_card_then_the_next_back_closes_the_library() =
        runTest {
            val library = FakeLibrary(catalogue)
            presenter(library).test {
                events.send(LibraryEvent.Open)
                awaitMatching { it.shown }
                events.send(LibraryEvent.Hold(key("Beta")))
                awaitMatching { it.menu != null }

                events.send(LibraryEvent.Back)
                val menuClosed = awaitMatching { it.menu == null }
                assertTrue(menuClosed.open)
                assertEquals(key("Beta"), menuClosed.focusRequest?.key)

                events.send(LibraryEvent.Back)
                assertFalse(awaitMatching { !it.open }.shown)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_menu_rows_open_the_app_change_its_shortcut_or_open_its_info_page() =
        runTest {
            val library = FakeLibrary(catalogue).apply { appInfoFor = setOf("pkg.Alpha") }
            presenter(library).test {
                events.send(LibraryEvent.Open)
                awaitMatching { it.shown }

                events.send(LibraryEvent.Hold(key("Alpha")))
                awaitMatching { it.menu != null }
                events.send(LibraryEvent.Pick(LibraryRow.AddToHome))
                awaitMatching { it.cards.firstOrNull { card -> card.app.label == "Alpha" }?.onHome == true }
                assertEquals(listOf("pkg.Alpha" to true), library.saved)

                events.send(LibraryEvent.Hold(key("Alpha")))
                awaitMatching { it.menu != null }
                events.send(LibraryEvent.Pick(LibraryRow.RemoveFromHome))
                awaitMatching { it.cards.firstOrNull { card -> card.app.label == "Alpha" }?.onHome == false }
                assertEquals(listOf("pkg.Alpha" to true, "pkg.Alpha" to false), library.saved)

                events.send(LibraryEvent.Hold(key("Alpha")))
                awaitMatching { it.menu != null }
                events.send(LibraryEvent.Pick(LibraryRow.AppInfo))
                awaitMatching { !it.open }
                assertEquals(LibraryEffect.OpenAppInfo("pkg.Alpha"), effects.receive())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun closing_and_opening_again_loads_the_catalogue_again_and_shows_the_panel_again() =
        runTest {
            val library = FakeLibrary(catalogue)
            presenter(library).test {
                events.send(LibraryEvent.Open)
                awaitMatching { it.shown }
                events.send(LibraryEvent.Close)
                assertFalse(awaitMatching { !it.open }.shown)

                events.send(LibraryEvent.Open)
                val again = awaitMatching { it.shown }

                assertEquals(2, library.loads)
                assertEquals(5, again.cards.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun an_empty_catalogue_still_shows_the_panel_with_no_focus() =
        runTest {
            val library = FakeLibrary(emptyList())
            presenter(library).test {
                events.send(LibraryEvent.Open)

                val shown = awaitMatching { it.shown }

                assertEquals(emptyList<LibraryCard>(), shown.cards)
                assertEquals(null, shown.focusRequest)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
