package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.InputArrival
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputsMenu
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

private val Console = InputArrival("PlayStation 5 · HDMI 2", InputTarget.TvInput("tv/.Cec/HDMI2"), token = 1)
private val Player = InputArrival("Apple TV · HDMI 1", InputTarget.TvInput("tv/.Cec/HDMI1"), token = 2)

/** The Inputs button offers a device that was switched on while Home shows, once its port has held connected for 2 s. */
class HomeInputArrivalTest {
    private val events = Channel<HomeEvent>(Channel.UNLIMITED)
    private val effects = Channel<HomeEffect>(Channel.UNLIMITED)
    private val inputs = MutableStateFlow(FourPorts)
    private var probes = 0

    private fun presenter(config: TvConfig = SixColumns.copy(inputArrival = true)): Flow<HomeUiState> {
        val sources = sources(inputs = inputs)
        return moleculeFlow(RecompositionMode.Immediate) {
            homePresenter(events.receiveAsFlow(), sources, config, effects::trySend, Locale.ENGLISH, "EEEE d MMMM")
        }
    }

    private fun arrive(arrival: InputArrival?) {
        inputs.value = FourPorts.copy(arrival = arrival)
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
    fun an_arrival_held_for_2_s_is_offered_with_the_device_and_the_port() =
        runTest {
            presenter().test {
                awaitMatching { it.inputs.available }
                arrive(Console)
                pass(1_999)
                assertNull(latest().inputOffer)

                pass(1)

                assertEquals(Console, awaitMatching { it.inputOffer != null }.inputOffer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_port_that_flaps_back_within_2_s_offers_nothing() =
        runTest {
            presenter().test {
                awaitMatching { it.inputs.available }
                arrive(Console)
                pass(1_500)
                arrive(null)
                pass(5_000)

                assertNull(latest().inputOffer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_port_that_comes_back_starts_the_2_s_again() =
        runTest {
            presenter().test {
                awaitMatching { it.inputs.available }
                arrive(Console)
                pass(1_500)
                arrive(null)
                pass(400)
                arrive(Console.copy(token = 3))
                pass(1_999)
                assertNull(latest().inputOffer)

                pass(1)

                assertEquals(3, awaitMatching { it.inputOffer != null }.inputOffer?.token)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun home_leaving_the_screen_within_2_s_offers_nothing() =
        runTest {
            presenter().test {
                awaitMatching { it.inputs.available }
                arrive(Console)
                pass(1_000)
                events.send(HomeEvent.Stopped)
                pass(5_000)

                assertNull(latest().inputOffer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_offer_ends_after_20_s_without_a_press() =
        runTest {
            presenter().test {
                awaitMatching { it.inputs.available }
                arrive(Console)
                pass(2_000)
                awaitMatching { it.inputOffer != null }
                pass(19_999)
                assertEquals(Console, latest().inputOffer)

                pass(1)

                assertNull(awaitMatching { it.inputOffer == null }.inputOffer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_offer_ends_when_its_port_leaves_connected_and_a_return_is_a_new_arrival() =
        runTest {
            presenter().test {
                awaitMatching { it.inputs.available }
                arrive(Console)
                pass(2_000)
                awaitMatching { it.inputOffer != null }

                arrive(null)
                assertNull(awaitMatching { it.inputOffer == null }.inputOffer)

                arrive(Console.copy(token = 3))
                pass(2_000)
                assertEquals(3, awaitMatching { it.inputOffer != null }.inputOffer?.token)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun home_leaving_the_screen_ends_the_offer() =
        runTest {
            presenter().test {
                awaitMatching { it.inputs.available }
                arrive(Console)
                pass(2_000)
                awaitMatching { it.inputOffer != null }

                events.send(HomeEvent.Stopped)

                assertNull(awaitMatching { it.inputOffer == null }.inputOffer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_latest_arrival_is_the_one_offered() =
        runTest {
            presenter().test {
                awaitMatching { it.inputs.available }
                arrive(Console)
                pass(2_000)
                awaitMatching { it.inputOffer == Console }

                arrive(Player)
                pass(2_000)

                assertEquals(Player, awaitMatching { it.inputOffer == Player }.inputOffer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun ok_on_the_button_switches_to_the_offered_input_ends_the_offer_and_opens_no_list() =
        runTest {
            presenter().test {
                awaitMatching { it.inputs.available }
                arrive(Console)
                pass(2_000)
                awaitMatching { it.inputOffer != null }

                events.send(HomeEvent.PressInputs)

                val after = awaitMatching { it.inputOffer == null }
                assertFalse(after.inputsOpen)
                assertEquals(HomeEffect.OpenInput(Console.target), effects.receive())
                pass(30_000)
                assertNull(latest().inputOffer)
                assertTrue(effects.tryReceive().isFailure)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun ok_on_the_button_without_an_offer_opens_the_list() =
        runTest {
            presenter().test {
                awaitMatching { it.inputs.available }

                events.send(HomeEvent.PressInputs)

                assertTrue(awaitMatching { it.inputsOpen }.inputsOpen)
                assertTrue(effects.tryReceive().isFailure)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_request_from_the_system_opens_the_list_and_ends_the_offer() =
        runTest {
            presenter().test {
                awaitMatching { it.inputs.available }
                arrive(Console)
                pass(2_000)
                awaitMatching { it.inputOffer != null }

                events.send(HomeEvent.OpenInputs)

                val state = awaitMatching { it.inputsOpen }
                assertNull(state.inputOffer)
                assertTrue(effects.tryReceive().isFailure)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun with_the_key_off_an_arrival_offers_nothing() =
        runTest {
            presenter(SixColumns).test {
                awaitMatching { it.inputs.available }
                arrive(Console)
                pass(5_000)

                assertNull(latest().inputOffer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun with_the_inputs_pill_hidden_an_arrival_offers_nothing() =
        runTest {
            presenter(SixColumns.copy(inputArrival = true, showInputsPill = false)).test {
                awaitMatching { it.inputs.available }
                arrive(Console)
                pass(5_000)

                assertNull(latest().inputOffer)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
