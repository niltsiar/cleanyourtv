package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.OpenedAt
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class HomePresenterTest {
    private val events = Channel<HomeEvent>(Channel.UNLIMITED)
    private val eventFlow = events.receiveAsFlow()
    private val effects = Channel<HomeEffect>(Channel.UNLIMITED)

    private fun presenter(
        sources: HomeSources = sources(),
        config: TvConfig = SixColumns,
    ) = moleculeFlow(RecompositionMode.Immediate) {
        homePresenter(eventFlow, sources, config, effects::trySend, Locale.ENGLISH, "EEEE d MMMM")
    }

    @Test
    fun focusing_a_dock_app_updates_the_shelf_label() =
        runTest {
            presenter().test {
                events.send(HomeEvent.Focus("pkg.4"))
                assertEquals("App 4", awaitMatching { it.focusedLabel == "App 4" }.focusedLabel)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_clock_tick_updates_the_clock_text() =
        runTest {
            val clock = MutableSharedFlow<LocalDateTime>(replay = 1)
            clock.emit(Evening)
            presenter(sources(clock = clock)).test {
                assertEquals("20:15", awaitMatching { it.clock.isNotEmpty() }.clock)

                clock.emit(Evening.plusMinutes(1))
                assertEquals("20:16", awaitMatching { it.clock == "20:16" }.clock)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_apps_flow_fills_the_dock_and_grid() =
        runTest {
            presenter().test {
                val state = awaitMatching { it.dock.isNotEmpty() }
                assertEquals(NineApps, state.dock + state.grid)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_focus_change_keeps_the_dock_and_grid_lists() =
        runTest {
            presenter().test {
                val before = awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Focus("pkg.4"))
                val after = awaitMatching { it.focusedLabel == "App 4" }
                assertSame(before.dock, after.dock)
                assertSame(before.grid, after.grid)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clicking_an_app_emits_a_launch_effect_for_its_package() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.Click("pkg.2"))
                assertEquals(HomeEffect.LaunchApp(NineApps[1]), effects.receive())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clicking_a_top_bar_app_emits_a_launch_effect_for_it() =
        runTest {
            val chat = AppEntry(key = "com.chat/.Main", packageName = "com.chat", label = "Chat")
            presenter(sources(topBarApps = flowOf(listOf(chat)))).test {
                awaitMatching { it.topBarApps.isNotEmpty() }
                events.send(HomeEvent.Click("com.chat/.Main"))
                assertEquals(HomeEffect.LaunchApp(chat), effects.receive())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_settings_and_network_buttons_emit_their_effects() =
        runTest {
            presenter().test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.OpenSettings)
                assertEquals(HomeEffect.OpenSettings, effects.receive())
                events.send(HomeEvent.OpenNetwork)
                assertEquals(HomeEffect.OpenNetwork, effects.receive())
                events.send(HomeEvent.OpenWifiSettings)
                assertEquals(HomeEffect.OpenWifiSettings, effects.receive())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun with_launcher_settings_button_the_settings_button_opens_the_page_and_not_the_tv_settings() =
        runTest {
            presenter(config = SixColumns.copy(launcherSettingsButton = true)).test {
                awaitMatching { it.dock.isNotEmpty() }
                events.send(HomeEvent.OpenSettings)

                assertEquals(HomeEffect.OpenLauncherSettings, effects.receive())
                assertEquals(true, effects.tryReceive().isFailure)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_network_flow_reaches_the_state() =
        runTest {
            val online = NetworkState(NetworkLink.Ethernet, validated = true)
            presenter(sources(network = flowOf(online))).test {
                assertEquals(online, awaitMatching { it.network == online }.network)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun a_cinematic_theme_shows_when_the_focused_dock_app_was_last_opened() =
        runTest {
            val now = Evening.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val fiveMinutesAgo = now - 5 * 60_000L
            val opened = flowOf(mapOf("pkg.2" to fiveMinutesAgo))
            presenter(sources(lastOpened = opened), SixColumns.copy(cinematicHome = true)).test {
                events.send(HomeEvent.Focus("pkg.2"))
                assertEquals(OpenedAt(fiveMinutesAgo, now, hour24 = true), awaitMatching { it.shelf?.lastOpened != null }.shelf?.lastOpened)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_default_theme_has_no_shelf() =
        runTest {
            presenter().test {
                events.send(HomeEvent.Focus("pkg.2"))
                assertNull(awaitMatching { it.focusedLabel == "App 2" }.shelf)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun the_wallpaper_flow_reaches_the_state() =
        runTest {
            val dusk = Wallpaper(caption = "Dusk")
            presenter(sources(wallpaper = flowOf(dusk))).test {
                assertEquals(dusk, awaitMatching { it.wallpaper == dusk }.wallpaper)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun an_invalid_time_hides_the_clock_block_until_a_valid_one_arrives() =
        runTest {
            val clock = MutableSharedFlow<LocalDateTime?>(replay = 1)
            clock.emit(null)
            presenter(sources(clock = clock)).test {
                val hidden = awaitMatching { it.dock.isNotEmpty() }
                assertEquals(listOf("", "", "", null), listOf(hidden.clock, hidden.amPm, hidden.date, hidden.greeting))

                clock.emit(Evening)
                val shown = awaitMatching { it.clock.isNotEmpty() }
                assertEquals(DayPart.Evening, shown.greeting?.part)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clock_format_system_follows_the_tv_and_a_chosen_format_does_not() =
        runTest {
            presenter(sources(systemHour24 = { false }), TvConfig(columns = 6, clockFormat = ClockFormat.System)).test {
                val state = awaitMatching { it.clock.isNotEmpty() }
                assertEquals("8:15" to "PM", state.clock to state.amPm)
                cancelAndIgnoreRemainingEvents()
            }
            presenter(
                sources(systemHour24 = { error("the TV is not asked") }),
                TvConfig(columns = 6, clockFormat = ClockFormat.Hour24),
            ).test {
                assertEquals("20:15", awaitMatching { it.clock.isNotEmpty() }.clock)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

internal suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
