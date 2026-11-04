package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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

/** The things the tiles and the top bar share for menus and reorder mode. None of them changes how often anything recomposes. */
@Stable
internal class HomeInput(
    private val currentActions: State<HomeActions>,
    /** The carried tile's key, the keys drawn dimmed and whether a session runs. Read them in layer lambdas only. */
    val carried: State<String?>,
    val dimmed: State<Set<String>>,
    val reordering: State<Boolean>,
    val hold: OkHold = OkHold(),
) {
    private val requesters = HashMap<String, FocusRequester>()

    val actions: HomeActions get() = currentActions.value

    /** Where the open menu hangs from, in the root's coordinates. */
    val anchor = mutableStateOf(Rect.Zero)

    /** Whether focus is on a tile, set by the grid. A reorder mode takes OK only there. */
    var gridFocused = false

    fun requester(target: String): FocusRequester = requesters.getOrPut(target) { FocusRequester() }

    /** Moves focus to [target] when something with that key is composed. */
    fun focus(target: String) {
        try {
            requesters[target]?.requestFocus()
        } catch (e: IllegalStateException) {
            // The tile is gone or not built yet.
        }
    }

    /**
     * Keys that act before the focused item sees them. The UP of a hold that opened a menu goes nowhere, wherever
     * focus is by now, so it does not also open the app or press the menu's first row. In reorder mode OK on a tile
     * picks up or puts down, and the arrows move a carried tile.
     */
    fun onPreview(
        event: KeyEvent,
        state: HomeUiState,
    ): Boolean {
        val ok = event.key.isOk()
        if (ok && event.type == KeyEventType.KeyUp && hold.up()) return true
        val reorder = state.reorder
        if (reorder == null || state.menu != null) return false
        val arrow = event.key.arrow()
        return when {
            ok && gridFocused -> {
                if (event.type == KeyEventType.KeyUp) actions.onReorderOk()
                true
            }

            reorder.carried != null && arrow != null -> {
                if (event.type == KeyEventType.KeyDown) actions.onReorderMove(arrow)
                true
            }

            else -> {
                false
            }
        }
    }
}

private fun Key.isOk() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter || this == Key.ButtonA

private fun Key.arrow(): Arrow? =
    when (this) {
        Key.DirectionLeft -> Arrow.Left
        Key.DirectionRight -> Arrow.Right
        Key.DirectionUp -> Arrow.Up
        Key.DirectionDown -> Arrow.Down
        else -> null
    }

/**
 * Opens a menu from the focused item: [onHold] after OK is held, with the item's bounds, and [onMenuKey] when MENU is
 * released, if given. The UP after a hold is eaten in [HomeInput.onPreview].
 */
internal fun Modifier.holdOk(
    input: HomeInput,
    onHold: (Rect) -> Unit,
    onMenuKey: ((Rect) -> Unit)? = null,
): Modifier = this then HoldOkElement(input, onHold, onMenuKey)

private data class HoldOkElement(
    val input: HomeInput,
    val onHold: (Rect) -> Unit,
    val onMenuKey: ((Rect) -> Unit)?,
) : ModifierNodeElement<HoldOkNode>() {
    override fun create() = HoldOkNode(input, onHold, onMenuKey)

    override fun update(node: HoldOkNode) {
        node.input = input
        node.onHold = onHold
        node.onMenuKey = onMenuKey
    }
}

private class HoldOkNode(
    var input: HomeInput,
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
                val opens = input.hold.down(native.repeatCount, native.downTime, native.eventTime)
                if (native.repeatCount == 0) {
                    timer?.cancel()
                    timer =
                        coroutineScope.launch {
                            delay(HOLD_OK_MS)
                            if (input.hold.timeUp()) open()
                        }
                }
                if (opens) open()
                opens
            }

            KeyEventType.KeyUp -> {
                timer?.cancel()
                input.hold.up()
            }

            else -> {
                false
            }
        }
    }

    /** A hold that ends with the window out of focus (an app opened meanwhile) opens nothing; its UP is still eaten. */
    private fun open() {
        if (currentValueOf(LocalWindowInfo).isWindowFocused) onHold(bounds()) else input.hold.cancel()
    }

    private fun bounds() = requireLayoutCoordinates().boundsInRoot()
}
