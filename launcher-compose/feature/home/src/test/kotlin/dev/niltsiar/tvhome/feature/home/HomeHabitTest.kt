package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

private const val QUARTER_HOUR = 15 * 60 * 1_000L

/** With habit_hint, Home asks for the app the household usually opens now when it comes to the front and every 15 minutes while it shows. */
class HomeHabitTest {
    private val events = Channel<HomeEvent>(Channel.UNLIMITED)
    private var usual: String? = "pkg.2"
    private var looks = 0
    private var probes = 0

    private fun presenter(config: TvConfig = SixColumns.copy(habitHint = true)): Flow<HomeUiState> {
        val sources = sources(habit = { usual.also { looks++ } })
        return moleculeFlow(RecompositionMode.Immediate) {
            homePresenter(events.receiveAsFlow(), sources, config, {}, Locale.ENGLISH, "EEEE d MMMM")
        }
    }

    /** The newest state, forced by a change of focus: a time that passes alone emits only if the state changed. */
    private suspend fun ReceiveTurbine<HomeUiState>.latest(): HomeUiState {
        val key = if (++probes % 2 == 0) "pkg.4" else "pkg.5"
        events.send(HomeEvent.Focus(key))
        return awaitMatching { it.focusedKey == key }
    }

    private suspend fun TestScope.pass(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun the_usual_app_reaches_the_state_when_home_shows() =
        runTest {
            presenter().test {
                assertEquals("pkg.2", awaitMatching { it.habit != null }.habit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun home_asks_again_every_15_minutes_and_a_new_answer_replaces_the_old() =
        runTest {
            presenter().test {
                awaitMatching { it.habit != null }
                assertEquals(1, looks)

                usual = null
                pass(QUARTER_HOUR - 1)
                assertEquals(1, looks)
                assertEquals("pkg.2", latest().habit)

                pass(1)
                assertEquals(2, looks)
                assertNull(latest().habit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun home_asks_no_more_while_it_is_off_the_screen_and_at_once_when_it_comes_back() =
        runTest {
            presenter().test {
                awaitMatching { it.habit != null }
                events.send(HomeEvent.Stopped)
                pass(3 * QUARTER_HOUR)
                assertEquals(1, looks)

                usual = "pkg.3"
                events.send(HomeEvent.Started)
                pass(0)

                assertEquals(2, looks)
                assertEquals("pkg.3", awaitMatching { it.habit == "pkg.3" }.habit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun without_the_key_home_never_asks_and_the_state_has_no_habit() =
        runTest {
            presenter(SixColumns).test {
                awaitItem()
                pass(2 * QUARTER_HOUR)
                assertNull(latest().habit)
                assertEquals(0, looks)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun only_the_usual_app_has_focus_on_its_tile_marked_usual() =
        runTest {
            presenter(SixColumns.copy(habitHint = true, cinematicHome = true)).test {
                awaitMatching { it.habit != null }

                events.send(HomeEvent.Focus("pkg.2"))
                awaitMatching { it.focusedKey == "pkg.2" }.let {
                    assertTrue(it.focusedUsual)
                    assertTrue(it.shelf?.usual == true)
                }

                events.send(HomeEvent.Focus("pkg.3"))
                awaitMatching { it.focusedKey == "pkg.3" }.let {
                    assertFalse(it.focusedUsual)
                    assertTrue(it.shelf?.usual == false)
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
}
