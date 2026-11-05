package dev.niltsiar.tvhome.service.keys

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
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.HomeActions
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
@Config(sdk = [31])
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
        assertEquals(HomeActions.VIEW_INPUTS, inputs?.action)
        assertEquals(home, inputs?.component)

        device += 1
        tap(366)
        val library = started()
        assertEquals(HomeActions.APP_LIBRARY, library?.action)
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
            assertEquals(HomeActions.VIEW_INPUTS, started()?.action)
        }
    }

    @Test
    fun a_keycode_a_theme_lists_in_input_key_keycodes_is_consumed_with_the_default_config() {
        service.start(TvConfig(inputKeyKeycodes = listOf(4100)))

        assertEquals(true to true, pressInputButton(4100))
        assertEquals(HomeActions.VIEW_INPUTS, started()?.action)
    }

    @Test
    fun the_switcher_request_goes_to_homes_own_activity_when_no_app_answers_it() {
        shadowOf(app).checkActivities(true)
        service.start(TvConfig(captureInputKeys = true))

        pressInputButton(KeyEvent.KEYCODE_TV_INPUT)

        val inputs = started()
        assertEquals(HomeActions.VIEW_INPUTS, inputs?.action)
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

    private fun screenOn() = app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))

    private fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

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

    @Test
    fun a_service_bound_while_the_user_is_locked_reads_the_theme_only_after_the_unlock() {
        installLeanbackApp("com.netflix.ninja")
        pushTheme("""{"app_keys": ["190:com.netflix.ninja"]}""")
        shadowOf(app.getSystemService(android.os.UserManager::class.java)).setUserUnlocked(false)

        service.connected()
        assertFalse(service.handleKey(key(KeyEvent.ACTION_DOWN, 190)))

        shadowOf(app.getSystemService(android.os.UserManager::class.java)).setUserUnlocked(true)
        app.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        shadowOf(Looper.getMainLooper()).idle()
        device += 1
        assertTrue(tap(190))
        assertEquals("com.netflix.ninja", started()?.component?.packageName)
    }

    @Test
    fun a_service_bound_after_the_unlock_reads_the_theme_at_once() {
        installLeanbackApp("com.netflix.ninja")
        pushTheme("""{"app_keys": ["190:com.netflix.ninja"]}""")

        service.connected()

        assertTrue(tap(190))
        assertEquals("com.netflix.ninja", started()?.component?.packageName)
    }
}
