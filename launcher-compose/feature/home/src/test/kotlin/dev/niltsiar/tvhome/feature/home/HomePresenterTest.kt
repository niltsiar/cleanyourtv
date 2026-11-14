package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.OpenedAt
import dev.niltsiar.tvhome.core.model.Wallpaper
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

val HomePresenterTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<HomeEvent>(Channel.UNLIMITED)

            val eventFlow = events.receiveAsFlow()

            val effects = Channel<HomeEffect>(Channel.UNLIMITED)

            fun presenter(
                sources: HomeSources = sources(),
                config: TvConfig = SixColumns,
            ) = moleculeFlow(RecompositionMode.Immediate) {
                homePresenter(eventFlow, sources, config, effects::trySend, Locale.ENGLISH, "EEEE d MMMM")
            }
        }
    } asContextForEach {
        test("focusing a dock app updates the shelf label") {
            with(it.testScope) {
                presenter().test {
                    events.send(HomeEvent.Focus("pkg.4"))
                    awaitMatching { it.focusedLabel == "App 4" }.focusedLabel shouldBe "App 4"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a clock tick updates the clock text") {
            with(it.testScope) {
                val clock = MutableSharedFlow<LocalDateTime>(replay = 1)
                clock.emit(Evening)
                presenter(sources(clock = clock)).test {
                    awaitMatching { it.clock.isNotEmpty() }.clock shouldBe "20:15"

                    clock.emit(Evening.plusMinutes(1))
                    awaitMatching { it.clock == "20:16" }.clock shouldBe "20:16"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the apps flow fills the dock and grid") {
            with(it.testScope) {
                presenter().test {
                    val state = awaitMatching { it.dock.isNotEmpty() }
                    state.dock + state.grid shouldBe NineApps
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a focus change keeps the dock and grid lists") {
            with(it.testScope) {
                presenter().test {
                    val before = awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Focus("pkg.4"))
                    val after = awaitMatching { it.focusedLabel == "App 4" }
                    after.dock shouldBeSameInstanceAs before.dock
                    after.grid shouldBeSameInstanceAs before.grid
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("clicking an app emits a launch effect for its package") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.Click("pkg.2"))
                    effects.receive() shouldBe HomeEffect.LaunchApp(NineApps[1])
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("clicking a top bar app emits a launch effect for it") {
            with(it.testScope) {
                val chat = AppEntry(key = "com.chat/.Main", packageName = "com.chat", label = "Chat")
                presenter(sources(topBarApps = flowOf(listOf(chat)))).test {
                    awaitMatching { it.topBarApps.isNotEmpty() }
                    events.send(HomeEvent.Click("com.chat/.Main"))
                    effects.receive() shouldBe HomeEffect.LaunchApp(chat)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the settings and network buttons emit their effects") {
            with(it.testScope) {
                presenter().test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.OpenSettings)
                    effects.receive() shouldBe HomeEffect.OpenSettings
                    events.send(HomeEvent.OpenNetwork)
                    effects.receive() shouldBe HomeEffect.OpenNetwork
                    events.send(HomeEvent.OpenWifiSettings)
                    effects.receive() shouldBe HomeEffect.OpenWifiSettings
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("with launcher settings button the settings button opens the page and not the tv settings") {
            with(it.testScope) {
                presenter(config = SixColumns.copy(launcherSettingsButton = true)).test {
                    awaitMatching { it.dock.isNotEmpty() }
                    events.send(HomeEvent.OpenSettings)

                    effects.receive() shouldBe HomeEffect.OpenLauncherSettings
                    effects.tryReceive().isFailure shouldBe true
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the network flow reaches the state") {
            with(it.testScope) {
                val online = NetworkState(NetworkLink.Ethernet, validated = true)
                presenter(sources(network = flowOf(online))).test {
                    awaitMatching { it.network == online }.network shouldBe online
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a cinematic theme shows when the focused dock app was last opened") {
            with(it.testScope) {
                val now = Evening.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val fiveMinutesAgo = now - 5 * 60_000L
                val opened = flowOf(mapOf("pkg.2" to fiveMinutesAgo))
                presenter(sources(lastOpened = opened), SixColumns.copy(cinematicHome = true)).test {
                    events.send(HomeEvent.Focus("pkg.2"))
                    awaitMatching { it.shelf?.lastOpened != null }.shelf?.lastOpened shouldBe OpenedAt(fiveMinutesAgo, now, hour24 = true)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the default theme has no shelf") {
            with(it.testScope) {
                presenter().test {
                    events.send(HomeEvent.Focus("pkg.2"))
                    awaitMatching { it.focusedLabel == "App 2" }.shelf.shouldBeNull()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the wallpaper flow reaches the state") {
            with(it.testScope) {
                val dusk = Wallpaper(caption = "Dusk")
                presenter(sources(wallpaper = flowOf(dusk))).test {
                    awaitMatching { it.wallpaper == dusk }.wallpaper shouldBe dusk
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("an invalid time hides the clock block until a valid one arrives") {
            with(it.testScope) {
                val clock = MutableSharedFlow<LocalDateTime?>(replay = 1)
                clock.emit(null)
                presenter(sources(clock = clock)).test {
                    val hidden = awaitMatching { it.dock.isNotEmpty() }
                    listOf(hidden.clock, hidden.amPm, hidden.date, hidden.greeting) shouldBe listOf("", "", "", null)

                    clock.emit(Evening)
                    val shown = awaitMatching { it.clock.isNotEmpty() }
                    shown.greeting?.part shouldBe DayPart.Evening
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("clock format system follows the tv and a chosen format does not") {
            with(it.testScope) {
                presenter(sources(systemHour24 = { false }), TvConfig(columns = 6, clockFormat = ClockFormat.System)).test {
                    val state = awaitMatching { it.clock.isNotEmpty() }
                    (state.clock to state.amPm) shouldBe ("8:15" to "PM")
                    cancelAndIgnoreRemainingEvents()
                }
                presenter(
                    sources(systemHour24 = { error("the TV is not asked") }),
                    TvConfig(columns = 6, clockFormat = ClockFormat.Hour24),
                ).test {
                    awaitMatching { it.clock.isNotEmpty() }.clock shouldBe "20:15"
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}

internal suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
