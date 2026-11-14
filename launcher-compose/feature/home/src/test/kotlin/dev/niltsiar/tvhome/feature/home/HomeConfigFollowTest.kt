package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale

/** A choice stored on the TV reaches Home's state through the flow, with the one presenter that was there before it. */
val HomeConfigFollowTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<HomeEvent>(Channel.UNLIMITED)

            val config = MutableStateFlow(TvConfig(columns = 7))

            val memory = FocusMemory()

            val saves = mutableListOf<Pair<List<String>, Int>>()

            fun presenter(libraryCount: Int? = null): Flow<HomeUiState> {
                val sources =
                    sources(
                        focusMemory = memory,
                        saveOrder = { keys, docked -> saves.add(keys to docked) },
                        libraryCount = libraryCount?.let(::flowOf) ?: emptyFlow(),
                    )
                return moleculeFlow(RecompositionMode.Immediate) {
                    homePresenter(events.receiveAsFlow(), sources, config, {}, Locale.ENGLISH, "EEEE d MMMM")
                }
            }
        }
    } asContextForEach {
        test("the columns resize the dock and the grid") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }.dock.size shouldBe 7

                    config.value = config.value.copy(columns = 4)

                    val state = awaitMatching { it.dock.size == 4 }
                    state.grid.size shouldBe 5
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the clock format changes the clock text") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.clock.isNotEmpty() }.clock shouldBe "20:15"

                    config.value = config.value.copy(clockFormat = ClockFormat.Hour12)

                    val state = awaitMatching { it.amPm.isNotEmpty() }
                    state.clock shouldBe "8:15"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the greeting follows show greeting") {
            with(it.testScope) {
                presenter().test {
                    (awaitMatching { it.greeting != null }.greeting).shouldNotBeNull()

                    config.value = config.value.copy(showGreeting = false)

                    (awaitMatching { it.greeting == null }.greeting).shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the library count shows only while the app library is on") {
            with(it.testScope) {
                config.value = config.value.copy(appLibrary = true)
                presenter(libraryCount = 12).test {
                    awaitMatching { it.libraryCount != null }.libraryCount shouldBe 12

                    config.value = config.value.copy(appLibrary = false)

                    (awaitMatching { it.libraryCount == null }.libraryCount).shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("an unrelated choice asks for no focus and keeps the lists") {
            with(it.testScope) {
                presenter().test {
                    events.send(HomeEvent.Focus("pkg.7"))
                    val before = awaitMatching { it.focusedKey == "pkg.7" }

                    config.value = config.value.copy(clockFormat = ClockFormat.Hour12)

                    val after = awaitMatching { it.amPm.isNotEmpty() }
                    after.focusRequest.shouldBeNull()
                    after.dock shouldBe before.dock
                    after.grid shouldBe before.grid
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("fewer columns with focus on the seventh tile land focus on that tile in the grid") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.7"))
                    awaitMatching { it.focusedKey == "pkg.7" }
                    memory.slot shouldBe 6

                    config.value = config.value.copy(columns = 4)

                    val state = awaitMatching { it.dock.size == 4 && it.focusRequest != null }
                    state.focusedKey shouldBe "pkg.7"
                    (state.grid.any { it.key == "pkg.7" }).shouldBeTrue()
                    state.focusRequest?.target shouldBe "pkg.7"
                    memory.slot shouldBeIn NineApps.indices
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("fewer columns end a reorder that is on and keep its order and focus") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.7"))
                    awaitMatching { it.focusedKey == "pkg.7" }
                    events.send(HomeEvent.ToggleReorder)
                    awaitMatching { it.reorder != null }

                    config.value = config.value.copy(columns = 4)

                    val state = awaitMatching { it.dock.size == 4 && it.reorder == null }
                    state.focusedKey shouldBe "pkg.7"
                    state.focusRequest?.target shouldBe "pkg.7"
                    saves shouldBe emptyList<Pair<List<String>, Int>>()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("fewer columns end an edit of the dock that is on") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.7"))
                    awaitMatching { it.focusedKey == "pkg.7" }
                    events.send(HomeEvent.SettingsMenu)
                    awaitMatching { it.menu != null }
                    events.send(HomeEvent.MenuPick(MenuRow.EditDock))
                    awaitMatching { it.reorder?.hint == ReorderHint.DockChoosePlace }

                    config.value = config.value.copy(columns = 4)

                    val state = awaitMatching { it.dock.size == 4 && it.reorder == null }
                    state.focusedKey shouldBe "pkg.7"
                    state.focusRequest?.target shouldBe "pkg.7"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("an open menu closes when the columns change") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.TileMenu("pkg.7", held = true))
                    awaitMatching { it.menu != null }

                    config.value = config.value.copy(columns = 4)

                    awaitMatching { it.dock.size == 4 && it.menu == null }.dock.size shouldBe 4
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a choice made while home is stopped applies when it starts") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }.dock.size shouldBe 7
                    events.send(HomeEvent.Stopped)
                    events.send(HomeEvent.Focus("pkg.2"))
                    awaitMatching { it.focusedKey == "pkg.2" }

                    config.value = config.value.copy(columns = 4)
                    expectNoEvents()

                    events.send(HomeEvent.Started)
                    awaitMatching { it.dock.size == 4 }.dock.size shouldBe 4
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("every state carries the config its dock and grid are split for") {
            with(it.testScope) {
                presenter().test {
                    val states = mutableListOf(awaitMatching { it.dock.isNotEmpty() })
                    events.send(HomeEvent.Stopped)
                    events.send(HomeEvent.Focus("pkg.2"))
                    states += awaitMatching { it.focusedKey == "pkg.2" }
                    config.value = config.value.copy(columns = 4)
                    events.send(HomeEvent.Focus("pkg.3"))
                    states += awaitMatching { it.focusedKey == "pkg.3" }
                    events.send(HomeEvent.Started)
                    while (states.last().dock.size != 4) states += awaitItem()

                    states.first().config.columns shouldBe 7
                    states[2].config.columns shouldBe 7
                    states.last().config.columns shouldBe 4
                    states.forEach { it.dock.size shouldBe it.config.columns }
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("closing launcher settings with other columns focuses the settings button in the new layout") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.7"))
                    awaitMatching { it.focusedKey == "pkg.7" }
                    events.send(HomeEvent.Stopped)
                    config.value = config.value.copy(columns = 4)
                    events.send(HomeEvent.Started)
                    events.send(HomeEvent.SettingsClosed(reorder = false))

                    val state = awaitMatching { it.focusRequest?.target == SETTINGS_FOCUS }

                    state.dock.size shouldBe 4
                    state.config.columns shouldBe 4
                    state.reorder.shouldBeNull()
                    events.send(HomeEvent.Focus("pkg.2"))
                    awaitMatching { it.focusedKey == "pkg.2" }.focusRequest?.target shouldBe SETTINGS_FOCUS
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("closing launcher settings with names toggled focuses the settings button") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.3"))
                    awaitMatching { it.focusedKey == "pkg.3" }
                    events.send(HomeEvent.Stopped)
                    config.value = config.value.copy(alwaysShowNames = true)
                    events.send(HomeEvent.Started)
                    events.send(HomeEvent.SettingsClosed(reorder = false))

                    val state = awaitMatching { it.focusRequest?.target == SETTINGS_FOCUS }

                    state.config.alwaysShowNames.shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the return from launcher settings waits for home to start") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Stopped)
                    config.value = config.value.copy(columns = 4)
                    events.send(HomeEvent.SettingsClosed(reorder = false))
                    events.send(HomeEvent.Focus("pkg.2"))

                    val waiting = awaitMatching { it.focusedKey == "pkg.2" }
                    waiting.focusRequest.shouldBeNull()
                    waiting.dock.size shouldBe 7

                    events.send(HomeEvent.Started)

                    val state = awaitMatching { it.focusRequest?.target == SETTINGS_FOCUS }
                    state.dock.size shouldBe 4
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the reorder row of launcher settings starts reorder mode in the new layout with the usual reorder focus") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.7"))
                    awaitMatching { it.focusedKey == "pkg.7" }
                    events.send(HomeEvent.Stopped)
                    config.value = config.value.copy(columns = 4)
                    events.send(HomeEvent.Started)
                    events.send(HomeEvent.SettingsClosed(reorder = true))

                    val state = awaitMatching { it.reorder != null }

                    state.dock.size shouldBe 4
                    state.focusRequest?.target shouldBe "pkg.7"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}
