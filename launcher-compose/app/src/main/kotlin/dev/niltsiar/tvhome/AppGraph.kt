package dev.niltsiar.tvhome

import android.app.Application
import android.text.format.DateFormat
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.data.apps.AppLauncher
import dev.niltsiar.tvhome.data.apps.LastOpened
import dev.niltsiar.tvhome.data.apps.LaunchLog
import dev.niltsiar.tvhome.data.apps.LibraryCatalog
import dev.niltsiar.tvhome.data.apps.PackageManagerAppsProvider
import dev.niltsiar.tvhome.data.inputs.InputLauncher
import dev.niltsiar.tvhome.data.inputs.InputsProvider
import dev.niltsiar.tvhome.data.inputs.OpenOnStart
import dev.niltsiar.tvhome.data.system.MinuteClock
import dev.niltsiar.tvhome.data.system.NetworkMonitor
import dev.niltsiar.tvhome.data.system.SystemSettings
import dev.niltsiar.tvhome.data.system.TimeChanges
import dev.niltsiar.tvhome.data.system.displayOn
import dev.niltsiar.tvhome.data.wallpaper.ScreensaverWallpaper
import dev.niltsiar.tvhome.data.wallpaper.WallpaperPictures
import dev.niltsiar.tvhome.data.wallpaper.WallpaperProvider
import dev.niltsiar.tvhome.feature.home.FocusMemory
import dev.niltsiar.tvhome.feature.home.HomeSources
import dev.niltsiar.tvhome.feature.library.LibrarySources
import dev.niltsiar.tvhome.feature.settings.PickerArt
import dev.niltsiar.tvhome.feature.settings.SettingsSources
import dev.niltsiar.tvhome.service.keys.PowerMenu
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import java.io.File

@DependencyGraph(AppScope::class)
interface AppGraph {
    val configRepository: ConfigRepository
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
    val settingsSources: SettingsSources

    @Provides
    @SingleIn(AppScope::class)
    fun provideConfigRepository(application: Application): ConfigRepository =
        ConfigRepository(application, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppsProvider(
        application: Application,
        repository: ConfigRepository,
    ): PackageManagerAppsProvider = PackageManagerAppsProvider(application, repository.config)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLibraryCatalog(
        application: Application,
        repository: ConfigRepository,
        appsProvider: PackageManagerAppsProvider,
    ): LibraryCatalog = LibraryCatalog(application, repository.config, appsProvider)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppLauncher(
        application: Application,
        repository: ConfigRepository,
        lastOpened: LastOpened,
        launchLog: LaunchLog,
    ): AppLauncher = AppLauncher(application, repository.config, lastOpened, launchLog)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLaunchLog(
        application: Application,
        repository: ConfigRepository,
    ): LaunchLog = LaunchLog(File(application.filesDir, "launch_log.txt"), repository.config)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLastOpened(
        application: Application,
        repository: ConfigRepository,
    ): LastOpened = LastOpened(application, repository.config)

    @Provides
    @SingleIn(AppScope::class)
    fun provideNetworkMonitor(application: Application): NetworkMonitor = NetworkMonitor(application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTimeChanges(application: Application): TimeChanges = TimeChanges(application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMinuteClock(
        application: Application,
        timeChanges: TimeChanges,
    ): MinuteClock = MinuteClock(timeChanges.changes, displayOn(application), CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @Provides
    @SingleIn(AppScope::class)
    fun provideInputsProvider(
        application: Application,
        repository: ConfigRepository,
    ): InputsProvider = InputsProvider(application, repository.config)

    @Provides
    @SingleIn(AppScope::class)
    fun provideInputLauncher(
        application: Application,
        repository: ConfigRepository,
    ): InputLauncher = InputLauncher(application, repository.config)

    @Provides
    @SingleIn(AppScope::class)
    fun provideOpenOnStart(
        application: Application,
        repository: ConfigRepository,
        networkMonitor: NetworkMonitor,
    ): OpenOnStart = OpenOnStart(application, repository.config, repository.openOnStartApp, networkMonitor.state.map { it.validated })

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettingsSources(
        application: Application,
        openOnStart: OpenOnStart,
        appsProvider: PackageManagerAppsProvider,
        wallpaperProvider: WallpaperProvider,
    ): SettingsSources {
        val pictures = WallpaperPictures(ThemeDirectory(application))
        return SettingsSources(
            openOnStart::apps,
            openOnStart::skipThisBoot,
            pictures::pictures,
            PickerArt(pictures::thumbnail, appsProvider::art),
            { wallpaperProvider.themeDefault() },
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSystemSettings(
        application: Application,
        repository: ConfigRepository,
        appLauncher: AppLauncher,
    ): SystemSettings = SystemSettings(application, appLauncher) { repository.config.value.vpnApp }

    @Provides
    @SingleIn(AppScope::class)
    fun provideWallpaperProvider(
        application: Application,
        repository: ConfigRepository,
    ): WallpaperProvider = WallpaperProvider(application, repository.config, repository.wallpaperChoice, ThemeDirectory(application))

    @Provides
    @SingleIn(AppScope::class)
    fun provideScreensaverWallpaper(
        application: Application,
        repository: ConfigRepository,
    ): ScreensaverWallpaper = ScreensaverWallpaper(application, repository.config, repository.wallpaperChoice, ThemeDirectory(application))

    /** Lives as long as the process, so an activity created again returns to the tile it left. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideFocusMemory(): FocusMemory = FocusMemory()

    @Provides
    @SingleIn(AppScope::class)
    fun provideHomeSources(
        application: Application,
        appsProvider: PackageManagerAppsProvider,
        lastOpened: LastOpened,
        launchLog: LaunchLog,
        networkMonitor: NetworkMonitor,
        minuteClock: MinuteClock,
        wallpaperProvider: WallpaperProvider,
        systemSettings: SystemSettings,
        libraryCatalog: LibraryCatalog,
        inputsProvider: InputsProvider,
        focusMemory: FocusMemory,
    ): HomeSources =
        HomeSources(
            apps = appsProvider.apps,
            clock = minuteClock.now,
            network = networkMonitor.state,
            topBarApps = appsProvider.topBarApps,
            wallpaper = wallpaperProvider.wallpaper(minuteClock.now),
            systemHour24 = { DateFormat.is24HourFormat(application) },
            lastOpened = lastOpened.times,
            openedSince = lastOpened.since,
            habit = { launchLog.habit(System.currentTimeMillis()) },
            heroArt = appsProvider::heroArt,
            hasAppInfo = systemSettings::hasAppInfo,
            hasNativeSettings = systemSettings::hasNativeSettings,
            powerMenuAvailable = PowerMenu::available,
            saveOrder = appsProvider::saveOrder,
            removeFromHome = { libraryCatalog.setOnHome(it, false) },
            inputs = inputsProvider.menu,
            focusMemory = focusMemory,
            libraryCount = libraryCatalog.counts,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideLibrarySources(
        application: Application,
        libraryCatalog: LibraryCatalog,
        systemSettings: SystemSettings,
        lastOpened: LastOpened,
    ): LibrarySources =
        LibrarySources(
            load = libraryCatalog::load,
            changes = libraryCatalog.changes,
            art = libraryCatalog::art,
            setOnHome = libraryCatalog::setOnHome,
            hasAppInfo = systemSettings::hasAppInfo,
            release = libraryCatalog::release,
            opened = lastOpened::snapshot,
            hour24 = { DateFormat.is24HourFormat(application) },
        )

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
        ): AppGraph
    }
}
