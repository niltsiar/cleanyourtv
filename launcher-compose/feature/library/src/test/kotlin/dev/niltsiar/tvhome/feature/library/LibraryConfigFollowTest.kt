package dev.niltsiar.tvhome.feature.library

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The library follows the stored choices: it closes when app_library turns off and applies the choices that shape it. */
class LibraryConfigFollowTest {
    private val events = Channel<LibraryEvent>(Channel.UNLIMITED)
    private val config = MutableStateFlow(TvConfig(appLibrary = true))
    private val library = FakeLibrary(listOf(libraryApp("Zeta"), libraryApp("Alpha")))

    private fun presenter() =
        moleculeFlow(RecompositionMode.Immediate) {
            libraryPresenter(events.receiveAsFlow(), library.sources, config, {}, Locale.ENGLISH)
        }

    @Test
    fun turning_app_library_off_closes_an_open_library() =
        runTest {
            presenter().test {
                events.send(LibraryEvent.Open)
                assertTrue(awaitMatching { it.shown }.open)

                config.value = config.value.copy(appLibrary = false)
                val closed = awaitMatching { !it.open && !it.shown && it.cards.isEmpty() }
                assertFalse(closed.open)
                assertEquals(1, library.released)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_library_that_was_never_open_is_not_released_when_app_library_is_off() =
        runTest {
            config.value = TvConfig(appLibrary = false)
            presenter().test {
                assertEquals(LibraryUiState(), awaitItem())
                expectNoEvents()
                assertEquals(0, library.released)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun library_least_used_turned_on_lets_the_open_library_switch_its_order() =
        runTest {
            presenter().test {
                events.send(LibraryEvent.Open)
                awaitMatching { it.shown }

                events.send(LibraryEvent.ToggleOrder)
                events.send(LibraryEvent.Focus(key("Zeta")))
                assertEquals(LibraryOrder.Usual, awaitMatching { it.focusedKey == key("Zeta") }.order)

                config.value = config.value.copy(libraryLeastUsed = true)
                events.send(LibraryEvent.ToggleOrder)

                val state = awaitMatching { it.order == LibraryOrder.LeastUsed }
                assertTrue(state.open)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
