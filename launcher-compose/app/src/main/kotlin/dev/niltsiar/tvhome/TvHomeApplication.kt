package dev.niltsiar.tvhome

import android.app.Application
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.ConfigRepositoryOwner
import dev.zacsweers.metro.createGraphFactory

class TvHomeApplication :
    Application(),
    ConfigRepositoryOwner {
    lateinit var graph: AppGraph
        private set

    override val configRepository: ConfigRepository get() = graph.configRepository

    override fun onCreate() {
        super.onCreate()
        graph = createGraphFactory<AppGraph.Factory>().create(this)
    }
}
