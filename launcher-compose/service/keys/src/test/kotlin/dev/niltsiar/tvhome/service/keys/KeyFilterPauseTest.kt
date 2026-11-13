package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import android.view.KeyEvent
import dev.niltsiar.tvhome.core.config.TvConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.shadows.ShadowAudioManager
import java.time.Duration

private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
private const val FILTER = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS

/** Keeps the info a service sets: with no system connection, Robolectric's getServiceInfo is always null. */
@Implements(AccessibilityService::class)
class ShadowKeptServiceInfo : ShadowAccessibilityService() {
    private var info: AccessibilityServiceInfo? = null

    @Implementation
    fun getServiceInfo(): AccessibilityServiceInfo? = info

    @Implementation
    fun setServiceInfo(info: AccessibilityServiceInfo) {
        this.info = info
    }
}

/** Notes each volume step, which Robolectric's AudioManager drops. */
@Implements(AudioManager::class)
class ShadowVolumeSteps : ShadowAudioManager() {
    val steps = mutableListOf<Int>()

    @Implementation
    fun adjustVolume(
        direction: Int,
        flags: Int,
    ) {
        steps += direction
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = ConfigApplication::class, shadows = [ShadowKeptServiceInfo::class, ShadowVolumeSteps::class])
class KeyFilterPauseTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private lateinit var service: KeyService

    @Before
    fun setUp() {
        SharedPresses.reset()
        service = Robolectric.setupService(KeyService::class.java)
        service.serviceInfo = AccessibilityServiceInfo().apply { flags = FILTER }
    }

    private fun filtering() = service.serviceInfo.flags and FILTER != 0

    private fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun volumeChanged() {
        app.sendBroadcast(Intent(VOLUME_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun watchingVolume() = shadowOf(app).registeredReceivers.any { it.intentFilter.hasAction(VOLUME_CHANGED) }

    private fun steps() = Shadow.extract<ShadowVolumeSteps>(service.getSystemService(AudioManager::class.java)).steps

    @Test
    fun with_volume_pauses_filter_filtering_stops_until_4_s_after_the_last_volume_change() {
        service.start(TvConfig(volumePausesFilter = true))
        assertTrue(filtering())

        volumeChanged()
        assertFalse(filtering())
        idleFor(3_000)
        volumeChanged()
        idleFor(VOLUME_PAUSE_MS - 1)
        assertFalse(filtering())
        idleFor(1)

        assertTrue(filtering())
    }

    @Test
    fun a_new_config_during_a_volume_pause_turns_filtering_back_on() {
        service.start(TvConfig(volumePausesFilter = true))
        volumeChanged()
        assertFalse(filtering())

        service.start(TvConfig())

        assertTrue(filtering())
    }

    @Test
    fun no_volume_change_is_watched_without_volume_pauses_filter_or_without_key_filter() {
        service.start(TvConfig())
        assertFalse(watchingVolume())

        service.start(TvConfig(keyFilter = false, volumePausesFilter = true))
        assertFalse(watchingVolume())
        volumeChanged()
        idleFor(VOLUME_PAUSE_MS)

        assertFalse(filtering())
    }

    @Test
    fun a_held_volume_key_stops_repeating_when_key_filtering_drops() {
        service.start(TvConfig(consumeVolumeKeys = true, volumePausesFilter = true))
        service.handleKey(KeyEvent(1_000, 1_000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0))
        idleFor(VOLUME_REPEAT_DELAY_MS + VOLUME_REPEAT_INTERVAL_MS)
        assertEquals(listOf(AudioManager.ADJUST_RAISE, AudioManager.ADJUST_RAISE), steps())

        volumeChanged()
        idleFor(1_000)

        assertEquals(2, steps().size)
    }
}
