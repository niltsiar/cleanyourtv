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
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputTile
import dev.niltsiar.tvhome.core.model.InputsMenu
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

            val pins = mutableListOf<Pair<InputTile, String>>()

            val unpins = mutableListOf<String>()

            /** The app list, which a pin ends with the new tile, as the pin store and the app list do. */
            val apps = MutableStateFlow(AppList(NineApps))

            fun presenter(
                inputs: Flow<InputsMenu>,
                config: TvConfig = SixColumns,
            ): Flow<HomeUiState> {
                val sources =
                    sources(
                        apps = apps,
                        inputs = inputs,
                        saveOrder = { keys, docked ->
                            saves += keys to docked
                            true
                        },
                        pinInput = { tile, label ->
                            pins += tile to label
                            apps.value = AppList(apps.value.apps + AppEntry("input:${tile.id}", "", label, input = tile))
                            true
                        },
                        unpinInput = { unpins.add(it) },
                    )
                return moleculeFlow(RecompositionMode.Immediate) {
                    homePresenter(events.receiveAsFlow(), sources, config, effects::trySend, Locale.ENGLISH, "EEEE d MMMM")
                }
            }

            /** Opens the list of [inputs] with input_tiles on, and holds OK on its [row]. */
            suspend fun ReceiveTurbine<HomeUiState>.holdOn(
                inputs: InputsMenu,
                row: InputRow.Port,
            ) {
                awaitMatching { it.inputs == inputs && it.dock.isNotEmpty() }
                events.send(HomeEvent.OpenInputs)
                awaitMatching { it.inputsOpen }
                events.send(HomeEvent.InputMenu(row))
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

        test("a held OK on an HDMI row opens Add to Home") {
            with(it.testScope) {
                presenter(flowOf(FourPorts), InputTilesOn).test {
                    holdOn(FourPorts, FourPorts.rows[0] as InputRow.Port)

                    val state = awaitMatching { it.inputMenu != null }

                    state.inputMenu?.rows shouldBe listOf(MenuRow.AddToHome)
                    state.inputsOpen.shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a held OK on a pinned row offers Remove from Home") {
            with(it.testScope) {
                apps.value = AppList(NineApps + PlayStationTile)
                val inputs = playStationInputs()
                presenter(flowOf(inputs), InputTilesOn).test {
                    holdOn(inputs, inputs.rows[1] as InputRow.Port)

                    val menu = awaitMatching { it.inputMenu != null }.inputMenu

                    menu?.rows shouldBe listOf(MenuRow.RemoveFromHome)
                    menu?.pinned shouldBe PlayStationId
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("Remove from Home on a port row unpins the device pinned on that port") {
            with(it.testScope) {
                apps.value = AppList(NineApps + PlayStationTile)
                val inputs = playStationInputs(label = null)
                presenter(flowOf(inputs), InputTilesOn).test {
                    holdOn(inputs, inputs.rows[1] as InputRow.Port)
                    awaitMatching { it.inputMenu != null }

                    events.send(HomeEvent.InputMenuPick(MenuRow.RemoveFromHome))

                    val closed = awaitMatching { !it.inputsOpen && it.inputMenu == null && it.focusRequest != null }
                    unpins shouldBe listOf(PlayStationId)
                    closed.focusRequest?.target shouldBe INPUTS_FOCUS
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a Coocaa source row opens no pin menu") {
            with(it.testScope) {
                val coocaa = InputRow.Port("HDMI 1", InputIcon.Hdmi, InputConnection.Unknown, InputTarget.Coocaa("HDMI", 1))
                val inputs = InputsMenu(available = true, rows = listOf(coocaa, InputRow.LiveTv))
                presenter(flowOf(inputs), InputTilesOn).test {
                    holdOn(inputs, coocaa)

                    stateAfterAnotherChange().inputMenu.shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("no row opens a pin menu with input_tiles off") {
            with(it.testScope) {
                presenter(flowOf(FourPorts)).test {
                    holdOn(FourPorts, FourPorts.rows[0] as InputRow.Port)

                    stateAfterAnotherChange().inputMenu.shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("no row opens a pin menu with the Inputs pill hidden") {
            with(it.testScope) {
                presenter(flowOf(FourPorts), InputTilesOn.copy(showInputsPill = false)).test {
                    holdOn(FourPorts, FourPorts.rows[0] as InputRow.Port)

                    stateAfterAnotherChange().inputMenu.shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("Back from the pin menu returns to the input list") {
            with(it.testScope) {
                presenter(flowOf(FourPorts), InputTilesOn).test {
                    holdOn(FourPorts, FourPorts.rows[0] as InputRow.Port)
                    awaitMatching { it.inputMenu != null }

                    events.send(HomeEvent.Back)

                    val back = awaitMatching { it.inputMenu == null }
                    back.inputsOpen.shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("Add to Home closes the list and the new tile ends the grid") {
            with(it.testScope) {
                val inputs = playStationInputs()
                val hdmi1 = inputs.rows[0] as InputRow.Port
                presenter(flowOf(inputs), InputTilesOn).test {
                    holdOn(inputs, hdmi1)
                    awaitMatching { it.inputMenu != null }

                    events.send(HomeEvent.InputMenuPick(MenuRow.AddToHome))

                    val pinned = awaitMatching { !it.inputsOpen && it.grid.lastOrNull()?.input != null }
                    pins shouldBe listOf(InputTile("tv/.Hdmi/HW1", "tv/.Hdmi/HW1", InputIcon.Hdmi) to "HDMI 1")
                    pinned.inputMenu.shouldBeNull()
                    pinned.grid.last().key shouldBe "input:tv/.Hdmi/HW1"
                    pinned.focusRequest?.target shouldBe INPUTS_FOCUS
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}
