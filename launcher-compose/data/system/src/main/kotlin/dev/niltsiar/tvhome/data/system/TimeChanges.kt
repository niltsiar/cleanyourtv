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
 * registered only while Home is started ([onStart], [onStop]). Starting also counts as a change: the time may
 * have been set while Home was stopped, and so may the 12/24-hour setting.
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
    private var receiving = false

    /** One item for each change since the collector started; none for the ones before it. */
    public val changes: Flow<Unit> = count.drop(1).map { }

    /** Home is on screen. Main thread. */
    public fun onStart() {
        if (!receiving) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiving = true
        }
        count.update { it + 1 }
    }

    /** Home left the screen. Main thread. */
    public fun onStop() {
        if (receiving) {
            context.unregisterReceiver(receiver)
            receiving = false
        }
    }
}
