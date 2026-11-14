package dev.niltsiar.tvhome.feature.library

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
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.util.Locale

/** press_feedback on the library's presenter: one start per card OK, the library open while it opens, and what ends it. */
val LibraryPressFeedbackTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<LibraryEvent>(Channel.UNLIMITED)
            val effects = mutableListOf<LibraryEffect>()
            val library = FakeLibrary(listOf(libraryApp("Alpha"), libraryApp("Gamma", onHome = true), libraryApp("Home", onHome = true)))

            fun presenter(config: TvConfig = TvConfig(appLibrary = true, pressFeedback = true)): Flow<LibraryUiState> =
                moleculeFlow(RecompositionMode.Immediate) {
                    libraryPresenter(events.receiveAsFlow(), library.sources, config, { effects += it }, Locale.ENGLISH)
                }

            fun launched() = effects.filterIsInstance<LibraryEffect.LaunchApp>().map { it.app.label }

            suspend fun ReceiveTurbine<LibraryUiState>.open(): LibraryUiState {
                events.send(LibraryEvent.Open)
                return awaitMatching { it.shown }
            }

            suspend fun TestScope.send(event: LibraryEvent) {
                events.send(event)
                runCurrent()
            }
        }
    } asContextForEach {
        test("ok on a card starts its app once and a second ok on any card does nothing while it opens") {
            with(it.testScope) {
                presenter().test {
                    open()
                    send(LibraryEvent.Click(key("Gamma")))
                    send(LibraryEvent.Click(key("Gamma")))
                    send(LibraryEvent.Click(key("Home")))
                    send(LibraryEvent.Click(key("Alpha")))

                    launched() shouldBe listOf("Gamma")
                    library.saved shouldBe emptyList()
                    val opening = expectMostRecentItem()
                    opening.open.shouldBeTrue()
                    opening.launching.shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("open in the card menu keeps the library open on that card and is the one start") {
            with(it.testScope) {
                presenter().test {
                    open()
                    send(LibraryEvent.Hold(key("Gamma")))
                    send(LibraryEvent.Pick(LibraryRow.Open))
                    send(LibraryEvent.Click(key("Home")))

                    launched() shouldBe listOf("Gamma")
                    val opening = expectMostRecentItem()
                    opening.open.shouldBeTrue()
                    opening.menu shouldBe null
                    opening.focusRequest?.key shouldBe key("Gamma")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("home leaving the screen closes the library and ends the opening") {
            with(it.testScope) {
                presenter().test {
                    open()
                    send(LibraryEvent.Click(key("Gamma")))
                    send(LibraryEvent.Stopped)
                    awaitMatching { !it.open && !it.launching }

                    open()
                    send(LibraryEvent.Click(key("Home")))

                    launched() shouldBe listOf("Gamma", "Home")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("an app the host did not start ends the opening and the library stays open") {
            with(it.testScope) {
                presenter().test {
                    open()
                    send(LibraryEvent.Click(key("Gamma")))
                    awaitMatching { it.launching }

                    send(LibraryEvent.AppNotStarted(key("Gamma")))
                    awaitMatching { !it.launching }.open.shouldBeTrue()
                    send(LibraryEvent.Click(key("Gamma")))

                    launched() shouldBe listOf("Gamma", "Gamma")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the home key closes the library and the opening goes on") {
            with(it.testScope) {
                presenter().test {
                    open()
                    send(LibraryEvent.Click(key("Gamma")))
                    send(LibraryEvent.Close)
                    awaitMatching { !it.open }.launching.shouldBeTrue()

                    open()
                    send(LibraryEvent.Click(key("Home")))

                    launched() shouldBe listOf("Gamma")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("after 10 s with home in front the opening ends and ok starts an app again") {
            with(it.testScope) {
                presenter().test {
                    open()
                    send(LibraryEvent.Click(key("Gamma")))
                    advanceTimeBy(OPENING_MAX_MS - 1)
                    runCurrent()
                    expectMostRecentItem().launching.shouldBeTrue()

                    advanceTimeBy(1)
                    runCurrent()
                    awaitMatching { !it.launching }
                    send(LibraryEvent.Click(key("Home")))

                    launched() shouldBe listOf("Gamma", "Home")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("without the key ok on a card opens its app, closes the library and takes the next ok") {
            with(it.testScope) {
                presenter(TvConfig(appLibrary = true)).test {
                    open()
                    send(LibraryEvent.Click(key("Gamma")))
                    awaitMatching { !it.open }.launching.shouldBeFalse()

                    open()
                    send(LibraryEvent.Click(key("Home")))

                    launched() shouldBe listOf("Gamma", "Home")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}
