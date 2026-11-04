package dev.niltsiar.tvhome.service.keys

import android.media.AudioManager
import android.view.KeyEvent

internal const val VOLUME_REPEAT_DELAY_MS: Long = 500
internal const val VOLUME_REPEAT_INTERVAL_MS: Long = 60

/** AudioManager direction for a volume key, or 0 for any other key. */
internal fun volumeDirection(keyCode: Int): Int =
    when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
        KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
        else -> 0
    }

/** Whether this event starts or ends a press; a repeated down event changes nothing. */
internal fun changesPress(
    action: Int,
    repeatCount: Int,
): Boolean = action != KeyEvent.ACTION_DOWN || repeatCount == 0

/**
 * consume_volume_keys: some firmware (TCL G03, Android 11, #1100) applies a volume key before the key service's filter and
 * again when the service hands it back, so one tap moved two steps. The service swallows Volume Up and Down instead, which
 * leaves the firmware's first step; a swallowed key gets no auto-repeat, so a held one is repeated here, from the same
 * delay and at about the rate Android uses. [adjust] steps the volume in a direction.
 */
internal class VolumeRepeat(
    private val timer: Timer,
    private val adjust: (direction: Int) -> Unit,
) {
    private var step: Runnable? = null

    /** Swallows a volume key (true) and repeats it while held; any other key is left alone (false). */
    fun handle(event: KeyEvent): Boolean {
        val direction = volumeDirection(event.keyCode)
        if (direction == 0) return false
        if (!changesPress(event.action, event.repeatCount)) return true
        stop()
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val repeat =
            object : Runnable {
                override fun run() {
                    adjust(direction)
                    timer.post(this, VOLUME_REPEAT_INTERVAL_MS)
                }
            }
        step = repeat
        timer.post(repeat, VOLUME_REPEAT_DELAY_MS)
        return true
    }

    /** Ends a repeat in progress, e.g. when the service goes away while a key is held. */
    fun stop() {
        step?.let(timer::cancel)
        step = null
    }
}
