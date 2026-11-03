package dev.niltsiar.tvhome

import android.app.Application
import dev.niltsiar.tvhome.data.apps.PackageManagerAppsProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

private const val TILE_COLUMNS = 6

@DependencyGraph(AppScope::class)
interface AppGraph {
    val appsProvider: PackageManagerAppsProvider

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppsProvider(application: Application): PackageManagerAppsProvider {
        // A 16:9 tile a little wider than one of six columns, so banners stay sharp after the focus scale.
        val width = application.resources.displayMetrics.widthPixels / TILE_COLUMNS
        return PackageManagerAppsProvider(application, tileWidthPx = width, tileHeightPx = width * 9 / 16)
    }

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
        ): AppGraph
    }
}
