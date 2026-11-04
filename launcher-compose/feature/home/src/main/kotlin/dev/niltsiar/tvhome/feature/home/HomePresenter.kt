package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.flow.Flow
import java.util.Locale

private val NoApps = AppList()
private val NoTopBarApps = emptyList<AppEntry>()
private val NoNetwork = NetworkState()
private val NoWallpaper = Wallpaper()
private val NoOpened = emptyMap<String, Long>()

/** Home's state logic for any host that runs a composable: Molecule today. It uses the Compose runtime only. */
@Composable
internal fun homePresenter(
    events: Flow<HomeEvent>,
    sources: HomeSources,
    config: TvConfig,
    onEffect: (HomeEffect) -> Unit,
    locale: Locale,
): HomeUiState {
    val appList by sources.apps.collectAsState(NoApps)
    val currentAppList by rememberUpdatedState(appList)
    val topBarApps by sources.topBarApps.collectAsState(NoTopBarApps)
    val currentTopBarApps by rememberUpdatedState(topBarApps)
    val currentOnEffect by rememberUpdatedState(onEffect)
    val layout = remember(config) { config.homeLayout() }
    val interaction = remember(sources, config) { HomeInteraction(sources, config) { currentOnEffect(it) } }
    val session = interaction.session
    val saved = interaction.saved?.takeIf { it.base === appList }
    val shownList =
        remember(appList, saved, session?.keys) {
            when {
                session != null -> appList.arranged(session.keys, 0)
                saved != null -> appList.arranged(saved.change.keys, saved.change.docked)
                else -> appList
            }
        }
    // A session shows every dock slot filled, so that each place holds a tile that can move.
    val homeApps =
        remember(shownList, layout, session != null) {
            HomeApps(
                shownList,
                if (session !=
                    null
                ) {
                    layout.copy(fillDock = true)
                } else {
                    layout
                },
            )
        }
    interaction.list = appList
    interaction.shown = homeApps
    LaunchedEffect(appList) { interaction.listChanged() }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is HomeEvent.Focus -> {
                    interaction.focusedKey = event.key
                }

                is HomeEvent.Click -> {
                    val app =
                        currentAppList.apps.firstOrNull { it.key == event.key }
                            ?: currentTopBarApps.firstOrNull { it.key == event.key }
                    if (app != null) currentOnEffect(HomeEffect.LaunchApp(app))
                }

                HomeEvent.OpenSettings -> {
                    currentOnEffect(HomeEffect.OpenSettings)
                }

                HomeEvent.OpenNetwork -> {
                    currentOnEffect(HomeEffect.OpenNetwork)
                }

                else -> {
                    interaction.handle(event)
                }
            }
        }
    }
    val now by sources.clock.collectAsState(null)
    val network by sources.network.collectAsState(NoNetwork)
    val wallpaper by sources.wallpaper.collectAsState(NoWallpaper)
    val lastOpened by sources.lastOpened.collectAsState(NoOpened)
    // Read again with each new time, which also follows the TV's 12/24-hour setting after a stop and start of Home.
    val hour24 = remember(now, config.clockFormat) { config.clockFormat.is24Hour(sources.systemHour24) }
    val header =
        remember(hour24, config, sources.datePattern) {
            HeaderStyle(hour24, sources.datePattern, config.showGreeting, config.greetingName)
        }
    return homeUiState(
        homeApps,
        now,
        interaction.focusedKey,
        locale,
        header,
        network,
        topBarApps,
        wallpaper,
        cinematic = config.cinematicHome,
        lastOpened = lastOpened,
        menu = interaction.menu,
        reorder = remember(session) { session?.view() },
        focusRequest = interaction.focusRequest,
    )
}
