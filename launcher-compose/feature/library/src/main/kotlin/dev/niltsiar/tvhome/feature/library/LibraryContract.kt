package dev.niltsiar.tvhome.feature.library

import androidx.compose.runtime.Immutable
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.LibraryApp
import dev.niltsiar.tvhome.core.model.LibraryArt
import kotlinx.coroutines.flow.Flow

/**
 * How the library behaves, from the theme. [selection] is app_library_selection: OK changes an app's Home shortcut and
 * a held OK opens nothing. [okOpens] is library_ok_opens, which selection overrides. [animated] is false for a theme
 * without animations or with reduced motion.
 */
@Immutable
public data class LibraryLayout(
    val selection: Boolean = false,
    val okOpens: Boolean = false,
    val animated: Boolean = true,
)

public fun TvConfig.libraryLayout(): LibraryLayout =
    LibraryLayout(
        selection = appLibrarySelection,
        okOpens = libraryOkOpens && !appLibrarySelection,
        animated = animations && !reducedMotion,
    )

/** What the host feeds the library: its catalogue and the choices it saves. */
public class LibrarySources(
    /** Reads the catalogue, off the main thread. */
    public val load: suspend () -> List<LibraryApp>,
    /** Emits each time the installed apps or the Home choices change after the collection starts. */
    public val changes: Flow<Int>,
    /** The art of one card, null for an app without any. */
    public val art: suspend (String) -> LibraryArt? = { null },
    /** Puts a package on Home (true) or takes it off. False when the choice was not saved. */
    public val setOnHome: suspend (String, Boolean) -> Boolean = { _, _ -> true },
    /** Whether the TV has an App info page for a package. */
    public val hasAppInfo: (String) -> Boolean = { false },
    /** The library closed: drop what it kept for its cards. */
    public val release: () -> Unit = {},
    /** The time, in milliseconds since the epoch, that decides which installs are recent. */
    public val now: () -> Long = System::currentTimeMillis,
)

/** One card: an app, whether it is on Home, and whether it is a recent install. */
@Immutable
public data class LibraryCard(
    val app: AppEntry,
    val onHome: Boolean,
    val recent: Boolean,
)

/** What the line under the title says for the focused card. */
public enum class LibraryHint {
    /** OK opens the app; hold for options. */
    Open,

    /** OK adds the app to Home; hold for options. */
    Add,

    /** Selection mode: OK adds the app to Home. */
    SelectAdd,

    /** Selection mode: OK takes the app off Home. */
    SelectRemove,
}

/** One row of the menu a held OK opens on a card. */
public enum class LibraryRow {
    Open,
    AddToHome,
    RemoveFromHome,
    AppInfo,
}

/** The open card menu: the [key] of its card and its [rows], top to bottom. */
@Immutable
public data class LibraryMenu(
    val key: String,
    val rows: List<LibraryRow>,
)

/** Focus goes to the card with this key. A new [token] asks again. */
@Immutable
public data class LibraryFocus(
    val key: String,
    val token: Int,
)

/**
 * What the screen shows. [open] is true from asking to open until closing; [shown] once the catalogue first loaded,
 * which is when the panel appears. [cards] are in the library's order; [focusedKey] is the card with focus.
 */
@Immutable
public data class LibraryUiState(
    val open: Boolean = false,
    val shown: Boolean = false,
    val cards: List<LibraryCard> = emptyList(),
    val focusedKey: String? = null,
    val hint: LibraryHint = LibraryHint.Add,
    val menu: LibraryMenu? = null,
    val focusRequest: LibraryFocus? = null,
)

/** One-shot results the host handles outside the UI state. */
public sealed interface LibraryEffect {
    /** Open the app. The library has closed. */
    public data class LaunchApp(
        val app: AppEntry,
    ) : LibraryEffect

    /** Open the TV's App info page for a package. The library has closed. */
    public data class OpenAppInfo(
        val packageName: String,
    ) : LibraryEffect

    /** The Home choice could not be written. */
    public data object SaveFailed : LibraryEffect
}

internal sealed interface LibraryEvent {
    data object Open : LibraryEvent

    data class Focus(
        val key: String,
    ) : LibraryEvent

    data class Click(
        val key: String,
    ) : LibraryEvent

    /** A held OK on a card. */
    data class Hold(
        val key: String,
    ) : LibraryEvent

    data class Pick(
        val row: LibraryRow,
    ) : LibraryEvent

    data object Back : LibraryEvent

    /** Home left the screen or the Home key was pressed. */
    data object Close : LibraryEvent
}
