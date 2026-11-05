package dev.niltsiar.tvhome.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvConfigParserTest {
    @Test
    fun an_empty_object_keeps_every_default_and_reports_nothing() {
        val parsed = parseTvConfig("{}")
        assertEquals(TvConfig(), parsed.config)
        assertTrue(parsed.overlayApplied)
        assertEquals(emptyList<String>(), parsed.unknownKeys + parsed.unsupportedKeys + parsed.rejectedKeys)
    }

    @Test
    fun defaults_are_the_reference_config_xml_values() {
        val config = TvConfig()
        assertEquals(
            listOf("com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.google.android.youtube.tv", "in.startv.hotstar"),
            config.dockApps,
        )
        assertEquals(listOf("com.mediatek.wwtv.tvcenter", "com.mitv.gallery"), config.hiddenApps)
        assertEquals("com.mediatek.wwtv.tvcenter", config.liveTvPackage)
        assertEquals(
            listOf(4, 0, 0, 50, 100, 50),
            listOf(
                config.columns,
                config.rowsBelowDock,
                config.wallpaperDim,
                config.cinematicArt,
                config.cinematicScrim,
                config.clockWallpaperDim,
            ),
        )
        assertEquals(ClockFormat.System, config.clockFormat)
        assertEquals(WallpaperOrder.Shuffle, config.wallpaperOrder)
        assertEquals(0xFF000000.toInt(), config.backgroundColor)
        assertTrue(config.wallpaper && config.fillDock && config.showGreeting && config.tileMenu && config.clockGlow)
        assertFalse(config.appLibrary || config.cinematicHome || config.singleRow || config.powerOnHomeAlways)
        assertTrue(config.cinematicDots && config.ambientGlow)
        assertFalse(config.cinematicWhiteFocus || config.glowSnap || config.appGlow)
        assertTrue(config.dockRow)
        assertFalse(config.reorderButton)
        assertFalse(config.appLibrarySelection || config.libraryButton || config.libraryOkOpens)
        assertTrue(config.showLibraryPill)
        assertEquals(emptyList<String>(), config.libraryApps + config.libraryExcludedApps)
        assertEquals(
            listOf(null, null, null, null, null, null),
            listOf(
                config.sourcePanelService,
                config.sourcePanelAction,
                config.sourcePanelExtra,
                config.inputAction,
                config.inputUriScheme,
                config.liveTvAction,
            ),
        )
        assertFalse(
            config.coocaaSources ||
                config.inputsConnectedOnly ||
                config.inputsCecStateOnly ||
                config.handleViewInputs ||
                config.bootToLiveTv,
        )
        assertEquals(0, config.openOnStartNetworkWait)
        assertTrue(config.keyFilter)
        assertFalse(config.gamepadPassthrough || config.consumeVolumeKeys || config.homeLongPressHome || config.captureInputKeys)
        assertFalse(config.backOpensClock || config.powerOnHome)
        assertEquals(emptyList<Int>(), config.settingsKeyKeycodes + config.settingsKeyScancodes + config.inputKeyKeycodes)
        assertEquals(null, config.stockHomeRedirect)
    }

    @Test
    fun a_byte_order_mark_is_dropped() {
        val parsed = parseTvConfig("\uFEFF{\"columns\": 6}")
        assertEquals(6, parsed.config.columns)
        assertTrue(parsed.overlayApplied)
    }

    @Test
    fun text_over_64_kb_applies_nothing() {
        val parsed = parseTvConfig("{\"columns\": 6, \"greeting_name\": \"" + "x".repeat(MAX_CONFIG_BYTES) + "\"}")
        assertFalse(parsed.overlayApplied)
        assertEquals(TvConfig(), parsed.config)
    }

    @Test
    fun text_that_is_not_a_json_object_applies_nothing() {
        for (text in listOf("", "[1, 2]", "\"columns\"", "{\"columns\": ")) {
            val parsed = parseTvConfig(text)
            assertFalse(text, parsed.overlayApplied)
            assertEquals(text, TvConfig(), parsed.config)
        }
    }

    @Test
    fun comments_and_trailing_commas_are_accepted_like_the_reference_tokenizer() {
        val parsed = parseTvConfig("{ // theme\n \"columns\": 6, /* more */ \"rows_below_dock\": 2, }")
        assertEquals(6 to 2, parsed.config.columns to parsed.config.rowsBelowDock)
    }

    private val ranged: List<Triple<String, IntRange, (TvConfig) -> Int>> =
        listOf(
            Triple("columns", 1..8, { it.columns }),
            Triple("rows_below_dock", 0..3, { it.rowsBelowDock }),
            Triple("wifi_label_width", 0..300, { it.wifiLabelWidth }),
            Triple("cinematic_art", 0..100, { it.cinematicArt }),
            Triple("cinematic_scrim", 0..100, { it.cinematicScrim }),
            Triple("wallpaper_rotation_minutes", 0..1440, { it.wallpaperRotationMinutes }),
            Triple("wallpaper_dim", 0..90, { it.wallpaperDim }),
            Triple("sky_intensity", 0..100, { it.skyIntensity }),
            Triple("home_idle_clock_minutes", 0..1440, { it.homeIdleClockMinutes }),
            Triple("clock_wallpaper_dim", 0..90, { it.clockWallpaperDim }),
            Triple("open_on_start_network_wait", 0..600, { it.openOnStartNetworkWait }),
        )

    @Test
    fun each_ranged_number_accepts_its_limits_and_rejects_one_step_beyond() {
        for ((key, range, read) in ranged) {
            val default = read(TvConfig())
            for (inside in listOf(range.first, range.last)) {
                val parsed = parseTvConfig("{\"$key\": $inside}")
                assertEquals("$key = $inside", inside, read(parsed.config))
                assertEquals(key, emptyList<String>(), parsed.rejectedKeys)
            }
            for (outside in listOf(range.first - 1, range.last + 1)) {
                val parsed = parseTvConfig("{\"$key\": $outside}")
                assertEquals("$key = $outside", default, read(parsed.config))
                assertEquals(key, listOf(key), parsed.rejectedKeys)
            }
        }
    }

    @Test
    fun a_value_of_the_wrong_type_keeps_the_default_and_is_rejected() {
        val parsed =
            parseTvConfig(
                """{"columns": "6", "fill_dock": "false", "dock_apps": "com.a", "greeting_name": 7,
                   "wifi_label_width": null, "animations": 0, "sky_intensity": 2147483648}""",
            )
        assertEquals(TvConfig(), parsed.config)
        assertEquals(
            listOf("columns", "fill_dock", "dock_apps", "greeting_name", "wifi_label_width", "animations", "sky_intensity"),
            parsed.rejectedKeys,
        )
    }

    @Test
    fun a_fractional_number_is_cut_off_as_the_reference_does() {
        assertEquals(4, parseTvConfig("""{"columns": 4.9}""").config.columns)
    }

    @Test
    fun a_keycode_list_with_any_bad_element_is_dropped_whole() {
        val bad = parseTvConfig("""{"settings_key_keycodes": [172, 0], "input_key_keycodes": [1, "x"]}""")
        assertEquals(emptyList<Int>(), bad.config.settingsKeyKeycodes + bad.config.inputKeyKeycodes)
        assertEquals(listOf("settings_key_keycodes", "input_key_keycodes"), bad.rejectedKeys)

        val good = parseTvConfig("""{"settings_key_keycodes": [172, 1001]}""")
        assertEquals(listOf(172, 1001), good.config.settingsKeyKeycodes)
    }

    @Test
    fun capture_input_keys_true_is_read() {
        val parsed = parseTvConfig("""{"capture_input_keys": true}""")

        assertTrue(parsed.config.captureInputKeys)
        assertEquals(emptyList<String>(), parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys)
    }

    @Test
    fun the_remote_key_service_keys_are_read_and_a_wrong_type_keeps_the_default() {
        val parsed =
            parseTvConfig(
                """{"key_filter": false, "gamepad_passthrough": true, "consume_volume_keys": true, "home_long_press_home": true,
                   "back_opens_clock": true, "settings_key_scancodes": [159], "stock_home_redirect": " com.google.android.apps.tv.launcherx ",
                   "app_keys": ["scan:490:com.wbd.hbomax", "hold:KEYCODE_BUTTON_8:org.xbmc.kodi"]}""",
            )
        val config = parsed.config
        assertFalse(config.keyFilter)
        assertTrue(config.gamepadPassthrough && config.consumeVolumeKeys && config.homeLongPressHome && config.backOpensClock)
        assertEquals(listOf(159), config.settingsKeyScancodes)
        assertEquals("com.google.android.apps.tv.launcherx", config.stockHomeRedirect)
        assertEquals(listOf("scan:490:com.wbd.hbomax", "hold:KEYCODE_BUTTON_8:org.xbmc.kodi"), config.appKeys)
        assertEquals(emptyList<String>(), parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys)

        val wrong = parseTvConfig("""{"key_filter": "no", "consume_volume_keys": 1, "settings_key_scancodes": [0]}""")
        assertTrue(wrong.config.keyFilter)
        assertFalse(wrong.config.consumeVolumeKeys)
        assertEquals(emptyList<Int>(), wrong.config.settingsKeyScancodes)
        assertEquals(listOf("key_filter", "consume_volume_keys", "settings_key_scancodes"), wrong.rejectedKeys)
    }

    @Test
    fun settings_key_panel_stays_unsupported_because_the_panel_does_not_exist() {
        val parsed = parseTvConfig("""{"settings_key_panel": true}""")
        assertEquals(listOf("settings_key_panel"), parsed.unsupportedKeys)
    }

    @Test
    fun string_lists_trim_their_items_and_skip_non_strings_and_blanks() {
        val parsed = parseTvConfig("""{"dock_apps": [" com.a ", "", 5, "com.b", null]}""")
        assertEquals(listOf("com.a", "com.b"), parsed.config.dockApps)
        assertEquals(emptyList<String>(), parsed.rejectedKeys)
    }

    @Test
    fun package_maps_keep_well_formed_entries_and_the_last_entry_for_a_package() {
        val parsed = parseTvConfig("""{"app_labels": ["com.a=Alpha", "nope", "=x", "com.b= ", "com.a = Beta"]}""")
        assertEquals(mapOf("com.a" to "Beta"), parsed.config.appLabels)
    }

    @Test
    fun top_bar_apps_leave_the_grid() {
        val parsed = parseTvConfig("""{"hidden_apps": ["com.a"], "top_bar_apps": ["com.b", "com.a", "com.b"]}""")
        assertEquals(listOf("com.a", "com.b"), parsed.config.hiddenApps)
        assertEquals(listOf("com.b", "com.a", "com.b"), parsed.config.topBarApps)
    }

    @Test
    fun a_blank_optional_string_clears_the_default_without_a_rejection() {
        val parsed = parseTvConfig("""{"live_tv_package": "  "}""")
        assertEquals(null, parsed.config.liveTvPackage)
        assertEquals(emptyList<String>(), parsed.rejectedKeys)
    }

    @Test
    fun the_background_colour_is_forced_opaque() {
        assertEquals(0xFF112233.toInt(), parseTvConfig("""{"background_color": "#80112233"}""").config.backgroundColor)
        assertEquals(0xFF112233.toInt(), parseTvConfig("""{"background_color": " #112233 "}""").config.backgroundColor)
        assertEquals(0xFFFF0000.toInt(), parseTvConfig("""{"background_color": "Red"}""").config.backgroundColor)
    }

    @Test
    fun a_background_colour_that_android_cannot_parse_keeps_black_and_is_rejected() {
        for (text in listOf("#12345", "#GGGGGG", "112233", "notacolour")) {
            val parsed = parseTvConfig("""{"background_color": "$text"}""")
            assertEquals(text, TvConfig().backgroundColor, parsed.config.backgroundColor)
            assertEquals(text, listOf("background_color"), parsed.rejectedKeys)
        }
    }

    @Test
    fun clock_format_and_wallpaper_order_accept_their_names_and_reject_others() {
        val good = parseTvConfig("""{"clock_format": "24", "wallpaper_order": "sequential"}""")
        assertEquals(ClockFormat.Hour24, good.config.clockFormat)
        assertEquals(WallpaperOrder.Sequential, good.config.wallpaperOrder)

        val bad = parseTvConfig("""{"clock_format": "13", "wallpaper_order": "random"}""")
        assertEquals(TvConfig(), bad.config)
        assertEquals(listOf("clock_format", "wallpaper_order"), bad.rejectedKeys)
    }

    @Test
    fun wallpaper_moods_keep_only_image_names_of_the_three_moods() {
        val parsed =
            parseTvConfig(
                """{"wallpaper_moods": {"morning": ["a.jpg", "notes.txt", ".hidden.jpg"], "night": ["b.PNG"],
                   "evening": [], "noon": ["c.jpg"]}}""",
            )
        assertEquals(mapOf(WallpaperMood.Morning to setOf("a.jpg"), WallpaperMood.Night to setOf("b.PNG")), parsed.config.wallpaperMoods)
        assertEquals(emptyList<String>(), parsed.rejectedKeys)
        assertEquals(listOf("wallpaper_moods"), parseTvConfig("""{"wallpaper_moods": ["a.jpg"]}""").rejectedKeys)
    }

    @Test
    fun keys_the_reference_knows_but_this_launcher_does_not_use_are_unsupported_not_unknown() {
        val parsed =
            parseTvConfig(
                """{"dock_title": false, "columns": 5, "festival": "diwali", "game_day": "x", "nonsense": 1, "dock_titel": true}""",
            )
        assertEquals(listOf("dock_title", "festival", "game_day"), parsed.unsupportedKeys)
        assertEquals(listOf("nonsense", "dock_titel"), parsed.unknownKeys)
        assertEquals(5, parsed.config.columns)
    }

    @Test
    fun the_cinematic_and_glow_keys_are_read_and_a_wrong_type_keeps_the_default() {
        val parsed =
            parseTvConfig(
                """{"cinematic_dots": false, "cinematic_white_focus": true, "ambient_glow": false, "glow_snap": true, "app_glow": true}""",
            )
        val config = parsed.config
        assertFalse(config.cinematicDots || config.ambientGlow)
        assertTrue(config.cinematicWhiteFocus && config.glowSnap && config.appGlow)
        assertEquals(emptyList<String>(), parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys)

        val wrong = parseTvConfig("""{"cinematic_dots": "no", "app_glow": 1}""")
        assertTrue(wrong.config.cinematicDots)
        assertFalse(wrong.config.appGlow)
        assertEquals(listOf("cinematic_dots", "app_glow"), wrong.rejectedKeys)
    }

    @Test
    fun the_network_button_keys_are_read_and_a_blank_vpn_app_is_none() {
        val parsed =
            parseTvConfig(
                """{"icon_only_buttons": true, "wifi_label_width": 120, "wifi_status_color": true,
                   "wifi_status_warnings": true, "vpn_app": " com.vpn.app "}""",
            )
        val config = parsed.config
        assertTrue(config.iconOnlyButtons && config.wifiStatusColor && config.wifiStatusWarnings)
        assertEquals(120, config.wifiLabelWidth)
        assertEquals("com.vpn.app", config.vpnApp)
        assertEquals(emptyList<String>(), parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys)
        assertEquals(null, parseTvConfig("""{"vpn_app": "  "}""").config.vpnApp)
    }

    @Test
    fun latitude_and_longitude_together_set_the_place() {
        val parsed = parseTvConfig("""{"latitude": 40.4168, "longitude": -3.7038}""")
        assertEquals(Place(40.4168, -3.7038), parsed.config.place)
        assertEquals(emptyList<String>(), parsed.unknownKeys + parsed.unsupportedKeys + parsed.rejectedKeys)
        assertEquals(Place(-90.0, 180.0), parseTvConfig("""{"latitude": -90, "longitude": 180}""").config.place)
    }

    @Test
    fun a_place_needs_both_keys_in_range_as_numbers() {
        val cases =
            mapOf(
                """{"latitude": 40.4}""" to listOf("latitude"),
                """{"longitude": -3.7}""" to listOf("longitude"),
                """{"latitude": 90.1, "longitude": 0}""" to listOf("latitude", "longitude"),
                """{"latitude": 0, "longitude": -180.1}""" to listOf("latitude", "longitude"),
                """{"latitude": "40.4", "longitude": -3.7}""" to listOf("latitude", "longitude"),
                """{"latitude": 40.4, "longitude": null}""" to listOf("latitude", "longitude"),
            )
        for ((text, rejected) in cases) {
            val parsed = parseTvConfig(text)
            assertEquals(text, null, parsed.config.place)
            assertEquals(text, rejected, parsed.rejectedKeys)
        }
    }

    @Test
    fun the_tile_menu_dock_row_and_reorder_button_keys_are_read() {
        val parsed = parseTvConfig("""{"tile_menu": false, "dock_row": false, "reorder_button": true}""")
        assertFalse(parsed.config.tileMenu || parsed.config.dockRow)
        assertTrue(parsed.config.reorderButton)
        assertEquals(emptyList<String>(), parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys)

        val wrong = parseTvConfig("""{"dock_row": "no", "reorder_button": 1}""")
        assertTrue(wrong.config.dockRow)
        assertFalse(wrong.config.reorderButton)
        assertEquals(listOf("dock_row", "reorder_button"), wrong.rejectedKeys)
    }

    @Test
    fun the_app_library_keys_are_read() {
        val parsed =
            parseTvConfig(
                """{"app_library": true, "app_library_selection": true, "library_button": true, "show_library_pill": false,
                    "library_ok_opens": true, "library_apps": ["a.b"], "library_excluded_apps": ["c.d"]}""",
            )
        val config = parsed.config
        assertTrue(config.appLibrary && config.appLibrarySelection && config.libraryButton && config.libraryOkOpens)
        assertFalse(config.showLibraryPill)
        assertEquals(listOf("a.b") to listOf("c.d"), config.libraryApps to config.libraryExcludedApps)
        assertEquals(emptyList<String>(), parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys)

        val wrong = parseTvConfig("""{"app_library_selection": "yes", "library_button": 1}""")
        assertFalse(wrong.config.appLibrarySelection || wrong.config.libraryButton)
        assertEquals(listOf("app_library_selection", "library_button"), wrong.rejectedKeys)
    }

    @Test
    fun the_inputs_and_open_on_start_keys_are_read() {
        val parsed =
            parseTvConfig(
                """{"source_panel_service": " com.tcl/.Panel ", "source_panel_action": "com.tcl.SHOW", "source_panel_extra": "cmd=100",
                    "coocaa_sources": true, "inputs_connected_only": true, "inputs_cec_state_only": true, "handle_view_inputs": true,
                    "open_on_start_network_wait": 180}""",
            )
        val config = parsed.config
        assertEquals(
            Triple("com.tcl/.Panel", "com.tcl.SHOW", "cmd=100"),
            Triple(config.sourcePanelService, config.sourcePanelAction, config.sourcePanelExtra),
        )
        assertTrue(config.coocaaSources && config.inputsConnectedOnly && config.inputsCecStateOnly && config.handleViewInputs)
        assertEquals(180, config.openOnStartNetworkWait)
        assertEquals(emptyList<String>(), parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys)

        val wrong = parseTvConfig("""{"coocaa_sources": "yes", "source_panel_service": 5, "open_on_start_network_wait": 601}""")
        assertFalse(wrong.config.coocaaSources)
        assertEquals(null, wrong.config.sourcePanelService)
        assertEquals(0, wrong.config.openOnStartNetworkWait)
        assertEquals(listOf("coocaa_sources", "source_panel_service", "open_on_start_network_wait"), wrong.rejectedKeys)
    }

    @Test
    fun keys_with_no_reader_in_this_launcher_are_unsupported_and_change_nothing() {
        val keys = listOf("dock_opacity", "dock_glass", "locale", "open_on_start_choice", "open_on_start_on_wake")
        val parsed = parseTvConfig(keys.joinToString(prefix = "{", postfix = "}") { "\"$it\": true" })

        assertEquals(keys, parsed.unsupportedKeys)
        assertEquals(emptyList<String>(), parsed.unknownKeys + parsed.rejectedKeys)
        assertEquals(TvConfig(), parsed.config)
    }

    private val defaults = TvConfig()

    /** key to (JSON value, the config that value alone produces); latitude and longitude set one field, so they share a row. */
    private val nonDefaults: Map<String, Pair<String, TvConfig>> =
        mapOf(
            "dock_apps" to ("""["com.a"]""" to defaults.copy(dockApps = listOf("com.a"))),
            "fill_dock" to ("false" to defaults.copy(fillDock = false)),
            "hidden_apps" to ("""["com.a"]""" to defaults.copy(hiddenApps = listOf("com.a"))),
            "columns" to ("6" to defaults.copy(columns = 6)),
            "rows_below_dock" to ("2" to defaults.copy(rowsBelowDock = 2)),
            "single_row" to ("true" to defaults.copy(singleRow = true)),
            "launch_actions" to ("""["com.a=ACT"]""" to defaults.copy(launchActions = mapOf("com.a" to "ACT"))),
            "app_labels" to ("""["com.a=Alpha"]""" to defaults.copy(appLabels = mapOf("com.a" to "Alpha"))),
            "app_banners" to ("""["com.a=banner"]""" to defaults.copy(appBanners = mapOf("com.a" to "banner"))),
            "top_bar_apps" to
                ("""["com.t"]""" to defaults.copy(topBarApps = listOf("com.t"), hiddenApps = defaults.hiddenApps + "com.t")),
            "greeting_name" to (""""Ann"""" to defaults.copy(greetingName = "Ann")),
            "show_greeting" to ("false" to defaults.copy(showGreeting = false)),
            "clock_format" to (""""24"""" to defaults.copy(clockFormat = ClockFormat.Hour24)),
            "date_below_clock" to ("true" to defaults.copy(dateBelowClock = true)),
            "show_network_pill" to ("false" to defaults.copy(showNetworkPill = false)),
            "show_inputs_pill" to ("false" to defaults.copy(showInputsPill = false)),
            "icon_only_buttons" to ("true" to defaults.copy(iconOnlyButtons = true)),
            "wifi_label_width" to ("120" to defaults.copy(wifiLabelWidth = 120)),
            "wifi_status_color" to ("true" to defaults.copy(wifiStatusColor = true)),
            "wifi_status_warnings" to ("true" to defaults.copy(wifiStatusWarnings = true)),
            "vpn_app" to (""""com.vpn"""" to defaults.copy(vpnApp = "com.vpn")),
            "cinematic_home" to ("true" to defaults.copy(cinematicHome = true)),
            "cinematic_art" to ("80" to defaults.copy(cinematicArt = 80)),
            "cinematic_scrim" to ("40" to defaults.copy(cinematicScrim = 40)),
            "cinematic_dots" to ("false" to defaults.copy(cinematicDots = false)),
            "cinematic_white_focus" to ("true" to defaults.copy(cinematicWhiteFocus = true)),
            "ambient_glow" to ("false" to defaults.copy(ambientGlow = false)),
            "glow_snap" to ("true" to defaults.copy(glowSnap = true)),
            "app_glow" to ("true" to defaults.copy(appGlow = true)),
            "background_color" to (""""#112233"""" to defaults.copy(backgroundColor = 0xFF112233.toInt())),
            "wallpaper" to ("false" to defaults.copy(wallpaper = false)),
            "wallpaper_rotation_minutes" to ("30" to defaults.copy(wallpaperRotationMinutes = 30)),
            "wallpaper_dim" to ("40" to defaults.copy(wallpaperDim = 40)),
            "wallpaper_order" to (""""sequential"""" to defaults.copy(wallpaperOrder = WallpaperOrder.Sequential)),
            "wallpaper_moods" to
                ("""{"night": ["a.jpg"]}""" to defaults.copy(wallpaperMoods = mapOf(WallpaperMood.Night to setOf("a.jpg")))),
            "wallpaper_captions" to ("true" to defaults.copy(wallpaperCaptions = true)),
            "wallpaper_true_color" to ("true" to defaults.copy(wallpaperTrueColor = true)),
            "sky_intensity" to ("60" to defaults.copy(skyIntensity = 60)),
            "latitude" to ("40.4" to defaults.copy(place = Place(40.4, -3.7))),
            "dock_row" to ("false" to defaults.copy(dockRow = false)),
            "animations" to ("false" to defaults.copy(animations = false)),
            "reduced_motion" to ("true" to defaults.copy(reducedMotion = true)),
            "focus_overshoot" to ("false" to defaults.copy(focusOvershoot = false)),
            "tile_menu" to ("false" to defaults.copy(tileMenu = false)),
            "reorder_button" to ("true" to defaults.copy(reorderButton = true)),
            "app_library" to ("true" to defaults.copy(appLibrary = true)),
            "app_library_selection" to ("true" to defaults.copy(appLibrarySelection = true)),
            "library_button" to ("true" to defaults.copy(libraryButton = true)),
            "library_apps" to ("""["a.b"]""" to defaults.copy(libraryApps = listOf("a.b"))),
            "library_excluded_apps" to ("""["a.b"]""" to defaults.copy(libraryExcludedApps = listOf("a.b"))),
            "show_library_pill" to ("false" to defaults.copy(showLibraryPill = false)),
            "library_ok_opens" to ("true" to defaults.copy(libraryOkOpens = true)),
            "input_sources" to ("""["HDMI1"]""" to defaults.copy(inputSources = listOf("HDMI1"))),
            "input_action" to (""""a.INPUT"""" to defaults.copy(inputAction = "a.INPUT")),
            "input_uri_scheme" to (""""tv"""" to defaults.copy(inputUriScheme = "tv")),
            "live_tv_package" to (""""com.live"""" to defaults.copy(liveTvPackage = "com.live")),
            "live_tv_action" to (""""a.LIVE"""" to defaults.copy(liveTvAction = "a.LIVE")),
            "live_tv_input_id" to (""""input/1"""" to defaults.copy(liveTvInputId = "input/1")),
            "boot_to_live_tv" to ("true" to defaults.copy(bootToLiveTv = true)),
            "source_panel_service" to (""""com.tcl/.Panel"""" to defaults.copy(sourcePanelService = "com.tcl/.Panel")),
            "source_panel_action" to (""""a.SHOW"""" to defaults.copy(sourcePanelAction = "a.SHOW")),
            "source_panel_extra" to (""""cmd=100"""" to defaults.copy(sourcePanelExtra = "cmd=100")),
            "coocaa_sources" to ("true" to defaults.copy(coocaaSources = true)),
            "inputs_connected_only" to ("true" to defaults.copy(inputsConnectedOnly = true)),
            "inputs_cec_state_only" to ("true" to defaults.copy(inputsCecStateOnly = true)),
            "handle_view_inputs" to ("true" to defaults.copy(handleViewInputs = true)),
            "open_on_start_network_wait" to ("30" to defaults.copy(openOnStartNetworkWait = 30)),
            "app_keys" to ("""["172:com.a"]""" to defaults.copy(appKeys = listOf("172:com.a"))),
            "settings_key_keycodes" to ("[172]" to defaults.copy(settingsKeyKeycodes = listOf(172))),
            "settings_key_scancodes" to ("[159]" to defaults.copy(settingsKeyScancodes = listOf(159))),
            "input_key_keycodes" to ("[1001]" to defaults.copy(inputKeyKeycodes = listOf(1001))),
            "capture_input_keys" to ("true" to defaults.copy(captureInputKeys = true)),
            "key_filter" to ("false" to defaults.copy(keyFilter = false)),
            "gamepad_passthrough" to ("true" to defaults.copy(gamepadPassthrough = true)),
            "consume_volume_keys" to ("true" to defaults.copy(consumeVolumeKeys = true)),
            "stock_home_redirect" to (""""com.stock"""" to defaults.copy(stockHomeRedirect = "com.stock")),
            "home_long_press_home" to ("true" to defaults.copy(homeLongPressHome = true)),
            "power_on_home" to ("true" to defaults.copy(powerOnHome = true)),
            "power_on_home_always" to ("true" to defaults.copy(powerOnHomeAlways = true)),
            "home_idle_clock_minutes" to ("10" to defaults.copy(homeIdleClockMinutes = 10)),
            "back_opens_clock" to ("true" to defaults.copy(backOpensClock = true)),
            "clock_wallpaper" to ("true" to defaults.copy(clockWallpaper = true)),
            "clock_wallpaper_dim" to ("20" to defaults.copy(clockWallpaperDim = 20)),
            "clock_glow" to ("false" to defaults.copy(clockGlow = false)),
        )

    @Test
    fun every_supported_key_at_a_non_default_value_changes_exactly_its_own_field() {
        for ((key, row) in nonDefaults) {
            val (value, expected) = row
            val json = if (key == "latitude") """{"latitude": 40.4, "longitude": -3.7}""" else """{"$key": $value}"""
            val parsed = parseTvConfig(json)
            assertEquals(key, expected, parsed.config)
            assertEquals(key, emptyList<String>(), parsed.unknownKeys + parsed.unsupportedKeys + parsed.rejectedKeys)
        }
        val fields =
            TvConfig::class.java.declaredFields.count {
                !java.lang.reflect.Modifier
                    .isStatic(it.modifiers)
            }
        assertEquals("a TvConfig field has no row", fields, nonDefaults.size)
    }
}
