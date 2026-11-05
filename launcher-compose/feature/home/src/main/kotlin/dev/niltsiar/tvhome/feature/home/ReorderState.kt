package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable

/** An arrow key. */
public enum class Arrow { Left, Right, Up, Down }

/** What the reorder hint line says. */
public enum class ReorderHint {
    /** Reorder mode, nothing carried. */
    Reorder,

    /** Edit dock: choose the dock place to change. */
    DockChoosePlace,

    /** Edit dock: the place is chosen, choose the app to put there. */
    DockChooseApp,

    /** Edit dock from "Add to dock": the app is chosen, choose its place. */
    DockPlaceForApp,
}

/**
 * What the screen shows of a reorder or edit-dock session: the [carried] tile, the tiles to draw [dimmed], and the [hint].
 * [canMoveLeft] and [canMoveRight] say which way the carried tile can still go along the list.
 */
@Immutable
public data class ReorderView(
    val carried: String?,
    val dimmed: Set<String>,
    val hint: ReorderHint,
    val canMoveLeft: Boolean = false,
    val canMoveRight: Boolean = false,
)

/** The order to keep: tile [keys] first to last and, with fill_dock off, how many lead the dock (else -1). */
internal data class OrderChange(
    val keys: List<String>,
    val docked: Int,
)

/** Edit-dock mode: the dock [slot] being changed, or -1 while it is chosen; [appKey] is the app waiting for a place. */
internal data class DockEdit(
    val slot: Int = -1,
    val appKey: String? = null,
)

/**
 * A reorder session on the tiles' [keys], first to last, in a dock [columns] wide. [leavesSlots] is fill_dock off: the
 * dock then keeps the apps the owner put in it, not the first [columns] tiles. [inDock] are the tiles in the dock
 * when the session started plus those carried or swapped into it since; a grid tile that only slid into a free dock
 * slot, which the session does not show, is not one of them (#499).
 */
internal data class ReorderState(
    val keys: List<String>,
    val columns: Int,
    val singleRow: Boolean,
    val leavesSlots: Boolean,
    val startKeys: List<String>,
    val dockedAtStart: Int,
    val inDock: Set<String>,
    val carried: String? = null,
    val dockEdit: DockEdit? = null,
) {
    fun pickUp(key: String): ReorderState = if (key in keys) copy(carried = key) else this

    fun drop(): ReorderState = copy(carried = null)

    /**
     * Moves the carried tile one step. Left and Right run along the list across rows; Up and Down move a row, and Down
     * into a short last row lands on the last tile. Nothing moves past either end.
     */
    fun move(arrow: Arrow): ReorderState {
        val key = carried ?: return this
        val from = keys.indexOf(key)
        if (from < 0) return drop()
        if (singleRow && (arrow == Arrow.Up || arrow == Arrow.Down)) return this
        val n = keys.size
        val to =
            when (arrow) {
                Arrow.Left -> {
                    from - 1
                }

                Arrow.Right -> {
                    from + 1
                }

                Arrow.Up -> {
                    from - columns
                }

                Arrow.Down -> {
                    when {
                        from + columns < n -> from + columns
                        from / columns < (n - 1) / columns -> n - 1
                        else -> from
                    }
                }
            }
        if (to < 0 || to >= n || to == from) return this
        return copy(keys = keys.moved(from, to), inDock = if (to < columns) inDock + key else inDock - key)
    }

    /** Puts the tile at [pos] into dock place [slot] and the tile that was there where the chosen one was. */
    fun swapIntoDock(
        slot: Int,
        pos: Int,
    ): ReorderState {
        val slotKey = keys[slot]
        var list = keys
        var docked = inDock
        if (pos != slot) {
            list = list.moved(pos, slot)
            list = list.moved(list.indexOf(slotKey), pos)
            if (pos >= columns) docked = docked - slotKey
        }
        // Outside the check: with no empty slots shown, the chosen app may already sit in the free place.
        return copy(keys = list, inDock = docked + list[slot], dockEdit = null)
    }

    /**
     * What the session leaves to save, or null when nothing changed. With fill_dock off the dock is what the owner put
     * there, saved with its size, so the free slots that come back go after it.
     */
    fun finish(): OrderChange? {
        val docked = if (leavesSlots) dockCount(keys, columns, inDock) else -1
        val saved = if (leavesSlots) dockFirst(keys, columns, inDock) else keys
        return if (saved != startKeys || (leavesSlots && docked != dockedAtStart)) OrderChange(saved, docked) else null
    }

    /** The keys of the tiles drawn dimmed: all but the carried one, or in edit-dock mode the dock tiles but the chosen place. */
    fun dimmed(): Set<String> =
        when {
            carried != null -> keys.toSet() - carried
            dockEdit != null && dockEdit.slot >= 0 -> keys.take(columns).toSet() - keys[dockEdit.slot]
            else -> emptySet()
        }

    fun hint(): ReorderHint =
        when {
            dockEdit == null -> ReorderHint.Reorder
            dockEdit.appKey != null -> ReorderHint.DockPlaceForApp
            dockEdit.slot >= 0 -> ReorderHint.DockChooseApp
            else -> ReorderHint.DockChoosePlace
        }

    fun view(): ReorderView {
        val at = keys.indexOf(carried)
        return ReorderView(carried, dimmed(), hint(), canMoveLeft = at > 0, canMoveRight = at in 0 until keys.lastIndex)
    }

    companion object {
        /**
         * A session over [keys], where [docked] dock slots hold an app when fill_dock is off (else the whole dock
         * does). Null when fewer than two tiles can move.
         */
        fun start(
            keys: List<String>,
            docked: Int,
            columns: Int,
            singleRow: Boolean,
            fillDock: Boolean,
        ): ReorderState? {
            if (keys.size < 2) return null
            val filled = if (emptyDockSlots(docked, columns, keys.size, fillDock) > 0) docked else columns
            return ReorderState(
                keys = keys,
                columns = columns,
                singleRow = singleRow,
                leavesSlots = !fillDock,
                startKeys = keys,
                dockedAtStart = minOf(filled, keys.size),
                inDock = keys.take(filled).toSet(),
            )
        }
    }
}

private fun List<String>.moved(
    from: Int,
    to: Int,
): List<String> = toMutableList().also { it.add(to, it.removeAt(from)) }

/**
 * The keys a session saves when fill_dock is off. Reorder shows no free dock slots, so a grid tile can slide into one
 * without being put there. Only the keys in [dock] that end inside the first [dockSize] places stay in the dock: they
 * come first, in order, and every other key follows in order.
 */
internal fun dockFirst(
    keys: List<String>,
    dockSize: Int,
    dock: Set<String>,
): List<String> {
    val (head, tail) = keys.withIndex().partition { (index, key) -> index < dockSize && key in dock }
    return (head + tail).map { it.value }
}

/** How many of the first [dockSize] keys are in [dock]: the dock size [dockFirst] puts first. */
internal fun dockCount(
    keys: List<String>,
    dockSize: Int,
    dock: Set<String>,
): Int = keys.take(dockSize).count { it in dock }
