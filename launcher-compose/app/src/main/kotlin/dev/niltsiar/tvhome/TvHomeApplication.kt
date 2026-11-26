package dev.niltsiar.tvhome

import android.app.Application
import android.content.Intent
import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.ConfigRepositoryOwner
import dev.niltsiar.tvhome.core.model.AppOpened
import dev.niltsiar.tvhome.core.model.HouseSeedOwner
import dev.niltsiar.tvhome.data.wallpaper.houseSeed
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.time.ZoneId

class TvHomeApplication :
    Application(),
    ConfigRepositoryOwner,
    HouseSeedOwner,
    AppOpened {
    lateinit var graph: AppGraph
        private set

    override val configRepository: ConfigRepository get() = graph.configRepository

    override val houseSeed: StateFlow<Color> get() = graph.houseSeed

    override fun houseSeedNow(): Color =
        houseSeed(graph.configRepository.config.value, graph.wallpaperProvider.hue.value, LocalDateTime.now(), ZoneId.systemDefault())

    override fun opened(intent: Intent) = graph.appLauncher.opened(intent)

    override fun onCreate() {
        super.onCreate()
        graph = createGraphFactory<AppGraph.Factory>().create(this)
    }
}
