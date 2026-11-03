package dev.niltsiar.tvhome

import dev.niltsiar.tvhome.core.model.AppEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

interface AppsProvider {
    fun apps(): List<AppEntry>
}

/** Hard-coded list for the skeleton; the real package scan replaces it. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PlaceholderAppsProvider : AppsProvider {
    override fun apps(): List<AppEntry> =
        listOf(
            "Movies",
            "Music",
            "Games",
            "Photos",
            "Weather",
            "Browser",
            "Files",
            "Radio",
        ).map { AppEntry(packageName = "example.${it.lowercase()}", label = it) }
}
