package dev.niltsiar.tvhome.core.config

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

val TvConfigParserTest by testSuite {
    /** What a theme that sets nothing gives: the defaults, with the Java launcher's icon_only_buttons and icon_only_inputs false. */
    val applied = TvConfig(iconOnlyButtons = false, iconOnlyInputs = false)

    test("an empty object keeps every default and reports nothing") {
        val parsed = parseTvConfig("{}")
        parsed.config shouldBe applied
        parsed.overlayApplied.shouldBeTrue()
        parsed.unknownKeys + parsed.unsupportedKeys + parsed.rejectedKeys shouldBe emptyList<String>()
    }

    test("defaults are the reference config xml values") {
        val config = TvConfig()
        config.dockApps shouldBe
            listOf("com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.google.android.youtube.tv", "in.startv.hotstar")
        config.hiddenApps shouldBe listOf("com.mediatek.wwtv.tvcenter", "com.mitv.gallery")
        config.liveTvPackage shouldBe "com.mediatek.wwtv.tvcenter"
        listOf(
            config.columns,
            config.rowsBelowDock,
            config.wallpaperDim,
            config.cinematicArt,
            config.cinematicScrim,
            config.clockWallpaperDim,
        ) shouldBe listOf(4, 0, 0, 50, 100, 50)
        config.clockFormat shouldBe ClockFormat.System
        config.wallpaperOrder shouldBe WallpaperOrder.Shuffle
        config.backgroundColor shouldBe 0xFF000000.toInt()
        (config.wallpaper && config.fillDock && config.showGreeting && config.tileMenu && config.clockGlow).shouldBeTrue()
        (
            config.appLibrary ||
                config.cinematicHome ||
                config.singleRow ||
                config.powerOnHomeAlways ||
                config.restoreKeyServices
        ).shouldBeFalse()
        (config.cinematicDots && config.ambientGlow).shouldBeTrue()
        (config.cinematicWhiteFocus || config.glowSnap).shouldBeFalse()
        config.appGlow.shouldBeTrue()
        config.dockRow.shouldBeTrue()
        config.reorderButton.shouldBeFalse()
        (config.appLibrarySelection || config.libraryButton || config.libraryOkOpens || config.libraryLeastUsed).shouldBeFalse()
        config.unusedFadeDays shouldBe 0
        config.showLibraryPill.shouldBeTrue()
        config.libraryApps + config.libraryExcludedApps shouldBe emptyList<String>()
        listOf(
            config.sourcePanelService,
            config.sourcePanelAction,
            config.sourcePanelExtra,
            config.inputAction,
            config.inputUriScheme,
            config.liveTvAction,
        ) shouldBe listOf(null, null, null, null, null, null)
        (
            config.coocaaSources ||
                config.inputsConnectedOnly ||
                config.inputsCecStateOnly ||
                config.inputArrival ||
                config.handleViewInputs ||
                config.bootToLiveTv
        ).shouldBeFalse()
        config.openOnStartNetworkWait shouldBe 0
        config.keyFilter.shouldBeTrue()
        (config.gamepadPassthrough || config.consumeVolumeKeys || config.volumePausesFilter).shouldBeFalse()
        (config.homeLongPressHome || config.captureInputKeys).shouldBeFalse()
        (config.backOpensClock || config.powerOnHome || config.homeIgnoreChannelKeys).shouldBeFalse()
        config.settingsKeyKeycodes + config.settingsKeyScancodes + config.inputKeyKeycodes shouldBe emptyList<Int>()
        config.stockHomeRedirect shouldBe null
    }

    test("a byte order mark is dropped") {
        val parsed = parseTvConfig("\uFEFF{\"columns\": 6}")
        parsed.config.columns shouldBe 6
        parsed.overlayApplied.shouldBeTrue()
    }

    test("text over 64 kb applies nothing") {
        val parsed = parseTvConfig("{\"columns\": 6, \"greeting_name\": \"" + "x".repeat(MAX_CONFIG_BYTES) + "\"}")
        parsed.overlayApplied.shouldBeFalse()
        parsed.config shouldBe TvConfig()
    }

    test("text that is not a json object applies nothing") {
        for (text in listOf("", "[1, 2]", "\"columns\"", "{\"columns\": ")) {
            val parsed = parseTvConfig(text)
            withClue(text) { parsed.overlayApplied.shouldBeFalse() }
            withClue(text) { parsed.config shouldBe TvConfig() }
        }
    }

    test("comments and trailing commas are accepted like the reference tokenizer") {
        val parsed = parseTvConfig("{ // theme\n \"columns\": 6, /* more */ \"rows_below_dock\": 2, }")
        (parsed.config.columns to parsed.config.rowsBelowDock) shouldBe (6 to 2)
    }

    val ranged: List<Triple<String, IntRange, (TvConfig) -> Int>> =
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
            Triple("unused_fade_days", 0..3650, { it.unusedFadeDays }),
        )

    test("each ranged number accepts its limits and rejects one step beyond") {
        for ((key, range, read) in ranged) {
            val default = read(TvConfig())
            for (inside in listOf(range.first, range.last)) {
                val parsed = parseTvConfig("{\"$key\": $inside}")
                withClue("$key = $inside") { read(parsed.config) shouldBe inside }
                withClue(key) { parsed.rejectedKeys shouldBe emptyList<String>() }
            }
            for (outside in listOf(range.first - 1, range.last + 1)) {
                val parsed = parseTvConfig("{\"$key\": $outside}")
                withClue("$key = $outside") { read(parsed.config) shouldBe default }
                withClue(key) { parsed.rejectedKeys shouldBe listOf(key) }
            }
        }
    }

    test("a value of the wrong type keeps the default and is rejected") {
        val parsed =
            parseTvConfig(
                """{"columns": "6", "fill_dock": "false", "dock_apps": "com.a", "greeting_name": 7,
                   "wifi_label_width": null, "animations": 0, "sky_intensity": 2147483648}""",
            )
        parsed.config shouldBe applied
        parsed.rejectedKeys shouldBe
            listOf("columns", "fill_dock", "dock_apps", "greeting_name", "wifi_label_width", "animations", "sky_intensity")
    }

    test("a fractional number is cut off as the reference does") {
        parseTvConfig("""{"columns": 4.9}""").config.columns shouldBe 4
    }

    test("a keycode list with any bad element is dropped whole") {
        val bad = parseTvConfig("""{"settings_key_keycodes": [172, 0], "input_key_keycodes": [1, "x"]}""")
        bad.config.settingsKeyKeycodes + bad.config.inputKeyKeycodes shouldBe emptyList<Int>()
        bad.rejectedKeys shouldBe listOf("settings_key_keycodes", "input_key_keycodes")

        val good = parseTvConfig("""{"settings_key_keycodes": [172, 1001]}""")
        good.config.settingsKeyKeycodes shouldBe listOf(172, 1001)
    }

    test("capture input keys true is read") {
        val parsed = parseTvConfig("""{"capture_input_keys": true}""")

        parsed.config.captureInputKeys.shouldBeTrue()
        parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys shouldBe emptyList<String>()
    }

    test("home ignore channel keys is read and a wrong type keeps the default") {
        val on = parseTvConfig("""{"home_ignore_channel_keys": true}""")
        on.config.homeIgnoreChannelKeys.shouldBeTrue()
        on.unsupportedKeys + on.unknownKeys + on.rejectedKeys shouldBe emptyList<String>()

        parseTvConfig("""{"home_ignore_channel_keys": false}""").config.homeIgnoreChannelKeys.shouldBeFalse()
        parseTvConfig("{}").config.homeIgnoreChannelKeys.shouldBeFalse()
        val wrong = parseTvConfig("""{"home_ignore_channel_keys": "yes"}""")
        wrong.config.homeIgnoreChannelKeys.shouldBeFalse()
        wrong.rejectedKeys shouldBe listOf("home_ignore_channel_keys")
    }

    test("the remote key service keys are read and a wrong type keeps the default") {
        val parsed =
            parseTvConfig(
                """{"key_filter": false, "gamepad_passthrough": true, "consume_volume_keys": true, "volume_pauses_filter": true,
                   "home_long_press_home": true,
                   "back_opens_clock": true, "settings_key_scancodes": [159], "stock_home_redirect": " com.google.android.apps.tv.launcherx ",
                   "app_keys": ["scan:490:com.wbd.hbomax", "hold:KEYCODE_BUTTON_8:org.xbmc.kodi"]}""",
            )
        val config = parsed.config
        config.keyFilter.shouldBeFalse()
        (config.gamepadPassthrough && config.consumeVolumeKeys && config.volumePausesFilter).shouldBeTrue()
        (config.homeLongPressHome && config.backOpensClock).shouldBeTrue()
        config.settingsKeyScancodes shouldBe listOf(159)
        config.stockHomeRedirect shouldBe "com.google.android.apps.tv.launcherx"
        config.appKeys shouldBe listOf("scan:490:com.wbd.hbomax", "hold:KEYCODE_BUTTON_8:org.xbmc.kodi")
        parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys shouldBe emptyList<String>()

        val wrong = parseTvConfig("""{"key_filter": "no", "consume_volume_keys": 1, "settings_key_scancodes": [0]}""")
        wrong.config.keyFilter.shouldBeTrue()
        wrong.config.consumeVolumeKeys.shouldBeFalse()
        wrong.config.settingsKeyScancodes shouldBe emptyList<Int>()
        wrong.rejectedKeys shouldBe listOf("key_filter", "consume_volume_keys", "settings_key_scancodes")
    }

    test("settings key clear task is off unless the theme turns it on") {
        parseTvConfig("{}").config.settingsKeyClearTask.shouldBeFalse()
        parseTvConfig("""{"settings_key_clear_task": true}""").config.settingsKeyClearTask.shouldBeTrue()
        parseTvConfig("""{"settings_key_clear_task": false}""").config.settingsKeyClearTask.shouldBeFalse()

        val wrong = parseTvConfig("""{"settings_key_clear_task": "yes"}""")
        wrong.config.settingsKeyClearTask.shouldBeFalse()
        wrong.rejectedKeys shouldBe listOf("settings_key_clear_task")
    }

    test("settings key panel stays unsupported because the panel does not exist") {
        val parsed = parseTvConfig("""{"settings_key_panel": true}""")
        parsed.unsupportedKeys shouldBe listOf("settings_key_panel")
    }

    test("string lists trim their items and skip non strings and blanks") {
        val parsed = parseTvConfig("""{"dock_apps": [" com.a ", "", 5, "com.b", null]}""")
        parsed.config.dockApps shouldBe listOf("com.a", "com.b")
        parsed.rejectedKeys shouldBe emptyList<String>()
    }

    test("package maps keep well formed entries and the last entry for a package") {
        val parsed = parseTvConfig("""{"app_labels": ["com.a=Alpha", "nope", "=x", "com.b= ", "com.a = Beta"]}""")
        parsed.config.appLabels shouldBe (mapOf("com.a" to "Beta"))
    }

    test("top bar apps leave the grid") {
        val parsed = parseTvConfig("""{"hidden_apps": ["com.a"], "top_bar_apps": ["com.b", "com.a", "com.b"]}""")
        parsed.config.hiddenApps shouldBe listOf("com.a", "com.b")
        parsed.config.topBarApps shouldBe listOf("com.b", "com.a", "com.b")
    }

    test("a blank optional string clears the default without a rejection") {
        val parsed = parseTvConfig("""{"live_tv_package": "  "}""")
        parsed.config.liveTvPackage shouldBe null
        parsed.rejectedKeys shouldBe emptyList<String>()
    }

    test("the background colour is forced opaque") {
        parseTvConfig("""{"background_color": "#80112233"}""").config.backgroundColor shouldBe 0xFF112233.toInt()
        parseTvConfig("""{"background_color": " #112233 "}""").config.backgroundColor shouldBe 0xFF112233.toInt()
        parseTvConfig("""{"background_color": "Red"}""").config.backgroundColor shouldBe 0xFFFF0000.toInt()
    }

    test("a background colour that android cannot parse keeps black and is rejected") {
        for (text in listOf("#12345", "#GGGGGG", "112233", "notacolour")) {
            val parsed = parseTvConfig("""{"background_color": "$text"}""")
            withClue(text) { parsed.config.backgroundColor shouldBe TvConfig().backgroundColor }
            withClue(text) { parsed.rejectedKeys shouldBe listOf("background_color") }
        }
    }

    test("clock format and wallpaper order accept their names and reject others") {
        val good = parseTvConfig("""{"clock_format": "24", "wallpaper_order": "sequential"}""")
        good.config.clockFormat shouldBe ClockFormat.Hour24
        good.config.wallpaperOrder shouldBe WallpaperOrder.Sequential

        val bad = parseTvConfig("""{"clock_format": "13", "wallpaper_order": "random"}""")
        bad.config shouldBe applied
        bad.rejectedKeys shouldBe listOf("clock_format", "wallpaper_order")
    }

    test("wallpaper moods keep only image names of the three moods") {
        val parsed =
            parseTvConfig(
                """{"wallpaper_moods": {"morning": ["a.jpg", "notes.txt", ".hidden.jpg"], "night": ["b.PNG"],
                   "evening": [], "noon": ["c.jpg"]}}""",
            )
        parsed.config.wallpaperMoods shouldBe (mapOf(WallpaperMood.Morning to setOf("a.jpg"), WallpaperMood.Night to setOf("b.PNG")))
        parsed.rejectedKeys shouldBe emptyList<String>()
        parseTvConfig("""{"wallpaper_moods": ["a.jpg"]}""").rejectedKeys shouldBe listOf("wallpaper_moods")
    }

    test("dock title is on unless a theme turns it off and a wrong type keeps it on") {
        parseTvConfig("{}").config.dockTitle.shouldBeTrue()
        parseTvConfig("""{"dock_title": true}""").config.dockTitle.shouldBeTrue()
        val off = parseTvConfig("""{"dock_title": false}""")
        off.config.dockTitle.shouldBeFalse()
        off.unsupportedKeys + off.unknownKeys + off.rejectedKeys shouldBe emptyList<String>()
        val wrong = parseTvConfig("""{"dock_title": "no"}""")
        wrong.config.dockTitle.shouldBeTrue()
        wrong.rejectedKeys shouldBe listOf("dock_title")
    }

    test("keys the reference knows but this launcher does not use are unsupported not unknown") {
        val parsed =
            parseTvConfig(
                """{"columns": 5, "festival": "diwali", "game_day": "x", "nonsense": 1, "dock_titel": true}""",
            )
        parsed.unsupportedKeys shouldBe listOf("festival", "game_day")
        parsed.unknownKeys shouldBe listOf("nonsense", "dock_titel")
        parsed.config.columns shouldBe 5
    }

    test("the cinematic and glow keys are read and a wrong type keeps the default") {
        val parsed =
            parseTvConfig(
                """{"cinematic_dots": false, "cinematic_white_focus": true, "ambient_glow": false, "glow_snap": true, "app_glow": false}""",
            )
        val config = parsed.config
        (config.cinematicDots || config.ambientGlow).shouldBeFalse()
        (config.cinematicWhiteFocus && config.glowSnap).shouldBeTrue()
        config.appGlow.shouldBeFalse()
        parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys shouldBe emptyList<String>()

        val wrong = parseTvConfig("""{"cinematic_dots": "no", "app_glow": 1}""")
        wrong.config.cinematicDots.shouldBeTrue()
        wrong.config.appGlow.shouldBeTrue()
        wrong.rejectedKeys shouldBe listOf("cinematic_dots", "app_glow")
    }

    test("header scrim is auto unless a theme says off and any other value keeps auto") {
        parseTvConfig("""{}""").config.headerScrim shouldBe HeaderScrim.Auto
        parseTvConfig("""{"header_scrim": "auto"}""").config.headerScrim shouldBe HeaderScrim.Auto

        val off = parseTvConfig("""{"header_scrim": " off "}""")
        off.config.headerScrim shouldBe HeaderScrim.Off
        off.rejectedKeys + off.unknownKeys shouldBe emptyList<String>()

        for (bad in listOf("\"strong\"", "false", "0")) {
            val parsed = parseTvConfig("""{"header_scrim": $bad}""")
            withClue(bad) { parsed.config.headerScrim shouldBe HeaderScrim.Auto }
            withClue(bad) { parsed.rejectedKeys shouldBe listOf("header_scrim") }
        }
    }

    test("grid scroll cue is on unless a theme says false and a value that is not a boolean keeps it on") {
        parseTvConfig("""{}""").config.gridScrollCue.shouldBeTrue()

        val off = parseTvConfig("""{"grid_scroll_cue": false}""")
        off.config.gridScrollCue.shouldBeFalse()
        off.rejectedKeys + off.unknownKeys shouldBe emptyList<String>()

        for (bad in listOf("\"false\"", "0")) {
            val parsed = parseTvConfig("""{"grid_scroll_cue": $bad}""")
            withClue(bad) { parsed.config.gridScrollCue.shouldBeTrue() }
            withClue(bad) { parsed.rejectedKeys shouldBe listOf("grid_scroll_cue") }
        }
    }

    test("glass style is glass unless a theme says rim or halo in any case and any other value keeps glass") {
        parseTvConfig("""{}""").config.glassStyle shouldBe GlassStyle.Glass
        parseTvConfig("""{"glass_style": "glass"}""").config.glassStyle shouldBe GlassStyle.Glass
        parseTvConfig("""{"glass_style": "rim"}""").config.glassStyle shouldBe GlassStyle.Rim

        val halo = parseTvConfig("""{"glass_style": " Halo "}""")
        halo.config.glassStyle shouldBe GlassStyle.Halo
        halo.rejectedKeys + halo.unknownKeys + halo.unsupportedKeys shouldBe emptyList<String>()

        for (bad in listOf("\"frosted\"", "\"\"", "true", "1")) {
            val parsed = parseTvConfig("""{"glass_style": $bad}""")
            withClue(bad) { parsed.config.glassStyle shouldBe GlassStyle.Glass }
            withClue(bad) { parsed.rejectedKeys shouldBe listOf("glass_style") }
        }
    }

    test("dock opacity takes 30 to 100 and anything else keeps the guarded default") {
        parseTvConfig("""{"dock_opacity": 30}""").config.dockOpacity shouldBe 30
        parseTvConfig("""{"dock_opacity": 100.9}""").config.dockOpacity shouldBe 100
        parseTvConfig("""{}""").config.dockOpacity shouldBe null

        for (bad in listOf("29", "101", "\"70\"", "true")) {
            val parsed = parseTvConfig("""{"dock_opacity": $bad}""")
            withClue(bad) { parsed.config.dockOpacity shouldBe null }
            withClue(bad) { parsed.rejectedKeys shouldBe listOf("dock_opacity") }
        }
    }

    test("icon only buttons is false in a theme that leaves it out and null with no theme") {
        parseTvConfig("""{"columns": 6}""").config.iconOnlyButtons shouldBe false
        parseTvConfig("{}").config.iconOnlyButtons shouldBe false
        parseTvConfig("[1, 2]").config.iconOnlyButtons shouldBe null
        parseTvConfig("").config.iconOnlyButtons shouldBe null
        parseTvConfig("""{"icon_only_buttons": true}""").config.iconOnlyButtons shouldBe true
        parseTvConfig("""{"icon_only_buttons": false}""").config.iconOnlyButtons shouldBe false
    }

    test("icon only buttons of the wrong type is rejected and reads as absent") {
        val parsed = parseTvConfig("""{"icon_only_buttons": "true"}""")
        parsed.config.iconOnlyButtons shouldBe false
        parsed.rejectedKeys shouldBe listOf("icon_only_buttons")
    }

    test("icon only inputs is false in a theme that leaves it out and null with no theme") {
        parseTvConfig("""{"columns": 6}""").config.iconOnlyInputs shouldBe false
        parseTvConfig("{}").config.iconOnlyInputs shouldBe false
        parseTvConfig("[1, 2]").config.iconOnlyInputs shouldBe null
        parseTvConfig("").config.iconOnlyInputs shouldBe null
        parseTvConfig("""{"icon_only_inputs": true}""").config.iconOnlyInputs shouldBe true
        parseTvConfig("""{"icon_only_inputs": false}""").config.iconOnlyInputs shouldBe false
    }

    test("dock even padding is false when absent true when set and rejected when not a boolean") {
        parseTvConfig("""{"columns": 6}""").config.dockEvenPadding shouldBe false
        parseTvConfig("""{"dock_even_padding": true}""").config.dockEvenPadding shouldBe true
        parseTvConfig("""{"dock_even_padding": false}""").config.dockEvenPadding shouldBe false
        val wrong = parseTvConfig("""{"dock_even_padding": "true"}""")
        wrong.config.dockEvenPadding shouldBe false
        wrong.rejectedKeys shouldBe listOf("dock_even_padding")
    }

    test("icon only inputs of the wrong type is rejected and reads as false") {
        val parsed = parseTvConfig("""{"icon_only_inputs": "true"}""")
        parsed.config.iconOnlyInputs shouldBe false
        parsed.rejectedKeys shouldBe listOf("icon_only_inputs")
    }

    test("the network button keys are read and a blank vpn app is none") {
        val parsed =
            parseTvConfig(
                """{"icon_only_buttons": true, "wifi_label_width": 120, "wifi_status_color": true,
                   "wifi_status_warnings": true, "vpn_app": " com.vpn.app "}""",
            )
        val config = parsed.config
        (config.wifiStatusColor && config.wifiStatusWarnings).shouldBeTrue()
        config.iconOnlyButtons shouldBe true
        config.wifiLabelWidth shouldBe 120
        config.vpnApp shouldBe "com.vpn.app"
        parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys shouldBe emptyList<String>()
        parseTvConfig("""{"vpn_app": "  "}""").config.vpnApp shouldBe null
    }

    test("latitude and longitude together set the place") {
        val parsed = parseTvConfig("""{"latitude": 40.4168, "longitude": -3.7038}""")
        parsed.config.place shouldBe Place(40.4168, -3.7038)
        parsed.unknownKeys + parsed.unsupportedKeys + parsed.rejectedKeys shouldBe emptyList<String>()
        parseTvConfig("""{"latitude": -90, "longitude": 180}""").config.place shouldBe Place(-90.0, 180.0)
    }

    test("a place needs both keys in range as numbers") {
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
            withClue(text) { parsed.config.place shouldBe null }
            withClue(text) { parsed.rejectedKeys shouldBe rejected }
        }
    }

    test("the tile menu dock row and reorder button keys are read") {
        val parsed = parseTvConfig("""{"tile_menu": false, "dock_row": false, "reorder_button": true}""")
        (parsed.config.tileMenu || parsed.config.dockRow).shouldBeFalse()
        parsed.config.reorderButton.shouldBeTrue()
        parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys shouldBe emptyList<String>()

        val wrong = parseTvConfig("""{"dock_row": "no", "reorder_button": 1}""")
        wrong.config.dockRow.shouldBeTrue()
        wrong.config.reorderButton.shouldBeFalse()
        wrong.rejectedKeys shouldBe listOf("dock_row", "reorder_button")
    }

    test("the app library keys are read") {
        val parsed =
            parseTvConfig(
                """{"app_library": true, "app_library_selection": true, "library_button": true, "show_library_pill": false,
                    "library_ok_opens": true, "library_apps": ["a.b"], "library_excluded_apps": ["c.d"]}""",
            )
        val config = parsed.config
        (config.appLibrary && config.appLibrarySelection && config.libraryButton && config.libraryOkOpens).shouldBeTrue()
        config.showLibraryPill.shouldBeFalse()
        (config.libraryApps to config.libraryExcludedApps) shouldBe (listOf("a.b") to listOf("c.d"))
        parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys shouldBe emptyList<String>()

        val wrong = parseTvConfig("""{"app_library_selection": "yes", "library_button": 1}""")
        (wrong.config.appLibrarySelection || wrong.config.libraryButton).shouldBeFalse()
        wrong.rejectedKeys shouldBe listOf("app_library_selection", "library_button")
    }

    test("the inputs and open on start keys are read") {
        val parsed =
            parseTvConfig(
                """{"source_panel_service": " com.tcl/.Panel ", "source_panel_action": "com.tcl.SHOW", "source_panel_extra": "cmd=100",
                    "coocaa_sources": true, "inputs_connected_only": true, "inputs_cec_state_only": true, "handle_view_inputs": true,
                    "open_on_start_network_wait": 180}""",
            )
        val config = parsed.config
        Triple(config.sourcePanelService, config.sourcePanelAction, config.sourcePanelExtra) shouldBe
            Triple("com.tcl/.Panel", "com.tcl.SHOW", "cmd=100")
        (config.coocaaSources && config.inputsConnectedOnly && config.inputsCecStateOnly && config.handleViewInputs).shouldBeTrue()
        config.openOnStartNetworkWait shouldBe 180
        parsed.unsupportedKeys + parsed.unknownKeys + parsed.rejectedKeys shouldBe emptyList<String>()

        val wrong = parseTvConfig("""{"coocaa_sources": "yes", "source_panel_service": 5, "open_on_start_network_wait": 601}""")
        wrong.config.coocaaSources.shouldBeFalse()
        wrong.config.sourcePanelService shouldBe null
        wrong.config.openOnStartNetworkWait shouldBe 0
        wrong.rejectedKeys shouldBe listOf("coocaa_sources", "source_panel_service", "open_on_start_network_wait")
    }

    test("the settings entry keys default off read true and reject a wrong type") {
        val keys = listOf("launcher_settings_button", "open_on_start_choice", "open_on_start_on_wake")
        val flags = { c: TvConfig -> listOf(c.launcherSettingsButton, c.openOnStartChoice, c.openOnStartOnWake) }
        flags(parseTvConfig("{}").config) shouldBe listOf(false, false, false)

        val on = parseTvConfig(keys.joinToString(prefix = "{", postfix = "}") { "\"$it\": true" })
        flags(on.config) shouldBe listOf(true, true, true)
        on.unsupportedKeys + on.unknownKeys + on.rejectedKeys shouldBe emptyList<String>()

        val wrong = parseTvConfig(keys.joinToString(prefix = "{", postfix = "}") { "\"$it\": \"yes\"" })
        flags(wrong.config) shouldBe listOf(false, false, false)
        wrong.rejectedKeys shouldBe keys
    }

    test("keys with no reader in this launcher are unsupported and change nothing") {
        val keys = listOf("app_banners", "locale")
        val parsed = parseTvConfig(keys.joinToString(prefix = "{", postfix = "}") { "\"$it\": true" })

        parsed.unsupportedKeys shouldBe keys
        parsed.unknownKeys + parsed.rejectedKeys shouldBe emptyList<String>()
        parsed.config shouldBe applied
    }

    val defaults = applied

    /** key to (JSON value, the config that value alone produces); latitude and longitude set one field, so they share a row. */
    val nonDefaults: Map<String, Pair<String, TvConfig>> =
        mapOf(
            "dock_apps" to ("""["com.a"]""" to defaults.copy(dockApps = listOf("com.a"))),
            "fill_dock" to ("false" to defaults.copy(fillDock = false)),
            "hidden_apps" to ("""["com.a"]""" to defaults.copy(hiddenApps = listOf("com.a"))),
            "columns" to ("6" to defaults.copy(columns = 6)),
            "rows_below_dock" to ("2" to defaults.copy(rowsBelowDock = 2)),
            "single_row" to ("true" to defaults.copy(singleRow = true)),
            "always_show_names" to ("true" to defaults.copy(alwaysShowNames = true)),
            "dock_title" to ("false" to defaults.copy(dockTitle = false)),
            "grid_scroll_cue" to ("false" to defaults.copy(gridScrollCue = false)),
            "launch_actions" to ("""["com.a=ACT"]""" to defaults.copy(launchActions = mapOf("com.a" to "ACT"))),
            "app_labels" to ("""["com.a=Alpha"]""" to defaults.copy(appLabels = mapOf("com.a" to "Alpha"))),
            "top_bar_apps" to
                ("""["com.t"]""" to defaults.copy(topBarApps = listOf("com.t"), hiddenApps = defaults.hiddenApps + "com.t")),
            "greeting_name" to (""""Ann"""" to defaults.copy(greetingName = "Ann")),
            "show_greeting" to ("false" to defaults.copy(showGreeting = false)),
            "clock_format" to (""""24"""" to defaults.copy(clockFormat = ClockFormat.Hour24)),
            "date_below_clock" to ("true" to defaults.copy(dateBelowClock = true)),
            "show_network_pill" to ("false" to defaults.copy(showNetworkPill = false)),
            "show_inputs_pill" to ("false" to defaults.copy(showInputsPill = false)),
            "icon_only_buttons" to ("true" to defaults.copy(iconOnlyButtons = true)),
            "icon_only_inputs" to ("true" to defaults.copy(iconOnlyInputs = true)),
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
            "app_glow" to ("false" to defaults.copy(appGlow = false)),
            "dock_opacity" to ("70" to defaults.copy(dockOpacity = 70)),
            "dock_glass" to ("true" to defaults.copy(dockGlass = true)),
            "dock_band" to ("false" to defaults.copy(dockBand = false)),
            "dock_edge_light" to ("false" to defaults.copy(dockEdgeLight = false)),
            "dock_even_padding" to ("true" to defaults.copy(dockEvenPadding = true)),
            "background_color" to (""""#112233"""" to defaults.copy(backgroundColor = 0xFF112233.toInt())),
            "header_scrim" to (""""off"""" to defaults.copy(headerScrim = HeaderScrim.Off)),
            "glass_style" to (""""rim"""" to defaults.copy(glassStyle = GlassStyle.Rim)),
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
            "focus_overshoot" to ("true" to defaults.copy(focusOvershoot = true)),
            "tile_menu" to ("false" to defaults.copy(tileMenu = false)),
            "reorder_button" to ("true" to defaults.copy(reorderButton = true)),
            "launcher_settings_button" to ("true" to defaults.copy(launcherSettingsButton = true)),
            "app_library" to ("true" to defaults.copy(appLibrary = true)),
            "app_library_selection" to ("true" to defaults.copy(appLibrarySelection = true)),
            "library_button" to ("true" to defaults.copy(libraryButton = true)),
            "library_apps" to ("""["a.b"]""" to defaults.copy(libraryApps = listOf("a.b"))),
            "library_excluded_apps" to ("""["a.b"]""" to defaults.copy(libraryExcludedApps = listOf("a.b"))),
            "show_library_pill" to ("false" to defaults.copy(showLibraryPill = false)),
            "library_ok_opens" to ("true" to defaults.copy(libraryOkOpens = true)),
            "library_least_used" to ("true" to defaults.copy(libraryLeastUsed = true)),
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
            "input_arrival" to ("true" to defaults.copy(inputArrival = true)),
            "habit_hint" to ("true" to defaults.copy(habitHint = true)),
            "unused_fade_days" to ("60" to defaults.copy(unusedFadeDays = 60)),
            "handle_view_inputs" to ("true" to defaults.copy(handleViewInputs = true)),
            "open_on_start_network_wait" to ("30" to defaults.copy(openOnStartNetworkWait = 30)),
            "open_on_start_choice" to ("true" to defaults.copy(openOnStartChoice = true)),
            "open_on_start_on_wake" to ("true" to defaults.copy(openOnStartOnWake = true)),
            "app_keys" to ("""["172:com.a"]""" to defaults.copy(appKeys = listOf("172:com.a"))),
            "settings_key_keycodes" to ("[172]" to defaults.copy(settingsKeyKeycodes = listOf(172))),
            "settings_key_scancodes" to ("[159]" to defaults.copy(settingsKeyScancodes = listOf(159))),
            "settings_key_clear_task" to ("true" to defaults.copy(settingsKeyClearTask = true)),
            "input_key_keycodes" to ("[1001]" to defaults.copy(inputKeyKeycodes = listOf(1001))),
            "capture_input_keys" to ("true" to defaults.copy(captureInputKeys = true)),
            "key_filter" to ("false" to defaults.copy(keyFilter = false)),
            "gamepad_passthrough" to ("true" to defaults.copy(gamepadPassthrough = true)),
            "consume_volume_keys" to ("true" to defaults.copy(consumeVolumeKeys = true)),
            "volume_pauses_filter" to ("true" to defaults.copy(volumePausesFilter = true)),
            "stock_home_redirect" to (""""com.stock"""" to defaults.copy(stockHomeRedirect = "com.stock")),
            "home_long_press_home" to ("true" to defaults.copy(homeLongPressHome = true)),
            "home_ignore_channel_keys" to ("true" to defaults.copy(homeIgnoreChannelKeys = true)),
            "power_on_home" to ("true" to defaults.copy(powerOnHome = true)),
            "power_on_home_always" to ("true" to defaults.copy(powerOnHomeAlways = true)),
            "restore_key_services" to ("true" to defaults.copy(restoreKeyServices = true)),
            "home_idle_clock_minutes" to ("10" to defaults.copy(homeIdleClockMinutes = 10)),
            "back_opens_clock" to ("true" to defaults.copy(backOpensClock = true)),
            "clock_wallpaper" to ("true" to defaults.copy(clockWallpaper = true)),
            "clock_wallpaper_dim" to ("20" to defaults.copy(clockWallpaperDim = 20)),
            "clock_glow" to ("false" to defaults.copy(clockGlow = false)),
        )

    test("every supported key at a non default value changes exactly its own field") {
        for ((key, row) in nonDefaults) {
            val (value, expected) = row
            val json = if (key == "latitude") """{"latitude": 40.4, "longitude": -3.7}""" else """{"$key": $value}"""
            val parsed = parseTvConfig(json)
            withClue(key) { parsed.config shouldBe expected }
            withClue(key) { parsed.unknownKeys + parsed.unsupportedKeys + parsed.rejectedKeys shouldBe emptyList<String>() }
        }
        val fields =
            TvConfig::class.java.declaredFields.count {
                !java.lang.reflect.Modifier
                    .isStatic(it.modifiers)
            }
        withClue("a TvConfig field has no row") { nonDefaults.size shouldBe fields }
    }
}
