package dev.niltsiar.tvhome.service.keys

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Environment
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.LauncherIntents
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = ConfigApplication::class)
class KeyServiceTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val packages = shadowOf(app.packageManager)
    private val home = ComponentName(app.packageName, "${app.packageName}.MainActivity")
    private val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    private lateinit var service: KeyService
    private var device = 100

    @Before
    fun setUp() {
        SharedPresses.reset()
        GuardKeep.reset()
        packages.addActivityIfNotPresent(home)
        packages.addIntentFilterForActivity(
            home,
            IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            },
        )
        service = Robolectric.setupService(KeyService::class.java)
    }

    private fun installLeanbackApp(packageName: String) {
        val component = ComponentName(packageName, "$packageName.Main")
        packages.addActivityIfNotPresent(component)
        packages.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
        )
    }

    private fun started(): Intent? = shadowOf(app).nextStartedActivity

    private fun key(
        action: Int,
        code: Int,
        scan: Int = 0,
        downAt: Long = 1_000,
        at: Long = downAt,
        flags: Int = 0,
    ) = KeyEvent(downAt, at, action, code, 0, 0, device, scan, flags)

    private fun tap(
        code: Int,
        scan: Int = 0,
    ): Boolean {
        val down = service.handleKey(key(KeyEvent.ACTION_DOWN, code, scan))
        val up = service.handleKey(key(KeyEvent.ACTION_UP, code, scan, at = 1_100))
        return down && up
    }

    @Test
    fun a_remapped_button_opens_its_app_on_a_tap_and_is_consumed() {
        installLeanbackApp("com.netflix.ninja")
        service.start(TvConfig(appKeys = listOf("190:com.netflix.ninja")))

        assertTrue(tap(190))

        assertEquals("com.netflix.ninja", started()?.component?.packageName)
        assertNull(started())
    }

    @Test
    fun a_button_that_is_not_mapped_passes_through() {
        service.start(TvConfig(appKeys = listOf("190:com.netflix.ninja")))

        assertFalse(service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
        assertFalse(service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)))
        assertNull(started())
    }

    @Test
    fun a_scancode_entry_wins_over_the_keycode_it_shares_a_button_with() {
        installLeanbackApp("com.amazon.amazonvideo.livingroom")
        installLeanbackApp("com.google.android.youtube.tv")
        service.start(
            TvConfig(appKeys = listOf("196:com.google.android.youtube.tv", "scan:490:com.amazon.amazonvideo.livingroom")),
        )

        assertTrue(tap(196, scan = 490))

        assertEquals("com.amazon.amazonvideo.livingroom", started()?.component?.packageName)
    }

    @Test
    fun a_held_button_opens_its_hold_target_and_not_the_tap_one() {
        installLeanbackApp("com.netflix.ninja")
        installLeanbackApp("org.xbmc.kodi")
        service.start(TvConfig(appKeys = listOf("190:com.netflix.ninja", "hold:190:org.xbmc.kodi")))

        service.handleKey(key(KeyEvent.ACTION_DOWN, 190))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(HOLD_MS))
        service.handleKey(key(KeyEvent.ACTION_UP, 190, at = 1_000 + HOLD_MS + 50))

        assertEquals("org.xbmc.kodi", started()?.component?.packageName)
        assertNull(started())
    }

    @Test
    fun the_inputs_and_library_words_reach_homes_own_activity() {
        service.start(TvConfig(appKeys = listOf("365:inputs", "366:library")))

        tap(365)
        val inputs = started()
        assertEquals(LauncherIntents.VIEW_INPUTS, inputs?.action)
        assertEquals(home, inputs?.component)

        device += 1
        tap(366)
        val library = started()
        assertEquals(LauncherIntents.APP_LIBRARY, library?.action)
        assertEquals(home, library?.component)
    }

    @Test
    fun an_input_target_opens_the_passthrough_channel_uri() {
        service.start(TvConfig(appKeys = listOf("4062:input:com.tcl.tvinput/.TvPassThroughService/HW1")))

        tap(4062)

        val view = started()
        assertEquals(Intent.ACTION_VIEW, view?.action)
        assertEquals("vnd.android.cursor.item/channel", view?.type)
        assertTrue(view?.dataString.orEmpty().contains("HW1"))
    }

    @Test
    fun the_back_word_performs_the_global_action_and_ignore_swallows_the_button() {
        service.start(TvConfig(appKeys = listOf("111:back", "112:ignore")))

        assertTrue(tap(111))
        device += 1
        assertTrue(tap(112))

        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_BACK), shadowOf(service).globalActionsPerformed)
        assertNull(started())
    }

    @Test
    fun the_settings_key_opens_settings_on_the_up_and_a_listed_keycode_does_too() {
        service.start(TvConfig(settingsKeyKeycodes = listOf(1234), settingsKeyScancodes = listOf(159)))

        assertTrue(service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SETTINGS)))
        assertNull(started())
        assertTrue(service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SETTINGS, at = 1_100)))
        assertEquals(Settings.ACTION_SETTINGS, started()?.action)

        device += 1
        assertTrue(tap(1234))
        assertEquals(Settings.ACTION_SETTINGS, started()?.action)

        device += 1
        assertTrue(tap(0, scan = 159))
        assertEquals(Settings.ACTION_SETTINGS, started()?.action)
    }

    @Test
    fun the_settings_key_clears_the_settings_task_only_with_settings_key_clear_task() {
        service.start(TvConfig())
        assertTrue(tap(KeyEvent.KEYCODE_SETTINGS))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started()?.flags)

        device += 1
        service.start(TvConfig(settingsKeyClearTask = true))
        assertTrue(tap(KeyEvent.KEYCODE_SETTINGS))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK, started()?.flags)
    }

    private fun pressInputButton(code: Int): Pair<Boolean, Boolean> {
        val down = service.onKeyEvent(key(KeyEvent.ACTION_DOWN, code))
        val up = service.onKeyEvent(key(KeyEvent.ACTION_UP, code, at = 1_100))
        return down to up
    }

    @Test
    fun the_tv_and_input_buttons_pass_through_with_the_default_config() {
        service.start(TvConfig())

        for (code in listOf(KeyEvent.KEYCODE_TV, KeyEvent.KEYCODE_TV_INPUT)) {
            assertEquals(false to false, pressInputButton(code))
        }
        assertNull(started())
    }

    @Test
    fun the_tv_and_input_buttons_are_consumed_and_open_the_switcher_on_the_up_with_capture_input_keys() {
        service.start(TvConfig(captureInputKeys = true))

        for (code in listOf(KeyEvent.KEYCODE_TV, KeyEvent.KEYCODE_TV_INPUT)) {
            assertTrue(service.onKeyEvent(key(KeyEvent.ACTION_DOWN, code)))
            assertNull(started())
            assertTrue(service.onKeyEvent(key(KeyEvent.ACTION_UP, code, at = 1_100)))
            assertEquals(LauncherIntents.VIEW_INPUTS, started()?.action)
        }
    }

    @Test
    fun a_keycode_a_theme_lists_in_input_key_keycodes_is_consumed_with_the_default_config() {
        service.start(TvConfig(inputKeyKeycodes = listOf(4100)))

        assertEquals(true to true, pressInputButton(4100))
        assertEquals(LauncherIntents.VIEW_INPUTS, started()?.action)
    }

    @Test
    fun the_switcher_request_goes_to_homes_own_activity_when_no_app_answers_it() {
        shadowOf(app).checkActivities(true)
        service.start(TvConfig(captureInputKeys = true))

        pressInputButton(KeyEvent.KEYCODE_TV_INPUT)

        val inputs = started()
        assertEquals(LauncherIntents.VIEW_INPUTS, inputs?.action)
        assertEquals(home, inputs?.component)
    }

    @Test
    fun a_listed_app_key_beats_the_input_button_it_shares() {
        installLeanbackApp("com.netflix.ninja")
        service.start(TvConfig(appKeys = listOf("${KeyEvent.KEYCODE_TV_INPUT}:com.netflix.ninja"), captureInputKeys = true))

        tap(KeyEvent.KEYCODE_TV_INPUT)

        assertEquals("com.netflix.ninja", started()?.component?.packageName)
    }

    @Test
    fun all_apps_opens_the_launcher_with_a_home_intent_when_it_is_the_home_app() {
        service.start(TvConfig())

        assertTrue(tap(KeyEvent.KEYCODE_ALL_APPS))

        val open = started()
        assertEquals(Intent.ACTION_MAIN, open?.action)
        assertEquals(app.packageName, open?.`package`)
        assertTrue(open?.hasCategory(Intent.CATEGORY_HOME) == true)
    }

    private fun stockLauncherIsHome(stock: String) {
        fun resolved(
            pkg: String,
            name: String,
        ) = ResolveInfo().apply {
            activityInfo =
                ActivityInfo().apply {
                    packageName = pkg
                    this.name = name
                }
        }
        packages.addResolveInfoForIntent(Intent(homeIntent), resolved(stock, "$stock.HomeActivity"))
        packages.addResolveInfoForIntent(Intent(homeIntent).setPackage(stock), resolved(stock, "$stock.HomeActivity"))
    }

    @Test
    fun while_the_stock_launcher_stays_home_the_launcher_starts_like_an_app_and_not_with_a_home_intent() {
        stockLauncherIsHome("com.stock.launcher")
        service.start(TvConfig(stockHomeRedirect = "com.stock.launcher"))

        assertTrue(tap(KeyEvent.KEYCODE_ALL_APPS))

        val open = started()
        assertEquals(home, open?.component)
        assertNull(open?.action)
        assertTrue(open?.hasCategory(Intent.CATEGORY_HOME) == true)
    }

    @Test
    fun a_window_of_the_stock_home_sends_the_tv_back_to_the_launcher_and_other_windows_do_not() {
        stockLauncherIsHome("com.stock.launcher")
        service.start(TvConfig(stockHomeRedirect = "com.stock.launcher"))

        service.handleWindow(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.netflix.ninja", "com.netflix.ninja.Main")
        assertNull(started())
        service.handleWindow(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.stock.launcher", "com.stock.launcher.HomeActivity")
        assertEquals(home, started()?.component)
    }

    @Test
    fun a_short_home_press_opens_the_launcher_after_the_system_saw_it_and_a_long_one_does_not() {
        stockLauncherIsHome("com.stock.launcher")
        service.start(TvConfig(stockHomeRedirect = "com.stock.launcher"))

        assertFalse(service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)))
        assertFalse(service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, at = 1_120)))
        assertNull(started())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(HOME_KEY_DELAY_MS))
        assertEquals(home, started()?.component)

        service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, downAt = 5_000))
        service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, downAt = 5_000, at = 6_500))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertNull(started())
    }

    @Test
    fun home_long_press_home_takes_the_home_key_and_opens_the_launcher_on_the_up() {
        service.start(TvConfig(homeLongPressHome = true))

        assertTrue(service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)))
        assertNull(started())
        assertTrue(service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, at = 2_000)))
        assertNotNull(started())

        device += 1
        service.start(TvConfig())
        assertFalse(service.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, at = 2_000)))
    }

    @Test
    fun key_filter_false_drops_the_filter_flag_and_a_redirect_asks_for_window_events() {
        val filtering = AccessibilityServiceInfo().apply { flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS }

        assertFalse(adjustServiceInfo(filtering, keyFilter = true, windowEvents = false))
        assertEquals(AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS, filtering.flags)

        assertTrue(adjustServiceInfo(filtering, keyFilter = false, windowEvents = false))
        assertEquals(0, filtering.flags)
        assertEquals(0, filtering.eventTypes)

        assertTrue(adjustServiceInfo(filtering, keyFilter = true, windowEvents = true))
        assertEquals(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, filtering.eventTypes)
    }

    @Test
    fun a_front_entry_follows_the_activity_in_front() {
        val tvCenter = ComponentName("com.vt.tv.tvcenter", "com.vt.tv.tvcenter.Main")
        packages.addActivityIfNotPresent(tvCenter)
        installLeanbackApp("com.vt.tv.tvcenter")
        service.start(TvConfig(appKeys = listOf("172:com.vt.tv.tvcenter", "front:172:home")))

        service.handleWindow(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, tvCenter.packageName, tvCenter.className)
        assertTrue(tap(172))
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_HOME), shadowOf(service).globalActionsPerformed)
        assertNull(started())

        // A dialog is not an activity: the app under it stays in front.
        service.handleWindow(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.vt.tv.tvcenter", "android.app.AlertDialog")
        device += 1
        tap(172)
        assertEquals(2, shadowOf(service).globalActionsPerformed.size)
    }

    private fun pushTheme(json: String) {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        File(ThemeDirectory(app).dir!!, "config.json").writeText(json)
    }

    /** The service reads its config off the main thread, then starts on it: wait until [code] is mapped. */
    private fun awaitMapped(code: Int) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            device += 1
            if (tap(code)) break
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(app).clearNextStartedActivities()
        device += 1
    }

    private fun screenOn() = app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))

    private fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun pick(
        picks: MutableStateFlow<String?>,
        app: String?,
    ) {
        picks.value = app
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun power_on_home_always_opens_home_800_ms_after_the_screen_comes_on() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10)) // past the boot window
        service.start(TvConfig(powerOnHomeAlways = true))
        idleFor(2_000)
        assertNull(started())

        screenOn()
        idleFor(POWER_ON_ALWAYS_DELAY_MS - 1)
        assertNull(started())
        idleFor(1)
        assertTrue(started()?.hasCategory(Intent.CATEGORY_HOME) == true)
    }

    @Test
    fun power_on_home_always_waits_when_a_key_was_pressed_after_the_screen_came_on() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10))
        service.start(TvConfig(powerOnHomeAlways = true))
        idleFor(2_000)
        shadowOf(app).clearNextStartedActivities()

        screenOn()
        idleFor(300)
        service.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        idleFor(POWER_ON_ALWAYS_DELAY_MS)

        assertNull(started())
    }

    private fun homeStarted() = generateSequence { started() }.any { it.hasCategory(Intent.CATEGORY_HOME) }

    private val alwaysOnWithChoice = TvConfig(powerOnHomeAlways = true, openOnStartChoice = true)

    @Test
    fun a_pick_written_after_the_bind_stops_power_on_home_always_and_clearing_it_brings_it_back() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10))
        val picks = MutableStateFlow<String?>(null)
        service.follow(MutableStateFlow(alwaysOnWithChoice), picks)

        pick(picks, "com.netflix.ninja")
        screenOn()
        idleFor(POWER_ON_ALWAYS_DELAY_MS * 2)
        assertFalse(homeStarted())

        pick(picks, null)
        screenOn()
        idleFor(POWER_ON_ALWAYS_DELAY_MS)
        assertTrue(homeStarted())
    }

    @Test
    fun a_pick_written_after_the_bind_stops_the_live_tv_redirect_when_it_is_live_tv() {
        val picks = MutableStateFlow<String?>("com.netflix.ninja")
        service.follow(MutableStateFlow(TvConfig(powerOnHome = true, openOnStartChoice = true)), picks)
        screenOn()
        service.handleWindow(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.mediatek.wwtv.tvcenter", null)
        assertTrue(homeStarted())

        pick(picks, "com.mediatek.wwtv.tvcenter")
        screenOn()
        service.handleWindow(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.mediatek.wwtv.tvcenter", null)
        assertFalse(homeStarted())
    }

    @Test
    fun clearing_the_pick_inside_the_boot_window_does_not_send_home_over_the_screen_in_front() {
        val picks = MutableStateFlow<String?>("com.netflix.ninja")
        service.follow(MutableStateFlow(alwaysOnWithChoice), picks)
        idleFor(2_000)

        pick(picks, null)
        idleFor(2_000)

        assertFalse(homeStarted())
    }

    @Test
    fun a_pick_that_leaves_the_plan_as_it_was_does_not_start_the_service_again() {
        installLeanbackApp("org.xbmc.kodi")
        val picks = MutableStateFlow<String?>("com.netflix.ninja")
        service.follow(MutableStateFlow(holdKeys()), picks)
        service.handleKey(key(KeyEvent.ACTION_DOWN, 190))

        pick(picks, "org.xbmc.kodi")
        idleFor(HOLD_MS)
        service.handleKey(key(KeyEvent.ACTION_UP, 190, at = 1_000 + HOLD_MS + 50))

        assertEquals("org.xbmc.kodi", started()?.component?.packageName)
    }

    @Test
    fun a_service_bound_while_the_user_is_locked_reads_the_theme_only_after_the_unlock() {
        installLeanbackApp("com.netflix.ninja")
        pushTheme("""{"app_keys": ["190:com.netflix.ninja"]}""")
        shadowOf(app.getSystemService(android.os.UserManager::class.java)).setUserUnlocked(false)

        service.connected()
        assertFalse(service.handleKey(key(KeyEvent.ACTION_DOWN, 190)))

        shadowOf(app.getSystemService(android.os.UserManager::class.java)).setUserUnlocked(true)
        app.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        awaitMapped(190)
        assertTrue(tap(190))
        assertEquals("com.netflix.ninja", started()?.component?.packageName)
    }

    @Test
    fun a_service_bound_after_the_unlock_reads_the_theme_at_once() {
        installLeanbackApp("com.netflix.ninja")
        pushTheme("""{"app_keys": ["190:com.netflix.ninja"]}""")

        service.connected()

        awaitMapped(190)
        assertTrue(tap(190))
        assertEquals("com.netflix.ninja", started()?.component?.packageName)
    }

    @Test
    fun the_power_menu_opens_through_the_bound_service_and_is_gone_once_it_is_destroyed() {
        service.connected()

        assertTrue(PowerMenu.available())
        PowerMenu.open()
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG), shadowOf(service).globalActionsPerformed)

        service.onDestroy()

        assertFalse(PowerMenu.available())
        PowerMenu.open()
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG), shadowOf(service).globalActionsPerformed)
    }

    @Test
    fun the_destroy_of_an_earlier_service_does_not_unbind_the_one_that_replaced_it() {
        val replacement = Robolectric.setupService(KeyService::class.java)
        service.connected()
        replacement.connected()

        service.onDestroy()

        assertTrue(PowerMenu.available())
        PowerMenu.open()
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG), shadowOf(replacement).globalActionsPerformed)
        replacement.onDestroy()
    }

    private fun holdKeys() = TvConfig(appKeys = listOf("190:com.netflix.ninja", "hold:190:org.xbmc.kodi"))

    @Test
    fun a_choice_the_service_does_not_read_leaves_it_running_and_a_pressed_hold_stands() {
        installLeanbackApp("org.xbmc.kodi")
        val configs = MutableStateFlow(holdKeys())
        service.follow(configs)
        service.handleKey(key(KeyEvent.ACTION_DOWN, 190))

        configs.value = configs.value.copy(columns = 4, rowsBelowDock = 2, appLibrary = true, clockFormat = ClockFormat.Hour12)
        idleFor(HOLD_MS)
        service.handleKey(key(KeyEvent.ACTION_UP, 190, at = 1_000 + HOLD_MS + 50))

        assertEquals("org.xbmc.kodi", started()?.component?.packageName)
    }

    @Test
    fun a_value_the_service_reads_starts_it_again_with_the_new_maps() {
        installLeanbackApp("org.xbmc.kodi")
        installLeanbackApp("com.plex.tv")
        val configs = MutableStateFlow(holdKeys())
        service.follow(configs)
        service.handleKey(key(KeyEvent.ACTION_DOWN, 190))

        configs.value = configs.value.copy(appKeys = listOf("190:com.plex.tv"))
        idleFor(HOLD_MS)
        service.handleKey(key(KeyEvent.ACTION_UP, 190, at = 1_000 + HOLD_MS + 50))
        assertNull(started())

        device += 1
        assertTrue(tap(190))
        assertEquals("com.plex.tv", started()?.component?.packageName)
    }

    private fun recordedServices(): Set<String> = ServiceRepair.recorded(app)

    @Test
    fun with_restore_key_services_the_service_records_itself_on_load_and_drops_the_record_when_unbound() {
        service.start(TvConfig(restoreKeyServices = true))

        assertEquals(setOf(KeyService::class.java.name), recordedServices())

        service.onUnbind(Intent())

        assertEquals(emptySet<String>(), recordedServices())
    }

    @Test
    fun without_restore_key_services_nothing_is_recorded() {
        service.start(TvConfig())

        assertEquals(emptySet<String>(), recordedServices())
    }

    private fun optInToStandbyExit() {
        GuardKeep.setOffInStandby(app, true)
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
    }

    private fun screenOff() {
        shadowOf(app.getSystemService(PowerManager::class.java)).turnScreenOn(false)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun watchingScreenOn() = shadowOf(app).registeredReceivers.any { it.intentFilter.hasAction(Intent.ACTION_SCREEN_ON) }

    @Test
    fun with_the_opt_in_and_the_permission_the_screen_going_off_makes_the_service_wait_for_the_screen() {
        optInToStandbyExit()
        service.start(TvConfig())

        screenOff()

        assertTrue(watchingScreenOn())
    }

    @Test
    fun without_the_permission_the_screen_going_off_leaves_the_service_bound() {
        GuardKeep.setOffInStandby(app, true)
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        service.start(TvConfig())

        screenOff()

        assertFalse(watchingScreenOn())
    }

    @Test
    fun without_the_opt_in_the_screen_going_off_leaves_the_service_bound() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        service.start(TvConfig())

        screenOff()

        assertFalse(watchingScreenOn())
    }

    @Test
    fun an_opt_in_that_arrives_after_the_service_bound_arms_it() {
        service.start(TvConfig())
        PowerMenu.bind(service)

        optInToStandbyExit()
        GuardKeep.ensureKeyService(app)
        screenOff()

        assertTrue(watchingScreenOn())
    }

    @Test
    fun turning_the_opt_in_off_disarms_a_bound_service() {
        optInToStandbyExit()
        service.start(TvConfig())
        PowerMenu.bind(service)

        GuardKeep.setOffInStandby(app, false)
        GuardKeep.ensureKeyService(app)
        screenOff()

        assertFalse(watchingScreenOn())
    }

    @Test
    fun a_service_armed_while_the_screen_is_already_off_steps_out_at_once() {
        optInToStandbyExit()
        shadowOf(app.getSystemService(PowerManager::class.java)).turnScreenOn(false)

        service.start(TvConfig())

        assertTrue(watchingScreenOn())
    }

    @Test
    fun a_service_that_stepped_out_for_standby_keeps_its_repair_record_when_it_unbinds() {
        optInToStandbyExit()
        service.start(TvConfig(restoreKeyServices = true))

        screenOff()
        service.onUnbind(Intent())

        assertEquals(setOf(KeyService::class.java.name), ServiceRepair.recorded(app))
    }
}
