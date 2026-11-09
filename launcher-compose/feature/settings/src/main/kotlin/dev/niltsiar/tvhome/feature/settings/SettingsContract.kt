package dev.niltsiar.tvhome.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.niltsiar.tvhome.core.model.OpenOnStartApp

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

/** The Open on start row, which follows the first section's rows. [value] is the picked app's label, its package when it is gone, or Off. */
@Immutable
public data class OpenOnStartRow(
    val value: OptionLabel,
)

/** The picker over the page: Off, then [apps] by label. [selected] indexes that list, so 0 is Off. */
@Immutable
public data class OpenOnStartPicker(
    val apps: List<OpenOnStartApp>,
    val selected: Int,
)

/** The Wallpaper row, which follows the Clock row. [value] is the picked picture's name, None, or Theme default. */
@Immutable
public data class WallpaperRow(
    val value: OptionLabel,
)

/** The picker over the page: Theme default, the theme's pictures, then None. [selected] indexes [entries]. */
@Immutable
public data class WallpaperPicker(
    val entries: List<OptionLabel>,
    val selected: Int,
)

/**
 * What the screen shows. [followsTheme] is true while no choice is stored. [reorder] adds a Reorder row first
 * (launcher_settings_button). [openOnStart] is there with open_on_start_choice, and [picker] while its picker is open.
 * [wallpaper] is there when the theme has a picture, and [wallpaperPicker] while its picker is open.
 */
@Immutable
public data class SettingsUiState(
    val sections: List<SettingsSection>,
    val followsTheme: Boolean,
    val reorder: Boolean = false,
    val openOnStart: OpenOnStartRow? = null,
    val picker: OpenOnStartPicker? = null,
    val wallpaper: WallpaperRow? = null,
    val wallpaperPicker: WallpaperPicker? = null,
)

/** What the page asks of the rest of the launcher for Open on start and Wallpaper. */
public class SettingsSources(
    /** The apps the picker offers, loaded off the main thread. */
    public val openOnStartApps: suspend () -> List<OpenOnStartApp>,
    /** The label of a package, null when it is gone. */
    public val appLabel: (String) -> String?,
    /** Makes the Home that follows a pick stay Home. */
    public val skipThisBoot: suspend () -> Unit,
    /** The file names of the theme's pictures, in the order the wallpaper picker lists them, loaded off the main thread. */
    public val wallpaperPictures: suspend () -> List<String>,
)

internal sealed interface SettingsEvent {
    data class Pick(
        val row: SettingsRowId,
        val option: Int,
    ) : SettingsEvent

    data object FollowTheme : SettingsEvent

    data object OpenPicker : SettingsEvent

    /** Closes whichever picker is open. */
    data object ClosePicker : SettingsEvent

    data object OpenWallpaperPicker : SettingsEvent

    /** The entry at an index of the wallpaper picker: 0 is Theme default, the last is None. */
    data class PickWallpaper(
        val index: Int,
    ) : SettingsEvent

    /** The entry at an index of the picker: 0 is Off. */
    data class PickStartApp(
        val index: Int,
    ) : SettingsEvent
}
