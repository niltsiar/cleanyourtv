package dev.niltsiar.tvhome.core.config

/** clock_format: the 12- or 24-hour clock whatever the TV is set to, or [System] for the TV's own. */
public enum class ClockFormat(
    internal val jsonValue: String,
) {
    System("system"),
    Hour12("12"),
    Hour24("24"),
}

/** A chosen 12 or 24 wins without asking the TV; [ClockFormat.System] asks [system] (the TV's own setting). */
public fun ClockFormat.is24Hour(system: () -> Boolean): Boolean =
    when (this) {
        ClockFormat.Hour24 -> true
        ClockFormat.Hour12 -> false
        ClockFormat.System -> system()
    }

/** wallpaper_order: how a wallpaper collection rotates. */
public enum class WallpaperOrder(
    internal val jsonValue: String,
) {
    Shuffle("shuffle"),
    Sequential("sequential"),
}

/** The time-of-day groups of wallpaper_moods. */
public enum class WallpaperMood(
    internal val jsonValue: String,
) {
    Morning("morning"),
    Evening("evening"),
    Night("night"),
}

/**
 * The theme keys the launcher supports, with the defaults of the reference launcher's config.xml (and
 * Config.java for the theme-only keys). Package names are plain strings. A blank optional string is null.
 * [place] comes from the latitude and longitude keys, which this launcher adds: the reference reads them from config.xml.
 */
public data class TvConfig(
    val dockApps: List<String> =
        listOf("com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.google.android.youtube.tv", "in.startv.hotstar"),
    val fillDock: Boolean = true,
    /** Apps never shown as tiles. The parser adds [topBarApps] here: a top-bar app leaves the grid. */
    val hiddenApps: List<String> = listOf("com.mediatek.wwtv.tvcenter", "com.mitv.gallery"),
    /** 1 to 8. */
    val columns: Int = 4,
    /** 0 to 3. */
    val rowsBelowDock: Int = 0,
    val singleRow: Boolean = false,
    /** Package to the action its tile sends instead of opening the start screen. */
    val launchActions: Map<String, String> = emptyMap(),
    /** Package to the name its tile shows instead of the app's label. */
    val appLabels: Map<String, String> = emptyMap(),
    /** Package to the name of a banner drawable its tile shows instead of the app's art. */
    val appBanners: Map<String, String> = emptyMap(),
    val topBarApps: List<String> = emptyList(),
    val greetingName: String? = null,
    val showGreeting: Boolean = true,
    val clockFormat: ClockFormat = ClockFormat.System,
    val dateBelowClock: Boolean = false,
    val showNetworkPill: Boolean = true,
    val showInputsPill: Boolean = true,
    /** Wi-Fi and Settings as round icon buttons, without their names. */
    val iconOnlyButtons: Boolean = false,
    /** 0 to 300 dp: with [iconOnlyButtons], the network name stays beside the Wi-Fi icon up to this width. */
    val wifiLabelWidth: Int = 0,
    /** The Wi-Fi icon turns green while the TV is online. */
    val wifiStatusColor: Boolean = false,
    /** With [wifiStatusColor]: orange while connected without internet, red while offline. */
    val wifiStatusWarnings: Boolean = false,
    /** Package of the VPN app the network button opens instead of Wi-Fi settings. */
    val vpnApp: String? = null,
    val cinematicHome: Boolean = false,
    /** Percent, 0 to 100. */
    val cinematicArt: Int = 50,
    /** Percent, 0 to 100. */
    val cinematicScrim: Int = 100,
    /** With [cinematicHome]: false leaves out the dock position dots under the app's name. */
    val cinematicDots: Boolean = true,
    /** With [cinematicHome]: the dock keeps the neutral focus highlight instead of the focused app's colour. */
    val cinematicWhiteFocus: Boolean = false,
    /** The glow in the focused app's colour behind the cinematic shelf. */
    val ambientGlow: Boolean = true,
    /** The glow takes a new app's colour in one frame instead of dipping and fading. */
    val glowSnap: Boolean = false,
    /** The focus highlight takes the focused app's colour on every tile, as the cinematic shelf does for the dock. */
    val appGlow: Boolean = false,
    /** Opaque ARGB. */
    val backgroundColor: Int = OPAQUE_BLACK,
    /** Whether the theme may show a wallpaper at all. */
    val wallpaper: Boolean = true,
    /** 0 to 1440; 0 shows one picture. */
    val wallpaperRotationMinutes: Int = 0,
    /** Percent, 0 to 90. */
    val wallpaperDim: Int = 0,
    val wallpaperOrder: WallpaperOrder = WallpaperOrder.Shuffle,
    /** Picture file names per mood; an empty map rotates through the whole collection. */
    val wallpaperMoods: Map<WallpaperMood, Set<String>> = emptyMap(),
    val wallpaperCaptions: Boolean = false,
    /** Decode wallpapers in 32-bit colour instead of RGB_565. */
    val wallpaperTrueColor: Boolean = false,
    /** Percent, 0 to 100; 0 shows no time-of-day sky. */
    val skyIntensity: Int = 0,
    /** Where the TV is, for the sun's times; null uses fixed times. */
    val place: Place? = null,
    /** The first row is a dock: its tiles can be replaced from the tile menu and the Settings menu. */
    val dockRow: Boolean = true,
    val animations: Boolean = true,
    val reducedMotion: Boolean = false,
    val focusOvershoot: Boolean = true,
    val tileMenu: Boolean = true,
    /** A top-bar button turns reorder mode on and off. */
    val reorderButton: Boolean = false,
    val appLibrary: Boolean = false,
    /** With [appLibrary]: OK in the library changes an app's Home shortcut, never opens it. */
    val appLibrarySelection: Boolean = false,
    /** With [appLibrary]: a round top-bar button opens the library. */
    val libraryButton: Boolean = false,
    val libraryApps: List<String> = emptyList(),
    val libraryExcludedApps: List<String> = emptyList(),
    val showLibraryPill: Boolean = true,
    val libraryOkOpens: Boolean = false,
    val inputSources: List<String> = emptyList(),
    val inputAction: String? = null,
    val inputUriScheme: String? = null,
    val liveTvPackage: String? = "com.mediatek.wwtv.tvcenter",
    val liveTvAction: String? = null,
    val liveTvInputId: String? = null,
    val bootToLiveTv: Boolean = false,
    /** The service or activity ("package/class") of the TV's own source panel, for TVs with no TvInputManager list. */
    val sourcePanelService: String? = null,
    val sourcePanelAction: String? = null,
    /** One int extra for the source panel, "name=value". */
    val sourcePanelExtra: String? = null,
    /** List Coocaa OS (Skyworth) sources in the Inputs menu when TvInputManager lists nothing. */
    val coocaaSources: Boolean = false,
    /** Ports with nothing connected, and the AV jacks, fold behind "More inputs". */
    val inputsConnectedOnly: Boolean = false,
    /** The TV puts port states on the wrong port: ports show none, CEC device rows show their own. */
    val inputsCecStateOnly: Boolean = false,
    /** Answer the system's VIEW_INPUTS request by opening the Inputs menu. */
    val handleViewInputs: Boolean = false,
    /** Seconds the boot's app waits for a validated network, 0 to 600; 0 opens it at once. */
    val openOnStartNetworkWait: Int = 0,
    /** "keycode:package" or "KEYCODE_NAME:package" entries, parsed where the key service reads them. */
    val appKeys: List<String> = emptyList(),
    val settingsKeyKeycodes: List<Int> = emptyList(),
    /** Raw scancodes of remote buttons that open Settings, for remotes that send no usable keycode. */
    val settingsKeyScancodes: List<Int> = emptyList(),
    /** More remote buttons that open the input switcher, taken by the key service whatever [captureInputKeys] says. */
    val inputKeyKeycodes: List<Int> = emptyList(),
    /** The key service takes KEYCODE_TV and KEYCODE_TV_INPUT to open the input switcher; off leaves them to the TV. */
    val captureInputKeys: Boolean = false,
    /** False keeps the key service from filtering key events, for firmware that loses key repeat once any service filters. */
    val keyFilter: Boolean = true,
    /** With [keyFilter]: the key service stops filtering while a game controller is connected. */
    val gamepadPassthrough: Boolean = false,
    /** The key service swallows Volume Up and Down and repeats a held one itself. */
    val consumeVolumeKeys: Boolean = false,
    /** Package of the stock launcher whose home screen is sent back here; null leaves it alone. */
    val stockHomeRedirect: String? = null,
    /** The key service takes every Home press, a held one too, instead of Android's long-press action. */
    val homeLongPressHome: Boolean = false,
    val powerOnHome: Boolean = false,
    val powerOnHomeAlways: Boolean = false,
    /** Minutes Home may sit untouched before the clock screensaver, 0 to 1440; 0 is off. */
    val homeIdleClockMinutes: Int = 0,
    /** Back on Home opens the clock screensaver instead of doing nothing. */
    val backOpensClock: Boolean = false,
    val clockWallpaper: Boolean = false,
    /** Percent, 0 to 90. */
    val clockWallpaperDim: Int = 50,
    val clockGlow: Boolean = true,
)

/** A point on Earth: [latitude] -90 to 90 and [longitude] -180 to 180, in degrees. */
public data class Place(
    val latitude: Double,
    val longitude: Double,
)

internal const val OPAQUE_BLACK: Int = 0xFF000000.toInt()

/**
 * What [parseTvConfig] found. [unknownKeys] are in neither the supported nor the unsupported set.
 * [unsupportedKeys] are keys the reference launcher knows and this one does not use: not an error.
 * [rejectedKeys] are supported keys whose value had a bad type or range, so the default stayed.
 * [overlayApplied] is false when the text was too large or not a JSON object, and [config] is the defaults.
 */
public data class ParsedConfig(
    val config: TvConfig,
    val unknownKeys: List<String>,
    val unsupportedKeys: List<String>,
    val rejectedKeys: List<String>,
    val overlayApplied: Boolean,
)
