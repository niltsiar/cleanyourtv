package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.util.Locale

private const val QUARTER_HOUR = 15 * 60 * 1_000L

/** With habit_hint, Home asks for the app the household usually opens now when it comes to the front and every 15 minutes while it shows. */
val HomeHabitTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<HomeEvent>(Channel.UNLIMITED)

            var usual: String? = "pkg.2"

            var looks = 0

            var probes = 0

            fun presenter(config: TvConfig = SixColumns.copy(habitHint = true)): Flow<HomeUiState> {
                val sources = sources(habit = { usual.also { looks++ } })
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
        }
    } asContextForEach {
        test("the usual app reaches the state when home shows") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.habit != null }.habit shouldBe "pkg.2"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("home asks again every 15 minutes and a new answer replaces the old") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.habit != null }
                    looks shouldBe 1

                    usual = null
                    pass(QUARTER_HOUR - 1)
                    looks shouldBe 1
                    latest().habit shouldBe "pkg.2"

                    pass(1)
                    looks shouldBe 2
                    latest().habit.shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("home asks no more while it is off the screen and at once when it comes back") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.habit != null }
                    events.send(HomeEvent.Stopped)
                    pass(3 * QUARTER_HOUR)
                    looks shouldBe 1

                    usual = "pkg.3"
                    events.send(HomeEvent.Started)
                    pass(0)

                    looks shouldBe 2
                    awaitMatching { it.habit == "pkg.3" }.habit shouldBe "pkg.3"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("without the key home never asks and the state has no habit") {
            with(it.testScope) {
                presenter(SixColumns).test {
                    awaitItem()
                    pass(2 * QUARTER_HOUR)
                    latest().habit.shouldBeNull()
                    looks shouldBe 0
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("only the usual app has focus on its tile marked usual") {
            with(it.testScope) {
                presenter(SixColumns.copy(habitHint = true, cinematicHome = true)).test {
                    awaitMatching { it.habit != null }

                    events.send(HomeEvent.Focus("pkg.2"))
                    awaitMatching { it.focusedKey == "pkg.2" }.let {
                        it.focusedUsual.shouldBeTrue()
                        it.shelf?.usual shouldBe true
                    }

                    events.send(HomeEvent.Focus("pkg.3"))
                    awaitMatching { it.focusedKey == "pkg.3" }.let {
                        it.focusedUsual.shouldBeFalse()
                        it.shelf?.usual shouldBe false
                    }
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}
