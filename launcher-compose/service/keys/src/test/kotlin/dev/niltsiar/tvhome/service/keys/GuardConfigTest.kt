package dev.niltsiar.tvhome.service.keys

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class GuardConfigTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        GuardKeep.reset()
    }

    /** What `am broadcast` prints back: the ordered broadcast's final result data. */
    private fun broadcast(extra: Boolean?): String? {
        var reply: String? = null
        val intent = Intent("dev.niltsiar.tvhome.GUARD").setClassName(app.packageName, "dev.niltsiar.tvhome.service.keys.GuardConfig")
        if (extra != null) intent.putExtra("guard_off_in_standby", extra)
        app.sendOrderedBroadcast(
            intent,
            null,
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    reply = resultData
                }
            },
            null,
            Activity.RESULT_OK,
            null,
            null,
        )
        shadowOf(Looper.getMainLooper()).idle()
        return reply
    }

    @Test
    fun the_broadcast_sets_the_opt_in_and_replies_with_its_value() {
        assertEquals("guard_off_in_standby=true", broadcast(true))
        assertTrue(GuardKeep.offInStandby(app))

        assertEquals("guard_off_in_standby=false", broadcast(false))
        assertFalse(GuardKeep.offInStandby(app))
    }

    @Test
    fun a_broadcast_without_the_extra_only_reads_the_opt_in() {
        GuardKeep.setOffInStandby(app, true)

        assertEquals("guard_off_in_standby=true", broadcast(null))
        assertTrue(GuardKeep.offInStandby(app))
    }

    @Test
    fun the_receiver_demands_the_secure_settings_permission_from_whoever_sends() {
        val receivers = checkNotNull(app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_RECEIVERS).receivers)
        val config = receivers.single { it.name == GuardConfig::class.java.name }

        assertTrue(config.exported)
        assertEquals("android.permission.WRITE_SECURE_SETTINGS", config.permission)
    }
}
