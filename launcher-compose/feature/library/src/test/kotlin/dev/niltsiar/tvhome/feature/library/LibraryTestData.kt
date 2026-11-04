package dev.niltsiar.tvhome.feature.library

import app.cash.turbine.ReceiveTurbine
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.LibraryApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow

internal const val DAY = 24L * 60 * 60 * 1000
internal const val NOW = 100 * DAY

internal fun libraryApp(
    name: String,
    onHome: Boolean = false,
    installedAt: Long = 0,
) = LibraryApp(AppEntry(key = "pkg.$name/.Main", packageName = "pkg.$name", label = name), onHome, installedAt)

internal fun key(name: String) = "pkg.$name/.Main"

/** The host side of the library: a catalogue that a saved choice changes and announces, as the real one does. */
internal class FakeLibrary(
    var apps: List<LibraryApp>,
) {
    val changes = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val saved = mutableListOf<Pair<String, Boolean>>()
    var saveWorks = true
    var released = 0
    var loads = 0

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
        )
}

internal suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
