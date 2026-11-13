package dev.niltsiar.tvhome.service.keys

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class ServiceRepairTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val resolver = app.contentResolver

    private fun enabled(): String? = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    private fun setEnabled(list: String) {
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list)
    }

    @Test
    fun a_recorded_service_missing_from_the_list_is_added_after_the_others() {
        val plan = repairPlan(OTHER, listOf(keyService), 1)

        assertEquals(RepairPlan(without = OTHER, with = "$OTHER:$KEYS"), plan)
    }

    @Test
    fun an_empty_or_absent_list_after_a_force_stop_rebuilds_to_the_recorded_service_alone() {
        assertEquals(RepairPlan(without = "", with = KEYS), repairPlan("", listOf(keyService), 1))
        assertEquals(RepairPlan(without = "", with = KEYS), repairPlan(null, listOf(keyService), 1))
    }

    @Test
    fun nothing_is_rebuilt_when_no_service_is_recorded_or_every_recorded_one_is_listed_and_the_switch_is_not_0() {
        assertNull(repairPlan("", emptyList(), 0))
        for (switch in listOf(1, -1)) {
            assertNull(repairPlan("$OTHER:$KEYS", listOf(keyService), switch))
            assertNull(repairPlan("dev.niltsiar.tvhome/dev.niltsiar.tvhome.service.keys.KeyService", listOf(keyService), switch))
        }
    }

    @Test
    fun a_listed_service_is_cycled_when_the_switch_reads_0_and_the_cycle_turns_accessibility_on() {
        setEnabled("$KEYS:$OTHER")
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        val plan = checkNotNull(repairPlan("$KEYS:$OTHER", listOf(keyService), 0))
        val seen = mutableListOf<String?>()

        runRepair(resolver, "$KEYS:$OTHER", plan) { seen += enabled() }

        assertEquals(RepairPlan(without = OTHER, with = "$OTHER:$KEYS"), plan)
        assertEquals(listOf("$OTHER:$KEYS", OTHER), seen)
        assertEquals("$OTHER:$KEYS", enabled())
        assertEquals(1, Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED))
    }

    @Test
    fun the_cycle_writes_with_without_with_a_second_apart_and_turns_accessibility_on() {
        val plan = checkNotNull(repairPlan(OTHER, listOf(keyService), 1))
        setEnabled(OTHER)
        val seen = mutableListOf<String?>()
        val pauses = mutableListOf<Long>()

        runRepair(resolver, OTHER, plan) {
            pauses += it
            seen += enabled()
        }

        assertEquals(listOf(1_000L, 1_000L), pauses)
        assertEquals(listOf("$OTHER:$KEYS", OTHER), seen)
        assertEquals("$OTHER:$KEYS", enabled())
        assertEquals(1, Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED))
    }

    @Test
    fun a_list_changed_during_a_pause_stops_the_cycle_and_is_left_as_it_is() {
        val plan = checkNotNull(repairPlan(OTHER, listOf(keyService), 1))
        setEnabled(OTHER)
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        var pauses = 0

        runRepair(resolver, OTHER, plan) {
            pauses++
            setEnabled("com.third.app/.Service")
        }

        assertEquals(1, pauses)
        assertEquals("com.third.app/.Service", enabled())
        assertEquals(0, Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED))
    }

    @Test
    fun a_list_changed_before_the_first_write_is_not_overwritten() {
        val plan = checkNotNull(repairPlan(OTHER, listOf(keyService), 1))
        setEnabled("com.third.app/.Service")

        runRepair(resolver, OTHER, plan) { error("no pause expected") }

        assertEquals("com.third.app/.Service", enabled())
    }

    @Test
    fun restore_leaves_the_list_alone_without_the_secure_settings_permission() {
        ServiceRepair.connected(app, KeyService::class.java)
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        setEnabled(OTHER)

        ServiceRepair.restore(app)

        assertEquals(OTHER, enabled())
    }
}
