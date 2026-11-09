package dev.niltsiar.tvhome

import android.app.Application
import android.os.Bundle
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import dev.niltsiar.tvhome.feature.settings.R as SettingsR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class HomeSettingsActivityTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    private fun backdrop() = Robolectric.buildActivity(HomeSettingsActivity::class.java)

    @Test
    fun it_starts_the_home_picker_with_the_help_toast_and_stays_behind_it() {
        val controller = backdrop().create().start().resume()

        assertEquals(Settings.ACTION_HOME_SETTINGS, shadowOf(app).nextStartedActivity.action)
        val label = app.applicationInfo.loadLabel(app.packageManager).toString()
        val toast = ShadowToast.getTextOfLatestToast()
        assertEquals(app.getString(SettingsR.string.home_settings_help, label), toast)
        assertTrue(toast, toast.contains(label))
        assertFalse(toast, toast.contains("CleanYourTV"))
        assertFalse(controller.get().isFinishing)
    }

    @Test
    fun with_no_picker_to_answer_it_says_not_available_and_finishes() {
        shadowOf(app).checkActivities(true)

        val activity = backdrop().create().get()

        assertEquals(app.getString(R.string.not_available), ShadowToast.getTextOfLatestToast())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun back_from_the_picker_finishes_the_backdrop() {
        val controller = backdrop().create().start().resume()

        controller.pause()
        assertFalse(controller.get().isFinishing)
        controller.resume()

        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun recreated_with_saved_state_it_finishes_at_the_first_resume_without_starting_the_picker_again() {
        val controller = backdrop().create(Bundle()).start()

        assertNull(shadowOf(app).nextStartedActivity)
        controller.resume()

        assertTrue(controller.get().isFinishing)
    }
}
