package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputArrival
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.InputTile
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NowPlayingItem
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.inputIdOf
import dev.niltsiar.tvhome.core.model.nowPlayingCard
import dev.niltsiar.tvhome.core.model.shelfSourceOf
import kotlinx.coroutines.delay

/** A saved order, applied over the list [base] it was made from until the app list changes (the next load reads the file). */
internal class SavedOrder(
    val base: AppList,
    val change: OrderChange,
)

/** What a session's tile places are counted in: a change of any of them ends the session and the menu. */
internal data class DockCounts(
    val columns: Int,
    val singleRow: Boolean,
    val fillDock: Boolean,
)

/**
 * A tile press that is opening the app of [key], named [label]; [says] once the line under the title says so. Each press has its
 * own [token]. [openedBefore] is the last-opened record from just before the press, which the line keeps while the app opens.
 */
internal data class Launch(
    val key: String,
    val label: String,
    val token: Int,
    val openedBefore: Map<String, Long>,
    val says: Boolean = false,
)

/** How long a dock tile holds focus before Home reads its app shelf, so moving along the dock reads nothing. */
internal const val SHELF_HOLD_MS = 600L

/** A dock [tile] whose app shelf was read, and the [items] read for it. */
private class ReadShelf(
    val tile: AppEntry,
    val items: List<ShelfItem>,
)

/** The Launcher settings page closed and Home has yet to apply what it changed; [reorder] asks for reorder mode next. */
internal data class SettingsReturn(
    val reorder: Boolean,
)

/**
 * Home's state owner: the app list as loaded, what the TV lists as inputs, the focused tile, the tile menu, the Settings
 * menu, reorder mode and edit-dock mode, and what each event does to them. [shown] is the dock and grid on screen. A
 * session works on the tiles' keys and saves them when it ends, only if they changed.
 */
internal class HomeInteraction(
    private val sources: HomeSources,
    private val config: State<TvConfig>,
    private val lastOpened: State<Map<String, Long>>,
    private val effect: (HomeEffect) -> Unit,
) {
    private val layout by derivedStateOf { config.value.homeLayout() }

    /** The counts the dock and the grid are laid out in. */
    val dockCounts: DockCounts get() = layout.run { DockCounts(columns, singleRow, fillDock) }
    private var counts = dockCounts

    /** The app list as loaded. */
    var list by mutableStateOf(AppList())
        private set

    /** True until the first app list arrives. */
    var awaitingApps by mutableStateOf(true)
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

    /** The Add to Home or Remove from Home menu over the open list, null while none is. */
    var inputMenu by mutableStateOf<InputPinMenu?>(null)
        private set

    /** What each input tile of [list] shows now, from what the TV lists. Unlike a new [list], a new value ends no session. */
    val inputTiles: Map<String, InputTileLive> by derivedStateOf { inputTilesLive(list.apps, inputs?.rows.orEmpty()) }

    private var heldToken by mutableIntStateOf(0)
    private var endedToken by mutableIntStateOf(0)

    /** The token of the arrival still waiting out its 2 s hold, null when none is or input_arrival is off. */
    val arriving: Int?
        get() = inputs?.arrival?.takeIf { layout.inputArrival && it.token != heldToken }?.token

    /** The device the Inputs button offers: an arrival that held, until 20 s pass, the person presses OK or its port leaves. */
    val offer: InputArrival?
        get() = inputs?.arrival?.takeIf { layout.inputArrival && it.token == heldToken && it.token != endedToken }

    fun arrivalHeld(token: Int) {
        if (inputs?.arrival?.token == token) heldToken = token
    }

    fun offerEnded(token: Int) {
        endedToken = token
    }

    /** Home is on screen: from its start to its stop. */
    var showing by mutableStateOf(true)
        private set

    /** The package the household usually opens now, as the last look at the launch log found it. */
    var habit by mutableStateOf<String?>(null)
        private set

    /** Counts the arrivals focus_habit_app acts on; each one looks the habit up again. */
    var arrivals by mutableIntStateOf(0)
        private set

    /** The last arrival still waits for the habit: no key, Home key or stop came since. */
    private var arrivalWaits = false

    /** With an arrival waiting and no menu, mode or list open, focus goes to the tile of [packageName] in the dock or grid. */
    fun habitFound(packageName: String?) {
        habit = packageName
        if (!arrivalWaits) return
        arrivalWaits = false
        if (packageName == null || menu != null || session != null || inputsOpen) return
        (shown.dock + shown.grid).firstOrNull { it.packageName == packageName }?.let { focus(it.key) }
    }

    /**
     * The app a tile press is opening with press_feedback, from the press until Home stops, the host starts no app or
     * [OPENING_MAX_MS] pass. A tile press meanwhile starts nothing.
     */
    var launch by mutableStateOf<Launch?>(null)
        private set
    private var launches = 0

    /** Home is still in front [OPENING_SAYS_MS] after the press [token]: the line under the title says the app is opening. */
    fun launchSays(token: Int) {
        launch = launch?.let { if (it.token == token) it.copy(says = true) else it }
    }

    fun launchEnded(token: Int) {
        if (launch?.token == token) launch = null
    }

    /** The dock tile [followShelf] read the shelf of, and the items it read; null while it has read none. */
    private var held by mutableStateOf<ReadShelf?>(null)

    /** What the held tile's app plays (now_playing), as [nowPlayingFound] last heard it. */
    private var playing by mutableStateOf<NowPlayingItem?>(null)

    /** The package whose session Home follows: the held tile's app while now_playing is on, else null. */
    val playingPackage: String? by derivedStateOf { held?.tile?.packageName?.takeIf { config.value.nowPlaying } }

    /**
     * The app shelf on screen: the focused dock tile's, once [followShelf] has read it, led by what its app plays. Null while
     * it has no card.
     */
    val appShelf: AppShelf? by derivedStateOf {
        held?.let { shelf ->
            val lead = playing?.takeIf { it.packageName == playingPackage }?.let(::nowPlayingCard)
            val items = listOfNotNull(lead) + shelf.items
            if (items.isEmpty()) null else AppShelf(shelf.tile.key, items)
        }
    }

    /** The items read per package, for the app_shelf_sources in [cachedSources]. Home coming to the front drops them. */
    private val shelfCache = HashMap<String, List<ShelfItem>>()
    private var cachedSources: List<String>? = null

    /** Counts Home coming to the front, so the shelf on screen is read again then. */
    var shelfReads by mutableIntStateOf(0)
        private set

    /** Whether the layout on screen has room for the cards; until the screen says, it is taken to have none. */
    private var shelfRoom by mutableStateOf(false)

    /**
     * The dock tile [key] when app_shelf is on, the layout has room and the tile's app has app_shelf_sources ticked or
     * now_playing is on, else null. A tile whose launch_actions switch to an input, or an input tile, opens no app, so it
     * has no shelf.
     */
    fun shelfTile(key: String?): AppEntry? {
        val current = config.value
        if (!current.appShelf || !shelfRoom) return null
        val tile = shown.dock.firstOrNull { it.key == key } ?: return null
        if (tile.input != null) return null
        if (inputIdOf(current.launchActions[tile.packageName]) != null) return null
        return tile.takeIf { current.nowPlaying || ticks(current, it.packageName) }
    }

    private fun ticks(
        current: TvConfig,
        packageName: String,
    ) = current.appShelfSources.any { shelfSourceOf(it).packageName == packageName }

    /**
     * Shows the app shelf of [tile], or none for null. A tile other than the one shown first holds focus for [SHELF_HOLD_MS];
     * the one shown is read again at once. An app without ticked sources has no items, only what it plays.
     */
    suspend fun followShelf(tile: AppEntry?) {
        if (tile == null) {
            held = null
            return
        }
        if (held?.tile?.key != tile.key) {
            held = null
            delay(SHELF_HOLD_MS)
        }
        val current = config.value
        val ticked = current.appShelfSources
        if (ticked != cachedSources) {
            shelfCache.clear()
            cachedSources = ticked
        }
        val pkg = tile.packageName
        val items = if (ticks(current, pkg)) shelfCache.getOrPut(pkg) { sources.shelfItems(pkg) } else emptyList()
        held = ReadShelf(tile, items)
    }

    /** What the app of [playingPackage] plays now, null for nothing to show. */
    fun nowPlayingFound(item: NowPlayingItem?) {
        playing = item
    }

    /** Set when the Launcher settings page closes, cleared once the presenter has applied the config and [settingsReturned] ran. */
    var settingsReturn by mutableStateOf<SettingsReturn?>(null)
        private set

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

            HomeEvent.PressInputs -> {
                val offered = offer
                if (offered != null && !inputsOpen) {
                    offerEnded(offered.token)
                    effect(HomeEffect.OpenInput(offered.target))
                } else {
                    openInputs()
                }
            }

            is HomeEvent.PickInput -> {
                pickInput(event.target)
            }

            is HomeEvent.InputMenu -> {
                openInputMenu(event.row)
            }

            is HomeEvent.InputMenuPick -> {
                pickInputMenu(event.row)
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
                rememberFocus(event.key)
            }

            HomeEvent.Stopped -> {
                showing = false
                arrivalWaits = false
                launch = null
                closeAll()
                inputs = inputs?.copy(arrival = null)
            }

            HomeEvent.Started -> {
                showing = true
                shelfCache.clear()
                shelfReads++
            }

            is HomeEvent.SettingsClosed -> {
                settingsReturn = SettingsReturn(event.reorder)
            }

            HomeEvent.HomePressed -> {
                arrivalWaits = false
                closeAll()
                shown.all.firstOrNull()?.let { rememberFocus(it.key, slot = 0) }
            }

            HomeEvent.Arrived -> {
                if (config.value.habitHint && config.value.focusHabitApp) {
                    arrivals++
                    arrivalWaits = true
                }
            }

            HomeEvent.ArrivalEnded -> {
                arrivalWaits = false
            }

            is HomeEvent.Click -> {
                click(event.key)
            }

            is HomeEvent.OpenShelfItem -> {
                openShelfItem(event.key, event.item)
            }

            is HomeEvent.ShelfRoom -> {
                shelfRoom = event.room
            }

            is HomeEvent.AppNotStarted -> {
                if (launch?.key == event.key) launch = null
            }

            HomeEvent.OpenSettings -> {
                effect(if (config.value.launcherSettingsButton) HomeEffect.OpenLauncherSettings else HomeEffect.OpenSettings)
            }

            HomeEvent.OpenNetwork -> {
                effect(HomeEffect.OpenNetwork)
            }

            HomeEvent.OpenWifiSettings -> {
                effect(HomeEffect.OpenWifiSettings)
            }
        }
    }

    /**
     * A tile press opens its app as [startLaunch] says; a top-bar app opens as before. An input tile switches to the input
     * its row opens now, as the Inputs list does, and records no open; while the TV does not list it, it switches nothing.
     */
    private fun click(key: String) {
        val tile = list.apps.firstOrNull { it.key == key }
        if (tile?.input != null) {
            effect(inputTiles[key]?.target?.let(HomeEffect::OpenInput) ?: HomeEffect.InputNotAvailable)
        } else if (tile != null) {
            startLaunch(tile, HomeEffect.LaunchApp(tile))
        } else {
            topBarApps.firstOrNull { it.key == key }?.let { effect(HomeEffect.LaunchApp(it)) }
        }
    }

    /** A card of the app shelf of the dock tile [key] opens its item as a press on that tile would. */
    private fun openShelfItem(
        key: String,
        item: ShelfItem,
    ) {
        val tile = list.apps.firstOrNull { it.key == key } ?: return
        startLaunch(tile, HomeEffect.OpenShelfItem(tile, item))
    }

    /** Sends [open] for [tile]; with press_feedback a press waits out the one before it and becomes [launch]. */
    private fun startLaunch(
        tile: AppEntry,
        open: HomeEffect,
    ) {
        if (config.value.pressFeedback) {
            if (launch != null) return
            launch = Launch(tile.key, tile.label, ++launches, lastOpened.value)
        }
        effect(open)
    }

    fun topBarAppsLoaded(loaded: List<AppEntry>) {
        topBarApps = loaded
    }

    /** An app came or went under a session: keep the order so far. */
    suspend fun listLoaded(loaded: AppList) {
        list = loaded
        awaitingApps = false
        if (restoring && loaded.apps.isNotEmpty()) {
            restoring = false
            focusedKey =
                (loaded.apps.firstOrNull { it.key == focusedKey } ?: loaded.apps[memory.slot.coerceIn(0, loaded.apps.lastIndex)]).key
        }
        end()
    }

    /**
     * The dock and grid are laid out in other counts: the menu and a session count their tiles' places in the old ones,
     * so they end (a session keeps its order), and focus lands again on the tile that had it, which a new row or the
     * dock may now hold.
     */
    suspend fun dockCountsChanged(
        now: DockCounts,
        refocus: Boolean = true,
    ) {
        if (now == counts) return
        counts = now
        menu = null
        end()
        if (refocus) focusedKey?.takeIf { key -> shown.all.any { it.key == key } }?.let(::focus)
    }

    /**
     * The config the Launcher settings page changed is applied: the tiles take their new counts, then reorder mode starts
     * if the page asked for it, else focus goes to the Settings button, which the Settings menu hangs from. A tile focus
     * could be lost to a change of names, columns or rows, and the button is not rebuilt by any of them.
     */
    suspend fun settingsReturned(reorder: Boolean) {
        dockCountsChanged(dockCounts, refocus = false)
        if (!reorder || !startReorder(carry = null)) focus(SETTINGS_FOCUS)
        settingsReturn = null
    }

    private suspend fun closeAll() {
        menu = null
        inputMenu = null
        inputsOpen = false
        inputsAsked = false
        end()
    }

    private fun rememberFocus(
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
            listed.panelOnly -> {
                effect(HomeEffect.OpenInput(InputTarget.SourcePanel))
            }

            listed.rows.none { it is InputRow.Port || it == InputRow.Panel || it == InputRow.LiveTv } -> {
                effect(HomeEffect.NoInputs)
            }

            else -> {
                inputsOpen = true
                offer?.let { offerEnded(it.token) }
            }
        }
    }

    private fun pickInput(target: InputTarget) {
        if (!inputsOpen) return
        inputsOpen = false
        effect(HomeEffect.OpenInput(target))
        focus(INPUTS_FOCUS)
    }

    /**
     * A held OK or MENU on an HDMI or AV [row] of the open list, with input_tiles and the Inputs pill on: Add to Home, or
     * Remove from Home when a pin stands for the row, as [resolvePin] matches a tile to its row.
     */
    private fun openInputMenu(row: InputRow.Port) {
        val target = row.target
        if (!inputsOpen || inputMenu != null || !layout.inputPins || target !is InputTarget.TvInput) return
        val rows = inputs?.rows.orEmpty()
        val pinned = list.apps.firstNotNullOfOrNull { app -> app.input?.takeIf { resolvePin(it, rows) == row } }
        inputMenu = InputPinMenu(InputTile(target.id, row.port, row.icon), row.label, pinned?.id)
    }

    /** Pins or unpins the row the menu is for, closes the menu and the list, and returns focus to the Inputs button. */
    private suspend fun pickInputMenu(pick: MenuRow) {
        val open = inputMenu?.takeIf { pick in it.rows } ?: return
        inputMenu = null
        inputsOpen = false
        val pinned = open.pinned
        val saved = if (pinned != null) sources.unpinInput(pinned) else sources.pinInput(open.tile, open.label)
        if (!saved) effect(HomeEffect.LibrarySaveFailed)
        focus(INPUTS_FOCUS)
    }

    private fun openTileMenu(event: HomeEvent.TileMenu) {
        if (session != null || menu != null) return
        val apps = shown
        val app = apps.all.firstOrNull { it.key == event.key } ?: return
        val input = app.input != null
        when {
            config.value.tileMenu -> {
                val current = config.value
                // An input tile has no App info, and Remove from Home unpins it, with or without the app library.
                val rows =
                    tileMenuRows(
                        current.dockRow,
                        inDock(apps, app.key),
                        !input && sources.hasAppInfo(app.packageName),
                        current.appLibrary || input,
                        moveTo = current.moveToMenu && movePlaces(app.key).isNotEmpty(),
                    )
                menu = HomeMenu(app.key, rows)
            }

            event.held && !input -> {
                effect(HomeEffect.OpenAppInfo(app.packageName))
            }
        }
    }

    private fun openSettingsMenu() {
        if (session != null || menu != null) return
        menu = HomeMenu(null, settingsMenuRows(config.value.dockRow, sources.hasNativeSettings(), sources.powerMenuAvailable()))
    }

    /** The dock row's tiles: the first [TvConfig.columns] with a single row, else the tiles the dock shows. */
    private fun inDock(
        apps: HomeApps,
        key: String,
    ): Boolean =
        config.value.let {
            if (it.singleRow) apps.all.indexOfFirst { tile -> tile.key == key } < it.columns else apps.dock.any { tile -> tile.key == key }
        }

    private suspend fun pick(row: MenuRow) {
        val open = menu ?: return
        menu = null
        val key = open.tileKey
        when (row) {
            MenuRow.Move -> {
                if (!startReorder(carry = key)) focus(key)
            }

            MenuRow.MoveTo -> {
                val places = key?.let(::movePlaces).orEmpty()
                if (places.isEmpty()) focus(key) else menu = HomeMenu(key, places, parent = open.copy(focus = MenuRow.MoveTo))
            }

            MenuRow.DockStart, MenuRow.DockEnd, MenuRow.GridStart, MenuRow.GridEnd -> {
                val change = key?.let { movedTo(it, row) }
                focus(key)
                if (change != null) keep(change)
            }

            MenuRow.AddToDock -> {
                if (!startDockEditFor(key, inDock = false)) focus(key)
            }

            MenuRow.ReplaceInDock -> {
                if (!startDockEditFor(key, inDock = true)) focus(key)
            }

            MenuRow.RemoveFromHome -> {
                val app = shown.all.firstOrNull { it.key == key }
                val pin = app?.input
                val removed =
                    when {
                        app == null -> true
                        pin != null -> sources.unpinInput(pin.id)
                        else -> sources.removeFromHome(app.packageName)
                    }
                if (!removed) effect(HomeEffect.LibrarySaveFailed)
                focus(key)
            }

            MenuRow.AddToHome -> {
                focus(key)
            }

            MenuRow.AppInfo -> {
                shown.all.firstOrNull { it.key == key }?.let { effect(HomeEffect.OpenAppInfo(it.packageName)) }
                focus(key)
            }

            MenuRow.LauncherSettings -> {
                effect(HomeEffect.OpenLauncherSettings)
                focus(SETTINGS_FOCUS)
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
                if (config.value.restartPress) {
                    menu = HomeMenu(null, listOf(MenuRow.CancelRestart, MenuRow.Restart), focus = MenuRow.CancelRestart)
                } else {
                    effect(HomeEffect.OpenPowerMenu)
                    focus(SETTINGS_FOCUS)
                }
            }

            MenuRow.CancelRestart -> {
                focus(SETTINGS_FOCUS)
            }

            MenuRow.Restart -> {
                effect(HomeEffect.PressRestart)
                focus(SETTINGS_FOCUS)
            }
        }
    }

    private suspend fun back() {
        val open = menu
        val current = session
        val edit = current?.dockEdit
        when {
            inputMenu != null -> {
                inputMenu = null
            }

            inputsOpen -> {
                inputsOpen = false
                focus(INPUTS_FOCUS)
            }

            open != null -> {
                menu = open.parent
                if (open.parent == null) focus(open.tileKey ?: SETTINGS_FOCUS)
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

    /** How many leading apps are dock apps: the saved order's count while it applies, else the loaded list's. */
    private fun docked(): Int =
        saved
            ?.takeIf { it.base === list }
            ?.change
            ?.docked
            ?.coerceAtLeast(0) ?: list.docked

    private fun begin(): ReorderState? {
        val current = config.value
        return ReorderState.start(shown.all.map { it.key }, docked(), current.columns, current.singleRow, current.fillDock)
    }

    /** The places "Move to" lists for [key]: those it would land in and that change the order; with dock_row off, the grid's only. */
    private fun movePlaces(key: String): List<MenuRow> =
        MovePlaces.filter { place ->
            (config.value.dockRow || place == MenuRow.GridStart || place == MenuRow.GridEnd) && movedTo(key, place) != null
        }

    /**
     * The order with [key] first or last in the dock or the grid, as Move leaves it: an app put into a full dock pushes the
     * dock's last app to the start of the grid, and with fill_dock off an app takes a free dock place or leaves its own free,
     * where with fill_dock on the grid's first app moves up into it. The dock and the grid are those of the grid layout, so
     * with single_row the dock is the row's first places. Null when the app would not land there or nothing would change.
     */
    private fun movedTo(
        key: String,
        place: MenuRow,
    ): OrderChange? {
        val session = begin() ?: return null
        val grid = layout.copy(singleRow = false)
        val before = HomeApps(AppList(shown.all, docked()), grid).dock
        val inDock = before.any { it.key == key }
        val (to, intoDock) =
            when (place) {
                MenuRow.DockStart -> 0 to true
                MenuRow.DockEnd -> (if (inDock) before.lastIndex else minOf(before.size, layout.columns - 1)) to true
                MenuRow.GridStart -> (if (inDock && !layout.fillDock) before.lastIndex else before.size) to false
                else -> session.keys.lastIndex to false
            }
        val change = session.place(key, to, intoDock).finish() ?: return null
        val after = HomeApps(AppList(shown.all).arranged(change.keys, change.docked), grid)
        val landed =
            when (place) {
                MenuRow.DockStart -> after.dock.firstOrNull()
                MenuRow.DockEnd -> after.dock.lastOrNull()
                MenuRow.GridStart -> after.grid.firstOrNull()
                else -> after.grid.lastOrNull()
            }
        return change.takeIf { landed?.key == key }
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
            focus(started.keys[minOf(config.value.columns, started.keys.size - 1)])
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
        val docks = minOf(config.value.columns, current.keys.size)
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
                    focus(current.keys[minOf(config.value.columns, current.keys.size - 1)])
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
        current.finish()?.let { keep(it) }
    }

    /** Shows [change] at once and writes it. */
    private suspend fun keep(change: OrderChange) {
        saved = SavedOrder(list, change)
        if (!sources.saveOrder(change.keys, change.docked)) effect(HomeEffect.OrderNotSaved)
    }

    private fun focus(target: String?) {
        if (target != null) focusRequest = FocusRequest(target, ++focusToken)
    }
}
