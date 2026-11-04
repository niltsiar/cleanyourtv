package dev.niltsiar.tvhome

import android.app.Application
import android.text.format.DateFormat
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.data.apps.AppLauncher
import dev.niltsiar.tvhome.data.apps.LastOpened
import dev.niltsiar.tvhome.data.apps.LibraryCatalog
import dev.niltsiar.tvhome.data.apps.PackageManagerAppsProvider
import dev.niltsiar.tvhome.data.system.NetworkMonitor
import dev.niltsiar.tvhome.data.system.SystemSettings
import dev.niltsiar.tvhome.data.system.TimeChanges
import dev.niltsiar.tvhome.data.wallpaper.WallpaperProvider
import dev.niltsiar.tvhome.feature.home.HomeSources
import dev.niltsiar.tvhome.feature.home.minuteClock
import dev.niltsiar.tvhome.feature.library.LibrarySources
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@DependencyGraph(AppScope::class)
interface AppGraph {
    val config: TvConfig
    val appsProvider: PackageManagerAppsProvider
    val appLauncher: AppLauncher
    val lastOpened: LastOpened
    val networkMonitor: NetworkMonitor
    val timeChanges: TimeChanges
    val systemSettings: SystemSettings
    val wallpaperProvider: WallpaperProvider
    val homeSources: HomeSources
    val librarySources: LibrarySources

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
    fun provideLibraryCatalog(
        application: Application,
        config: TvConfig,
        appsProvider: PackageManagerAppsProvider,
    ): LibraryCatalog = LibraryCatalog(application, config, appsProvider)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppLauncher(
        application: Application,
        config: TvConfig,
    ): AppLauncher = AppLauncher(application, config.launchActions)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLastOpened(application: Application): LastOpened = LastOpened(application)

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
    fun provideWallpaperProvider(
        application: Application,
        config: TvConfig,
    ): WallpaperProvider = WallpaperProvider(application, config, ThemeDirectory(application))

    @Provides
    @SingleIn(AppScope::class)
    fun provideHomeSources(
        application: Application,
        appsProvider: PackageManagerAppsProvider,
        lastOpened: LastOpened,
        networkMonitor: NetworkMonitor,
        timeChanges: TimeChanges,
        wallpaperProvider: WallpaperProvider,
        systemSettings: SystemSettings,
        libraryCatalog: LibraryCatalog,
    ): HomeSources {
        val clock = minuteClock(timeChanges.changes)
        return HomeSources(
            apps = appsProvider.apps,
            clock = clock,
            network = networkMonitor.state,
            topBarApps = appsProvider.topBarApps,
            wallpaper = wallpaperProvider.wallpaper(clock),
            datePattern = application.getString(R.string.date_format),
            systemHour24 = { DateFormat.is24HourFormat(application) },
            lastOpened = lastOpened.times,
            heroArt = appsProvider::heroArt,
            hasAppInfo = systemSettings::hasAppInfo,
            hasNativeSettings = systemSettings::hasNativeSettings,
            saveOrder = appsProvider::saveOrder,
            removeFromHome = { libraryCatalog.setOnHome(it, false) },
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideLibrarySources(
        libraryCatalog: LibraryCatalog,
        systemSettings: SystemSettings,
    ): LibrarySources =
        LibrarySources(
            load = libraryCatalog::load,
            changes = libraryCatalog.changes,
            art = libraryCatalog::art,
            setOnHome = libraryCatalog::setOnHome,
            hasAppInfo = systemSettings::hasAppInfo,
            release = libraryCatalog::release,
        )

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
        ): AppGraph
    }
}
