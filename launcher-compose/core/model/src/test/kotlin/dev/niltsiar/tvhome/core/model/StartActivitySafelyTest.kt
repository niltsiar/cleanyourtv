package dev.niltsiar.tvhome.core.model

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class StartActivitySafelyTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val intent = Intent("vendor.PLAY").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Test
    fun an_activity_that_starts_gives_true() {
        assertTrue(context.startActivitySafely(intent))

        assertEquals("vendor.PLAY", shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity.action)
    }

    @Test
    fun an_intent_nothing_answers_gives_false() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)

        assertFalse(context.startActivitySafely(intent))
    }

    @Test
    fun a_permission_the_app_lacks_gives_false() {
        val refusing =
            object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) = throw SecurityException("needs a permission")
            }

        assertFalse(refusing.startActivitySafely(intent))
    }
}
