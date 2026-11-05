package dev.niltsiar.tvhome

import android.app.Application
import android.text.format.DateFormat
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.data.apps.AppLauncher
import dev.niltsiar.tvhome.data.apps.LastOpened
import dev.niltsiar.tvhome.data.apps.LibraryCatalog
import dev.niltsiar.tvhome.data.apps.PackageManagerAppsProvider
import dev.niltsiar.tvhome.data.inputs.InputLauncher
import dev.niltsiar.tvhome.data.inputs.InputsProvider
import dev.niltsiar.tvhome.data.inputs.OpenOnStart
import dev.niltsiar.tvhome.data.system.MinuteClock
import dev.niltsiar.tvhome.data.system.NetworkMonitor
import dev.niltsiar.tvhome.data.system.SystemSettings
import dev.niltsiar.tvhome.data.system.TimeChanges
import dev.niltsiar.tvhome.data.wallpaper.ScreensaverWallpaper
import dev.niltsiar.tvhome.data.wallpaper.WallpaperProvider
import dev.niltsiar.tvhome.feature.home.HomeSources
import dev.niltsiar.tvhome.feature.library.LibrarySources
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map

@DependencyGraph(AppScope::class)
interface AppGraph {
    val config: TvConfig
    val appsProvider: PackageManagerAppsProvider
    val appLauncher: AppLauncher
    val networkMonitor: NetworkMonitor
    val timeChanges: TimeChanges
    val minuteClock: MinuteClock
    val systemSettings: SystemSettings
    val wallpaperProvider: WallpaperProvider
    val screensaverWallpaper: ScreensaverWallpaper
    val homeSources: HomeSources
    val librarySources: LibrarySources
    val inputsProvider: InputsProvider
    val inputLauncher: InputLauncher
    val openOnStart: OpenOnStart

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
        lastOpened: LastOpened,
    ): AppLauncher = AppLauncher(application, config.launchActions, lastOpened, config.cinematicHome)

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
    fun provideMinuteClock(timeChanges: TimeChanges): MinuteClock =
        MinuteClock(timeChanges.changes, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @Provides
    @SingleIn(AppScope::class)
    fun provideInputsProvider(
        application: Application,
        config: TvConfig,
    ): InputsProvider = InputsProvider(application, config)

    @Provides
    @SingleIn(AppScope::class)
    fun provideInputLauncher(
        application: Application,
        config: TvConfig,
    ): InputLauncher = InputLauncher(application, config)

    @Provides
    @SingleIn(AppScope::class)
    fun provideOpenOnStart(
        application: Application,
        config: TvConfig,
        networkMonitor: NetworkMonitor,
    ): OpenOnStart = OpenOnStart(application, config, networkMonitor.state.map { it.validated })

    @Provides
    @SingleIn(AppScope::class)
    fun provideSystemSettings(
        application: Application,
        config: TvConfig,
    ): SystemSettings = SystemSettings(application, config.vpnApp)

    @Provides
    @SingleIn(AppScope::class)
    fun provideWallpaperProvider(
        application: Application,
        config: TvConfig,
    ): WallpaperProvider = WallpaperProvider(application, config, ThemeDirectory(application))

    @Provides
    @SingleIn(AppScope::class)
    fun provideScreensaverWallpaper(
        application: Application,
        config: TvConfig,
    ): ScreensaverWallpaper = ScreensaverWallpaper(application, config, ThemeDirectory(application))

    @Provides
    @SingleIn(AppScope::class)
    fun provideHomeSources(
        application: Application,
        appsProvider: PackageManagerAppsProvider,
        lastOpened: LastOpened,
        networkMonitor: NetworkMonitor,
        minuteClock: MinuteClock,
        wallpaperProvider: WallpaperProvider,
        systemSettings: SystemSettings,
        libraryCatalog: LibraryCatalog,
        inputsProvider: InputsProvider,
    ): HomeSources =
        HomeSources(
            apps = appsProvider.apps,
            clock = minuteClock.now,
            network = networkMonitor.state,
            topBarApps = appsProvider.topBarApps,
            wallpaper = wallpaperProvider.wallpaper(minuteClock.now),
            systemHour24 = { DateFormat.is24HourFormat(application) },
            lastOpened = lastOpened.times,
            heroArt = appsProvider::heroArt,
            hasAppInfo = systemSettings::hasAppInfo,
            hasNativeSettings = systemSettings::hasNativeSettings,
            saveOrder = appsProvider::saveOrder,
            removeFromHome = { libraryCatalog.setOnHome(it, false) },
            inputs = inputsProvider.menu,
        )

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
