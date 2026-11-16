package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.NowPlayingItem
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** A pinned input's tile on Home: what OK and its menu do, and what its live state leaves alone. */
val HomeInputTilesTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<HomeEvent>(Channel.UNLIMITED)
            val effects = mutableListOf<HomeEffect>()
            val unpinned = mutableListOf<String>()
            val removed = mutableListOf<String>()
            val sessionReads = mutableListOf<String>()
            val inputs = MutableStateFlow(playStationInputs())

            /** Eight apps, then the PlayStation's tile, in the grid. */
            val inGrid = NineApps.take(8) + PlayStationTile

            /** The PlayStation's tile second in the dock. */
            val inDock = listOf(NineApps[0], PlayStationTile) + NineApps.drop(1)

            fun TestScope.home(
                apps: List<AppEntry> = inGrid,
                config: TvConfig = SixColumns,
                lastOpened: Map<String, Long> = emptyMap(),
                openedSince: Long? = null,
                timeMillis: () -> Long = System::currentTimeMillis,
            ): StateFlow<HomeUiState> {
                val sources =
                    sources(
                        apps = flowOf(AppList(apps)),
                        inputs = inputs,
                        lastOpened = flowOf(lastOpened),
                        openedSince = flowOf(openedSince),
                        timeMillis = timeMillis,
                        removeFromHome = { removed.add(it) },
                        unpinInput = { unpinned.add(it) },
                        nowPlaying = { pkg ->
                            sessionReads += pkg
                            flowOf(NowPlayingItem(pkg, "Song", null, paused = false, art = null))
                        },
                    )
                val flow =
                    moleculeFlow(RecompositionMode.Immediate) {
                        homePresenter(events.receiveAsFlow(), sources, config, { effects += it }, Locale.ENGLISH, "EEEE d MMMM")
                    }
                return flow.stateIn(backgroundScope, SharingStarted.Eagerly, HomeUiState()).also { runCurrent() }
            }

            suspend fun TestScope.send(event: HomeEvent) {
                events.send(event)
                runCurrent()
            }

            fun TestScope.pass(millis: Long) {
                advanceTimeBy(millis)
                runCurrent()
            }

            /** Picks the PlayStation's tile up with Move from its tile menu. */
            suspend fun TestScope.carryTile() {
                send(HomeEvent.TileMenu(PlayStationTile.key, held = true))
                send(HomeEvent.MenuPick(MenuRow.Move))
            }
        }
    } asContextForEach {
        test("OK on an input tile switches to its input and records no open") {
            with(it.testScope) {
                val home = home(config = SixColumns.copy(pressFeedback = true))

                send(HomeEvent.Click(PlayStationTile.key))

                effects shouldBe listOf(HomeEffect.OpenInput(InputTarget.TvInput(PlayStationId)))
                home.value.opening.shouldBeNull()
            }
        }

        test("OK on an input tile that is not listed switches nothing and asks for the not-available toast") {
            with(it.testScope) {
                inputs.value = playStationInputs(listed = false)
                home()

                send(HomeEvent.Click(PlayStationTile.key))

                effects shouldBe listOf(HomeEffect.InputNotAvailable)
            }
        }

        test("the tile menu of an input tile offers Remove from Home without the app library and never App info") {
            with(it.testScope) {
                val home = home(config = SixColumns.copy(appLibrary = false))

                send(HomeEvent.TileMenu(PlayStationTile.key, held = true))

                val rows =
                    home.value.menu
                        .shouldNotBeNull()
                        .rows
                rows shouldContain MenuRow.RemoveFromHome
                rows shouldContain MenuRow.Move
                rows shouldNotContain MenuRow.AppInfo
            }
        }

        test("a held OK on an input tile with the tile menu off opens nothing") {
            with(it.testScope) {
                val home = home(config = SixColumns.copy(tileMenu = false))

                send(HomeEvent.TileMenu(PlayStationTile.key, held = true))

                effects.shouldBeEmpty()
                home.value.menu.shouldBeNull()
            }
        }

        test("Remove from Home on an input tile unpins it") {
            with(it.testScope) {
                home()
                send(HomeEvent.TileMenu(PlayStationTile.key, held = true))

                send(HomeEvent.MenuPick(MenuRow.RemoveFromHome))

                unpinned shouldBe listOf(PlayStationId)
                removed.shouldBeEmpty()
                effects.shouldBeEmpty()
            }
        }

        test("an input state change does not end a Move session") {
            with(it.testScope) {
                val home = home()
                carryTile()
                home.value.reorder?.carried shouldBe PlayStationTile.key

                inputs.value = playStationInputs(connection = InputConnection.Standby)
                runCurrent()

                home.value.inputTiles[PlayStationTile.key]?.connection shouldBe InputConnection.Standby
                home.value.reorder?.carried shouldBe PlayStationTile.key
            }
        }

        test("a live name change does not end a Move session") {
            with(it.testScope) {
                val home = home()
                carryTile()

                inputs.value = playStationInputs(label = null)
                runCurrent()

                home.value.inputTiles[PlayStationTile.key]?.label shouldBe "HDMI 2"
                home.value.reorder?.carried shouldBe PlayStationTile.key
            }
        }

        test("an input tile in the grid never fades as unused") {
            with(it.testScope) {
                val today = LocalDateTime.of(2026, 11, 6, 20, 0).atZone(ZoneId.systemDefault())
                val home =
                    home(
                        config = SixColumns.copy(unusedFadeDays = 60),
                        openedSince = today.minusDays(200).toInstant().toEpochMilli(),
                        timeMillis = { today.toInstant().toEpochMilli() },
                    )

                home.value.faded shouldContainAll listOf("pkg.7", "pkg.8")
                home.value.faded shouldNotContain PlayStationTile.key
            }
        }

        test("a docked input tile has no app shelf") {
            with(it.testScope) {
                val home = home(apps = inDock, config = SixColumns.copy(appShelf = true, nowPlaying = true))
                send(HomeEvent.ShelfRoom(true))

                send(HomeEvent.Focus(PlayStationTile.key))
                pass(10 * SHELF_HOLD_MS)

                home.value.appShelf.shouldBeNull()
                sessionReads.shouldBeEmpty()
            }
        }
    }
}
