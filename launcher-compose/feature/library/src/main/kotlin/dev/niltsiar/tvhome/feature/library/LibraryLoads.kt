package dev.niltsiar.tvhome.feature.library

/**
 * Which catalogue load fills the library, and when the panel appears. Opening starts a load and so does a package
 * change while the library is open or still loading; only the latest load fills the catalogue, so the reveal an
 * opening asked for belongs to whichever load ends up the latest, not to the one the opening started.
 */
internal class LibraryLoads {
    private var latest = 0
    private var revealPending = false

    /** Starts a load, from opening the library when [reveal] is true. Returns its number. */
    fun start(reveal: Boolean): Int {
        if (reveal) revealPending = true
        return ++latest
    }

    /** Whether a finished load is the latest one, the only one that fills the catalogue. */
    fun isLatest(load: Int): Boolean = load == latest

    /** Once the latest load has filled the catalogue: whether the panel still has to appear. True once per opening. */
    fun takeReveal(): Boolean = revealPending.also { revealPending = false }
}
