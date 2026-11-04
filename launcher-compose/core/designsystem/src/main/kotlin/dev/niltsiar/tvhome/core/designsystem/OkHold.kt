package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.KeyEvent as AndroidKeyEvent

/** How long OK is held before it counts as a long press, as in the reference launcher. */
public const val HOLD_OK_MS: Long = 550

/** The flags of the short MENU press a Xiaomi remote injects. FLAG_CANCELED is one of them (#1637). */
internal const val XIAOMI_SHORT_MENU_FLAGS: Int = 0x7531

/**
 * Times one held OK. Some remotes never repeat the DOWN, so a timer calls [timeUp] too; others repeat it faster than
 * the timer, so a repeat that is old enough opens the menu as well. The UP of a press that opened the menu is eaten,
 * so it does not also open the app.
 */
public class OkHold(
    private val holdMs: Long = HOLD_OK_MS,
) {
    private var downTime = NONE

    /** The press being timed already opened the menu. */
    private var fired = false

    /** A DOWN of OK. True when the menu opens now. */
    public fun down(
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
    public fun timeUp(): Boolean {
        if (downTime == NONE || fired) return false
        fired = true
        return true
    }

    /** OK went up. True when this press opened the menu, so the event is eaten. */
    public fun up(): Boolean {
        downTime = NONE
        return fired.also { fired = false }
    }

    /** The press stops counting but, if it was running, its UP is still eaten: the window lost focus mid-press. */
    public fun cancel() {
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

/** Whether this is an OK or select key: D-pad centre, Enter, numpad Enter or a gamepad A. */
public fun Key.isOk(): Boolean = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter || this == Key.ButtonA

/** True for the UP of an OK that opened a menu, which goes nowhere so it does not also press what is under focus. */
public fun OkHold.eatsOkUp(event: KeyEvent): Boolean = event.key.isOk() && event.type == KeyEventType.KeyUp && up()

/**
 * Opens a menu from the focused item: [onHold] after OK is held, with the item's bounds in the root, and [onMenuKey]
 * when MENU is released, if given. The UP after a hold is eaten by [eatsOkUp], which the screen calls before its
 * items see the key.
 */
public fun Modifier.holdOk(
    hold: OkHold,
    onHold: (Rect) -> Unit,
    onMenuKey: ((Rect) -> Unit)? = null,
): Modifier = this then HoldOkElement(hold, onHold, onMenuKey)

private data class HoldOkElement(
    val hold: OkHold,
    val onHold: (Rect) -> Unit,
    val onMenuKey: ((Rect) -> Unit)?,
) : ModifierNodeElement<HoldOkNode>() {
    override fun create() = HoldOkNode(hold, onHold, onMenuKey)

    override fun update(node: HoldOkNode) {
        node.hold = hold
        node.onHold = onHold
        node.onMenuKey = onMenuKey
    }
}

private class HoldOkNode(
    var hold: OkHold,
    var onHold: (Rect) -> Unit,
    var onMenuKey: ((Rect) -> Unit)?,
) : Modifier.Node(),
    KeyInputModifierNode,
    CompositionLocalConsumerModifierNode {
    private var timer: Job? = null

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        val menu = onMenuKey
        return when {
            event.key.isOk() -> {
                okEvent(event)
            }

            event.key == Key.Menu && menu != null -> {
                if (menuReleased(event.type == KeyEventType.KeyUp, event.nativeKeyEvent.flags)) menu(bounds())
                true
            }

            else -> {
                false
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent) = false

    override fun onDetach() {
        timer?.cancel()
    }

    private fun okEvent(event: KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        return when (event.type) {
            KeyEventType.KeyDown -> {
                val opens = hold.down(native.repeatCount, native.downTime, native.eventTime)
                if (native.repeatCount == 0) {
                    timer?.cancel()
                    timer =
                        coroutineScope.launch {
                            delay(HOLD_OK_MS)
                            if (hold.timeUp()) open()
                        }
                }
                if (opens) open()
                opens
            }

            KeyEventType.KeyUp -> {
                timer?.cancel()
                hold.up()
            }

            else -> {
                false
            }
        }
    }

    /** A hold that ends with the window out of focus (an app opened meanwhile) opens nothing; its UP is still eaten. */
    private fun open() {
        if (currentValueOf(LocalWindowInfo).isWindowFocused) onHold(bounds()) else hold.cancel()
    }

    private fun bounds() = requireLayoutCoordinates().boundsInRoot()
}
