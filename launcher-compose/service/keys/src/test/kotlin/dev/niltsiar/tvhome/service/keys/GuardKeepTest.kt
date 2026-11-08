package dev.niltsiar.tvhome.service.keys

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val KEYS = "dev.niltsiar.tvhome/.service.keys.KeyService"
private const val OTHER = "com.other.app/.Service"
private val keyService = ComponentName("dev.niltsiar.tvhome", "dev.niltsiar.tvhome.service.keys.KeyService")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class GuardDecisionTest {
    @Test
    fun the_standby_exit_needs_both_the_opt_in_and_the_permission() {
        assertTrue(leavesInStandby(optedIn = true, permitted = true))
        assertFalse(leavesInStandby(optedIn = true, permitted = false))
        assertFalse(leavesInStandby(optedIn = false, permitted = true))
        assertFalse(leavesInStandby(optedIn = false, permitted = false))
    }

    @Test
    fun a_wake_request_writes_the_list_with_the_screen_on_and_waits_with_it_off() {
        assertEquals(WakeStep.PutBack, wakeStep(optedIn = true, permitted = true, interactive = true))
        assertEquals(WakeStep.WaitForScreenOn, wakeStep(optedIn = true, permitted = true, interactive = false))
    }

    @Test
    fun a_wake_request_does_nothing_without_the_opt_in_or_without_the_permission() {
        assertEquals(WakeStep.Idle, wakeStep(optedIn = false, permitted = true, interactive = true))
        assertEquals(WakeStep.Idle, wakeStep(optedIn = true, permitted = false, interactive = true))
        assertEquals(WakeStep.Idle, wakeStep(optedIn = true, permitted = false, interactive = false))
    }

    @Test
    fun the_service_is_added_once_after_the_other_entries_in_their_order() {
        assertEquals("$OTHER:com.third.app/.Service:$KEYS", withService("$OTHER:com.third.app/.Service", keyService))
    }

    @Test
    fun an_absent_empty_or_null_text_list_becomes_the_service_alone() {
        assertEquals(KEYS, withService(null, keyService))
        assertEquals(KEYS, withService("", keyService))
        assertEquals(KEYS, withService("null", keyService))
    }

    @Test
    fun empty_entries_are_dropped_and_the_others_keep_their_text() {
        assertEquals("$OTHER:$KEYS", withService("$OTHER:", keyService))
        assertEquals("com.third.app/com.third.app.Service:$KEYS", withService(":com.third.app/com.third.app.Service", keyService))
    }

    @Test
    fun a_listed_service_is_not_added_again_in_either_spelling() {
        assertNull(withService("$OTHER:$KEYS", keyService))
        assertNull(withService("dev.niltsiar.tvhome/dev.niltsiar.tvhome.service.keys.KeyService", keyService))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class GuardKeepTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        GuardKeep.reset()
    }

    @After
    fun tearDown() {
        GuardKeep.reset()
    }

    private val ownKeys = ComponentName(app, KeyService::class.java).flattenToShortString()

    private fun enabled(): String? = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    private fun setEnabled(list: String) {
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list)
    }

    private fun permit(granted: Boolean) {
        if (granted) {
            shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        } else {
            shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        }
    }

    private fun screen(on: Boolean) = shadowOf(app.getSystemService(PowerManager::class.java)).turnScreenOn(on)

    private fun watchingScreenOn() = shadowOf(app).registeredReceivers.any { it.intentFilter.hasAction(Intent.ACTION_SCREEN_ON) }

    @Test
    fun the_opt_in_is_off_until_it_is_set_and_reads_off_before_the_unlock() {
        assertFalse(GuardKeep.offInStandby(app))

        GuardKeep.setOffInStandby(app, true)
        assertTrue(GuardKeep.offInStandby(app))

        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        assertFalse(GuardKeep.offInStandby(app))
    }

    @Test
    fun with_the_opt_in_and_the_permission_and_the_screen_on_the_service_joins_the_list_and_accessibility_turns_on() {
        GuardKeep.setOffInStandby(app, true)
        permit(true)
        setEnabled(OTHER)
        Settings.Secure.putInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)

        GuardKeep.ensureKeyService(app)

        assertEquals("$OTHER:$ownKeys", enabled())
        assertEquals(1, Settings.Secure.getInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED))
    }

    @Test
    fun a_service_that_is_listed_already_leaves_the_list_as_it_is() {
        GuardKeep.setOffInStandby(app, true)
        permit(true)
        setEnabled("$ownKeys:$OTHER")

        GuardKeep.ensureKeyService(app)

        assertEquals("$ownKeys:$OTHER", enabled())
    }

    @Test
    fun without_the_opt_in_the_list_is_left_alone() {
        permit(true)
        setEnabled(OTHER)

        GuardKeep.ensureKeyService(app)

        assertEquals(OTHER, enabled())
    }

    @Test
    fun without_the_permission_the_list_is_left_alone_and_no_screen_watch_starts() {
        GuardKeep.setOffInStandby(app, true)
        permit(false)
        setEnabled(OTHER)
        screen(false)

        GuardKeep.ensureKeyService(app)

        assertEquals(OTHER, enabled())
        assertFalse(watchingScreenOn())
    }

    @Test
    fun with_the_screen_off_the_list_waits_for_the_screen_and_is_written_once_when_it_turns_on() {
        GuardKeep.setOffInStandby(app, true)
        permit(true)
        setEnabled(OTHER)
        screen(false)

        GuardKeep.ensureKeyService(app)
        GuardKeep.ensureKeyService(app)

        assertEquals(OTHER, enabled())
        assertEquals(1, shadowOf(app).registeredReceivers.count { it.intentFilter.hasAction(Intent.ACTION_SCREEN_ON) })

        screen(true)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("$OTHER:$ownKeys", enabled())
        assertFalse(watchingScreenOn())
    }
}
