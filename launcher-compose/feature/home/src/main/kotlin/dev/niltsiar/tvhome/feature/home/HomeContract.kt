package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import dev.niltsiar.tvhome.core.model.AppEntry
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val DOCK_SIZE = 6

@Immutable
public data class HomeUiState(
    val clock: String = "",
    val date: String = "",
    val dock: List<AppEntry> = emptyList(),
    val grid: List<AppEntry> = emptyList(),
    val focusedLabel: String = "",
)

/** One-shot results the host handles outside the UI state. */
public sealed interface HomeEffect {
    public data class LaunchApp(
        val packageName: String,
    ) : HomeEffect
}

internal sealed interface HomeEvent {
    data class Focus(
        val packageName: String,
    ) : HomeEvent

    data class Click(
        val packageName: String,
    ) : HomeEvent
}

/**
 * The app list split into the dock and the grid. Hosts remember one per app list, so a focus change or a clock tick
 * keeps both list instances and the screen can skip them.
 */
internal class HomeApps(
    val all: List<AppEntry>,
) {
    val dock: List<AppEntry> = all.take(DOCK_SIZE)
    val grid: List<AppEntry> = all.drop(DOCK_SIZE)
}

/** The mapping both hosts share; each host owns only how its inputs reach this call. */
internal fun homeUiState(
    apps: HomeApps,
    now: LocalDateTime?,
    focusedPackage: String?,
    locale: Locale,
): HomeUiState =
    HomeUiState(
        clock = now?.format(DateTimeFormatter.ofPattern("HH:mm", locale)).orEmpty(),
        date = now?.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).orEmpty(),
        dock = apps.dock,
        grid = apps.grid,
        focusedLabel = (apps.all.firstOrNull { it.packageName == focusedPackage } ?: apps.all.firstOrNull())?.label.orEmpty(),
    )
