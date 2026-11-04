package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        val app: AppEntry,
    ) : HomeEffect
}

internal sealed interface HomeEvent {
    data class Focus(
        val key: String,
    ) : HomeEvent

    data class Click(
        val key: String,
    ) : HomeEvent
}

/**
 * The app list split into the dock and the grid. [homePresenter] remembers one per app list, so a focus change or a
 * clock tick keeps both list instances and the screen can skip them.
 * The dock is the first row: the leading dock apps, then the slots dock_apps leaves free if fill_dock is off (the
 * screen leaves them empty), else the first [HomeLayout.columns] apps. single_row puts every app in the dock.
 */
internal class HomeApps(
    list: AppList,
    layout: HomeLayout,
) {
    val all: List<AppEntry> = list.apps
    val dock: List<AppEntry>
    val grid: List<AppEntry>

    init {
        val dockSize =
            when {
                layout.singleRow -> all.size
                emptyDockSlots(list.docked, layout.columns, all.size, layout.fillDock) > 0 -> list.docked
                else -> layout.columns
            }
        dock = all.take(dockSize)
        grid = all.drop(dockSize)
    }
}

/**
 * Empty slots to leave after [docked] dock apps in a dock of [dockSize], when fill_dock is off. None when the dock is
 * full, when no dock app is installed (the first row fills so Home still has a dock) or when no app follows.
 */
internal fun emptyDockSlots(
    docked: Int,
    dockSize: Int,
    tiles: Int,
    fill: Boolean,
): Int = if (fill || docked <= 0 || docked >= dockSize || tiles <= docked) 0 else dockSize - docked

/** The mapping from inputs to Home state. [homePresenter] only decides how its inputs reach this call. */
internal fun homeUiState(
    apps: HomeApps,
    now: LocalDateTime?,
    focusedKey: String?,
    locale: Locale,
): HomeUiState =
    HomeUiState(
        clock = now?.format(DateTimeFormatter.ofPattern("HH:mm", locale)).orEmpty(),
        date = now?.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).orEmpty(),
        dock = apps.dock,
        grid = apps.grid,
        focusedLabel = (apps.all.firstOrNull { it.key == focusedKey } ?: apps.all.firstOrNull())?.label.orEmpty(),
    )
