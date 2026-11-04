package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the screen shows. [clock], [amPm], [date] and [greeting] are empty or null until the TV's time is valid.
 * [topBarApps] are the top_bar_apps that can be opened. [wallpaper] is the picture behind Home and its caption.
 * [shelf] is the cinematic shelf's app while focus is on a dock tile, null otherwise and without cinematic_home.
 * [menu] is the open tile or Settings menu, [reorder] a reorder or edit-dock session, and [focusRequest] asks the
 * screen to move focus to a tile or the Settings button. [inputs] is what the Inputs button and its list show, which
 * is [inputsOpen] while the list hangs under the button; the button shows until the TV says it has nothing to list.
 */
@Immutable
public data class HomeUiState(
    val clock: String = "",
    val amPm: String = "",
    val date: String = "",
    val greeting: Greeting? = null,
    val network: NetworkState = NetworkState(),
    val topBarApps: List<AppEntry> = emptyList(),
    val dock: List<AppEntry> = emptyList(),
    val grid: List<AppEntry> = emptyList(),
    val focusedLabel: String = "",
    val wallpaper: Wallpaper = Wallpaper(),
    val shelf: Shelf? = null,
    val menu: HomeMenu? = null,
    val reorder: ReorderView? = null,
    val focusRequest: FocusRequest? = null,
    val inputs: InputsMenu = InputsMenu(available = true),
    val inputsOpen: Boolean = false,
)

/** Focus goes to the tile with this key, or to the Settings button for [SETTINGS_FOCUS]. A new [token] asks again. */
@Immutable
public data class FocusRequest(
    val target: String,
    val token: Int,
)

/** The [FocusRequest.target] of the Settings button: a tile key always has a slash. */
public const val SETTINGS_FOCUS: String = "settings"

/** The [FocusRequest.target] of the Inputs button. */
public const val INPUTS_FOCUS: String = "inputs"

/** The [FocusRequest.target] of the library pill after the last row. */
public const val LIBRARY_FOCUS: String = "library"

/** The [FocusRequest.target] of the library button in the top bar. */
public const val LIBRARY_BUTTON_FOCUS: String = "library-button"

/**
 * The dock app the cinematic shelf shows: its [key] and [label], the [accent] colour of its art, its [position] among
 * the [count] dock apps (one dot each) and [lastOpened], the relative time Home last opened it, null if it never did.
 */
@Immutable
public data class Shelf(
    val key: String,
    val label: String,
    val accent: Color,
    val position: Int,
    val count: Int,
    val lastOpened: String?,
)

/** One-shot results the host handles outside the UI state. */
public sealed interface HomeEffect {
    public data class LaunchApp(
        val app: AppEntry,
    ) : HomeEffect

    /** The Settings button was pressed: open the TV's system settings. */
    public data object OpenSettings : HomeEffect

    /** The network button was pressed: open the VPN app or Wi-Fi settings. */
    public data object OpenNetwork : HomeEffect

    /** Open the TV's App info page for a package. */
    public data class OpenAppInfo(
        val packageName: String,
    ) : HomeEffect

    /** Open the vendor settings app the Settings menu offers. */
    public data object OpenNativeSettings : HomeEffect

    /** Switch to what an Inputs row stands for: a TV input, a Coocaa source, the source panel or Live TV. */
    public data class OpenInput(
        val target: InputTarget,
    ) : HomeEffect

    /** The Inputs button was pressed and the TV has nothing to list. */
    public data object NoInputs : HomeEffect

    /** The new tile order could not be written. */
    public data object OrderNotSaved : HomeEffect

    /** The choice to take an app off Home could not be written. */
    public data object LibrarySaveFailed : HomeEffect
}

/** What the host feeds Home besides the theme: its data sources and two system formats. */
public class HomeSources(
    public val apps: Flow<AppList>,
    /** The local time, again each minute; null while the TV's time is not valid. */
    public val clock: Flow<LocalDateTime?>,
    public val network: Flow<NetworkState>,
    public val topBarApps: Flow<List<AppEntry>>,
    public val wallpaper: Flow<Wallpaper>,
    /** The date_format pattern for [DateTimeFormatter]. */
    public val datePattern: String,
    /** Whether the TV is set to the 24-hour clock; read when the clock_format is system. */
    public val systemHour24: () -> Boolean,
    /** Tile key to the milliseconds since the epoch of the last time Home opened it. */
    public val lastOpened: Flow<Map<String, Long>> = flowOf(emptyMap()),
    /** The softened art of a tile for the cinematic shelf; null for a tile without art. */
    public val heroArt: suspend (String) -> ImageBitmap? = { null },
    /** Whether the TV has an App info page for a package. */
    public val hasAppInfo: (String) -> Boolean = { false },
    /** Whether a vendor settings app is installed, for the Settings menu's "Native settings". */
    public val hasNativeSettings: () -> Boolean = { false },
    /** Keeps the tile order: keys first to last, and with fill_dock off how many lead the dock (else -1). False when it was not written. */
    public val saveOrder: suspend (List<String>, Int) -> Boolean = { _, _ -> true },
    /** Takes a package off Home, where the library keeps that choice. False when it was not written. */
    public val removeFromHome: suspend (String) -> Boolean = { true },
    /** The Inputs button and its list; never emits for a TV that cannot say, which leaves the button showing. */
    public val inputs: Flow<InputsMenu> = emptyFlow(),
)

internal sealed interface HomeEvent {
    data class Focus(
        val key: String,
    ) : HomeEvent

    data class Click(
        val key: String,
    ) : HomeEvent

    data object OpenSettings : HomeEvent

    data object OpenNetwork : HomeEvent

    /** A held OK or the MENU key on a tile. */
    data class TileMenu(
        val key: String,
        val held: Boolean,
    ) : HomeEvent

    data object SettingsMenu : HomeEvent

    /** OK on the Inputs button, or the system asked for the input list. */
    data object OpenInputs : HomeEvent

    data class PickInput(
        val target: InputTarget,
    ) : HomeEvent

    data class MenuPick(
        val row: MenuRow,
    ) : HomeEvent

    data object Back : HomeEvent

    data object ToggleReorder : HomeEvent

    data class ReorderMove(
        val arrow: Arrow,
    ) : HomeEvent

    data object ReorderOk : HomeEvent

    /** Home left the screen. */
    data object Stopped : HomeEvent
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

/** The theme and system choices that shape the clock block. */
internal data class HeaderStyle(
    val hour24: Boolean = true,
    val datePattern: String = "EEEE d MMMM",
    val showGreeting: Boolean = true,
    val greetingName: String? = null,
)

/** The mapping from inputs to Home state. [homePresenter] only decides how its inputs reach this call. */
internal fun homeUiState(
    apps: HomeApps,
    now: LocalDateTime?,
    focusedKey: String?,
    locale: Locale,
    header: HeaderStyle = HeaderStyle(),
    network: NetworkState = NetworkState(),
    topBarApps: List<AppEntry> = emptyList(),
    wallpaper: Wallpaper = Wallpaper(),
    cinematic: Boolean = false,
    lastOpened: Map<String, Long> = emptyMap(),
    zone: ZoneId = ZoneId.systemDefault(),
    menu: HomeMenu? = null,
    reorder: ReorderView? = null,
    focusRequest: FocusRequest? = null,
    inputs: InputsMenu = InputsMenu(available = true),
    inputsOpen: Boolean = false,
): HomeUiState =
    HomeUiState(
        clock = now?.let { clockText(it, header.hour24, locale) }.orEmpty(),
        amPm = now?.let { amPmText(it, header.hour24, locale) }.orEmpty(),
        date = now?.format(DateTimeFormatter.ofPattern(header.datePattern, locale)).orEmpty(),
        greeting = if (now != null && header.showGreeting) Greeting(dayPart(now.hour, locale), header.greetingName) else null,
        network = network,
        topBarApps = topBarApps,
        dock = apps.dock,
        grid = apps.grid,
        focusedLabel = (apps.all.firstOrNull { it.key == focusedKey } ?: apps.all.firstOrNull())?.label.orEmpty(),
        wallpaper = wallpaper,
        shelf = if (cinematic) shelfFor(apps, focusedKey, lastOpened, now, header.hour24, zone) else null,
        menu = menu,
        reorder = reorder,
        focusRequest = focusRequest,
        inputs = inputs,
        inputsOpen = inputsOpen,
    )

/**
 * The shelf for the focused app, resolved like the label: an unknown key gives the first app. Null when that app is
 * not in the dock. The "last opened" line waits for a valid time.
 */
internal fun shelfFor(
    apps: HomeApps,
    focusedKey: String?,
    lastOpened: Map<String, Long>,
    now: LocalDateTime?,
    hour24: Boolean,
    zone: ZoneId,
): Shelf? {
    val app = apps.all.firstOrNull { it.key == focusedKey } ?: apps.all.firstOrNull() ?: return null
    val position = apps.dock.indexOfFirst { it.key == app.key }
    if (position < 0) return null
    val opened = lastOpened[app.key]
    return Shelf(
        key = app.key,
        label = app.label,
        accent = app.accent,
        position = position,
        count = apps.dock.size,
        lastOpened =
            if (opened == null || now == null) {
                null
            } else {
                describeLastOpened(LocalDateTime.ofInstant(Instant.ofEpochMilli(opened), zone), now, hour24)
            },
    )
}

/**
 * [list] in the order of [keys]: the tiles of those keys first, then any other tiles (new installs) as they were.
 * [docked] is the new count of leading dock apps.
 */
internal fun AppList.arranged(
    keys: List<String>,
    docked: Int,
): AppList {
    val byKey = apps.associateBy { it.key }
    val ordered = keys.mapNotNull(byKey::get)
    val rest = apps.filter { it.key !in keys.toSet() }
    return AppList(ordered + rest, docked.coerceAtLeast(0))
}
