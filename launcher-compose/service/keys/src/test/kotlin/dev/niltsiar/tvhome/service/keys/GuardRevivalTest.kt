package dev.niltsiar.tvhome.service.keys

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class GuardRevivalTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val users = shadowOf(app.getSystemService(UserManager::class.java))
    private val ownKeys = ComponentName(app, KeyService::class.java).flattenToShortString()

    @Before
    fun setUp() {
        GuardKeep.reset()
        GuardKeep.setOffInStandby(app, true)
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
    }

    private fun enabled(): String? = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    @Test
    fun the_system_binding_the_listener_after_a_kill_puts_the_key_service_back_on_the_list() {
        Robolectric.setupService(GuardRevival::class.java).onListenerConnected()

        assertEquals(ownKeys, enabled())
    }

    @Test
    fun a_listener_bound_before_the_unlock_waits_for_it() {
        users.setUserUnlocked(false)
        val listener = Robolectric.setupService(GuardRevival::class.java)

        listener.onListenerConnected()
        assertEquals("", enabled())

        users.setUserUnlocked(true)
        app.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(ownKeys, enabled())
    }
}
