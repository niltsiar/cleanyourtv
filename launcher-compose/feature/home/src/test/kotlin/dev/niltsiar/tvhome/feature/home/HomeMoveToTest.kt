package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale

private val MoveTo = SixColumns.copy(moveToMenu = true)

/** move_to_menu on the presenter: the places listed, the order each one saves, focus and Back. Nine apps, six columns. */
val HomeMoveToTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<HomeEvent>(Channel.UNLIMITED)

            val eventFlow = events.receiveAsFlow()

            val saves = mutableListOf<Pair<List<String>, Int>>()

            fun presenter(
                config: TvConfig = MoveTo,
                docked: Int = 0,
            ): Flow<HomeUiState> {
                val sources =
                    sources(
                        apps = flowOf(AppList(NineApps, docked)),
                        saveOrder = { keys, count -> saves.add(keys to count) },
                    )
                return moleculeFlow(RecompositionMode.Immediate) {
                    homePresenter(eventFlow, sources, config, {}, Locale.ENGLISH, "EEEE d MMMM")
                }
            }

            fun keys(vararg n: Int) = n.map { "pkg.$it" }

            /** Opens the tile menu of [key] and then its "Move to" list; returns the places listed. */
            suspend fun ReceiveTurbine<HomeUiState>.places(key: String): List<MenuRow> {
                events.send(HomeEvent.TileMenu(key, held = true))
                awaitMatching { it.menu != null }
                events.send(HomeEvent.MenuPick(MenuRow.MoveTo))
                return awaitMatching { it.menu?.parent != null }.menu?.rows.orEmpty()
            }

            /** Moves [key] to [place] through the menu; returns the first state with the menu closed and focus asked for. */
            suspend fun ReceiveTurbine<HomeUiState>.moveTo(
                key: String,
                place: MenuRow,
            ): HomeUiState {
                places(key)
                events.send(HomeEvent.MenuPick(place))
                return awaitMatching { it.menu == null && it.focusRequest?.target == key && saves.isNotEmpty() }
            }
        }
    } asContextForEach {
        test("the tile menu has move to right after move") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.8", held = true))

                    awaitMatching { it.menu != null }.menu?.rows shouldBe
                        listOf(MenuRow.AddToDock, MenuRow.Move, MenuRow.MoveTo, MenuRow.AppInfo)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("move to lists only the places that would change where the app is") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    places("pkg.1") shouldBe listOf(MenuRow.DockEnd, MenuRow.GridStart, MenuRow.GridEnd)
                    events.send(HomeEvent.Back)
                    events.send(HomeEvent.Back)
                    awaitMatching { it.menu == null }

                    places("pkg.9") shouldBe listOf(MenuRow.DockStart, MenuRow.DockEnd, MenuRow.GridStart)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("an app put at the end of a full dock pushes the dock's last app to the start of the grid") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    val moved = moveTo("pkg.8", MenuRow.DockEnd)

                    saves shouldBe listOf(keys(1, 2, 3, 4, 5, 8, 6, 7, 9) to -1)
                    moved.dock.map { it.key } shouldBe keys(1, 2, 3, 4, 5, 8)
                    moved.grid.map { it.key } shouldBe keys(6, 7, 9)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("with fill dock off an app put at the end of the dock takes a free place and pushes nothing") {
            with(it.testScope) {
                presenter(MoveTo.copy(fillDock = false), docked = 2).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    val moved = moveTo("pkg.5", MenuRow.DockEnd)

                    saves shouldBe listOf(keys(1, 2, 5, 3, 4, 6, 7, 8, 9) to 3)
                    moved.dock.map { it.key } shouldBe keys(1, 2, 5)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a dock app put at the start of the grid lets the grid's first app move up into the dock") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    val moved = moveTo("pkg.2", MenuRow.GridStart)

                    saves shouldBe listOf(keys(1, 3, 4, 5, 6, 7, 2, 8, 9) to -1)
                    moved.grid.first().key shouldBe "pkg.2"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("with fill dock off a dock app put at the start of the grid leaves its dock place free") {
            with(it.testScope) {
                presenter(MoveTo.copy(fillDock = false), docked = 3).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    val moved = moveTo("pkg.2", MenuRow.GridStart)

                    saves shouldBe listOf(keys(1, 3, 2, 4, 5, 6, 7, 8, 9) to 2)
                    moved.dock.map { it.key } shouldBe keys(1, 3)
                    moved.grid.first().key shouldBe "pkg.2"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("an app put at the end of the grid is last and focus goes to it") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    val moved = moveTo("pkg.1", MenuRow.GridEnd)

                    saves shouldBe listOf(keys(2, 3, 4, 5, 6, 7, 8, 9, 1) to -1)
                    moved.grid.last().key shouldBe "pkg.1"
                    moved.focusRequest?.target shouldBe "pkg.1"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("back from the places returns to the tile menu and back again closes it") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    places("pkg.8")
                    events.send(HomeEvent.Back)
                    awaitMatching { it.menu?.parent == null }.menu shouldBe
                        HomeMenu("pkg.8", listOf(MenuRow.AddToDock, MenuRow.Move, MenuRow.MoveTo, MenuRow.AppInfo))

                    events.send(HomeEvent.Back)
                    awaitMatching { it.menu == null }.focusRequest?.target shouldBe "pkg.8"
                    saves shouldBe emptyList()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("without the dock row move to lists the grid places only") {
            with(it.testScope) {
                presenter(MoveTo.copy(dockRow = false)).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    places("pkg.4") shouldBe listOf(MenuRow.GridStart, MenuRow.GridEnd)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("with a single row the dock is the row's first six places") {
            with(it.testScope) {
                presenter(MoveTo.copy(singleRow = true)).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    places("pkg.8") shouldBe listOf(MenuRow.DockStart, MenuRow.DockEnd, MenuRow.GridStart, MenuRow.GridEnd)
                    events.send(HomeEvent.MenuPick(MenuRow.DockStart))
                    awaitMatching { it.menu == null && saves.isNotEmpty() }

                    saves shouldBe listOf(keys(8, 1, 2, 3, 4, 5, 6, 7, 9) to -1)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("without the key the tile menu has no move to row") {
            with(it.testScope) {
                presenter(SixColumns).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.8", held = true))

                    awaitMatching { it.menu != null }.menu?.rows shouldBe listOf(MenuRow.AddToDock, MenuRow.Move, MenuRow.AppInfo)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}
