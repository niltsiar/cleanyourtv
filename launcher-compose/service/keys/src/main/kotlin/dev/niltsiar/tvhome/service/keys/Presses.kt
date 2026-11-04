package dev.niltsiar.tvhome.service.keys

import android.view.KeyEvent

/**
 * Each button's presses, kept per device, keycode and scancode: KeyEvent.getDownTime() can't tell presses apart, since a
 * key pressed together with another can report the other's. The key service and Home both call in for one event when a
 * vendor key reaches both, so a DOWN or UP whose event time was already seen for that button is the other owner's copy and
 * is skipped. One small record per mapped button that has been pressed, not a history.
 */
internal class Presses(
    private val timer: Timer,
) {
    private class Press {
        var downAt = -1L
        var upAt = -1L
        var fired = false
        var hold: Runnable? = null

        /** The owners that saw this DOWN while its hold is pending, each with the way it opens a target. */
        val owners = LinkedHashMap<Any, (String) -> Unit>()
    }

    private val buttons = HashMap<String, Press>()

    fun down(
        button: String,
        eventTime: Long,
        repeatCount: Int,
        holdTarget: String?,
        owner: Any,
        open: (String) -> Unit,
    ) {
        if (repeatCount != 0) return
        val press = buttons.getOrPut(button) { Press() }
        if (press.downAt == eventTime) {
            // The other owner's copy: it can answer the hold too, should the first one leave.
            if (press.hold != null) press.owners.putIfAbsent(owner, open)
            return
        }
        press.hold?.let(timer::cancel) // its UP never came
        press.downAt = eventTime
        press.fired = false
        press.hold = null
        press.owners.clear()
        if (holdTarget == null) return
        press.owners[owner] = open
        val hold =
            Runnable {
                val first = press.owners.values.first()
                press.hold = null
                press.owners.clear()
                press.fired = true
                first(holdTarget)
            }
        press.hold = hold
        timer.post(hold, HOLD_MS)
    }

    /** Drops this owner from every pending hold, cancelling a hold it was the last to own. */
    fun release(owner: Any) {
        for (press in buttons.values) {
            val hold = press.hold ?: continue
            if (press.owners.remove(owner) == null || press.owners.isNotEmpty()) continue
            timer.cancel(hold)
            press.hold = null
            press.fired = true // the press is over: an UP that still comes later opens nothing
        }
    }

    fun up(
        button: String,
        eventTime: Long,
        downTime: Long,
        canceled: Boolean,
        tapTarget: String?,
        holdTarget: String?,
        open: (String) -> Unit,
    ) {
        val press = buttons.getOrPut(button) { Press() }
        if (press.upAt == eventTime) return
        val sawDown = press.downAt > press.upAt
        press.upAt = eventTime
        press.hold?.let {
            timer.cancel(it)
            press.hold = null
            press.owners.clear()
        }
        if (canceled || (sawDown && press.fired)) return
        // Without its DOWN (the service started while the button was held), the event's own down time will do.
        tapOrHold(tapTarget, holdTarget, eventTime - (if (sawDown) press.downAt else downTime))?.let(open)
    }
}

/** The presses of this process: the key service and Home share them, as a vendor key can reach both. */
internal object SharedPresses {
    private var instance: Presses? = null

    val presses: Presses get() = instance ?: Presses(HandlerTimer()).also { instance = it }

    /** For an owner that stops receiving keys: no UP will reach it to cancel a hold it saw go down. */
    fun release(owner: Any) {
        instance?.release(owner)
    }
}

/**
 * Consumes a key mapped by either entry: the hold target opens once the button has been down for [HOLD_MS], the tap one on
 * an earlier release. False means neither entry exists, so the caller can pass the key on.
 */
internal fun handleAppKey(
    event: KeyEvent,
    tapTarget: String?,
    holdTarget: String?,
    owner: Any,
    open: (String) -> Unit,
): Boolean {
    if (tapTarget == null && holdTarget == null) return false
    val button = "${event.deviceId}:${event.keyCode}:${event.scanCode}"
    when (event.action) {
        KeyEvent.ACTION_DOWN -> {
            SharedPresses.presses.down(button, event.eventTime, event.repeatCount, holdTarget, owner, open)
        }

        KeyEvent.ACTION_UP -> {
            SharedPresses.presses.up(button, event.eventTime, event.downTime, event.isCanceled, tapTarget, holdTarget, open)
        }
    }
    return true
}
