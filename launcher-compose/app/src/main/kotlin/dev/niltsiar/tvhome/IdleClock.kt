package dev.niltsiar.tvhome

import android.os.Handler
import android.os.Looper

/**
 * home_idle_clock_minutes: runs [show] once Home has gone [idleMs] without input. Input restarts the count; it runs only
 * while [isResumed], since a key's UP can still reach Home after it paused, when that key opened another app, and must not
 * start the clock over the app. [idleMs] of 0 is off.
 */
internal class IdleClock(
    private val idleMs: Long,
    private val isResumed: () -> Boolean,
    show: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val task = Runnable(show)

    fun restart() {
        handler.removeCallbacks(task)
        if (idleMs > 0 && isResumed()) handler.postDelayed(task, idleMs)
    }

    fun cancel() {
        handler.removeCallbacks(task)
    }
}
