package dev.niltsiar.tvhome

import android.app.Application
import android.text.format.DateFormat
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.data.apps.AppLauncher
import dev.niltsiar.tvhome.data.apps.PackageManagerAppsProvider
import dev.niltsiar.tvhome.data.system.NetworkMonitor
import dev.niltsiar.tvhome.data.system.SystemSettings
import dev.niltsiar.tvhome.data.system.TimeChanges
import dev.niltsiar.tvhome.feature.home.HomeSources
import dev.niltsiar.tvhome.feature.home.minuteClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@DependencyGraph(AppScope::class)
interface AppGraph {
    val config: TvConfig
    val appsProvider: PackageManagerAppsProvider
    val appLauncher: AppLauncher
    val networkMonitor: NetworkMonitor
    val timeChanges: TimeChanges
    val systemSettings: SystemSettings
    val homeSources: HomeSources

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

    @Provides
    @SingleIn(AppScope::class)
    fun provideNetworkMonitor(application: Application): NetworkMonitor = NetworkMonitor(application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTimeChanges(application: Application): TimeChanges = TimeChanges(application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSystemSettings(
        application: Application,
        config: TvConfig,
    ): SystemSettings =
        SystemSettings(
            application,
            application.resources.getStringArray(R.array.settings_fallback_components).toList(),
            config.vpnApp,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideHomeSources(
        application: Application,
        appsProvider: PackageManagerAppsProvider,
        networkMonitor: NetworkMonitor,
        timeChanges: TimeChanges,
    ): HomeSources =
        HomeSources(
            apps = appsProvider.apps,
            clock = minuteClock(timeChanges.changes),
            network = networkMonitor.state,
            topBarApps = appsProvider.topBarApps,
            datePattern = application.getString(R.string.date_format),
            systemHour24 = { DateFormat.is24HourFormat(application) },
        )

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
        ): AppGraph
    }
}
