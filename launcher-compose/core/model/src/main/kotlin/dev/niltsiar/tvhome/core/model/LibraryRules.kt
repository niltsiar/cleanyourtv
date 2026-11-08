package dev.niltsiar.tvhome.core.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import java.text.Collator

private const val RECENT_MS = 7L * 24 * 60 * 60 * 1000

/**
 * One app of the library's catalogue. [app] has no art: the library asks for that when a card shows. [onHome] is
 * whether the app has a tile on Home, [installedAt] its first install time in milliseconds (0 when unknown).
 */
@Immutable
public data class LibraryApp(
    val app: AppEntry,
    val onHome: Boolean,
    val installedAt: Long = 0,
)

/**
 * The art of a library card: a banner or an icon on a tile, null for an app without art, its vivid [accent] colour and
 * the [edge] colour a banner shows at the card's edge (unspecified for an icon).
 */
@Immutable
public data class LibraryArt(
    val image: ImageBitmap?,
    val accent: Color,
    val edge: Color = Color.Unspecified,
)

/**
 * The App library pill after the last Home row. show_library_pill false hides it only while the top-bar button is
 * a way in.
 */
public fun showLibraryPill(
    library: Boolean,
    button: Boolean,
    pill: Boolean,
): Boolean = library && (pill || !button)

/**
 * Whether OK on a library card opens the app (true) or changes its Home shortcut. In selection mode OK always changes
 * it; otherwise an app on Home opens, and with library_ok_opens every app does.
 */
public fun okOpens(
    selection: Boolean,
    openAll: Boolean,
    onHome: Boolean,
): Boolean = !selection && (openAll || onHome)

/** First installed within the last seven days, and not at an unknown or future time. */
public fun isRecent(
    installedAt: Long,
    now: Long,
): Boolean = installedAt > 0 && installedAt <= now && now - installedAt <= RECENT_MS

/** Missing shortcuts first; recent installs lead that group, the newest first. The caller sorts ties by label. */
internal fun comparePriority(
    a: LibraryApp,
    b: LibraryApp,
    now: Long,
): Int {
    if (a.onHome != b.onHome) return if (a.onHome) 1 else -1
    if (a.onHome) return 0
    val recentA = isRecent(a.installedAt, now)
    val recentB = isRecent(b.installedAt, now)
    if (recentA != recentB) return if (recentA) -1 else 1
    return if (recentA) b.installedAt.compareTo(a.installedAt) else 0
}

/** Label, then package. */
private fun compareByLabel(
    a: LibraryApp,
    b: LibraryApp,
    collator: Collator,
): Int = collator.compare(a.app.label, b.app.label).takeIf { it != 0 } ?: a.app.packageName.compareTo(b.app.packageName)

/**
 * The catalogue in the order the library shows it. Normal mode: apps not on Home first, recent installs leading that
 * group, then by label. Selection mode: by label alone, so a changed shortcut moves no card.
 */
public fun sortLibrary(
    apps: List<LibraryApp>,
    selection: Boolean,
    now: Long,
    collator: Collator,
): List<LibraryApp> =
    apps.sortedWith { a, b ->
        val priority = if (selection) 0 else comparePriority(a, b, now)
        priority.takeIf { it != 0 } ?: compareByLabel(a, b, collator)
    }

/** After adding a shortcut, prefer another missing app; otherwise stay by a neighbour. -1 when there is no app. */
public fun nextFocus(
    current: Int,
    count: Int,
    missing: (Int) -> Boolean,
): Int {
    if (count <= 0) return -1
    val from = current.coerceIn(0, count - 1)
    for (step in 1 until count) {
        val candidate = (from + step) % count
        if (missing(candidate)) return candidate
    }
    return if (from + 1 < count) from + 1 else maxOf(0, from - 1)
}
