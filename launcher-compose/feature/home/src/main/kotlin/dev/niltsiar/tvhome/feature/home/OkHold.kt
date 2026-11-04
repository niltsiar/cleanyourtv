package dev.niltsiar.tvhome.feature.home

import android.view.KeyEvent as AndroidKeyEvent

/** How long OK is held before it counts as a long press, as in the reference launcher. */
internal const val HOLD_OK_MS: Long = 550

/** The flags of the short MENU press a Xiaomi remote injects. FLAG_CANCELED is one of them (#1637). */
internal const val XIAOMI_SHORT_MENU_FLAGS: Int = 0x7531

/**
 * Times one held OK. Some remotes never repeat the DOWN, so a timer calls [timeUp] too; others repeat it faster than
 * the timer, so a repeat that is old enough opens the menu as well. The UP of a press that opened the menu is eaten,
 * so it does not also open the app.
 */
internal class OkHold(
    private val holdMs: Long = HOLD_OK_MS,
) {
    private var downTime = NONE

    /** The press being timed already opened the menu. */
    private var fired = false

    /** A DOWN of OK. True when the menu opens now. */
    fun down(
        repeatCount: Int,
        downTime: Long,
        eventTime: Long,
    ): Boolean {
        if (repeatCount == 0) {
            fired = false
            this.downTime = downTime
            return false
        }
        if (fired || this.downTime == NONE || eventTime - this.downTime < holdMs) return false
        fired = true
        return true
    }

    /** The timer ran out. True when the menu opens now. */
    fun timeUp(): Boolean {
        if (downTime == NONE || fired) return false
        fired = true
        return true
    }

    /** OK went up. True when this press opened the menu, so the event is eaten. */
    fun up(): Boolean {
        downTime = NONE
        return fired.also { fired = false }
    }

    /** The press stops counting but, if it was running, its UP is still eaten: the window lost focus mid-press. */
    fun cancel() {
        if (downTime != NONE) fired = true
        downTime = NONE
    }

    private companion object {
        const val NONE = -1L
    }
}

/** Whether a MENU key event opens the menu: an UP that is not cancelled, or one with the Xiaomi flags (#1637). */
internal fun menuReleased(
    isUp: Boolean,
    flags: Int,
): Boolean = isUp && ((flags and AndroidKeyEvent.FLAG_CANCELED) == 0 || (flags and XIAOMI_SHORT_MENU_FLAGS) == XIAOMI_SHORT_MENU_FLAGS)
