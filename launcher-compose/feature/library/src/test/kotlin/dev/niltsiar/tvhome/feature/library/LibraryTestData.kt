package dev.niltsiar.tvhome.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import app.cash.turbine.ReceiveTurbine
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.LibraryApp
import dev.niltsiar.tvhome.core.model.OpenedRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/** [libraryPresenter] over a config that never changes. */
@Composable
internal fun libraryPresenter(
    events: Flow<LibraryEvent>,
    sources: LibrarySources,
    config: TvConfig,
    onEffect: (LibraryEffect) -> Unit,
    locale: Locale,
): LibraryUiState = libraryPresenter(events, sources, remember(config) { MutableStateFlow(config) }, onEffect, locale)

internal const val DAY = 24L * 60 * 60 * 1000
internal const val NOW = 100 * DAY

internal fun libraryApp(
    name: String,
    onHome: Boolean = false,
    installedAt: Long = 0,
) = LibraryApp(AppEntry(key = "pkg.$name/.Main", packageName = "pkg.$name", label = name), onHome, installedAt)

internal fun key(name: String) = "pkg.$name/.Main"

/** The texts drawn on the card of [name], top to bottom: its name, then its status line when it has one. */
internal fun SemanticsNodeInteractionsProvider.textsOnCard(name: String): List<String> =
    onAllNodes(hasAnyAncestor(hasTestTag("card:${key(name)}")), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
        .map { it.text }

/** The host side of the library: a catalogue that a saved choice changes and announces, as the real one does. */
internal class FakeLibrary(
    var apps: List<LibraryApp>,
) {
    val changes = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val saved = mutableListOf<Pair<String, Boolean>>()
    var saveWorks = true
    var released = 0
    var loads = 0

    /** When Home last opened each app, and how often the library asked. */
    var record = OpenedRecord()
    var recordReads = 0

    /** While set, a load waits for it. */
    var gate: CompletableDeferred<Unit>? = null
    var appInfoFor: Set<String> = emptySet()

    val sources =
        LibrarySources(
            load = {
                loads++
                gate?.await()
                apps
            },
            changes = changes,
            setOnHome = { pkg, onHome ->
                saved += pkg to onHome
                if (saveWorks) {
                    apps = apps.map { if (it.app.packageName == pkg) it.copy(onHome = onHome) else it }
                    changes.emit(1)
                }
                saveWorks
            },
            hasAppInfo = { it in appInfoFor },
            release = { released++ },
            now = { NOW },
            opened = {
                recordReads++
                record
            },
            hour24 = { true },
        )
}

internal suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
