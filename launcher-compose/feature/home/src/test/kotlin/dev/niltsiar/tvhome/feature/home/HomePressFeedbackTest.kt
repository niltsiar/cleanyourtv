package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.time.ZoneId
import java.util.Locale

/** press_feedback on the presenter: one start per press, the line that says the app is opening, and what ends it. */
val HomePressFeedbackTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<HomeEvent>(Channel.UNLIMITED)

            val effects = mutableListOf<HomeEffect>()

            /** The last-opened record, which notes an app the moment an effect opens it, as the host's launcher does. */
            val opened = MutableStateFlow(emptyMap<String, Long>())

            fun open(effect: HomeEffect) {
                effects += effect
                val app =
                    when (effect) {
                        is HomeEffect.LaunchApp -> effect.app
                        is HomeEffect.OpenShelfItem -> effect.app
                        else -> return
                    }
                opened.value += app.key to OPENED_AT
            }

            var probes = 0

            val eventFlow = events.receiveAsFlow()

            fun presenter(config: TvConfig = SixColumns.copy(pressFeedback = true)): Flow<HomeUiState> {
                val sources = sources(lastOpened = opened, timeMillis = { OPENED_AT })
                return moleculeFlow(RecompositionMode.Immediate) {
                    homePresenter(eventFlow, sources, config, ::open, Locale.ENGLISH, "EEEE d MMMM")
                }
            }

            fun launched() = effects.filterIsInstance<HomeEffect.LaunchApp>().map { it.app.key }

            /** The newest state, forced by a change of focus: a time that passes alone emits only if the state changed. */
            suspend fun ReceiveTurbine<HomeUiState>.latest(): HomeUiState {
                val key = if (++probes % 2 == 0) "pkg.4" else "pkg.5"
                events.send(HomeEvent.Focus(key))
                return awaitMatching { it.focusedKey == key }
            }

            suspend fun TestScope.press(key: String) {
                events.send(HomeEvent.Click(key))
                runCurrent()
            }

            suspend fun TestScope.pass(millis: Long) {
                advanceTimeBy(millis)
                runCurrent()
            }
        }
    } asContextForEach {
        test("a tile press starts its app once and a second press on any tile starts nothing while it opens") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    press("pkg.1")
                    press("pkg.1")
                    press("pkg.8")

                    launched() shouldBe listOf("pkg.1")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("with home still in front 1 s after the press the line says the app is opening") {
            with(it.testScope) {
                presenter(SixColumns.copy(pressFeedback = true, cinematicHome = true)).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    press("pkg.2")
                    pass(OPENING_SAYS_MS - 1)
                    latest().opening shouldBe Opening("App 2", says = false)

                    pass(1)
                    val said = latest()
                    said.opening shouldBe Opening("App 2", says = true)
                    said.shelf?.opening shouldBe "App 2"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("home leaving the screen ends the opening and the next press starts its app") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    press("pkg.1")
                    pass(OPENING_SAYS_MS)
                    events.send(HomeEvent.Stopped)
                    latest().opening.shouldBeNull()
                    events.send(HomeEvent.Started)
                    press("pkg.3")

                    launched() shouldBe listOf("pkg.1", "pkg.3")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("an app the host did not start ends the opening at once") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    press("pkg.1")
                    awaitMatching { it.opening != null }

                    events.send(HomeEvent.AppNotStarted("pkg.1"))
                    latest().opening.shouldBeNull()
                    press("pkg.1")

                    launched() shouldBe listOf("pkg.1", "pkg.1")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("after 10 s with home in front the opening ends and a press starts an app again") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    press("pkg.1")
                    pass(OPENING_MAX_MS - 1)
                    latest().opening shouldBe Opening("App 1", says = true)

                    pass(1)
                    latest().opening.shouldBeNull()
                    press("pkg.2")

                    launched() shouldBe listOf("pkg.1", "pkg.2")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a tile press keeps the line's never opened until the opening ends, then shows the new record") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.2"))
                    awaitMatching { it.focusedKey == "pkg.2" }.focusedNeverOpened shouldBe true
                    press("pkg.2")
                    pass(OPENING_SAYS_MS - 1)
                    expectMostRecentItem().focusedNeverOpened shouldBe true

                    pass(1)
                    expectMostRecentItem().focusedNeverOpened shouldBe true
                    events.send(HomeEvent.Stopped)

                    val ended = awaitMatching { it.opening == null }
                    ended.focusedNeverOpened shouldBe false
                    ended.focusedOpened.shouldNotBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a press on a faded tile keeps it faded until the opening ends, then shows it unfaded") {
            with(it.testScope) {
                opened.value = mapOf("pkg.7" to OPENED_AT - 61 * DAY_MS)
                presenter(SixColumns.copy(pressFeedback = true, unusedFadeDays = 60)).test {
                    awaitMatching { "pkg.7" in it.faded }
                    events.send(HomeEvent.Focus("pkg.7"))
                    press("pkg.7")
                    pass(OPENING_SAYS_MS)
                    latest().faded shouldBe setOf("pkg.7")

                    events.send(HomeEvent.Stopped)

                    awaitMatching { it.opening == null }.faded shouldBe emptySet()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a card press keeps the line's never opened until the opening ends") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.2"))
                    awaitMatching { it.focusedKey == "pkg.2" }.focusedNeverOpened shouldBe true
                    events.send(HomeEvent.OpenShelfItem("pkg.2", ShelfItem(packageName = "pkg.2", title = "Andor", kind = ShelfKind.Next)))
                    pass(OPENING_SAYS_MS)
                    expectMostRecentItem().focusedNeverOpened shouldBe true

                    events.send(HomeEvent.AppNotStarted("pkg.2"))

                    awaitMatching { it.opening == null }.focusedNeverOpened shouldBe false
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("without the key a press shows the new last-opened record at once") {
            with(it.testScope) {
                presenter(SixColumns).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.2"))
                    awaitMatching { it.focusedKey == "pkg.2" }.focusedNeverOpened shouldBe true
                    press("pkg.2")

                    expectMostRecentItem().focusedNeverOpened shouldBe false
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("without the key every press starts its app and nothing is opening") {
            with(it.testScope) {
                presenter(SixColumns).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    press("pkg.1")
                    press("pkg.1")
                    pass(OPENING_SAYS_MS)

                    latest().opening.shouldBeNull()
                    launched() shouldBe listOf("pkg.1", "pkg.1")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}

private val OPENED_AT = Evening.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private const val DAY_MS = 24 * 60 * 60 * 1_000L
