package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputsMenu
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale

/** What OK on the Inputs button, Back and a pick do to Home's state, and what they ask the host to open. */
val HomeInputsTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<HomeEvent>(Channel.UNLIMITED)

            val effects = Channel<HomeEffect>(Channel.UNLIMITED)

            val saves = mutableListOf<Pair<List<String>, Int>>()

            fun presenter(inputs: Flow<InputsMenu>): Flow<HomeUiState> {
                val sources =
                    sources(inputs = inputs, saveOrder = { keys, docked ->
                        saves += keys to docked
                        true
                    })
                return moleculeFlow(RecompositionMode.Immediate) {
                    homePresenter(events.receiveAsFlow(), sources, SixColumns, effects::trySend, Locale.ENGLISH, "EEEE d MMMM")
                }
            }

            /** A state that follows what was sent before it, since a press that changes nothing emits nothing. */
            suspend fun ReceiveTurbine<HomeUiState>.stateAfterAnotherChange(): HomeUiState {
                events.send(HomeEvent.Focus("pkg.4"))
                return awaitMatching { it.focusedLabel == "App 4" }
            }
        }
    } asContextForEach {
        test("ok on the button opens the list and back closes it and returns focus to the button") {
            with(it.testScope) {
                presenter(flowOf(FourPorts)).test {
                    awaitMatching { it.inputs == FourPorts }
                    events.send(HomeEvent.OpenInputs)
                    (awaitMatching { it.inputsOpen }.inputsOpen).shouldBeTrue()

                    events.send(HomeEvent.Back)
                    val closed = awaitMatching { !it.inputsOpen && it.focusRequest != null }

                    closed.focusRequest?.target shouldBe INPUTS_FOCUS
                    effects.tryReceive().isFailure.shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("picking a row closes the list asks the host to open it and returns focus to the button") {
            with(it.testScope) {
                presenter(flowOf(FourPorts)).test {
                    awaitMatching { it.inputs == FourPorts }
                    events.send(HomeEvent.OpenInputs)
                    awaitMatching { it.inputsOpen }

                    events.send(HomeEvent.PickInput(InputTarget.LiveTv))
                    val closed = awaitMatching { !it.inputsOpen && it.focusRequest != null }

                    closed.focusRequest?.target shouldBe INPUTS_FOCUS
                    effects.receive() shouldBe HomeEffect.OpenInput(InputTarget.LiveTv)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a source panel without a live tv row is opened directly and no list shows") {
            with(it.testScope) {
                val panel = InputsMenu(available = true, rows = listOf(InputRow.Panel), panelOnly = true)
                presenter(flowOf(panel)).test {
                    awaitMatching { it.inputs == panel }
                    events.send(HomeEvent.OpenInputs)

                    effects.receive() shouldBe HomeEffect.OpenInput(InputTarget.SourcePanel)
                    stateAfterAnotherChange().inputsOpen.shouldBeFalse()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a tv with nothing to list says so") {
            with(it.testScope) {
                val empty = InputsMenu(available = true)
                presenter(flowOf(empty)).test {
                    awaitMatching { it.inputs == empty }
                    events.send(HomeEvent.OpenInputs)

                    effects.receive() shouldBe HomeEffect.NoInputs
                    stateAfterAnotherChange().inputsOpen.shouldBeFalse()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a press before the tv has said what it lists opens the list once it has") {
            with(it.testScope) {
                val inputs = MutableSharedFlow<InputsMenu>()
                presenter(inputs).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.OpenInputs)
                    stateAfterAnotherChange().inputsOpen.shouldBeFalse()

                    inputs.emit(FourPorts)

                    awaitMatching { it.inputsOpen }.inputs shouldBe FourPorts
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the button stays hidden until the tv has said what it lists") {
            with(it.testScope) {
                val inputs = MutableSharedFlow<InputsMenu>()
                presenter(inputs).test {
                    (awaitMatching { it.dock.isNotEmpty() }.inputs.available).shouldBeFalse()

                    inputs.emit(FourPorts)

                    (awaitMatching { it.inputs == FourPorts }.inputs.available).shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a tv with no inputs hides the button and home stopping closes the list") {
            with(it.testScope) {
                val none = InputsMenu(available = false)
                presenter(flowOf(none)).test {
                    (awaitMatching { it.inputs == none }.inputs.available).shouldBeFalse()
                    cancelAndIgnoreRemainingEvents()
                }
                presenter(flowOf(FourPorts)).test {
                    awaitMatching { it.inputs == FourPorts }
                    events.send(HomeEvent.OpenInputs)
                    awaitMatching { it.inputsOpen }

                    events.send(HomeEvent.Stopped)

                    (awaitMatching { !it.inputsOpen }.inputsOpen).shouldBeFalse()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the home key closes an open inputs list") {
            with(it.testScope) {
                presenter(flowOf(FourPorts)).test {
                    awaitMatching { it.inputs == FourPorts }
                    events.send(HomeEvent.OpenInputs)
                    awaitMatching { it.inputsOpen }

                    events.send(HomeEvent.HomePressed)

                    (awaitMatching { !it.inputsOpen }.inputsOpen).shouldBeFalse()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a request from the system replaces an open tile menu with the list") {
            with(it.testScope) {
                presenter(flowOf(FourPorts)).test {
                    awaitMatching { it.inputs == FourPorts }
                    events.send(HomeEvent.TileMenu("pkg.2", held = true))
                    awaitMatching { it.menu != null }

                    events.send(HomeEvent.OpenInputs)

                    val state = awaitMatching { it.inputsOpen }
                    state.menu.shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a request from the system replaces an open settings menu with the list") {
            with(it.testScope) {
                presenter(flowOf(FourPorts)).test {
                    awaitMatching { it.inputs == FourPorts }
                    events.send(HomeEvent.SettingsMenu)
                    awaitMatching { it.menu != null }

                    events.send(HomeEvent.OpenInputs)

                    val state = awaitMatching { it.inputsOpen }
                    state.menu.shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a request from the system ends a reorder keeps its order and opens the list") {
            with(it.testScope) {
                presenter(flowOf(FourPorts)).test {
                    awaitMatching { it.inputs == FourPorts }
                    events.send(HomeEvent.TileMenu("pkg.2", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.Move))
                    awaitMatching { it.reorder?.carried == "pkg.2" }
                    events.send(HomeEvent.ReorderMove(Arrow.Right))
                    awaitMatching { it.dock.getOrNull(2)?.key == "pkg.2" }

                    events.send(HomeEvent.OpenInputs)

                    val state = awaitMatching { it.inputsOpen }
                    state.reorder.shouldBeNull()
                    saves shouldBe listOf(listOf("pkg.1", "pkg.3", "pkg.2", "pkg.4", "pkg.5", "pkg.6", "pkg.7", "pkg.8", "pkg.9") to -1)
                    state.dock.map { it.key } shouldBe listOf("pkg.1", "pkg.3", "pkg.2", "pkg.4", "pkg.5", "pkg.6")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a request from the system leaves an open list open") {
            with(it.testScope) {
                presenter(flowOf(FourPorts)).test {
                    awaitMatching { it.inputs == FourPorts }
                    events.send(HomeEvent.OpenInputs)
                    awaitMatching { it.inputsOpen }

                    events.send(HomeEvent.OpenInputs)

                    stateAfterAnotherChange().inputsOpen.shouldBeTrue()
                    effects.tryReceive().isFailure.shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}
