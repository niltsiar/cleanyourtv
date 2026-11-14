package dev.niltsiar.tvhome.feature.library

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale

/** The library follows the stored choices: it closes when app_library turns off and applies the choices that shape it. */
val LibraryConfigFollowTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<LibraryEvent>(Channel.UNLIMITED)
            val config = MutableStateFlow(TvConfig(appLibrary = true))
            val library = FakeLibrary(listOf(libraryApp("Zeta"), libraryApp("Alpha")))

            fun presenter() =
                moleculeFlow(RecompositionMode.Immediate) {
                    libraryPresenter(events.receiveAsFlow(), library.sources, config, {}, Locale.ENGLISH)
                }
        }
    } asContextForEach {
        test("turning app library off closes an open library") {
            with(it.testScope) {
                presenter().test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }.open.shouldBeTrue()

                    config.value = config.value.copy(appLibrary = false)
                    val closed = awaitMatching { !it.open && !it.shown && it.cards.isEmpty() }
                    closed.open.shouldBeFalse()
                    library.released shouldBe 1
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a library that was never open is not released when app library is off") {
            with(it.testScope) {
                config.value = TvConfig(appLibrary = false)
                presenter().test {
                    awaitItem() shouldBe LibraryUiState()
                    expectNoEvents()
                    library.released shouldBe 0
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("library least used turned on lets the open library switch its order") {
            with(it.testScope) {
                presenter().test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }

                    events.send(LibraryEvent.ToggleOrder)
                    events.send(LibraryEvent.Focus(key("Zeta")))
                    awaitMatching { it.focusedKey == key("Zeta") }.order shouldBe LibraryOrder.Usual

                    config.value = config.value.copy(libraryLeastUsed = true)
                    events.send(LibraryEvent.ToggleOrder)

                    val state = awaitMatching { it.order == LibraryOrder.LeastUsed }
                    state.open.shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}
