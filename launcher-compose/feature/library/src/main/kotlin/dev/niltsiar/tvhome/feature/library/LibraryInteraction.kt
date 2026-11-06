package dev.niltsiar.tvhome.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.niltsiar.tvhome.core.model.LibraryApp
import dev.niltsiar.tvhome.core.model.OpenedAt
import dev.niltsiar.tvhome.core.model.OpenedRecord
import dev.niltsiar.tvhome.core.model.isRecent
import dev.niltsiar.tvhome.core.model.nextAfterRemoval
import dev.niltsiar.tvhome.core.model.nextFocus
import dev.niltsiar.tvhome.core.model.okOpens
import dev.niltsiar.tvhome.core.model.sortLeastUsed
import dev.niltsiar.tvhome.core.model.sortLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * The library's state and what each event does to it. Opening starts a load, and so does each change of the installed
 * apps or of the Home choices while it is open; [LibraryLoads] lets only the latest fill the cards, and the panel
 * appears once, when the latest first has. With library_least_used the MENU key switches [order] and sorts the loaded
 * catalogue again; focus stays on its app. While no app of the catalogue has an open time, no card gets an opened line
 * and [noUsageYet] says so.
 */
internal class LibraryInteraction(
    private val sources: LibrarySources,
    private val layout: LibraryLayout,
    private val collator: Collator,
    private val scope: CoroutineScope,
    private val effect: (LibraryEffect) -> Unit,
) {
    var open by mutableStateOf(false)
        private set
    var shown by mutableStateOf(false)
        private set
    var cards by mutableStateOf<List<LibraryCard>>(emptyList())
        private set
    var focusedKey by mutableStateOf<String?>(null)
        private set
    var menu by mutableStateOf<LibraryMenu?>(null)
        private set
    var focusRequest by mutableStateOf<LibraryFocus?>(null)
        private set
    var order by mutableStateOf(LibraryOrder.Usual)
        private set
    var noUsageYet by mutableStateOf(false)
        private set

    private var catalogue = emptyList<LibraryApp>()
    private var record = OpenedRecord()

    private val loads = LibraryLoads()
    private var focusToken = 0

    /** The card focus moves to when the cards load again: where a changed shortcut sends it. */
    private var pendingFocus: String? = null

    val hint: LibraryHint
        get() {
            val card = cards.firstOrNull { it.app.key == focusedKey } ?: return LibraryHint.Add
            return when {
                layout.selection -> if (card.onHome) LibraryHint.SelectRemove else LibraryHint.SelectAdd
                card.onHome || layout.okOpens -> LibraryHint.Open
                else -> LibraryHint.Add
            }
        }

    fun handle(event: LibraryEvent) {
        when (event) {
            LibraryEvent.Open -> open()
            is LibraryEvent.Focus -> focusedKey = event.key
            is LibraryEvent.Click -> click(event.key)
            is LibraryEvent.Hold -> hold(event.key)
            is LibraryEvent.Pick -> pick(event.row)
            LibraryEvent.Back -> back()
            LibraryEvent.ToggleOrder -> toggleOrder()
            LibraryEvent.Close -> close()
        }
    }

    /** The installed apps or the Home choices changed. */
    fun reload() {
        if (open) load(reveal = false)
    }

    private fun open() {
        if (open) return
        open = true
        load(reveal = true)
    }

    private fun load(reveal: Boolean) {
        menu = null
        val number = loads.start(reveal)
        scope.launch {
            val apps = sources.load()
            val opened = if (layout.leastUsed) sources.opened() else OpenedRecord()
            if (!open || !loads.isLatest(number)) return@launch
            catalogue = apps
            record = opened
            fill()
            if (loads.takeReveal()) shown = true
        }
    }

    private fun toggleOrder() {
        if (!layout.leastUsed || !shown || menu != null) return
        order = if (order == LibraryOrder.Usual) LibraryOrder.LeastUsed else LibraryOrder.Usual
        fill()
    }

    private fun fill() {
        val now = sources.now()
        val leastUsed = order == LibraryOrder.LeastUsed
        val sorted = if (leastUsed) sortLeastUsed(catalogue, record, collator) else sortLibrary(catalogue, layout.selection, now, collator)
        val usageKnown = catalogue.any { it.app.key in record.times }
        noUsageYet = leastUsed && !usageKnown
        cards =
            sorted.map {
                LibraryCard(
                    it.app,
                    it.onHome,
                    recent = !layout.selection && isRecent(it.installedAt, now),
                    opened = if (leastUsed && usageKnown) openedOn(it.app.key, now) else null,
                )
            }
        val target = pendingFocus ?: focusedKey?.takeIf { key -> cards.any { it.app.key == key } } ?: cards.firstOrNull()?.app?.key
        pendingFocus = null
        focusedKey = target
        if (target != null) focus(target)
    }

    private fun openedOn(
        key: String,
        now: Long,
    ) = CardOpened(record.times[key]?.let { OpenedAt(it, now, sources.hour24()) })

    private fun click(key: String) {
        val card = cards.firstOrNull { it.app.key == key } ?: return
        if (okOpens(layout.selection, layout.okOpens, card.onHome)) launch(card) else toggle(card, add = !card.onHome)
    }

    private fun hold(key: String) {
        if (layout.selection || menu != null) return
        val card = cards.firstOrNull { it.app.key == key } ?: return
        focusedKey = key
        menu =
            LibraryMenu(
                key,
                buildList {
                    add(LibraryRow.Open)
                    add(if (card.onHome) LibraryRow.RemoveFromHome else LibraryRow.AddToHome)
                    if (sources.hasAppInfo(card.app.packageName)) add(LibraryRow.AppInfo)
                },
            )
    }

    private fun pick(row: LibraryRow) {
        val card = menu?.let { open -> cards.firstOrNull { it.app.key == open.key } } ?: return
        menu = null
        when (row) {
            LibraryRow.Open -> {
                launch(card)
            }

            LibraryRow.AddToHome -> {
                toggle(card, add = true)
            }

            LibraryRow.RemoveFromHome -> {
                toggle(card, add = false)
            }

            LibraryRow.AppInfo -> {
                effect(LibraryEffect.OpenAppInfo(card.app.packageName))
                close()
            }
        }
    }

    private fun back() {
        val shownMenu = menu
        if (shownMenu == null) {
            close()
        } else {
            menu = null
            focus(shownMenu.key)
        }
    }

    private fun launch(card: LibraryCard) {
        effect(LibraryEffect.LaunchApp(card.app))
        close()
    }

    /**
     * Puts the app on Home or takes it off. The cards load again once the choice is saved. Focus then goes to the next
     * app that is not on Home after an add, to the next app after a removal in selection mode, else stays on the app;
     * a choice that was not saved leaves focus on the app.
     */
    private fun toggle(
        card: LibraryCard,
        add: Boolean,
    ) {
        val index = cards.indexOf(card)
        val next =
            when {
                layout.selection && !add -> nextAfterRemoval(index, cards.size)
                !layout.selection && add -> nextFocus(index, cards.size) { !cards[it].onHome }
                else -> -1
            }
        val target = cards.getOrNull(next)?.app?.key ?: card.app.key
        pendingFocus = target
        scope.launch {
            if (sources.setOnHome(card.app.packageName, add)) {
                focus(target)
            } else {
                pendingFocus = null
                focus(card.app.key)
                effect(LibraryEffect.SaveFailed)
            }
        }
    }

    private fun close() {
        open = false
        shown = false
        cards = emptyList()
        focusedKey = null
        menu = null
        focusRequest = null
        pendingFocus = null
        order = LibraryOrder.Usual
        noUsageYet = false
        catalogue = emptyList()
        record = OpenedRecord()
        sources.release()
    }

    private fun focus(key: String) {
        focusRequest = LibraryFocus(key, ++focusToken)
    }
}
