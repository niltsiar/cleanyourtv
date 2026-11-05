package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.niltsiar.tvhome.core.designsystem.OkHold
import dev.niltsiar.tvhome.core.designsystem.eatsOkUp
import dev.niltsiar.tvhome.core.designsystem.isOk

/**
 * The things the tiles and the top bar share for menus and reorder mode. Only [reordering] is read in composition, by the
 * grid outside its items and by the reorder button, and [pillFocused] and [headerControl] by the band over the grid.
 */
@Stable
internal class HomeInput(
    private val currentActions: State<HomeMenuActions>,
    /** How far down the band and the dock are drawn now, in pixels. */
    private val groupShift: () -> Float = { 0f },
    /** The carried tile's key, the keys drawn dimmed and whether a session runs. Read them in layer lambdas only. */
    val carried: State<String?>,
    val dimmed: State<Set<String>>,
    val reordering: State<Boolean>,
    private val openLibrary: State<() -> Unit> = mutableStateOf({}),
    val hold: OkHold = OkHold(),
) {
    /** The colour of the focus ring as it moves from tile to tile. */
    val travel = RingTravel()

    private val requesters = HashMap<String, FocusRequester>()

    val actions: HomeMenuActions get() = currentActions.value

    /** Where the open menu hangs from, in the root's coordinates. */
    val anchor = mutableStateOf(Rect.Zero)

    /**
     * Hangs the open menu from the tile at [bounds] (in the root's coordinates), where that tile will be once the group
     * has risen to the grid layout, which a tile menu does.
     */
    fun anchorTile(bounds: Rect) {
        anchor.value = bounds.translate(0f, -groupShift())
    }

    /** Where the Inputs button is, in the root's coordinates; zero until it is laid out. The Inputs list hangs from it. */
    val inputsBounds = mutableStateOf(Rect.Zero)

    /** Whether focus is in the grid, set by the grid. A reorder mode takes OK only there. */
    var gridFocused by mutableStateOf(false)

    /** Whether focus is on the library pill, which sits in the grid but is no tile. */
    var pillFocused by mutableStateOf(false)

    /** Whether focus is on a top-bar button. */
    var topBarFocused by mutableStateOf(false)

    /** The top-bar button that had focus last, which the band names. */
    var topBarControl by mutableStateOf<BarControl?>(null)

    /** The control the band names: the Inputs list's, the Settings menu's, or the button that has focus on the top bar. */
    fun headerControl(state: HomeUiState): BarControl? =
        when {
            state.inputsOpen -> BarControl.Inputs
            state.menu != null && state.menu.tileKey == null -> BarControl.Settings
            topBarFocused -> topBarControl
            else -> null
        }

    /** The library was opened from the top bar button, so closing it returns there. */
    var fromTopBar = false

    /** OK on the Inputs button, or the system asking for the list. */
    fun openInputs() {
        actions.onInputs()
    }

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
     * picks up or puts down, and the arrows move a carried tile; OK on the library pill ends the mode, which saves the
     * order, and opens the library.
     */
    fun onPreview(
        event: KeyEvent,
        state: HomeUiState,
    ): Boolean {
        if (hold.eatsOkUp(event)) return true
        val ok = event.key.isOk()
        val reorder = state.reorder
        if (reorder == null || state.menu != null) return false
        val arrow = event.key.arrow()
        return when {
            ok && pillFocused -> {
                if (event.type == KeyEventType.KeyUp) {
                    actions.onToggleReorder()
                    openLibrary.value()
                }
                true
            }

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

private fun Key.arrow(): Arrow? =
    when (this) {
        Key.DirectionLeft -> Arrow.Left
        Key.DirectionRight -> Arrow.Right
        Key.DirectionUp -> Arrow.Up
        Key.DirectionDown -> Arrow.Down
        else -> null
    }
