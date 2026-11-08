package dev.niltsiar.tvhome

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.startActivitySafely
import dev.niltsiar.tvhome.data.apps.LaunchResult
import dev.niltsiar.tvhome.feature.library.MoleculeLibraryViewModel
import dev.niltsiar.tvhome.service.keys.PowerMenu
import dev.niltsiar.tvhome.service.keys.ServiceRepair
import dev.niltsiar.tvhome.service.keys.VendorKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import dev.niltsiar.tvhome.feature.home.R as FeatureR

class MainActivity : ComponentActivity() {
    private val graph get() = (application as TvHomeApplication).graph
    private var homePresses by mutableIntStateOf(0)
    private var inputsRequests by mutableIntStateOf(0)
    private var bootOpening: Job? = null

    /**
     * Between onResume and onPause: Home is in front. The lifecycle state is not enough, it only reaches RESUMED after
     * onResume returns. The idle clock and the vendor keys run only then: a key's UP can still reach Home after onPause,
     * when that key opened another app.
     */
    private var resumed = false
    private val vendorKeys by lazy { VendorKeys(this, graph.config, graph.systemSettings, ::openSettingsFromKey) }
    private val idleClock by lazy {
        IdleClock(TimeUnit.MINUTES.toMillis(graph.config.homeIdleClockMinutes.toLong()), { resumed }, ::showClock)
    }
    private val library: MoleculeLibraryViewModel by viewModels {
        viewModelFactory {
            initializer {
                MoleculeLibraryViewModel(
                    graph.librarySources,
                    graph.config,
                    resources.configuration.locales[0],
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is Home: Back has nowhere to go, unless back_opens_clock.
        onBackPressedDispatcher.addCallback(this) { if (graph.config.backOpensClock) showClock() }
        // Not after a recreate: the launch intent survives it and would open the library or the list again.
        if (savedInstanceState == null) homeRequestOf(intent).takeIf { it != HomeRequest.HomeKey }?.let(::handle)
        applyViewInputsHandler(graph.config.handleViewInputs)
        if (graph.config.restoreKeyServices) ServiceRepair.restore(this)
        setContent {
            TvTheme(motion = graph.config.motion(), focusStyle = graph.config.focusStyle()) {
                val glass = rememberGlassState()
                val libraryOpen = if (graph.config.appLibrary) library.uiState.collectAsStateWithLifecycle() else null
                HomeHost(
                    graph = graph,
                    onLaunchApp = ::launchApp,
                    onOpenSettings = ::openSettings,
                    onOpenNetwork = ::openNetwork,
                    onOpenWifiSettings = ::openWifiSettings,
                    onOpenAppInfo = ::openAppInfo,
                    onOpenNativeSettings = ::openNativeSettings,
                    onOpenPowerMenu = PowerMenu::open,
                    onOrderNotSaved = ::orderNotSaved,
                    onLibrarySaveFailed = ::librarySaveFailed,
                    onOpenInput = ::openInput,
                    onNoInputs = { toast(R.string.no_inputs) },
                    glass = glass,
                    libraryOpen = { libraryOpen?.value?.open == true },
                    onOpenLibrary = { library.onOpen() },
                    homePresses = { homePresses },
                    inputsRequests = { inputsRequests },
                    overlays = {
                        if (graph.config.appLibrary) {
                            LibraryHost(
                                graph = graph,
                                viewModel = library,
                                onLaunchApp = ::launchLibraryApp,
                                onOpenAppInfo = ::openAppInfo,
                                onSaveFailed = ::librarySaveFailed,
                            )
                        }
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        graph.appsProvider.onStart()
        graph.inputsProvider.onStart()
        graph.networkMonitor.onStart()
        graph.timeChanges.onStart()
        graph.wallpaperProvider.onStart()
    }

    override fun onStop() {
        super.onStop()
        graph.appsProvider.onStop()
        graph.inputsProvider.onStop()
        graph.networkMonitor.onStop()
        graph.timeChanges.onStop()
        graph.wallpaperProvider.onStop()
    }

    /** Another app needs the memory while Home is stopped: drop the artwork and the picture, which the next start loads again. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!shouldReleaseOnTrim(level, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))) return
        graph.appsProvider.trimImages()
        graph.wallpaperProvider.releasePicture()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        bootOpening = lifecycleScope.launch { graph.openOnStart.openAfterBoot(::openIntent) }
        idleClock.restart()
    }

    /** Any key or touch on Home restarts the countdown to the idle clock. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        idleClock.restart()
    }

    /**
     * Vendor keys (TCL's Netflix button, 4062) never reach the key service, only the focused window, so their app_keys
     * entries run here too or nowhere; so does a Settings button the service is not there to take. Only while resumed:
     * onPause releases Home's holds, and a DOWN taken after that would arm one nothing releases. Channel Up and Down with
     * home_ignore_channel_keys go first and need no resume: Home only has to be the window that receives them.
     */
    @SuppressLint("RestrictedApi") // androidx marks the override restricted, but it is the only hook that sees every key first
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        isIgnoredChannelKey(graph.config.homeIgnoreChannelKeys, event.keyCode) ||
            (resumed && vendorKeys.handle(event)) ||
            super.dispatchKeyEvent(event)

    /** Leaving Home before the network came back keeps the boot's app closed; its boot is already written. */
    override fun onPause() {
        super.onPause()
        resumed = false
        bootOpening?.cancel()
        vendorKeys.release()
        idleClock.cancel()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(homeRequestOf(intent))
    }

    /**
     * The intent of an open request is replaced so a later recreate does not act on it again. With app_library off there
     * is no library, so OpenLibrary only brings Home forward; ShowInputs closes the library first, as the Home key does.
     */
    private fun handle(request: HomeRequest) {
        when (request) {
            HomeRequest.OpenLibrary -> {
                setIntent(Intent(Intent.ACTION_MAIN))
                if (graph.config.appLibrary) library.onOpen()
            }

            HomeRequest.ShowInputs -> {
                setIntent(Intent(Intent.ACTION_MAIN))
                if (graph.config.appLibrary) library.onClose()
                inputsRequests++
            }

            HomeRequest.HomeKey -> {
                if (graph.config.appLibrary) library.onClose()
                homePresses++
            }

            HomeRequest.None -> {
                Unit
            }
        }
    }

    private fun showClock() {
        startActivity(Intent(this, ClockActivity::class.java))
    }

    private fun launchApp(app: AppEntry) {
        when (val result = graph.appLauncher.open(app)) {
            is LaunchResult.SwitchInput -> openInput(result.target)
            LaunchResult.CantOpen -> cantOpen(app.label)
            LaunchResult.Opened -> Unit
        }
    }

    /**
     * handle_view_inputs: switch InputsRequestActivity, the VIEW_INPUTS handler, on or off. It is disabled in the
     * manifest, so a TV with its own input picker never sees it; only the sets that need it turn it on. Written only
     * when the state differs, so an ordinary start does not change the package.
     */
    private fun applyViewInputsHandler(on: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val handler = ComponentName(this@MainActivity, InputsRequestActivity::class.java)
            viewInputsHandlerChange(on, packageManager.getComponentEnabledSetting(handler))?.let {
                packageManager.setComponentEnabledSetting(handler, it, PackageManager.DONT_KILL_APP)
            }
        }
    }

    private fun openInput(target: InputTarget) {
        lifecycleScope.launch { if (!graph.inputLauncher.open(target)) toast(R.string.not_available) }
    }

    /** The Live TV app of boot_to_live_tv. */
    private fun openIntent(intent: Intent) {
        if (!startActivitySafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) toast(R.string.not_available)
    }

    private fun toast(
        message: Int,
        length: Int = Toast.LENGTH_SHORT,
    ) {
        Toast.makeText(this, message, length).show()
    }

    private fun launchLibraryApp(app: AppEntry) {
        if (!graph.appLauncher.openFromLibrary(app)) cantOpen(app.label)
    }

    private fun librarySaveFailed() {
        toast(FeatureR.string.library_save_failed, Toast.LENGTH_LONG)
    }

    private fun openSettings() {
        if (!graph.systemSettings.openSettings()) cantOpen(getString(FeatureR.string.settings))
    }

    private fun openSettingsFromKey() {
        if (!graph.systemSettings.openSettings(graph.config.settingsKeyClearTask)) cantOpen(getString(FeatureR.string.settings))
    }

    private fun openNetwork() {
        if (!graph.systemSettings.openNetwork()) cantOpen(getString(FeatureR.string.wifi))
    }

    private fun openWifiSettings() {
        if (!graph.systemSettings.openWifiSettings()) cantOpen(getString(FeatureR.string.wifi))
    }

    private fun openAppInfo(packageName: String) {
        if (!graph.systemSettings.openAppInfo(packageName)) cantOpen(getString(FeatureR.string.app_info))
    }

    private fun openNativeSettings() {
        if (!graph.systemSettings.openNativeSettings()) cantOpen(getString(FeatureR.string.native_settings))
    }

    private fun orderNotSaved() {
        toast(R.string.order_not_saved)
    }

    private fun cantOpen(what: String) {
        Toast.makeText(this, getString(R.string.cant_open, what), Toast.LENGTH_SHORT).show()
    }
}
