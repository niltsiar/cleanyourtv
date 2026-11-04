package dev.niltsiar.tvhome.feature.library

internal const val STAGGER_STEP_MS = 45
internal const val STAGGER_WINDOW_MS = 700L
private const val MAX_STAGGERED = 8

/**
 * Which cards still have to enter in one opening. A card enters once: when the cards load again, or a card scrolls out
 * of view and back, it is not shown entering a second time. While the opening's [staggering] window lasts each card
 * waits for the ones before it; a card first built later enters at once.
 */
internal class CardEntrance {
    private val entered = HashSet<String>()

    /** True while the cards of the opening are still coming in one after another. */
    var staggering = true

    /** The milliseconds the card with [key], [index]th in the row, waits before it enters; null when it entered already. */
    fun delayFor(
        key: String,
        index: Int,
    ): Int? {
        if (!entered.add(key)) return null
        return if (staggering) minOf(index, MAX_STAGGERED) * STAGGER_STEP_MS else 0
    }
}
