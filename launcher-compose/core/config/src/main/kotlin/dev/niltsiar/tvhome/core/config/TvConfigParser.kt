package dev.niltsiar.tvhome.core.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val LATITUDE = "latitude"
private const val LONGITUDE = "longitude"

/** The largest config.json that is read, in bytes; a bigger one is ignored as a whole. */
internal const val MAX_CONFIG_BYTES: Int = 64 * 1024

/**
 * The keys of the reference launcher's config.json that this launcher does not use (yet). A theme that
 * carries them is not broken, so they are listed apart from the unknown keys.
 */
private val unsupportedKeys: Set<String> =
    setOf(
        "accent_color",
        "advanced_effects",
        "app_banners",
        "dock_band_all",
        "fade_rows",
        "festival",
        "festival_until",
        "game_day",
        "glow_style",
        "greeting",
        "header_white",
        "icon_only_vpn",
        "locale",
        "lunar_date",
        "lunar_utc_offset",
        "module_cards",
        "race_calendar",
        "seasonal_skin",
        "settings_key_panel",
        "shamsi_date",
        "show_home_settings_shortcut",
        "show_stats",
        "top_menu",
        "uniform_tiles",
        "vpn_pill",
    )

// The reference's JSONTokener accepts comments and trailing commas, so a hand-edited theme that works there must here.
@OptIn(ExperimentalSerializationApi::class)
private val lenientJson =
    Json {
        isLenient = true
        allowComments = true
        allowTrailingComma = true
    }

/**
 * Parses the text of a config.json over the defaults, with the reference launcher's rules: a UTF-8 byte order
 * mark is dropped, text over [MAX_CONFIG_BYTES] or not a JSON object applies nothing, and a value of the wrong
 * type or outside its range keeps the default (and is listed in [ParsedConfig.rejectedKeys]).
 */
public fun parseTvConfig(json: String): ParsedConfig {
    val text = json.removePrefix("\uFEFF")
    val root =
        if (json.encodeToByteArray().size > MAX_CONFIG_BYTES) {
            null
        } else {
            runCatching { lenientJson.parseToJsonElement(text) }.getOrNull() as? JsonObject
        }
    if (root == null) return ParsedConfig(TvConfig(), emptyList(), emptyList(), emptyList(), overlayApplied = false)

    val d = TvConfig()
    val o = Overlay(root)
    val topBarApps = o.stringList("top_bar_apps", d.topBarApps)
    val hiddenApps = o.stringList("hidden_apps", d.hiddenApps)
    val config =
        TvConfig(
            dockApps = o.stringList("dock_apps", d.dockApps),
            fillDock = o.bool("fill_dock", d.fillDock),
            hiddenApps = hiddenApps + topBarApps.distinct().filterNot { it in hiddenApps },
            columns = o.int("columns", d.columns, 1..8),
            rowsBelowDock = o.int("rows_below_dock", d.rowsBelowDock, 0..3),
            singleRow = o.bool("single_row", d.singleRow),
            alwaysShowNames = o.bool("always_show_names", d.alwaysShowNames),
            dockTitle = o.bool("dock_title", d.dockTitle),
            gridScrollCue = o.bool("grid_scroll_cue", d.gridScrollCue),
            launchActions = packageMap(o.stringList("launch_actions", emptyList())),
            appLabels = packageMap(o.stringList("app_labels", emptyList())),
            topBarApps = topBarApps,
            greetingName = o.optionalString("greeting_name", d.greetingName),
            showGreeting = o.bool("show_greeting", d.showGreeting),
            clockFormat = o.enum("clock_format", d.clockFormat, ClockFormat.entries) { it.jsonValue },
            dateBelowClock = o.bool("date_below_clock", d.dateBelowClock),
            showNetworkPill = o.bool("show_network_pill", d.showNetworkPill),
            showInputsPill = o.bool("show_inputs_pill", d.showInputsPill),
            iconOnlyButtons = o.optionalBool("icon_only_buttons", false),
            iconOnlyInputs = o.optionalBool("icon_only_inputs", false),
            wifiLabelWidth = o.int("wifi_label_width", d.wifiLabelWidth, 0..300),
            wifiStatusColor = o.bool("wifi_status_color", d.wifiStatusColor),
            wifiStatusWarnings = o.bool("wifi_status_warnings", d.wifiStatusWarnings),
            vpnApp = o.optionalString("vpn_app", d.vpnApp),
            cinematicHome = o.bool("cinematic_home", d.cinematicHome),
            cinematicArt = o.int("cinematic_art", d.cinematicArt, 0..100),
            cinematicScrim = o.int("cinematic_scrim", d.cinematicScrim, 0..100),
            cinematicDots = o.bool("cinematic_dots", d.cinematicDots),
            cinematicWhiteFocus = o.bool("cinematic_white_focus", d.cinematicWhiteFocus),
            ambientGlow = o.bool("ambient_glow", d.ambientGlow),
            glowSnap = o.bool("glow_snap", d.glowSnap),
            appGlow = o.bool("app_glow", d.appGlow),
            dockOpacity = o.optionalInt("dock_opacity", 30..100),
            dockGlass = o.bool("dock_glass", d.dockGlass),
            dockBand = o.bool("dock_band", d.dockBand),
            dockEdgeLight = o.bool("dock_edge_light", d.dockEdgeLight),
            dockEvenPadding = o.bool("dock_even_padding", d.dockEvenPadding),
            backgroundColor = o.color("background_color", d.backgroundColor),
            headerScrim = o.enum("header_scrim", d.headerScrim, HeaderScrim.entries) { it.jsonValue },
            glassStyle = o.enum("glass_style", d.glassStyle, GlassStyle.entries, ignoreCase = true) { it.jsonValue },
            wallpaper = o.bool("wallpaper", d.wallpaper),
            wallpaperRotationMinutes = o.int("wallpaper_rotation_minutes", d.wallpaperRotationMinutes, 0..1440),
            wallpaperDim = o.int("wallpaper_dim", d.wallpaperDim, 0..90),
            wallpaperOrder = o.enum("wallpaper_order", d.wallpaperOrder, WallpaperOrder.entries) { it.jsonValue },
            wallpaperMoods = o.moods("wallpaper_moods", d.wallpaperMoods),
            wallpaperCaptions = o.bool("wallpaper_captions", d.wallpaperCaptions),
            wallpaperTrueColor = o.bool("wallpaper_true_color", d.wallpaperTrueColor),
            skyIntensity = o.int("sky_intensity", d.skyIntensity, 0..100),
            place = o.place(d.place),
            dockRow = o.bool("dock_row", d.dockRow),
            animations = o.bool("animations", d.animations),
            reducedMotion = o.bool("reduced_motion", d.reducedMotion),
            focusOvershoot = o.bool("focus_overshoot", d.focusOvershoot),
            tileMenu = o.bool("tile_menu", d.tileMenu),
            reorderButton = o.bool("reorder_button", d.reorderButton),
            launcherSettingsButton = o.bool("launcher_settings_button", d.launcherSettingsButton),
            appLibrary = o.bool("app_library", d.appLibrary),
            appLibrarySelection = o.bool("app_library_selection", d.appLibrarySelection),
            libraryButton = o.bool("library_button", d.libraryButton),
            libraryApps = o.stringList("library_apps", d.libraryApps),
            libraryExcludedApps = o.stringList("library_excluded_apps", d.libraryExcludedApps),
            showLibraryPill = o.bool("show_library_pill", d.showLibraryPill),
            libraryOkOpens = o.bool("library_ok_opens", d.libraryOkOpens),
            libraryLeastUsed = o.bool("library_least_used", d.libraryLeastUsed),
            inputSources = o.stringList("input_sources", d.inputSources),
            inputAction = o.optionalString("input_action", d.inputAction),
            inputUriScheme = o.optionalString("input_uri_scheme", d.inputUriScheme),
            liveTvPackage = o.optionalString("live_tv_package", d.liveTvPackage),
            liveTvAction = o.optionalString("live_tv_action", d.liveTvAction),
            liveTvInputId = o.optionalString("live_tv_input_id", d.liveTvInputId),
            bootToLiveTv = o.bool("boot_to_live_tv", d.bootToLiveTv),
            sourcePanelService = o.optionalString("source_panel_service", d.sourcePanelService),
            sourcePanelAction = o.optionalString("source_panel_action", d.sourcePanelAction),
            sourcePanelExtra = o.optionalString("source_panel_extra", d.sourcePanelExtra),
            coocaaSources = o.bool("coocaa_sources", d.coocaaSources),
            inputsConnectedOnly = o.bool("inputs_connected_only", d.inputsConnectedOnly),
            inputsCecStateOnly = o.bool("inputs_cec_state_only", d.inputsCecStateOnly),
            inputArrival = o.bool("input_arrival", d.inputArrival),
            habitHint = o.bool("habit_hint", d.habitHint),
            unusedFadeDays = o.int("unused_fade_days", d.unusedFadeDays, 0..3650),
            handleViewInputs = o.bool("handle_view_inputs", d.handleViewInputs),
            openOnStartNetworkWait = o.int("open_on_start_network_wait", d.openOnStartNetworkWait, 0..600),
            openOnStartChoice = o.bool("open_on_start_choice", d.openOnStartChoice),
            openOnStartOnWake = o.bool("open_on_start_on_wake", d.openOnStartOnWake),
            appKeys = o.stringList("app_keys", d.appKeys),
            settingsKeyKeycodes = o.keycodes("settings_key_keycodes", d.settingsKeyKeycodes),
            settingsKeyScancodes = o.keycodes("settings_key_scancodes", d.settingsKeyScancodes),
            settingsKeyClearTask = o.bool("settings_key_clear_task", d.settingsKeyClearTask),
            inputKeyKeycodes = o.keycodes("input_key_keycodes", d.inputKeyKeycodes),
            captureInputKeys = o.bool("capture_input_keys", d.captureInputKeys),
            keyFilter = o.bool("key_filter", d.keyFilter),
            gamepadPassthrough = o.bool("gamepad_passthrough", d.gamepadPassthrough),
            consumeVolumeKeys = o.bool("consume_volume_keys", d.consumeVolumeKeys),
            volumePausesFilter = o.bool("volume_pauses_filter", d.volumePausesFilter),
            stockHomeRedirect = o.optionalString("stock_home_redirect", d.stockHomeRedirect),
            homeLongPressHome = o.bool("home_long_press_home", d.homeLongPressHome),
            homeIgnoreChannelKeys = o.bool("home_ignore_channel_keys", d.homeIgnoreChannelKeys),
            powerOnHome = o.bool("power_on_home", d.powerOnHome),
            powerOnHomeAlways = o.bool("power_on_home_always", d.powerOnHomeAlways),
            restoreKeyServices = o.bool("restore_key_services", d.restoreKeyServices),
            homeIdleClockMinutes = o.int("home_idle_clock_minutes", d.homeIdleClockMinutes, 0..1440),
            backOpensClock = o.bool("back_opens_clock", d.backOpensClock),
            clockWallpaper = o.bool("clock_wallpaper", d.clockWallpaper),
            clockWallpaperDim = o.int("clock_wallpaper_dim", d.clockWallpaperDim, 0..90),
            clockGlow = o.bool("clock_glow", d.clockGlow),
        )
    return ParsedConfig(
        config = config,
        unknownKeys = root.keys.filter { it !in o.read && it !in unsupportedKeys },
        unsupportedKeys = root.keys.filter { it in unsupportedKeys },
        rejectedKeys = root.keys.filter { it in o.rejected },
        overlayApplied = true,
    )
}

/** Typed reads from one JSON object; each remembers the key it was asked for and the ones it rejected. */
private class Overlay(
    private val root: JsonObject,
) {
    val read = mutableSetOf<String>()
    val rejected = mutableSetOf<String>()

    /** The value of [key] when [convert] accepts it; a present key that it does not accept is rejected. */
    private fun <T> value(
        key: String,
        default: T,
        convert: (JsonElement) -> T?,
    ): T {
        read += key
        val element = root[key] ?: return default
        return convert(element) ?: default.also { rejected += key }
    }

    fun optionalBool(
        key: String,
        default: Boolean?,
    ): Boolean? = value(key, default) { (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.content?.toBooleanStrictOrNull() }

    fun bool(
        key: String,
        default: Boolean,
    ): Boolean = optionalBool(key, default) ?: default

    fun int(
        key: String,
        default: Int,
        range: IntRange,
    ): Int = value(key, default) { it.asInt()?.takeIf { n -> n in range } }

    /** An int in [range], or null when the key is absent, not a number or out of range (the last two reject it). */
    fun optionalInt(
        key: String,
        range: IntRange,
    ): Int? = value(key, null) { it.asInt()?.takeIf { n -> n in range } }

    /**
     * "latitude" and "longitude" together: JSON numbers in range. A key that is bad or has no partner is rejected
     * and the place stays [default].
     */
    fun place(default: Place?): Place? {
        read += LATITUDE
        read += LONGITUDE
        val latitude = coordinate(LATITUDE, 90.0)
        val longitude = coordinate(LONGITUDE, 180.0)
        if (latitude == null || longitude == null) {
            rejected += listOfNotNull(LATITUDE.takeIf { LATITUDE in root }, LONGITUDE.takeIf { LONGITUDE in root })
            return default
        }
        return Place(latitude, longitude)
    }

    private fun coordinate(
        key: String,
        limit: Double,
    ): Double? =
        (root[key] as? JsonPrimitive)
            ?.takeUnless { it.isString }
            ?.content
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in -limit..limit }

    /** A trimmed string; blank is null, which is not a rejection. */
    fun optionalString(
        key: String,
        default: String?,
    ): String? {
        read += key
        val element = root[key] ?: return default
        val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return default.also { rejected += key }
        return text.trim().ifEmpty { null }
    }

    /** An array's string items, trimmed; items that are not strings, or are blank, are skipped. */
    fun stringList(
        key: String,
        default: List<String>,
    ): List<String> =
        value(key, default) { array ->
            (array as? JsonArray)?.mapNotNull { item ->
                (item as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
                    ?.trim()
                    ?.ifEmpty { null }
            }
        }

    /** Positive keycodes or scancodes only (vendor remotes use codes in the thousands); a list with anything else in it is rejected whole. */
    fun keycodes(
        key: String,
        default: List<Int>,
    ): List<Int> =
        value(key, default) { array ->
            (array as? JsonArray)?.map { item -> item.asInt()?.takeIf { it >= 1 } ?: return@value null }
        }

    fun color(
        key: String,
        default: Int,
    ): Int =
        value(key, default) { element ->
            (element as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.trim()
                ?.let(::parseColor)
                ?.or(OPAQUE_BLACK)
        }

    fun <E : Enum<E>> enum(
        key: String,
        default: E,
        entries: List<E>,
        ignoreCase: Boolean = false,
        jsonValue: (E) -> String,
    ): E =
        value(key, default) { element ->
            val name = (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            entries.firstOrNull { jsonValue(it).equals(name, ignoreCase) }
        }

    /** {"night": ["03-moon-lake.jpg", ...], ...}: each mood's picture names; other keys and names are left out. */
    fun moods(
        key: String,
        default: Map<WallpaperMood, Set<String>>,
    ): Map<WallpaperMood, Set<String>> =
        value(key, default) { element ->
            val groups = element as? JsonObject ?: return@value null
            buildMap {
                for (mood in WallpaperMood.entries) {
                    val names =
                        (groups[mood.jsonValue] as? JsonArray)
                            ?.mapNotNull { item -> (item as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim() }
                            ?.filter(ThemeFiles::collectionImage)
                            ?.toSet()
                    if (!names.isNullOrEmpty()) put(mood, names)
                }
            }
        }
}

/** A JSON number as the reference reads it: fractions are cut off, and anything that does not fit an Int is no number. */
private fun JsonElement.asInt(): Int? {
    val number = (this as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toDoubleOrNull()
    return number?.takeIf { it.isFinite() && it > Int.MIN_VALUE && it < Int.MAX_VALUE }?.toInt()
}

/** "package=value" entries; an entry without both halves is skipped, and a later entry for a package wins. */
private fun packageMap(entries: List<String>): Map<String, String> =
    buildMap {
        for (entry in entries) {
            val separator = entry.indexOf('=')
            if (separator <= 0) continue
            val pkg = entry.substring(0, separator).trim()
            val value = entry.substring(separator + 1).trim()
            if (pkg.isNotEmpty() && value.isNotEmpty()) put(pkg, value)
        }
    }

private val colorNames: Map<String, Int> =
    mapOf(
        "black" to 0x000000,
        "darkgray" to 0x444444,
        "darkgrey" to 0x444444,
        "gray" to 0x888888,
        "grey" to 0x888888,
        "lightgray" to 0xCCCCCC,
        "lightgrey" to 0xCCCCCC,
        "white" to 0xFFFFFF,
        "red" to 0xFF0000,
        "green" to 0x00FF00,
        "lime" to 0x00FF00,
        "blue" to 0x0000FF,
        "yellow" to 0xFFFF00,
        "cyan" to 0x00FFFF,
        "aqua" to 0x00FFFF,
        "magenta" to 0xFF00FF,
        "fuchsia" to 0xFF00FF,
        "maroon" to 0x800000,
        "navy" to 0x000080,
        "olive" to 0x808000,
        "purple" to 0x800080,
        "silver" to 0xC0C0C0,
        "teal" to 0x008080,
    )

/** What android.graphics.Color.parseColor accepts: #RRGGBB, #AARRGGBB or one of its colour names; null otherwise. */
private fun parseColor(text: String): Int? {
    if (!text.startsWith("#")) return colorNames[text.lowercase(java.util.Locale.ROOT)]
    val digits = text.substring(1)
    if ((digits.length != 6 && digits.length != 8) || !digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return digits.toLong(16).toInt()
}
