package dev.niltsiar.tvhome.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import dev.niltsiar.tvhome.core.model.ShelfPublisher
import dev.niltsiar.tvhome.core.model.WallpaperPicture

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
    MoveToMenu,
    AppShelf,
    AppLibrary,
    LibraryLeastUsed,
    UnusedFadeDays,
    HomeIdleClockMinutes,
    BackOpensClock,
    ClockWallpaper,
    ReducedMotion,
    FocusOvershoot,
    PressFeedback,
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

/** Rows under one [heading]. */
@Immutable
public data class SettingsSection(
    @StringRes val heading: Int,
    val rows: List<SettingsRow>,
)

/** The picker over the page: Off, then [apps] by label. [selected] indexes that list, so 0 is Off. */
@Immutable
public data class OpenOnStartPicker(
    val apps: List<OpenOnStartApp>,
    val selected: Int,
)

/**
 * The picker over the page: Theme default, the theme's [pictures], then None. [entries] are what each of them is called, and
 * [selected] indexes them. [themeDefault] is the picture Home shows for Theme default, or null when it shows none.
 */
@Immutable
public data class WallpaperPicker(
    val entries: List<OptionLabel>,
    val pictures: List<WallpaperPicture>,
    val selected: Int,
    val themeDefault: WallpaperPicture? = null,
)

/**
 * One source of the shelf picker: the Watch Next rows of the app [packageName] when [channel] is null, else its preview channel
 * of that display name. [source] is the app_shelf_sources entry it ticks, and [items] how many rows it holds.
 */
@Immutable
public data class ShelfSourceEntry(
    val source: String,
    val packageName: String,
    val appLabel: String,
    val channel: String?,
    val items: Int,
)

/** The shelf sources picker over the page: [entries] in order, each ticked when its source is in [ticked]. */
@Immutable
public data class ShelfPicker(
    val entries: List<ShelfSourceEntry>,
    val ticked: Set<String>,
)

/**
 * What the screen shows. [followsTheme] is true while no choice is stored. [reorder] adds a Reorder row first
 * (launcher_settings_button). [openOnStart] is the value of the Open on start row, which follows the first section's rows
 * (the picked app's label, its package when it is gone, Off, or empty while the label loads); it is there with
 * open_on_start_choice, and [picker] is set while its picker is open. [wallpaper] is the value of the Wallpaper row, which
 * follows the Clock row (the picked picture's name, None, or Theme default); it is there when the theme has a picture, and
 * [wallpaperPicker] is set while its picker is open. [shelfSources] is the value of the Shelf sources row, which follows the App
 * shelf row while app_shelf is on (No access without READ_TV_LISTINGS, None, or how many are ticked), and [shelfPicker] is set
 * while its picker is open. [backdrop] is what Home draws where it has no picture: the theme's background_color.
 */
@Immutable
public data class SettingsUiState(
    val sections: List<SettingsSection>,
    val followsTheme: Boolean,
    val reorder: Boolean = false,
    val openOnStart: OptionLabel? = null,
    val picker: OpenOnStartPicker? = null,
    val wallpaper: OptionLabel? = null,
    val wallpaperPicker: WallpaperPicker? = null,
    val shelfSources: OptionLabel? = null,
    val shelfPicker: ShelfPicker? = null,
    val backdrop: Color = Color.Black,
)

/** What the page asks of the rest of the launcher for Open on start and Wallpaper. */
public class SettingsSources(
    /** The apps the picker offers, loaded off the main thread. The Open on start row names the pick by the label listed here. */
    public val openOnStartApps: suspend () -> List<OpenOnStartApp>,
    /** Makes the Home that follows a pick stay Home. */
    public val skipThisBoot: suspend () -> Unit,
    /** The theme's pictures, in the order the wallpaper picker lists them, loaded off the main thread. */
    public val wallpaperPictures: suspend () -> List<WallpaperPicture>,
    /** What the pickers show beside their entries. */
    public val art: PickerArt,
    /** The picture Home shows for the theme default, or null for none, loaded off the main thread. */
    public val themeDefaultPicture: suspend () -> WallpaperPicture?,
    /** The apps that publish rows for the shelf, loaded off the main thread. */
    public val shelfPublishers: suspend () -> List<ShelfPublisher> = { emptyList() },
    /** True when Home holds READ_TV_LISTINGS, which the shelf needs. */
    public val shelfAccess: () -> Boolean = { false },
)

/** The art beside the pickers' entries, loaded off the main thread at a width and height in pixels; null when there is none. */
public class PickerArt(
    /** A picture's centre, cropped to that size. */
    public val wallpaper: suspend (WallpaperPicture, Int, Int) -> ImageBitmap? = { _, _, _ -> null },
    /** An app's art as its tile on Home shows it, from its package name. */
    public val app: suspend (String, Int, Int) -> ImageBitmap? = { _, _, _ -> null },
)

/** One-shot results the host handles outside the UI state. */
public sealed interface SettingsEffect {
    /** The store refused a write. */
    public data object SaveFailed : SettingsEffect

    /** A picture or None was stored as the wallpaper. */
    public data object WallpaperApplied : SettingsEffect

    /** The shelf needs READ_TV_LISTINGS, which Home lacks: the host asks for it and then calls [SettingsActions.onShelfAccessAnswered]. */
    public data object AskShelfAccess : SettingsEffect

    /** The shelf sources picker has nothing to list: no installed app publishes rows. */
    public data object NoShelfSources : SettingsEffect
}

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

    data object OpenShelfPicker : SettingsEvent

    /** Ticks or unticks the source of the shelf picker's entry at an index. */
    data class ToggleShelfSource(
        val index: Int,
    ) : SettingsEvent

    /** The person answered the request for READ_TV_LISTINGS. */
    data object ShelfAccessAnswered : SettingsEvent
}
