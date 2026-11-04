package dev.niltsiar.tvhome

import android.app.Application
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.data.apps.AppLauncher
import dev.niltsiar.tvhome.data.apps.PackageManagerAppsProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@DependencyGraph(AppScope::class)
interface AppGraph {
    val config: TvConfig
    val appsProvider: PackageManagerAppsProvider
    val appLauncher: AppLauncher

    /** Read once per process: a pushed theme restarts Home, as in the reference launcher. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideConfig(application: Application): TvConfig = ThemeDirectory(application).loadConfig().config

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppsProvider(
        application: Application,
        config: TvConfig,
    ): PackageManagerAppsProvider = PackageManagerAppsProvider(application, config)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppLauncher(
        application: Application,
        config: TvConfig,
    ): AppLauncher = AppLauncher(application, config.launchActions)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
        ): AppGraph
    }
}
