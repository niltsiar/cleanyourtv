package dev.niltsiar.tvhome.feature.home

import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDateTime

internal val NineApps = (1..9).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

internal val Evening: LocalDateTime = LocalDateTime.of(2026, 11, 3, 20, 15)

internal val SixColumns = TvConfig(columns = 6)

internal fun homeApps(
    apps: List<AppEntry> = NineApps,
    docked: Int = 0,
    config: TvConfig = SixColumns,
) = HomeApps(AppList(apps, docked), config.homeLayout())

internal fun sources(
    apps: Flow<AppList> = flowOf(AppList(NineApps)),
    clock: Flow<LocalDateTime?> = flowOf(Evening),
    network: Flow<NetworkState> = flowOf(NetworkState()),
    topBarApps: Flow<List<AppEntry>> = flowOf(emptyList()),
    wallpaper: Flow<Wallpaper> = flowOf(Wallpaper()),
    systemHour24: () -> Boolean = { true },
    lastOpened: Flow<Map<String, Long>> = flowOf(emptyMap()),
) = HomeSources(
    apps,
    clock,
    network,
    topBarApps,
    wallpaper,
    datePattern = "EEEE d MMMM",
    systemHour24 = systemHour24,
    lastOpened = lastOpened,
)
