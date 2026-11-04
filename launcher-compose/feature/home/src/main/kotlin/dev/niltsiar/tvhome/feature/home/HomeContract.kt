package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.flow.Flow
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
)

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
