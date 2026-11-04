package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.niltsiar.tvhome.core.model.AppEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class HomePresenterTest {
    private val events = Channel<HomeEvent>(Channel.UNLIMITED)
    private val eventFlow = events.receiveAsFlow()
    private val effects = Channel<HomeEffect>(Channel.UNLIMITED)

    private fun presenter(
        apps: Flow<List<AppEntry>> = flowOf(NineApps),
        clock: Flow<LocalDateTime> = flowOf(Evening),
    ) = moleculeFlow(RecompositionMode.Immediate) {
        homePresenter(eventFlow, apps, clock, effects::trySend, Locale.ENGLISH)
    }

    @Test
    fun focusing_a_dock_app_updates_the_shelf_label() =
        runTest {
            presenter().test {
                events.send(HomeEvent.Focus("pkg.4"))
                assertEquals("App 4", awaitMatching { it.focusedLabel == "App 4" }.focusedLabel)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_clock_tick_updates_the_clock_text() =
        runTest {
            val clock = MutableSharedFlow<LocalDateTime>(replay = 1)
            clock.emit(Evening)
            presenter(clock = clock).test {
                assertEquals("20:15", awaitMatching { it.clock.isNotEmpty() }.clock)

                clock.emit(Evening.plusMinutes(1))
                assertEquals("20:16", awaitMatching { it.clock == "20:16" }.clock)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_apps_flow_fills_the_dock_and_grid() =
        runTest {
            presenter().test {
                val state = awaitMatching { it.dock.isNotEmpty() }
                assertEquals(NineApps, state.dock + state.grid)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_focus_change_keeps_the_dock_and_grid_lists() =
        runTest {
            presenter().test {
                val before = awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.4"))
                val after = awaitMatching { it.focusedLabel == "App 4" }
                assertSame(before.dock, after.dock)
                assertSame(before.grid, after.grid)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clicking_an_app_emits_a_launch_effect_for_its_package() =
        runTest {
            presenter().test {
                events.send(HomeEvent.Click("pkg.2"))
                assertEquals(HomeEffect.LaunchApp("pkg.2"), effects.receive())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

internal suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
