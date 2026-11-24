package dev.niltsiar.tvhome.core.designsystem

import android.os.SystemClock
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.modifier.modifierLocalOf
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
 * so it does not also open the app. While the press runs, [progress] says how far it is to the menu for the item that
 * took it, on the clock [now]; its UP, the menu opening and [cancel] end it.
 */
public class OkHold(
    internal val holdMs: Long = HOLD_OK_MS,
    private val now: () -> Long = SystemClock::uptimeMillis,
) {
    private var downTime = NONE

    /** The press being timed already opened the menu. */
    private var fired = false

    /** The item whose press runs, until the press ends or the menu opens. */
    private var owner: Any? by mutableStateOf(null)

    /** When the press began on [now]'s clock. */
    private var pressAt = 0L

    /** The time of the last frame while a press runs: a [progress] read in draw draws again each frame. */
    private var frame by mutableLongStateOf(0L)

    /** A DOWN of OK on [owner]. True when the menu opens now. */
    public fun down(
        repeatCount: Int,
        downTime: Long,
        eventTime: Long,
        owner: Any? = null,
    ): Boolean {
        if (repeatCount == 0) {
            fired = false
            this.downTime = downTime
            this.owner = owner
            pressAt = now()
            return false
        }
        if (fired || this.downTime == NONE || eventTime - this.downTime < holdMs) return false
        fired = true
        this.owner = null
        return true
    }

    /** The timer ran out. True when the menu opens now. */
    public fun timeUp(): Boolean {
        if (downTime == NONE || fired) return false
        fired = true
        owner = null
        return true
    }

    /** OK went up. True when this press opened the menu, so the event is eaten. */
    public fun up(): Boolean {
        downTime = NONE
        owner = null
        return fired.also { fired = false }
    }

    /** The press stops counting but, if it was running, its UP is still eaten: the window lost focus mid-press. */
    public fun cancel() {
        if (downTime != NONE) fired = true
        downTime = NONE
        owner = null
    }

    /** How far the press on [of] is to the menu, 0 to 1; 0 when no press runs on it. */
    internal fun progress(of: Any): Float {
        if (owner !== of) return 0f
        frame
        return ((now() - pressAt).toFloat() / holdMs).coerceIn(0f, 1f)
    }

    internal fun holds(of: Any): Boolean = owner === of

    internal fun tick(frameMs: Long) {
        frame = frameMs
    }

    /** [of] leaves the screen: its press, if it runs, no longer shows. Another item's press goes on. */
    internal fun detach(of: Any) {
        if (owner === of) owner = null
    }

    private companion object {
        const val NONE = -1L
    }
}

/** Whether a MENU key event opens the menu: an UP that is not cancelled, or one with the Xiaomi flags (#1637). */
public fun menuReleased(
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

/** The [OkHold.progress] of the item whose [holdOk] comes earlier in the chain, for its [FocusSurface] to draw. */
internal val ModifierLocalHeldOk = modifierLocalOf<(() -> Float)?> { null }

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
    CompositionLocalConsumerModifierNode,
    ModifierLocalModifierNode {
    private var timer: Job? = null

    override val providedValues = modifierLocalMapOf(ModifierLocalHeldOk to { hold.progress(this) })

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
        hold.detach(this)
    }

    private fun okEvent(event: KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        return when (event.type) {
            KeyEventType.KeyDown -> {
                val opens = hold.down(native.repeatCount, native.downTime, native.eventTime, owner = this)
                if (native.repeatCount == 0) {
                    timer?.cancel()
                    timer =
                        coroutineScope.launch {
                            // Infinite to a test clock: one that advances on its own cancels the loop rather than running it on to the menu.
                            launch { while (hold.holds(this@HoldOkNode)) withInfiniteAnimationFrameMillis(hold::tick) }
                            delay(hold.holdMs)
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
