package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.millisUntilNextDay
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

private val zone = ZoneId.systemDefault()

private fun millis(time: LocalDateTime) = time.atZone(zone).toInstant().toEpochMilli()

/** With unused_fade_days, Home works out which grid tiles are faded when it loads, and again at each local midnight while it shows. */
val HomeUnusedFadeTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<HomeEvent>(Channel.UNLIMITED)

            val apps = (1..12).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

            val today = LocalDateTime.of(2026, 11, 6, 20, 0)

            var time = millis(today)

            var probes = 0

            val opened = MutableStateFlow<Map<String, Long>>(emptyMap())

            var since: Long? = millis(today.minusDays(200))

            var reads = 0

            fun presenter(config: TvConfig = SixColumns.copy(unusedFadeDays = 60)): Flow<HomeUiState> {
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
            suspend fun ReceiveTurbine<HomeUiState>.latest(): HomeUiState {
                val key = if (++probes % 2 == 0) "pkg.4" else "pkg.5"
                events.send(HomeEvent.Focus(key))
                return awaitMatching { it.focusedKey == key }
            }

            suspend fun TestScope.pass(millis: Long) {
                advanceTimeBy(millis)
                runCurrent()
            }

            fun daysAgo(days: Long) = millis(today.minusDays(days))
        }
    } asContextForEach {
        test("a grid tile opened more than n days ago is faded and one opened within n days is not") {
            with(it.testScope) {
                opened.value = mapOf("pkg.8" to daysAgo(61), "pkg.9" to daysAgo(60), "pkg.10" to daysAgo(1))
                presenter().test {
                    val state = awaitMatching { it.faded.isNotEmpty() }

                    state.faded shouldBe setOf("pkg.7", "pkg.8", "pkg.11", "pkg.12")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the dock is never faded whatever its apps last opened") {
            with(it.testScope) {
                presenter().test {
                    val state = awaitMatching { it.faded.isNotEmpty() }

                    state.faded shouldBe apps.drop(6).map { it.key }.toSet()
                    state.dock.map { it.key } shouldBe apps.take(6).map { it.key }
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("an app never opened is not faded while the record is n days old or less") {
            with(it.testScope) {
                since = daysAgo(60)
                presenter().test {
                    awaitItem()
                    latest().faded shouldBe emptySet<String>()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("an app never opened is faded once the record is more than n days old") {
            with(it.testScope) {
                since = daysAgo(61)
                presenter().test {
                    awaitMatching { it.faded.isNotEmpty() }.faded shouldBe apps.drop(6).map { it.key }.toSet()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("opening a faded app takes it out of the set at once") {
            with(it.testScope) {
                presenter().test {
                    ("pkg.9" in awaitMatching { it.faded.isNotEmpty() }.faded) shouldBe true

                    opened.value = mapOf("pkg.9" to time)

                    ("pkg.9" in awaitMatching { "pkg.9" !in it.faded }.faded) shouldBe false
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("at local midnight the set is worked out again and not before") {
            with(it.testScope) {
                opened.value = mapOf("pkg.9" to daysAgo(60))
                val untilMidnight = millisUntilNextDay(time)
                presenter().test {
                    ("pkg.9" in awaitMatching { it.faded.isNotEmpty() }.faded) shouldBe false

                    time += untilMidnight
                    pass(untilMidnight - 1)
                    ("pkg.9" in latest().faded) shouldBe false

                    pass(1)
                    ("pkg.9" in latest().faded) shouldBe true
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("home looks at the time only at midnights and not while it is off the screen") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.faded.isNotEmpty() }
                    reads shouldBe 1

                    pass(60 * 60 * 1_000L)
                    reads shouldBe 1

                    events.send(HomeEvent.Stopped)
                    time += 3 * 24 * 60 * 60 * 1_000L
                    pass(3 * 24 * 60 * 60 * 1_000L)
                    reads shouldBe 1

                    events.send(HomeEvent.Started)
                    pass(0)
                    reads shouldBe 2
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("without the key nothing is faded and the time is never read") {
            with(it.testScope) {
                presenter(SixColumns).test {
                    awaitItem()
                    pass(2 * 24 * 60 * 60 * 1_000L)
                    latest().faded shouldBe emptySet<String>()
                    reads shouldBe 0
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}
