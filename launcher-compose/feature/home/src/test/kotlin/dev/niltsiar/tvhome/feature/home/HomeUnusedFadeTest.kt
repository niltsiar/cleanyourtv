package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.millisUntilNextDay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

private val zone = ZoneId.systemDefault()

private fun millis(time: LocalDateTime) = time.atZone(zone).toInstant().toEpochMilli()

/** With unused_fade_days, Home works out which grid tiles are faded when it loads, and again at each local midnight while it shows. */
class HomeUnusedFadeTest {
    private val events = Channel<HomeEvent>(Channel.UNLIMITED)
    private val apps = (1..12).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }
    private val today = LocalDateTime.of(2026, 11, 6, 20, 0)
    private var time = millis(today)
    private var probes = 0
    private val opened = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var since: Long? = millis(today.minusDays(200))
    private var reads = 0

    private fun presenter(config: TvConfig = SixColumns.copy(unusedFadeDays = 60)): Flow<HomeUiState> {
        val sources =
            sources(
                apps = flowOf(AppList(apps)),
                lastOpened = opened,
                openedSince = flowOf(since),
                timeMillis = { time.also { reads++ } },
            )
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

    private fun daysAgo(days: Long) = millis(today.minusDays(days))

    @Test
    fun a_grid_tile_opened_more_than_n_days_ago_is_faded_and_one_opened_within_n_days_is_not() =
        runTest {
            opened.value = mapOf("pkg.8" to daysAgo(61), "pkg.9" to daysAgo(60), "pkg.10" to daysAgo(1))
            presenter().test {
                val state = awaitMatching { it.faded.isNotEmpty() }

                assertEquals(setOf("pkg.7", "pkg.8", "pkg.11", "pkg.12"), state.faded)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_dock_is_never_faded_whatever_its_apps_last_opened() =
        runTest {
            presenter().test {
                val state = awaitMatching { it.faded.isNotEmpty() }

                assertEquals(apps.drop(6).map { it.key }.toSet(), state.faded)
                assertEquals(apps.take(6).map { it.key }, state.dock.map { it.key })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun an_app_never_opened_is_not_faded_while_the_record_is_n_days_old_or_less() =
        runTest {
            since = daysAgo(60)
            presenter().test {
                awaitItem()
                assertEquals(emptySet<String>(), latest().faded)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun an_app_never_opened_is_faded_once_the_record_is_more_than_n_days_old() =
        runTest {
            since = daysAgo(61)
            presenter().test {
                assertEquals(apps.drop(6).map { it.key }.toSet(), awaitMatching { it.faded.isNotEmpty() }.faded)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun opening_a_faded_app_takes_it_out_of_the_set_at_once() =
        runTest {
            presenter().test {
                assertEquals(true, "pkg.9" in awaitMatching { it.faded.isNotEmpty() }.faded)

                opened.value = mapOf("pkg.9" to time)

                assertEquals(false, "pkg.9" in awaitMatching { "pkg.9" !in it.faded }.faded)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun at_local_midnight_the_set_is_worked_out_again_and_not_before() =
        runTest {
            opened.value = mapOf("pkg.9" to daysAgo(60))
            val untilMidnight = millisUntilNextDay(time)
            presenter().test {
                assertEquals(false, "pkg.9" in awaitMatching { it.faded.isNotEmpty() }.faded)

                time += untilMidnight
                pass(untilMidnight - 1)
                assertEquals(false, "pkg.9" in latest().faded)

                pass(1)
                assertEquals(true, "pkg.9" in latest().faded)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun home_looks_at_the_time_only_at_midnights_and_not_while_it_is_off_the_screen() =
        runTest {
            presenter().test {
                awaitMatching { it.faded.isNotEmpty() }
                assertEquals(1, reads)

                pass(60 * 60 * 1_000L)
                assertEquals(1, reads)

                events.send(HomeEvent.Stopped)
                time += 3 * 24 * 60 * 60 * 1_000L
                pass(3 * 24 * 60 * 60 * 1_000L)
                assertEquals(1, reads)

                events.send(HomeEvent.Started)
                pass(0)
                assertEquals(2, reads)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun without_the_key_nothing_is_faded_and_the_time_is_never_read() =
        runTest {
            presenter(SixColumns).test {
                awaitItem()
                pass(2 * 24 * 60 * 60 * 1_000L)
                assertEquals(emptySet<String>(), latest().faded)
                assertEquals(0, reads)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
