package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.Color
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale

val HomeInteractionTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<HomeEvent>(Channel.UNLIMITED)

            val eventFlow = events.receiveAsFlow()

            val effects = Channel<HomeEffect>(Channel.UNLIMITED)

            val saves = mutableListOf<Pair<List<String>, Int>>()

            var saveWorks = true

            val removed = mutableListOf<String>()

            var removeWorks = true

            fun removeFromHome(pkg: String): Boolean {
                removed += pkg
                return removeWorks
            }

            fun saveOrder(
                keys: List<String>,
                docked: Int,
            ): Boolean {
                saves += keys to docked
                return saveWorks
            }

            fun presenter(
                config: TvConfig = SixColumns,
                apps: MutableStateFlow<AppList> = MutableStateFlow(AppList(NineApps)),
                appInfo: Boolean = true,
                nativeSettings: Boolean = false,
                powerMenu: Boolean = false,
                focusMemory: FocusMemory = FocusMemory(),
            ): Flow<HomeUiState> {
                val sources =
                    sources(
                        apps = apps,
                        hasAppInfo = { appInfo },
                        hasNativeSettings = { nativeSettings },
                        powerMenuAvailable = { powerMenu },
                        saveOrder = ::saveOrder,
                        removeFromHome = ::removeFromHome,
                        focusMemory = focusMemory,
                    )
                return moleculeFlow(RecompositionMode.Immediate) {
                    homePresenter(eventFlow, sources, config, effects::trySend, Locale.ENGLISH, "EEEE d MMMM")
                }
            }

            fun keys(vararg n: Int) = n.map { "pkg.$it" }
        }
    } asContextForEach {
        test("a dock tile menu replaces and a grid tile menu adds") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.2", held = true))
                    val dock = awaitMatching { it.menu != null }.menu
                    dock shouldBe HomeMenu("pkg.2", listOf(MenuRow.ReplaceInDock, MenuRow.Move, MenuRow.AppInfo))

                    events.send(HomeEvent.Back)
                    awaitMatching { it.menu == null }

                    events.send(HomeEvent.TileMenu("pkg.8", held = false))
                    val grid = awaitMatching { it.menu != null }.menu
                    grid shouldBe HomeMenu("pkg.8", listOf(MenuRow.AddToDock, MenuRow.Move, MenuRow.AppInfo))
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("with the app library the tile menu removes the app from home and asks for focus back on its tile") {
            with(it.testScope) {
                presenter(SixColumns.copy(appLibrary = true)).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.8", held = true))
                    val menu = awaitMatching { it.menu != null }.menu
                    menu?.rows shouldBe listOf(MenuRow.AddToDock, MenuRow.Move, MenuRow.RemoveFromHome, MenuRow.AppInfo)

                    events.send(HomeEvent.MenuPick(MenuRow.RemoveFromHome))
                    val closed = awaitMatching { it.menu == null }

                    removed shouldBe listOf("pkg.8")
                    closed.focusRequest?.target shouldBe "pkg.8"
                    effects.tryReceive().isFailure shouldBe true
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a removal that was not saved says so") {
            with(it.testScope) {
                removeWorks = false
                presenter(SixColumns.copy(appLibrary = true)).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.2", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.RemoveFromHome))

                    effects.receive() shouldBe HomeEffect.LibrarySaveFailed
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("without the app library the tile menu has no remove row") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.8", held = true))

                    awaitMatching { it.menu != null }.menu?.rows?.contains(MenuRow.RemoveFromHome) shouldBe false
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the rows follow dock row and the apps info page") {
            with(it.testScope) {
                presenter(SixColumns.copy(dockRow = false), appInfo = false).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.2", held = true))
                    awaitMatching { it.menu != null }.menu?.rows shouldBe listOf(MenuRow.Move)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("without tile menu a held ok opens app info and the menu key does nothing") {
            with(it.testScope) {
                presenter(SixColumns.copy(tileMenu = false)).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.2", held = false))
                    events.send(HomeEvent.TileMenu("pkg.3", held = true))
                    effects.receive() shouldBe HomeEffect.OpenAppInfo("pkg.3")
                    expectNoEvents()
                    effects.tryReceive().isFailure shouldBe true
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the settings menu lists launcher settings first then the dock and reorder rows and native settings when installed") {
            with(it.testScope) {
                presenter(nativeSettings = true).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.SettingsMenu)
                    val menu = awaitMatching { it.menu != null }.menu
                    menu shouldBe
                        HomeMenu(null, listOf(MenuRow.LauncherSettings, MenuRow.NativeSettings, MenuRow.EditDock, MenuRow.Reorder))
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the settings menu ends with restart while the key service is bound") {
            with(it.testScope) {
                presenter(powerMenu = true).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.SettingsMenu)
                    val menu = awaitMatching { it.menu != null }.menu
                    menu shouldBe
                        HomeMenu(
                            null,
                            listOf(MenuRow.LauncherSettings, MenuRow.EditDock, MenuRow.Reorder, MenuRow.PowerMenu),
                        )
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("picking restart asks the host for the power menu and hands focus back to settings") {
            with(it.testScope) {
                presenter(powerMenu = true).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.SettingsMenu)
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.PowerMenu))

                    val state = awaitMatching { it.menu == null && it.focusRequest != null }

                    effects.receive() shouldBe HomeEffect.OpenPowerMenu
                    state.focusRequest?.target shouldBe SETTINGS_FOCUS
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("back closes the menu and asks for focus on its tile") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.8", held = true))
                    awaitMatching { it.menu != null }

                    events.send(HomeEvent.Back)

                    val closed = awaitMatching { it.menu == null && it.focusRequest != null }
                    closed.focusRequest?.target shouldBe "pkg.8"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("picking app info opens it and hands focus back to the tile") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.8", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.AppInfo))

                    val state = awaitMatching { it.menu == null && it.focusRequest != null }

                    effects.receive() shouldBe HomeEffect.OpenAppInfo("pkg.8")
                    state.focusRequest?.target shouldBe "pkg.8"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("move picks the tile up and the arrows carry it into the dock and the order is saved on back") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.8", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.Move))
                    val started = awaitMatching { it.reorder?.carried == "pkg.8" }
                    started.dock.map { it.key } shouldBe keys(1, 2, 3, 4, 5, 6)

                    events.send(HomeEvent.ReorderMove(Arrow.Up))
                    val moved = awaitMatching { it.dock.getOrNull(1)?.key == "pkg.8" }
                    moved.dock.map { it.key } shouldBe keys(1, 8, 2, 3, 4, 5)
                    moved.focusRequest?.target shouldBe "pkg.8"
                    saves shouldBe emptyList<Pair<List<String>, Int>>()

                    events.send(HomeEvent.ReorderOk)
                    events.send(HomeEvent.Back)
                    val done = awaitMatching { it.reorder == null }

                    saves shouldBe listOf(keys(1, 8, 2, 3, 4, 5, 6, 7, 9) to -1)
                    done.dock.map { it.key } shouldBe keys(1, 8, 2, 3, 4, 5)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a session that moved nothing saves nothing") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.3"))
                    events.send(HomeEvent.ToggleReorder)
                    awaitMatching { it.reorder != null }
                    events.send(HomeEvent.ReorderOk)
                    awaitMatching { it.reorder?.carried == "pkg.3" }
                    events.send(HomeEvent.ReorderOk)
                    awaitMatching { it.reorder?.carried == null }
                    events.send(HomeEvent.Back)
                    awaitMatching { it.reorder == null }

                    saves shouldBe emptyList<Pair<List<String>, Int>>()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the reorder button toggles the mode and back ends it at once when nothing is carried") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.2"))
                    events.send(HomeEvent.ToggleReorder)
                    val on = awaitMatching { it.reorder != null }
                    on.reorder?.hint shouldBe ReorderHint.Reorder
                    on.focusRequest?.target shouldBe "pkg.2"

                    events.send(HomeEvent.ToggleReorder)
                    (awaitMatching { it.reorder == null }.reorder).shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("home stopping ends the mode and saves the order so far") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.2", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.Move))
                    awaitMatching { it.reorder?.carried == "pkg.2" }
                    events.send(HomeEvent.ReorderMove(Arrow.Right))
                    awaitMatching { it.dock.getOrNull(2)?.key == "pkg.2" }

                    events.send(HomeEvent.Stopped)
                    awaitMatching { it.reorder == null }

                    saves shouldBe listOf(keys(1, 3, 2, 4, 5, 6, 7, 8, 9) to -1)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the home key closes an open tile menu") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.2", held = true))
                    awaitMatching { it.menu != null }

                    events.send(HomeEvent.HomePressed)

                    (awaitMatching { it.menu == null }.menu).shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the home key ends a reorder that moved nothing without saving") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.2", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.Move))
                    awaitMatching { it.reorder?.carried == "pkg.2" }

                    events.send(HomeEvent.HomePressed)

                    (awaitMatching { it.reorder == null }.reorder).shouldBeNull()
                    saves shouldBe emptyList<Pair<List<String>, Int>>()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the home key ends a reorder and keeps the order so far") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.2", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.Move))
                    awaitMatching { it.reorder?.carried == "pkg.2" }
                    events.send(HomeEvent.ReorderMove(Arrow.Right))
                    awaitMatching { it.dock.getOrNull(2)?.key == "pkg.2" }

                    events.send(HomeEvent.HomePressed)

                    (awaitMatching { it.reorder == null }.reorder).shouldBeNull()
                    saves shouldBe listOf(keys(1, 3, 2, 4, 5, 6, 7, 8, 9) to -1)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("an app installed during the mode ends it and keeps the order so far") {
            with(it.testScope) {
                val apps = MutableStateFlow(AppList(NineApps))
                presenter(apps = apps).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.2", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.Move))
                    awaitMatching { it.reorder?.carried == "pkg.2" }
                    events.send(HomeEvent.ReorderMove(Arrow.Right))
                    awaitMatching { it.dock.getOrNull(2)?.key == "pkg.2" }

                    apps.value = AppList(NineApps.drop(1) + NineApps.first().copy(key = "pkg.10", packageName = "pkg.10"))
                    awaitMatching { it.reorder == null }

                    saves shouldBe listOf(keys(1, 3, 2, 4, 5, 6, 7, 8, 9) to -1)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a failed write is reported and the new order stays on screen") {
            with(it.testScope) {
                saveWorks = false
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.2", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.Move))
                    awaitMatching { it.reorder?.carried == "pkg.2" }
                    events.send(HomeEvent.ReorderMove(Arrow.Right))
                    events.send(HomeEvent.ReorderOk)
                    events.send(HomeEvent.Back)

                    val done = awaitMatching { it.reorder == null && it.dock.getOrNull(2)?.key == "pkg.2" }

                    effects.receive() shouldBe HomeEffect.OrderNotSaved
                    done.shouldNotBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("with fill dock off the saved dock size leaves the free slots after the dock") {
            with(it.testScope) {
                val config = SixColumns.copy(fillDock = false)
                presenter(config, MutableStateFlow(AppList(NineApps, docked = 3))).test {
                    val before = awaitMatching { it.dock.isNotEmpty() }
                    before.dock.map { it.key } shouldBe keys(1, 2, 3)

                    events.send(HomeEvent.TileMenu("pkg.7", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.Move))
                    val session = awaitMatching { it.reorder != null }
                    session.dock.map { it.key } shouldBe keys(1, 2, 3, 4, 5, 6)

                    events.send(HomeEvent.ReorderMove(Arrow.Up))
                    awaitMatching { it.dock.contains(NineApps[6]) }
                    events.send(HomeEvent.ReorderOk)
                    events.send(HomeEvent.Back)
                    val done = awaitMatching { it.reorder == null }

                    saves shouldBe listOf(keys(7, 1, 2, 3, 4, 5, 6, 8, 9) to 4)
                    done.dock.map { it.key } shouldBe keys(7, 1, 2, 3)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("add to dock asks for a place then swaps the app into it and saves") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.8", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.AddToDock))
                    val choosing = awaitMatching { it.reorder != null }
                    choosing.reorder?.hint shouldBe ReorderHint.DockPlaceForApp
                    choosing.focusRequest?.target shouldBe "pkg.1"

                    events.send(HomeEvent.Focus("pkg.3"))
                    events.send(HomeEvent.ReorderOk)
                    val done = awaitMatching { it.reorder == null }

                    saves shouldBe listOf(keys(1, 2, 8, 4, 5, 6, 7, 3, 9) to -1)
                    done.dock.map { it.key } shouldBe keys(1, 2, 8, 4, 5, 6)
                    done.focusRequest?.target shouldBe "pkg.8"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("replace in dock keeps the place then takes the app chosen in the grid") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.2", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.ReplaceInDock))
                    val choosing = awaitMatching { it.reorder != null }
                    choosing.reorder?.hint shouldBe ReorderHint.DockChooseApp
                    choosing.reorder?.dimmed shouldBe setOf("pkg.1", "pkg.3", "pkg.4", "pkg.5", "pkg.6")

                    events.send(HomeEvent.Focus("pkg.9"))
                    events.send(HomeEvent.ReorderOk)
                    val done = awaitMatching { it.reorder == null }

                    saves shouldBe listOf(keys(1, 9, 3, 4, 5, 6, 7, 8, 2) to -1)
                    done.dock.map { it.key } shouldBe keys(1, 9, 3, 4, 5, 6)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("back in edit dock steps back from the app to the place and then leaves without saving") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.SettingsMenu)
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.EditDock))
                    awaitMatching { it.reorder?.hint == ReorderHint.DockChoosePlace }

                    events.send(HomeEvent.Focus("pkg.2"))
                    events.send(HomeEvent.ReorderOk)
                    awaitMatching { it.reorder?.hint == ReorderHint.DockChooseApp }
                    events.send(HomeEvent.Back)
                    val back = awaitMatching { it.reorder?.hint == ReorderHint.DockChoosePlace }
                    back.focusRequest?.target shouldBe "pkg.2"
                    events.send(HomeEvent.Back)
                    awaitMatching { it.reorder == null }

                    saves shouldBe emptyList<Pair<List<String>, Int>>()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("with fewer than two tiles move starts nothing and focus goes back to the tile") {
            with(it.testScope) {
                presenter(apps = MutableStateFlow(AppList(NineApps.take(1)))).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.1", held = true))
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.Move))

                    val state = awaitMatching { it.menu == null && it.focusRequest != null }

                    state.reorder.shouldBeNull()
                    state.focusRequest?.target shouldBe "pkg.1"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("no menu opens for an app that is not a tile") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("gone", held = true))
                    events.send(HomeEvent.Focus("pkg.4"))
                    (awaitMatching { it.focusedLabel == "App 4" }.menu).shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a home created again returns to the tile that had focus") {
            with(it.testScope) {
                val memory = FocusMemory()
                presenter(focusMemory = memory).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.8"))
                    awaitMatching { it.focusedKey == "pkg.8" }
                    cancelAndIgnoreRemainingEvents()
                }

                presenter(focusMemory = memory).test {
                    awaitMatching { it.dock.isNotEmpty() }.focusedKey shouldBe "pkg.8"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a home created again without that app takes the tile at its place or the last one") {
            with(it.testScope) {
                val memory = FocusMemory()
                presenter(focusMemory = memory).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.8"))
                    awaitMatching { it.focusedKey == "pkg.8" }
                    cancelAndIgnoreRemainingEvents()
                }

                presenter(apps = MutableStateFlow(AppList(NineApps.filterNot { it.key == "pkg.8" })), focusMemory = memory).test {
                    awaitMatching { it.dock.isNotEmpty() }.focusedKey shouldBe "pkg.9"
                    cancelAndIgnoreRemainingEvents()
                }
                presenter(apps = MutableStateFlow(AppList(NineApps.take(3))), focusMemory = memory).test {
                    awaitMatching { it.dock.isNotEmpty() }.focusedKey shouldBe "pkg.3"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the home key sends the memory back to the first tile") {
            with(it.testScope) {
                val memory = FocusMemory()
                presenter(focusMemory = memory).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.8"))
                    awaitMatching { it.focusedKey == "pkg.8" }

                    events.send(HomeEvent.HomePressed)

                    awaitMatching { it.focusedKey == "pkg.1" }.focusedKey shouldBe "pkg.1"
                    cancelAndIgnoreRemainingEvents()
                }

                presenter(focusMemory = memory).test {
                    awaitMatching { it.dock.isNotEmpty() }.focusedKey shouldBe "pkg.1"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("home stopping keeps the memory") {
            with(it.testScope) {
                val memory = FocusMemory()
                presenter(focusMemory = memory).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.8"))
                    awaitMatching { it.focusedKey == "pkg.8" }
                    events.send(HomeEvent.Stopped)
                    cancelAndIgnoreRemainingEvents()
                }

                presenter(focusMemory = memory).test {
                    awaitMatching { it.dock.isNotEmpty() }.focusedKey shouldBe "pkg.8"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("tiles that lose their artwork and get it back keep their order and focus and save nothing") {
            with(it.testScope) {
                val apps = MutableStateFlow(AppList(NineApps.map { it.copy(accent = Color.Red) }))
                presenter(apps = apps).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.3"))
                    val before = awaitMatching { it.focusedKey == "pkg.3" && it.dock.first().accent == Color.Red }

                    apps.value = AppList(NineApps)
                    val trimmed = awaitMatching { it.dock.first().accent == Color.Unspecified }
                    apps.value = AppList(NineApps.map { it.copy(accent = Color.Blue) })
                    val reloaded = awaitMatching { it.dock.first().accent == Color.Blue }

                    for (state in listOf(trimmed, reloaded)) {
                        state.dock.map { it.key } shouldBe before.dock.map { it.key }
                        state.grid.map { it.key } shouldBe before.grid.map { it.key }
                        state.focusedKey shouldBe "pkg.3"
                    }
                    saves shouldBe emptyList<Pair<List<String>, Int>>()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}
