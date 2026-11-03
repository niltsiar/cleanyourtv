package dev.niltsiar.tvhome

import android.app.Application
import dev.zacsweers.metro.createGraphFactory

class TvHomeApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createGraphFactory<AppGraph.Factory>().create(this)
    }
}
