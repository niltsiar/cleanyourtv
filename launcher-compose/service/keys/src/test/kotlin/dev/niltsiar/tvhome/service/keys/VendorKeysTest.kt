package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.view.KeyEvent
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.data.system.SystemSettings
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class VendorKeysTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private var settingsOpened = 0
    private var config = MutableStateFlow(TvConfig(settingsKeyKeycodes = listOf(83)))
    private var vendorKeys = keys()

    private fun keys(vararg entries: String): VendorKeys {
        config = MutableStateFlow(TvConfig(appKeys = entries.asList(), settingsKeyKeycodes = listOf(83)))
        return VendorKeys(app, config, SystemSettings(app), {}) { settingsOpened++ }
    }

    @Before
    fun installApps() {
        for (pkg in listOf("com.netflix.ninja", "org.xbmc.kodi")) {
            val component = ComponentName(pkg, "$pkg.Main")
            shadowOf(app.packageManager).addActivityIfNotPresent(component)
            shadowOf(app.packageManager).addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) },
            )
        }
    }

    private fun key(
        action: Int,
        code: Int,
        device: Int,
        at: Long = 1_000,
    ) = KeyEvent(1_000, at, action, code, 0, 0, device, 0)

    @Test
    fun a_vendor_key_opens_its_app_on_a_tap() {
        vendorKeys = keys("4062:com.netflix.ninja")

        assertTrue(vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 4062, device = 501)))
        assertTrue(vendorKeys.handle(key(KeyEvent.ACTION_UP, 4062, device = 501, at = 1_100)))

        assertEquals("com.netflix.ninja", shadowOf(app).nextStartedActivity?.component?.packageName)
    }

    @Test
    fun a_keycode_below_1000_is_left_to_the_key_service() {
        vendorKeys = keys("190:com.netflix.ninja")

        assertFalse(vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 190, device = 502)))
        assertFalse(vendorKeys.handle(key(KeyEvent.ACTION_UP, 190, device = 502, at = 1_100)))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun a_hold_that_home_saw_go_down_is_dropped_when_home_leaves_the_front() {
        vendorKeys = keys("4062:com.netflix.ninja", "hold:4062:org.xbmc.kodi")

        vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 4062, device = 503))
        vendorKeys.release()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(HOLD_MS * 2))

        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun a_held_vendor_key_opens_its_hold_app() {
        vendorKeys = keys("4062:com.netflix.ninja", "hold:4062:org.xbmc.kodi")

        vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 4062, device = 504))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(HOLD_MS))

        assertEquals("org.xbmc.kodi", shadowOf(app).nextStartedActivity?.component?.packageName)
    }

    @Test
    fun a_global_action_on_a_vendor_key_goes_through_the_bound_key_service() {
        val service = Robolectric.setupService(KeyService::class.java)
        PowerMenu.bind(service)
        try {
            vendorKeys = keys("4062:back")

            assertTrue(vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 4062, device = 507)))
            assertTrue(vendorKeys.handle(key(KeyEvent.ACTION_UP, 4062, device = 507, at = 1_100)))

            assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_BACK), shadowOf(service).globalActionsPerformed)
            assertNull(shadowOf(app).nextStartedActivity)
        } finally {
            PowerMenu.unbind(service)
        }
    }

    @Test
    fun a_settings_keycode_opens_settings_on_the_up_only() {
        assertTrue(vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 83, device = 505)))
        assertEquals(0, settingsOpened)
        assertTrue(vendorKeys.handle(key(KeyEvent.ACTION_UP, 83, device = 505, at = 1_100)))
        assertEquals(1, settingsOpened)
        assertFalse(vendorKeys.handle(key(KeyEvent.ACTION_UP, 84, device = 505, at = 1_200)))
    }

    @Test
    fun a_changed_app_key_map_and_settings_keycode_apply_to_the_next_key() {
        vendorKeys = keys("4062:com.netflix.ninja")
        assertTrue(vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 4062, device = 506)))
        vendorKeys.handle(key(KeyEvent.ACTION_UP, 4062, device = 506, at = 1_100))
        assertEquals("com.netflix.ninja", shadowOf(app).nextStartedActivity?.component?.packageName)

        config.value = config.value.copy(appKeys = listOf("4062:org.xbmc.kodi"), settingsKeyKeycodes = listOf(84))
        vendorKeys.handle(key(KeyEvent.ACTION_DOWN, 4062, device = 506, at = 2_000))
        vendorKeys.handle(key(KeyEvent.ACTION_UP, 4062, device = 506, at = 2_100))

        assertEquals("org.xbmc.kodi", shadowOf(app).nextStartedActivity?.component?.packageName)
        assertFalse(vendorKeys.handle(key(KeyEvent.ACTION_UP, 83, device = 506, at = 2_200)))
        assertTrue(vendorKeys.handle(key(KeyEvent.ACTION_UP, 84, device = 506, at = 2_300)))
    }
}
