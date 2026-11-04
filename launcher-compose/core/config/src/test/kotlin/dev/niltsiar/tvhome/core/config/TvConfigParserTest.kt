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
            listOf(4, 0, 0, 50, 100, 50, 100),
            listOf(
                config.columns,
                config.rowsBelowDock,
                config.wallpaperDim,
                config.cinematicArt,
                config.cinematicScrim,
                config.clockWallpaperDim,
                config.dockOpacity,
            ),
        )
        assertEquals(ClockFormat.System, config.clockFormat)
        assertEquals(WallpaperOrder.Shuffle, config.wallpaperOrder)
        assertEquals(0xFF000000.toInt(), config.backgroundColor)
        assertTrue(config.wallpaper && config.fillDock && config.showGreeting && config.tileMenu && config.clockGlow)
        assertFalse(config.appLibrary || config.cinematicHome || config.singleRow || config.powerOnHomeAlways)
    }

    @Test
    fun a_byte_order_mark_is_dropped() {
        val parsed = parseTvConfig("{\"columns\": 6}")
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
            Triple("dock_opacity", 30..100, { it.dockOpacity }),
            Triple("home_idle_clock_minutes", 0..1440, { it.homeIdleClockMinutes }),
            Triple("clock_wallpaper_dim", 0..90, { it.clockWallpaperDim }),
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
                   "wifi_label_width": null, "animations": 0, "dock_opacity": 2147483648}""",
            )
        assertEquals(TvConfig(), parsed.config)
        assertEquals(
            listOf("columns", "fill_dock", "dock_apps", "greeting_name", "wifi_label_width", "animations", "dock_opacity"),
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
        val parsed = parseTvConfig("""{"live_tv_package": "  ", "locale": " es-ES "}""")
        assertEquals(null, parsed.config.liveTvPackage)
        assertEquals("es-ES", parsed.config.locale)
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
                """{"dock_title": false, "columns": 5, "festival": "diwali", "sky_intensity": 40, "nonsense": 1, "dock_titel": true}""",
            )
        assertEquals(listOf("dock_title", "festival", "sky_intensity"), parsed.unsupportedKeys)
        assertEquals(listOf("nonsense", "dock_titel"), parsed.unknownKeys)
        assertEquals(5, parsed.config.columns)
    }
}
