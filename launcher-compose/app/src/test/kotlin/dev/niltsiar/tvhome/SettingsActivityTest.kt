package dev.niltsiar.tvhome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = TvHomeApplication::class)
class SettingsActivityTest {
    private val settings =
        Robolectric
            .buildActivity(SettingsActivity::class.java)
            .create()
            .start()
            .resume()

    @Test
    fun back_closes_the_settings() {
        assertFalse(settings.get().isFinishing)

        settings.get().onBackPressedDispatcher.onBackPressed()

        assertTrue(settings.get().isFinishing)
    }
}
