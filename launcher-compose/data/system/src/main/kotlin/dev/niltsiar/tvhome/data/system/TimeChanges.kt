package dev.niltsiar.tvhome.data.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Says when the wall clock or the time zone was set, so the clock can read the time again. The receiver is
 * registered only while a screen that shows the time is started ([onStart], [onStop]): Home, or the clock
 * screensaver, which starts before Home stops. Starting also counts as a change: the time may have been set while
 * nothing showed it, and so may the 12/24-hour setting.
 */
public class TimeChanges(
    private val context: Context,
) {
    private val count = MutableStateFlow(0)
    private val filter =
        IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                count.update { it + 1 }
            }
        }
    private var started = 0

    /** One item for each change since the collector started; none for the ones before it. */
    public val changes: Flow<Unit> = count.drop(1).map { }

    /** A screen that shows the time is on screen. Main thread. */
    public fun onStart() {
        if (started++ == 0) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        count.update { it + 1 }
    }

    /** That screen left. The receiver goes when no such screen is left. Main thread. */
    public fun onStop() {
        if (started > 0 && --started == 0) context.unregisterReceiver(receiver)
    }
}
