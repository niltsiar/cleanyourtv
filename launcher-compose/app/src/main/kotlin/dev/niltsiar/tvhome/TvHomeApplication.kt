package dev.niltsiar.tvhome

import android.app.Application
import android.content.Intent
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.ConfigRepositoryOwner
import dev.niltsiar.tvhome.core.model.AppOpened
import dev.zacsweers.metro.createGraphFactory

class TvHomeApplication :
    Application(),
    ConfigRepositoryOwner,
    AppOpened {
    lateinit var graph: AppGraph
        private set

    override val configRepository: ConfigRepository get() = graph.configRepository

    override fun opened(intent: Intent) = graph.appLauncher.opened(intent)

    override fun onCreate() {
        super.onCreate()
        graph = createGraphFactory<AppGraph.Factory>().create(this)
    }
}
