package dev.niltsiar.tvhome.feature.library

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.focusRingColour
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.LibraryApp
import dev.niltsiar.tvhome.core.model.LibraryArt
import dev.niltsiar.tvhome.core.model.OpenedAt
import dev.niltsiar.tvhome.core.model.OpenedRecord
import kotlinx.coroutines.flow.Flow

/**
 * How the library behaves, from the theme. [selection] is app_library_selection: OK changes an app's Home shortcut and
 * a held OK opens nothing. [okOpens] is library_ok_opens, which selection overrides. [appGlow] and [whiteFocus] decide the
 * colour of a focused card's ring as they do in Home's grid. [animated] is false for a theme without animations or with
 * reduced motion. [leastUsed] is library_least_used: the MENU key switches to the least used first order. [pressFeedback]
 * is press_feedback: OK on a card dips it and lights its ring, and the library stays open while an app it started opens.
 */
@Immutable
public data class LibraryLayout(
    val selection: Boolean = false,
    val okOpens: Boolean = false,
    val appGlow: Boolean = false,
    val whiteFocus: Boolean = false,
    val animated: Boolean = true,
    val leastUsed: Boolean = false,
    val pressFeedback: Boolean = false,
)

/** The ring of a focused card whose art gives [art] and shows [edge] at the card's edge. */
internal fun LibraryLayout.ringColour(
    art: Color,
    edge: Color,
): Color = focusRingColour(art, coloured = appGlow, whiteFocus = whiteFocus, tile = edge)

public fun TvConfig.libraryLayout(): LibraryLayout =
    LibraryLayout(
        selection = appLibrarySelection,
        okOpens = libraryOkOpens && !appLibrarySelection,
        appGlow = appGlow,
        whiteFocus = cinematicWhiteFocus,
        animated = animations && !reducedMotion,
        leastUsed = libraryLeastUsed,
        pressFeedback = pressFeedback,
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
    /** When Home last opened each app, read off the main thread; only the least used order asks. */
    public val opened: suspend () -> OpenedRecord = { OpenedRecord() },
    /** Whether the TV is set to the 24-hour clock, for the time on a card. */
    public val hour24: () -> Boolean = { true },
)

/** One card: an app, whether it is on Home, whether it is a recent install and, in the least used order, when it was opened. */
@Immutable
public data class LibraryCard(
    val app: AppEntry,
    val onHome: Boolean,
    val recent: Boolean,
    val opened: CardOpened? = null,
)

/** When Home last opened a card's app, [at] null for an app it never opened. */
@Immutable
public data class CardOpened(
    val at: OpenedAt?,
)

/** The order of the cards: the usual one, or with library_least_used the least used first. */
public enum class LibraryOrder {
    Usual,
    LeastUsed,
}

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
 * which is when the panel appears. [cards] are in the library's [order]; [focusedKey] is the card with focus.
 * [noUsageYet] is true in the least used order while Home has no open time for any app of the catalogue; no card has an
 * opened line then. [launching] is true while an app a card started is opening (press_feedback): no card takes OK then.
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
    val order: LibraryOrder = LibraryOrder.Usual,
    val noUsageYet: Boolean = false,
    val launching: Boolean = false,
)

/** One-shot results the host handles outside the UI state. */
public sealed interface LibraryEffect {
    /** Open the app. The library has closed, or with press_feedback stays open until Home stops. */
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

    /** The MENU key: the other order. */
    data object ToggleOrder : LibraryEvent

    /** The Home key was pressed, the Inputs list was asked for, or app_library turned off. */
    data object Close : LibraryEvent

    /** Home left the screen: the library closes, and an app it started has opened. */
    data object Stopped : LibraryEvent

    /** The host started no app for the card [key]. */
    data class AppNotStarted(
        val key: String,
    ) : LibraryEvent
}
