package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputsMenu

/** A saved order, applied over the list [base] it was made from until the app list changes (the next load reads the file). */
internal class SavedOrder(
    val base: AppList,
    val change: OrderChange,
)

/**
 * Home's state owner: the app list as loaded, what the TV lists as inputs, the focused tile, the tile menu, the Settings
 * menu, reorder mode and edit-dock mode, and what each event does to them. [shown] is the dock and grid on screen. A
 * session works on the tiles' keys and saves them when it ends, only if they changed.
 */
internal class HomeInteraction(
    private val sources: HomeSources,
    private val config: TvConfig,
    private val effect: (HomeEffect) -> Unit,
) {
    private val layout = config.homeLayout()

    /** The app list as loaded. */
    var list by mutableStateOf(AppList())
        private set

    /** The top_bar_apps that can be opened. */
    var topBarApps by mutableStateOf(emptyList<AppEntry>())
        private set

    /** What the TV lists as inputs, null until it has said. */
    var inputs by mutableStateOf<InputsMenu?>(null)
        private set

    /** The list is open, or was asked for before the TV said what it lists. */
    var inputsOpen by mutableStateOf(false)
        private set
    private var inputsAsked = false

    private val memory = sources.focusMemory

    /** The tile that had focus last. It starts as the remembered one, which the first list of apps may replace. */
    var focusedKey by mutableStateOf(memory.key)
        private set
    private var restoring = memory.key != null
    var menu by mutableStateOf<HomeMenu?>(null)
        private set
    var session by mutableStateOf<ReorderState?>(null)
        private set
    var saved by mutableStateOf<SavedOrder?>(null)
        private set
    var focusRequest by mutableStateOf<FocusRequest?>(null)
        private set
    private var focusToken = 0

    private val sessionKeys by derivedStateOf { session?.keys }

    /**
     * The dock and grid on screen: [list] in the saved or session order. A session shows every dock slot filled, so that
     * each place holds a tile that can move. A new instance only when one of those changes, so the screen can skip it.
     */
    val shown by derivedStateOf {
        val keys = sessionKeys
        val kept = saved?.takeIf { it.base === list }
        val arranged =
            when {
                keys != null -> list.arranged(keys, 0)
                kept != null -> list.arranged(kept.change.keys, kept.change.docked)
                else -> list
            }
        HomeApps(arranged, if (keys != null) layout.copy(fillDock = true) else layout)
    }

    suspend fun handle(event: HomeEvent) {
        when (event) {
            is HomeEvent.TileMenu -> {
                openTileMenu(event)
            }

            HomeEvent.SettingsMenu -> {
                openSettingsMenu()
            }

            HomeEvent.OpenInputs -> {
                openInputs()
            }

            is HomeEvent.PickInput -> {
                pickInput(event.target)
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

            is HomeEvent.Focus -> {
                remember(event.key)
            }

            HomeEvent.Stopped -> {
                closeAll()
            }

            HomeEvent.HomePressed -> {
                closeAll()
                shown.all.firstOrNull()?.let { remember(it.key, slot = 0) }
            }

            is HomeEvent.Click -> {
                val app = list.apps.firstOrNull { it.key == event.key } ?: topBarApps.firstOrNull { it.key == event.key }
                if (app != null) effect(HomeEffect.LaunchApp(app))
            }

            HomeEvent.OpenSettings -> {
                effect(HomeEffect.OpenSettings)
            }

            HomeEvent.OpenNetwork -> {
                effect(HomeEffect.OpenNetwork)
            }

            HomeEvent.OpenWifiSettings -> {
                effect(HomeEffect.OpenWifiSettings)
            }
        }
    }

    fun topBarAppsLoaded(loaded: List<AppEntry>) {
        topBarApps = loaded
    }

    /** An app came or went under a session: keep the order so far. */
    suspend fun listLoaded(loaded: AppList) {
        list = loaded
        if (restoring && loaded.apps.isNotEmpty()) {
            restoring = false
            focusedKey =
                (loaded.apps.firstOrNull { it.key == focusedKey } ?: loaded.apps[memory.slot.coerceIn(0, loaded.apps.lastIndex)]).key
        }
        end()
    }

    private suspend fun closeAll() {
        menu = null
        inputsOpen = false
        inputsAsked = false
        end()
    }

    private fun remember(
        key: String,
        slot: Int = shown.all.indexOfFirst { it.key == key },
    ) {
        focusedKey = key
        memory.key = key
        if (slot >= 0) memory.slot = slot
    }

    /** A menu or a session in the way gives place to the list, as the Home key would clear them; an open list stays. */
    private suspend fun openInputs() {
        if (!inputsOpen) {
            menu = null
            end()
        }
        inputsAsked = true
        inputsChanged()
    }

    fun inputsLoaded(loaded: InputsMenu) {
        inputs = loaded
        inputsChanged()
    }

    /**
     * The Inputs button was pressed. With a source panel and no Live TV row the button opens the panel; with nothing to
     * list it says so; else the list opens. A press before the TV has said what it lists waits for it.
     */
    private fun inputsChanged() {
        val listed = inputs ?: return
        if (!inputsAsked) return
        inputsAsked = false
        if (menu != null || inputsOpen) return
        when {
            listed.panelOnly -> effect(HomeEffect.OpenInput(InputTarget.SourcePanel))
            listed.rows.none { it is InputRow.Port || it == InputRow.Panel || it == InputRow.LiveTv } -> effect(HomeEffect.NoInputs)
            else -> inputsOpen = true
        }
    }

    private fun pickInput(target: InputTarget) {
        if (!inputsOpen) return
        inputsOpen = false
        effect(HomeEffect.OpenInput(target))
        focus(INPUTS_FOCUS)
    }

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
        menu = HomeMenu(null, settingsMenuRows(config.dockRow, sources.hasNativeSettings(), sources.powerMenuAvailable()))
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

            MenuRow.PowerMenu -> {
                effect(HomeEffect.OpenPowerMenu)
                focus(SETTINGS_FOCUS)
            }
        }
    }

    private suspend fun back() {
        val open = menu
        val current = session
        val edit = current?.dockEdit
        when {
            inputsOpen -> {
                inputsOpen = false
                focus(INPUTS_FOCUS)
            }

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
