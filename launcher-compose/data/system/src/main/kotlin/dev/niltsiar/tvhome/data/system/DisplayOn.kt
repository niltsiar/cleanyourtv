package dev.niltsiar.tvhome.data.system

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * One item each time the default display goes from not on (off, doze) to on, so the clock can read the time at once:
 * a wait that runs on uptime stands still while the set sleeps. A display listener, not ACTION_SCREEN_ON, as some
 * firmware leaves the app that was on screen out of that broadcast. The listener is registered while collected.
 */
public fun displayOn(context: Context): Flow<Unit> =
    callbackFlow {
        val displays = context.getSystemService(DisplayManager::class.java)
        var last = displays.getDisplay(Display.DEFAULT_DISPLAY)?.state ?: Display.STATE_UNKNOWN
        val listener =
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit

                override fun onDisplayRemoved(displayId: Int) = Unit

                override fun onDisplayChanged(displayId: Int) {
                    if (displayId != Display.DEFAULT_DISPLAY) return
                    val state = displays.getDisplay(displayId)?.state ?: return
                    if (turnedOn(last, state)) trySend(Unit)
                    last = state
                }
            }
        displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        awaitClose { displays.unregisterDisplayListener(listener) }
    }

/** True for a change into [Display.STATE_ON] from any other state. */
internal fun turnedOn(
    from: Int,
    to: Int,
): Boolean = from != Display.STATE_ON && to == Display.STATE_ON
