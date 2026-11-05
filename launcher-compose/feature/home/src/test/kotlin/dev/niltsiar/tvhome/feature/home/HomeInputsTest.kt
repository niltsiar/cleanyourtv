package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputsMenu
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** What OK on the Inputs button, Back and a pick do to Home's state, and what they ask the host to open. */
class HomeInputsTest {
    private val events = Channel<HomeEvent>(Channel.UNLIMITED)
    private val effects = Channel<HomeEffect>(Channel.UNLIMITED)

    private fun presenter(inputs: Flow<InputsMenu>): Flow<HomeUiState> {
        val sources = sources(inputs = inputs)
        return moleculeFlow(RecompositionMode.Immediate) {
            homePresenter(events.receiveAsFlow(), sources, SixColumns, effects::trySend, Locale.ENGLISH, "EEEE d MMMM")
        }
    }

    /** A state that follows what was sent before it, since a press that changes nothing emits nothing. */
    private suspend fun ReceiveTurbine<HomeUiState>.stateAfterAnotherChange(): HomeUiState {
        events.send(HomeEvent.Focus("pkg.4"))
        return awaitMatching { it.focusedLabel == "App 4" }
    }

    @Test
    fun ok_on_the_button_opens_the_list_and_back_closes_it_and_returns_focus_to_the_button() =
        runTest {
            presenter(flowOf(FourPorts)).test {
                awaitMatching { it.inputs == FourPorts }
                events.send(HomeEvent.OpenInputs)
                assertTrue(awaitMatching { it.inputsOpen }.inputsOpen)

                events.send(HomeEvent.Back)
                val closed = awaitMatching { !it.inputsOpen && it.focusRequest != null }

                assertEquals(INPUTS_FOCUS, closed.focusRequest?.target)
                assertTrue(effects.tryReceive().isFailure)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun picking_a_row_closes_the_list_asks_the_host_to_open_it_and_returns_focus_to_the_button() =
        runTest {
            presenter(flowOf(FourPorts)).test {
                awaitMatching { it.inputs == FourPorts }
                events.send(HomeEvent.OpenInputs)
                awaitMatching { it.inputsOpen }

                events.send(HomeEvent.PickInput(InputTarget.LiveTv))
                val closed = awaitMatching { !it.inputsOpen && it.focusRequest != null }

                assertEquals(INPUTS_FOCUS, closed.focusRequest?.target)
                assertEquals(HomeEffect.OpenInput(InputTarget.LiveTv), effects.receive())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_source_panel_without_a_live_tv_row_is_opened_directly_and_no_list_shows() =
        runTest {
            val panel = InputsMenu(available = true, rows = listOf(InputRow.Panel), panelOnly = true)
            presenter(flowOf(panel)).test {
                awaitMatching { it.inputs == panel }
                events.send(HomeEvent.OpenInputs)

                assertEquals(HomeEffect.OpenInput(InputTarget.SourcePanel), effects.receive())
                assertFalse(stateAfterAnotherChange().inputsOpen)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_tv_with_nothing_to_list_says_so() =
        runTest {
            val empty = InputsMenu(available = true)
            presenter(flowOf(empty)).test {
                awaitMatching { it.inputs == empty }
                events.send(HomeEvent.OpenInputs)

                assertEquals(HomeEffect.NoInputs, effects.receive())
                assertFalse(stateAfterAnotherChange().inputsOpen)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_press_before_the_tv_has_said_what_it_lists_opens_the_list_once_it_has() =
        runTest {
            val inputs = MutableSharedFlow<InputsMenu>()
            presenter(inputs).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.OpenInputs)
                assertFalse(stateAfterAnotherChange().inputsOpen)

                inputs.emit(FourPorts)

                assertEquals(FourPorts, awaitMatching { it.inputsOpen }.inputs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_button_stays_hidden_until_the_tv_has_said_what_it_lists() =
        runTest {
            val inputs = MutableSharedFlow<InputsMenu>()
            presenter(inputs).test {
                assertFalse(awaitMatching { it.dock.isNotEmpty() }.inputs.available)

                inputs.emit(FourPorts)

                assertTrue(awaitMatching { it.inputs == FourPorts }.inputs.available)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_tv_with_no_inputs_hides_the_button_and_home_stopping_closes_the_list() =
        runTest {
            val none = InputsMenu(available = false)
            presenter(flowOf(none)).test {
                assertFalse(awaitMatching { it.inputs == none }.inputs.available)
                cancelAndIgnoreRemainingEvents()
            }
            presenter(flowOf(FourPorts)).test {
                awaitMatching { it.inputs == FourPorts }
                events.send(HomeEvent.OpenInputs)
                awaitMatching { it.inputsOpen }

                events.send(HomeEvent.Stopped)

                assertFalse(awaitMatching { !it.inputsOpen }.inputsOpen)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_home_key_closes_an_open_inputs_list() =
        runTest {
            presenter(flowOf(FourPorts)).test {
                awaitMatching { it.inputs == FourPorts }
                events.send(HomeEvent.OpenInputs)
                awaitMatching { it.inputsOpen }

                events.send(HomeEvent.HomePressed)

                assertFalse(awaitMatching { !it.inputsOpen }.inputsOpen)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
