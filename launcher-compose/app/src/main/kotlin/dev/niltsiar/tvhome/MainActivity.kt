package dev.niltsiar.tvhome

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.feature.library.MoleculeLibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import dev.niltsiar.tvhome.feature.home.R as FeatureR

class MainActivity : ComponentActivity() {
    private val graph get() = (application as TvHomeApplication).graph
    private var homePresses by mutableIntStateOf(0)
    private var inputsRequests by mutableIntStateOf(0)
    private var bootOpening: Job? = null
    private val wakeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                lifecycleScope.launch { graph.openOnStart.openOnWake(::openIntent) }
            }
        }
    private val library: MoleculeLibraryViewModel by viewModels {
        viewModelFactory { initializer { MoleculeLibraryViewModel(graph.librarySources, graph.config) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is Home: Back has nowhere to go.
        onBackPressedDispatcher.addCallback(this) {}
        // Not after a recreate: the launch intent survives it and would open the library or the list again.
        if (savedInstanceState == null && !openLibraryIfAsked(intent)) openInputsIfAsked(intent)
        applyViewInputsHandler(graph.config.handleViewInputs)
        if (graph.config.openOnStartChoice && graph.config.openOnStartOnWake) {
            ContextCompat.registerReceiver(this, wakeReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        setContent {
            TvTheme(glassKind = GlassKind.valueOf(BuildConfig.GLASS_KIND)) {
                // Home and the library both draw glass from what Home shows, so they share one state.
                val glass = rememberGlassState()
                Box(Modifier.fillMaxSize()) {
                    val libraryOpen = if (graph.config.appLibrary) library.uiState.collectAsStateWithLifecycle() else null
                    HomeHost(
                        graph = graph,
                        onLaunchApp = ::launchApp,
                        onOpenSettings = ::openSettings,
                        onOpenNetwork = ::openNetwork,
                        onOpenAppInfo = ::openAppInfo,
                        onOpenNativeSettings = ::openNativeSettings,
                        onOrderNotSaved = ::orderNotSaved,
                        onLibrarySaveFailed = ::librarySaveFailed,
                        onOpenInput = ::openInput,
                        onNoInputs = { toast(R.string.no_inputs) },
                        glass = glass,
                        libraryOpen = { libraryOpen?.value?.open == true },
                        onOpenLibrary = { library.onOpen() },
                        homePresses = { homePresses },
                        inputsRequests = { inputsRequests },
                    )
                    if (graph.config.appLibrary) {
                        LibraryHost(
                            graph = graph,
                            viewModel = library,
                            glass = glass,
                            onLaunchApp = ::launchLibraryApp,
                            onOpenAppInfo = ::openAppInfo,
                            onSaveFailed = ::librarySaveFailed,
                        )
                    }
                }
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

    override fun onResume() {
        super.onResume()
        bootOpening = lifecycleScope.launch { graph.openOnStart.openAfterBoot(::openIntent) }
    }

    /** Leaving Home before the network came back keeps the boot's app closed; its boot is already written. */
    override fun onPause() {
        super.onPause()
        bootOpening?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (graph.config.openOnStartChoice && graph.config.openOnStartOnWake) unregisterReceiver(wakeReceiver)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (openLibraryIfAsked(intent) || openInputsIfAsked(intent)) return
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (graph.config.appLibrary) library.onClose()
            homePresses++
        }
    }

    /**
     * ACTION_APP_LIBRARY, sent by the "library" action word of app_keys: open the App library over Home. The intent is
     * replaced so a later recreate does not open it again. With app_library off there is no library, so the press only
     * brings Home forward.
     */
    private fun openLibraryIfAsked(intent: Intent?): Boolean {
        if (intent?.action != ACTION_APP_LIBRARY) return false
        setIntent(Intent(Intent.ACTION_MAIN))
        if (graph.config.appLibrary) library.onOpen()
        return true
    }

    private fun launchApp(app: AppEntry) {
        val input = graph.appLauncher.inputId(app)
        if (input != null) {
            openInput(InputTarget.TvInput(input))
        } else if (!graph.appLauncher.launch(app)) {
            cantOpen(app.label)
        } else if (graph.config.cinematicHome) {
            graph.lastOpened.record(app.key)
        }
    }

    /**
     * VIEW_INPUTS, forwarded by InputsRequestActivity while handle_view_inputs is on: Home opens its Inputs list once
     * the button is laid out. The intent is replaced so a later recreate does not open it again. The library closes
     * first, as for the Home key.
     */
    private fun openInputsIfAsked(intent: Intent?): Boolean {
        if (intent?.action != ACTION_VIEW_INPUTS) return false
        setIntent(Intent(Intent.ACTION_MAIN))
        if (graph.config.appLibrary) library.onClose()
        inputsRequests++
        return true
    }

    /**
     * handle_view_inputs: switch InputsRequestActivity, the VIEW_INPUTS handler, on or off. It is disabled in the
     * manifest, so a TV with its own input picker never sees it; only the sets that need it turn it on. Written only
     * when the state differs, so an ordinary start does not change the package.
     */
    private fun applyViewInputsHandler(on: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val handler = ComponentName(this@MainActivity, InputsRequestActivity::class.java)
            val want = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            if (packageManager.getComponentEnabledSetting(handler) != want) {
                packageManager.setComponentEnabledSetting(handler, want, PackageManager.DONT_KILL_APP)
            }
        }
    }

    private fun openInput(target: InputTarget) {
        lifecycleScope.launch { if (!graph.inputLauncher.open(target)) toast(R.string.not_available) }
    }

    /** The app of boot_to_live_tv or the Open on start choice, or the Live TV app. */
    private fun openIntent(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.not_available)
        } catch (e: SecurityException) {
            toast(R.string.not_available)
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun launchLibraryApp(app: AppEntry) {
        if (!graph.appLauncher.launch(app, useLaunchAction = false)) cantOpen(app.label)
    }

    private fun librarySaveFailed() {
        Toast.makeText(this, FeatureR.string.library_save_failed, Toast.LENGTH_LONG).show()
    }

    private fun openSettings() {
        if (!graph.systemSettings.openSettings()) cantOpen(getString(FeatureR.string.settings))
    }

    private fun openNetwork() {
        if (!graph.systemSettings.openNetwork()) cantOpen(getString(FeatureR.string.wifi))
    }

    private fun openAppInfo(packageName: String) {
        if (!graph.systemSettings.openAppInfo(packageName)) cantOpen(getString(FeatureR.string.app_info))
    }

    private fun openNativeSettings() {
        if (!graph.systemSettings.openNativeSettings()) cantOpen(getString(FeatureR.string.native_settings))
    }

    private fun orderNotSaved() {
        Toast.makeText(this, R.string.order_not_saved, Toast.LENGTH_SHORT).show()
    }

    private fun cantOpen(what: String) {
        Toast.makeText(this, getString(R.string.cant_open, what), Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** Sent by the "library" action word of the remote key service: open the App library over Home. */
        const val ACTION_APP_LIBRARY: String = "dev.niltsiar.tvhome.action.APP_LIBRARY"

        /** The system's input switcher, which InputsRequestActivity forwards while handle_view_inputs is on. */
        const val ACTION_VIEW_INPUTS: String = "com.android.tv.action.VIEW_INPUTS"
    }
}
