package dev.niltsiar.tvhome.feature.home

import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import java.time.LocalDateTime

internal val NineApps = (1..9).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }

internal val Evening: LocalDateTime = LocalDateTime.of(2026, 11, 3, 20, 15)

internal val SixColumns = TvConfig(columns = 6)

internal fun homeApps(
    apps: List<AppEntry> = NineApps,
    docked: Int = 0,
    config: TvConfig = SixColumns,
) = HomeApps(AppList(apps, docked), config.homeLayout())
