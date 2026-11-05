package dev.niltsiar.tvhome.data.system

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class TimeChangesTest {
    private val app = RuntimeEnvironment.getApplication()

    init {
        // Before Android 13 the compat call asks for this permission of its own to register a not-exported receiver.
        shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }

    private val registered get() = shadowOf(app).registeredReceivers.size

    @Test
    fun the_receiver_stays_until_every_screen_that_started_it_has_stopped() {
        val before = registered
        val changes = TimeChanges(app)

        changes.onStart() // Home
        changes.onStart() // the clock screensaver opens before Home stops
        changes.onStop()
        assertEquals(before + 1, registered)

        changes.onStop()
        assertEquals(before, registered)
    }
}
