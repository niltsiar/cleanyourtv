package dev.niltsiar.tvhome

import android.app.Application
import android.os.UserManager
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.ConfigRepositoryOwner
import dev.zacsweers.metro.createGraphFactory
import kotlin.concurrent.thread

class TvHomeApplication :
    Application(),
    ConfigRepositoryOwner {
    lateinit var graph: AppGraph
        private set

    override val configRepository: ConfigRepository get() = graph.configRepository

    override fun onCreate() {
        super.onCreate()
        graph = createGraphFactory<AppGraph.Factory>().create(this)
        // The store's file work overlaps with the activity's creation; the first read of the repository waits for it. Not
        // while locked: the key service then builds the repository itself, after the unlock, so its first config has the choices.
        if (getSystemService(UserManager::class.java).isUserUnlocked) thread(name = "ConfigPrewarm") { graph.configRepository }
    }
}
