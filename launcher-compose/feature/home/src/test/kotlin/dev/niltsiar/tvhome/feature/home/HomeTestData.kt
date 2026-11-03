package dev.niltsiar.tvhome.feature.home

import dev.niltsiar.tvhome.core.model.AppEntry
import java.time.LocalDateTime

internal val NineApps = (1..9).map { AppEntry(packageName = "pkg.$it", label = "App $it") }

internal val Evening: LocalDateTime = LocalDateTime.of(2026, 11, 3, 20, 15)
