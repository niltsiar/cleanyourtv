package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppList

/** A saved order, applied over the list [base] it was made from until the app list changes (the next load reads the file). */
internal class SavedOrder(
    val base: AppList,
    val change: OrderChange,
)

/**
 * The tile menu, the Settings menu, reorder mode and edit-dock mode: what they show, and what each event does to them.
 * [list] is the app list as loaded and [shown] the dock and grid on screen, both set by the presenter on each
 * composition. A session works on the tiles' keys and saves them when it ends, only if they changed.
 */
internal class HomeInteraction(
    private val sources: HomeSources,
    private val config: TvConfig,
    private val effect: (HomeEffect) -> Unit,
) {
    var list = AppList()
    var shown = HomeApps(list, config.homeLayout())

    var focusedKey by mutableStateOf<String?>(null)
    var menu by mutableStateOf<HomeMenu?>(null)
        private set
    var session by mutableStateOf<ReorderState?>(null)
        private set
    var saved by mutableStateOf<SavedOrder?>(null)
        private set
    var focusRequest by mutableStateOf<FocusRequest?>(null)
        private set
    private var focusToken = 0

    suspend fun handle(event: HomeEvent) {
        when (event) {
            is HomeEvent.TileMenu -> {
                openTileMenu(event)
            }

            HomeEvent.SettingsMenu -> {
                openSettingsMenu()
            }

            is HomeEvent.MenuPick -> {
                pick(event.row)
            }

            HomeEvent.Back -> {
                back()
            }

            HomeEvent.ToggleReorder -> {
                if (session != null) end() else startReorder(carry = null)
            }

            is HomeEvent.ReorderMove -> {
                move(event.arrow)
            }

            HomeEvent.ReorderOk -> {
                ok()
            }

            HomeEvent.Stopped -> {
                menu = null
                end()
            }

            else -> {
                Unit
            }
        }
    }

    /** An app came or went under a session: keep the order so far. */
    suspend fun listChanged() = end()

    private fun openTileMenu(event: HomeEvent.TileMenu) {
        if (session != null || menu != null) return
        val apps = shown
        val app = apps.all.firstOrNull { it.key == event.key } ?: return
        when {
            config.tileMenu -> {
                val rows = tileMenuRows(config.dockRow, inDock(apps, app.key), sources.hasAppInfo(app.packageName), config.appLibrary)
                menu = HomeMenu(app.key, rows)
            }

            event.held -> {
                effect(HomeEffect.OpenAppInfo(app.packageName))
            }
        }
    }

    private fun openSettingsMenu() {
        if (session != null || menu != null) return
        menu = HomeMenu(null, settingsMenuRows(config.dockRow, sources.hasNativeSettings()))
    }

    /** The dock row's tiles: the first [TvConfig.columns] with a single row, else the tiles the dock shows. */
    private fun inDock(
        apps: HomeApps,
        key: String,
    ): Boolean = if (config.singleRow) apps.all.indexOfFirst { it.key == key } < config.columns else apps.dock.any { it.key == key }

    private suspend fun pick(row: MenuRow) {
        val open = menu ?: return
        menu = null
        val key = open.tileKey
        when (row) {
            MenuRow.Move -> {
                if (!startReorder(carry = key)) focus(key)
            }

            MenuRow.AddToDock -> {
                if (!startDockEditFor(key, inDock = false)) focus(key)
            }

            MenuRow.ReplaceInDock -> {
                if (!startDockEditFor(key, inDock = true)) focus(key)
            }

            MenuRow.RemoveFromHome -> {
                val app = shown.all.firstOrNull { it.key == key }
                if (app != null && !sources.removeFromHome(app.packageName)) effect(HomeEffect.LibrarySaveFailed)
                focus(key)
            }

            MenuRow.AppInfo -> {
                shown.all.firstOrNull { it.key == key }?.let { effect(HomeEffect.OpenAppInfo(it.packageName)) }
                focus(key)
            }

            MenuRow.NativeSettings -> {
                effect(HomeEffect.OpenNativeSettings)
                focus(SETTINGS_FOCUS)
            }

            MenuRow.EditDock -> {
                if (!startDockEdit()) focus(SETTINGS_FOCUS)
            }

            MenuRow.Reorder -> {
                if (!startReorder(carry = null)) focus(SETTINGS_FOCUS)
            }
        }
    }

    private suspend fun back() {
        val open = menu
        val current = session
        val edit = current?.dockEdit
        when {
            open != null -> {
                menu = null
                focus(open.tileKey ?: SETTINGS_FOCUS)
            }

            current == null -> {
                Unit
            }

            edit == null && current.carried != null -> {
                session = current.drop()
            }

            edit != null && edit.appKey == null && edit.slot >= 0 -> {
                session = current.copy(dockEdit = DockEdit())
                focus(current.keys[edit.slot])
            }

            else -> {
                end()
            }
        }
    }

    private fun begin(): ReorderState? {
        val loaded = list
        val docked =
            saved
                ?.takeIf { it.base === loaded }
                ?.change
                ?.docked
                ?.coerceAtLeast(0) ?: loaded.docked
        return ReorderState.start(shown.all.map { it.key }, docked, config.columns, config.singleRow, config.fillDock)
    }

    /** False when fewer than two tiles can move. */
    private fun startReorder(carry: String?): Boolean {
        val started = begin() ?: return false
        session = carry?.let(started::pickUp) ?: started
        focus(carry ?: focusedKey?.takeIf { it in started.keys } ?: started.keys.first())
        return true
    }

    private fun startDockEdit(): Boolean {
        val started = begin() ?: return false
        session = started.copy(dockEdit = DockEdit())
        focus(started.keys.first())
        return true
    }

    /** The place of a dock tile is chosen already and an app is wanted for it; for a grid tile it is the other way round. */
    private fun startDockEditFor(
        key: String?,
        inDock: Boolean,
    ): Boolean {
        val started = begin() ?: return false
        val place = started.keys.indexOf(key)
        if (key == null || place < 0) return false
        if (inDock) {
            session = started.copy(dockEdit = DockEdit(slot = place))
            focus(started.keys[minOf(config.columns, started.keys.size - 1)])
        } else {
            session = started.copy(dockEdit = DockEdit(appKey = key))
            focus(started.keys.first())
        }
        return true
    }

    private fun move(arrow: Arrow) {
        val current = session ?: return
        val moved = current.move(arrow)
        if (moved == current) return
        session = moved
        focus(moved.carried)
    }

    private suspend fun ok() {
        val current = session ?: return
        val edit = current.dockEdit
        if (edit == null && current.carried != null) {
            session = current.drop()
            return
        }
        val pos = current.keys.indexOf(focusedKey)
        if (pos < 0) return
        val docks = minOf(config.columns, current.keys.size)
        when {
            edit == null -> {
                session = current.pickUp(current.keys[pos])
            }

            edit.appKey != null -> {
                if (pos < docks) swap(current, pos, current.keys.indexOf(edit.appKey))
            }

            edit.slot < 0 -> {
                if (pos < docks) {
                    session = current.copy(dockEdit = DockEdit(slot = pos))
                    focus(current.keys[minOf(config.columns, current.keys.size - 1)])
                }
            }

            else -> {
                swap(current, edit.slot, pos)
            }
        }
    }

    /** Puts the app at [from] into dock place [slot], saves and ends the session. */
    private suspend fun swap(
        current: ReorderState,
        slot: Int,
        from: Int,
    ) {
        if (from < 0) {
            end()
            return
        }
        val swapped = current.swapIntoDock(slot, from)
        session = swapped
        end()
        focus(swapped.keys[slot])
    }

    /** Ends the session and keeps its order if it changed. */
    private suspend fun end() {
        val current = session ?: return
        session = null
        val change = current.finish() ?: return
        saved = SavedOrder(list, change)
        if (!sources.saveOrder(change.keys, change.docked)) effect(HomeEffect.OrderNotSaved)
    }

    private fun focus(target: String?) {
        if (target != null) focusRequest = FocusRequest(target, ++focusToken)
    }
}
