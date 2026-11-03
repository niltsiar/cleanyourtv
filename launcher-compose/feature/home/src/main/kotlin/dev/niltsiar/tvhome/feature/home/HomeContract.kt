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

/** The mapping both hosts share; each host owns only how its inputs reach this call. */
internal fun homeUiState(
    apps: List<AppEntry>,
    now: LocalDateTime?,
    focusedPackage: String?,
    locale: Locale,
): HomeUiState =
    HomeUiState(
        clock = now?.format(DateTimeFormatter.ofPattern("HH:mm", locale)).orEmpty(),
        date = now?.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).orEmpty(),
        dock = apps.take(DOCK_SIZE),
        grid = apps.drop(DOCK_SIZE),
        focusedLabel = (apps.firstOrNull { it.packageName == focusedPackage } ?: apps.firstOrNull())?.label.orEmpty(),
    )
