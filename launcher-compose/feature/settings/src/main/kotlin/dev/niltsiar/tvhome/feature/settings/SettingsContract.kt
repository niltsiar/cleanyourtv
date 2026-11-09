package dev.niltsiar.tvhome.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/** One row of Launcher settings that offers a set of options. */
public enum class SettingsRowId {
    Columns,
    RowsBelowDock,
    DockOpacity,
    ClockFormat,
    Animations,
    AlwaysShowNames,
    ShowGreeting,
    DateBelowClock,
    AppLibrary,
    LibraryLeastUsed,
    UnusedFadeDays,
    HomeIdleClockMinutes,
    BackOpensClock,
    ClockWallpaper,
    ReducedMotion,
    FocusOvershoot,
    HabitHint,
    InputArrival,
}

/** What an option says: a number or a string of the module. */
@Immutable
public sealed interface OptionLabel {
    public data class Text(
        val text: String,
    ) : OptionLabel

    public data class Res(
        @StringRes val id: Int,
    ) : OptionLabel
}

/** A row and its [options], left to right. [selected] is the index of the option the config holds, or -1 when none of them matches it. */
@Immutable
public data class SettingsRow(
    val id: SettingsRowId,
    @StringRes val label: Int,
    val options: List<OptionLabel>,
    val selected: Int,
)

/** Rows under one [heading]; the first group has none. */
@Immutable
public data class SettingsSection(
    @StringRes val heading: Int?,
    val rows: List<SettingsRow>,
)

/** What the screen shows. [followsTheme] is true while no choice is stored. [reorder] adds a Reorder row first (launcher_settings_button). */
@Immutable
public data class SettingsUiState(
    val sections: List<SettingsSection>,
    val followsTheme: Boolean,
    val reorder: Boolean = false,
)

internal sealed interface SettingsEvent {
    data class Pick(
        val row: SettingsRowId,
        val option: Int,
    ) : SettingsEvent

    data object FollowTheme : SettingsEvent
}
