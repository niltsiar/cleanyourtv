package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.InputArrival
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputsMenu
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.util.Locale

private val Console = InputArrival("PlayStation 5 · HDMI 2", InputTarget.TvInput("tv/.Cec/HDMI2"), token = 1)
private val Player = InputArrival("Apple TV · HDMI 1", InputTarget.TvInput("tv/.Cec/HDMI1"), token = 2)

/** The Inputs button offers a device that was switched on while Home shows, once its port has held connected for 2 s. */
val HomeInputArrivalTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<HomeEvent>(Channel.UNLIMITED)

            val effects = Channel<HomeEffect>(Channel.UNLIMITED)

            val inputs = MutableStateFlow(FourPorts)

            var probes = 0

            fun presenter(config: TvConfig = SixColumns.copy(inputArrival = true)): Flow<HomeUiState> {
                val sources = sources(inputs = inputs)
                return moleculeFlow(RecompositionMode.Immediate) {
                    homePresenter(events.receiveAsFlow(), sources, config, effects::trySend, Locale.ENGLISH, "EEEE d MMMM")
                }
            }

            fun arrive(arrival: InputArrival?) {
                inputs.value = FourPorts.copy(arrival = arrival)
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
        test("an arrival held for 2 s is offered with the device and the port") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.inputs.available }
                    arrive(Console)
                    pass(1_999)
                    latest().inputOffer.shouldBeNull()

                    pass(1)

                    awaitMatching { it.inputOffer != null }.inputOffer shouldBe Console
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a port that flaps back within 2 s offers nothing") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.inputs.available }
                    arrive(Console)
                    pass(1_500)
                    arrive(null)
                    pass(5_000)

                    latest().inputOffer.shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a port that comes back starts the 2 s again") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.inputs.available }
                    arrive(Console)
                    pass(1_500)
                    arrive(null)
                    pass(400)
                    arrive(Console.copy(token = 3))
                    pass(1_999)
                    latest().inputOffer.shouldBeNull()

                    pass(1)

                    awaitMatching { it.inputOffer != null }.inputOffer?.token shouldBe 3
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("home leaving the screen within 2 s offers nothing") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.inputs.available }
                    arrive(Console)
                    pass(1_000)
                    events.send(HomeEvent.Stopped)
                    pass(5_000)

                    latest().inputOffer.shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the offer ends after 20 s without a press") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.inputs.available }
                    arrive(Console)
                    pass(2_000)
                    awaitMatching { it.inputOffer != null }
                    pass(19_999)
                    latest().inputOffer shouldBe Console

                    pass(1)

                    (awaitMatching { it.inputOffer == null }.inputOffer).shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the offer ends when its port leaves connected and a return is a new arrival") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.inputs.available }
                    arrive(Console)
                    pass(2_000)
                    awaitMatching { it.inputOffer != null }

                    arrive(null)
                    (awaitMatching { it.inputOffer == null }.inputOffer).shouldBeNull()

                    arrive(Console.copy(token = 3))
                    pass(2_000)
                    awaitMatching { it.inputOffer != null }.inputOffer?.token shouldBe 3
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("home leaving the screen ends the offer") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.inputs.available }
                    arrive(Console)
                    pass(2_000)
                    awaitMatching { it.inputOffer != null }

                    events.send(HomeEvent.Stopped)

                    (awaitMatching { it.inputOffer == null }.inputOffer).shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the latest arrival is the one offered") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.inputs.available }
                    arrive(Console)
                    pass(2_000)
                    awaitMatching { it.inputOffer == Console }

                    arrive(Player)
                    pass(2_000)

                    awaitMatching { it.inputOffer == Player }.inputOffer shouldBe Player
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("ok on the button switches to the offered input ends the offer and opens no list") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.inputs.available }
                    arrive(Console)
                    pass(2_000)
                    awaitMatching { it.inputOffer != null }

                    events.send(HomeEvent.PressInputs)

                    val after = awaitMatching { it.inputOffer == null }
                    after.inputsOpen.shouldBeFalse()
                    effects.receive() shouldBe HomeEffect.OpenInput(Console.target)
                    pass(30_000)
                    latest().inputOffer.shouldBeNull()
                    effects.tryReceive().isFailure.shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("ok on the button without an offer opens the list") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.inputs.available }

                    events.send(HomeEvent.PressInputs)

                    (awaitMatching { it.inputsOpen }.inputsOpen).shouldBeTrue()
                    effects.tryReceive().isFailure.shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a request from the system opens the list and ends the offer") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.inputs.available }
                    arrive(Console)
                    pass(2_000)
                    awaitMatching { it.inputOffer != null }

                    events.send(HomeEvent.OpenInputs)

                    val state = awaitMatching { it.inputsOpen }
                    state.inputOffer.shouldBeNull()
                    effects.tryReceive().isFailure.shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("with the key off an arrival offers nothing") {
            with(it.testScope) {
                presenter(SixColumns).test {
                    awaitMatching { it.inputs.available }
                    arrive(Console)
                    pass(5_000)

                    latest().inputOffer.shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("with the inputs pill hidden an arrival offers nothing") {
            with(it.testScope) {
                presenter(SixColumns.copy(inputArrival = true, showInputsPill = false)).test {
                    awaitMatching { it.inputs.available }
                    arrive(Console)
                    pass(5_000)

                    latest().inputOffer.shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}
