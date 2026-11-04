package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
    var focusedKey by remember { mutableStateOf<String?>(null) }
    val appList by sources.apps.collectAsState(NoApps)
    val currentAppList by rememberUpdatedState(appList)
    val topBarApps by sources.topBarApps.collectAsState(NoTopBarApps)
    val currentTopBarApps by rememberUpdatedState(topBarApps)
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is HomeEvent.Focus -> {
                    focusedKey = event.key
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
            }
        }
    }
    val layout = remember(config) { config.homeLayout() }
    val homeApps = remember(appList, layout) { HomeApps(appList, layout) }
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
        focusedKey,
        locale,
        header,
        network,
        topBarApps,
        wallpaper,
        cinematic = config.cinematicHome,
        lastOpened = lastOpened,
    )
}
