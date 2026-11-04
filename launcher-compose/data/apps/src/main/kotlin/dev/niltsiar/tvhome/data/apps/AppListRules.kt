package dev.niltsiar.tvhome.data.apps

import dev.niltsiar.tvhome.core.config.TvConfig

private const val MAX_LABEL_LENGTH = 40

/** A launcher activity the system reported. [label] is the app's own, before overrides. */
internal data class Candidate(
    val key: String,
    val packageName: String,
    val label: String,
    val exported: Boolean,
)

/** A [candidate] that Home shows, under the name its tile carries. */
internal data class Tile(
    val candidate: Candidate,
    val label: String,
)

/** The tiles in Home's order and how many leading ones are dock apps. */
internal data class Arrangement(
    val tiles: List<Tile>,
    val docked: Int,
)

/**
 * Picks and orders the tiles. [candidates] come in query order, TV launcher activities first.
 * A tile needs an exported activity in another package, and is shown when the package is a TV app that is not
 * hidden, or was added through library_apps. Two tiles with one package and one name are one tile.
 * Order: the saved order, then dock_apps, then name.
 */
internal fun arrange(
    candidates: List<Candidate>,
    tvPackages: Set<String>,
    config: TvConfig,
    order: Order?,
    ownPackage: String,
): Arrangement {
    val hidden = config.hiddenApps.toSet()
    val added = config.libraryApps.toSet()
    val seen = HashSet<String>()
    val tiles =
        candidates.mapNotNull { candidate ->
            val pkg = candidate.packageName
            val shown = pkg in added || (pkg in tvPackages && pkg !in hidden)
            if (pkg == ownPackage || !candidate.exported || !shown) return@mapNotNull null
            val label = cleanLabel(config.appLabels[pkg] ?: candidate.label, pkg)
            if (seen.add("$pkg\n$label")) Tile(candidate, label) else null
        }

    val ranks = order?.ranks(config.dockApps).orEmpty()
    val sorted =
        tiles.sortedWith { a, b ->
            val rankA = ranks[a.candidate.key]
            val rankB = ranks[b.candidate.key]
            val dockA = config.dockApps.indexOf(a.candidate.packageName)
            val dockB = config.dockApps.indexOf(b.candidate.packageName)
            when {
                rankA != null && rankB != null -> rankA.compareTo(rankB)
                rankA != null -> -1
                rankB != null -> 1
                dockA >= 0 && dockB >= 0 -> dockA.compareTo(dockB)
                dockA >= 0 -> -1
                dockB >= 0 -> 1
                else -> a.label.compareTo(b.label, ignoreCase = true)
            }
        }

    val savedDocked = order?.dockedFor(config.dockApps) ?: -1
    val docked =
        sorted
            .takeWhile { tile ->
                if (savedDocked < 0) {
                    tile.candidate.packageName in config.dockApps
                } else {
                    (ranks[tile.candidate.key] ?: Int.MAX_VALUE) < savedDocked
                }
            }.size
    return Arrangement(sorted, docked)
}

/**
 * A tile name an app cannot use to draw something else (a bidi override, a line break): whitespace and format
 * characters become single spaces, the ends are trimmed, at most 40 characters (never half a surrogate pair), and
 * [fallback] when nothing is left.
 */
internal fun cleanLabel(
    label: String,
    fallback: String,
): String {
    val out = StringBuilder()
    var space = true
    var index = 0
    while (index < label.length) {
        val c = label[index]
        val blank =
            c == ' ' ||
                c.isISOControl() ||
                c.isWhitespace() ||
                c.category in BLANK_CATEGORIES
        if (blank) {
            if (!space) out.append(' ')
            space = true
            index++
            continue
        }
        val pair = c.isHighSurrogate() && index + 1 < label.length && label[index + 1].isLowSurrogate()
        if (out.length + (if (pair) 2 else 1) > MAX_LABEL_LENGTH) break
        out.append(c)
        if (pair) out.append(label[++index])
        space = false
        index++
    }
    return out.toString().trim().ifEmpty { fallback }
}

private val BLANK_CATEGORIES =
    setOf(CharCategory.FORMAT, CharCategory.LINE_SEPARATOR, CharCategory.PARAGRAPH_SEPARATOR, CharCategory.SPACE_SEPARATOR)
