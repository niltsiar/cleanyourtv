package dev.niltsiar.tvhome

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph

@DependencyGraph(AppScope::class)
interface AppGraph {
    val appsProvider: AppsProvider
}
