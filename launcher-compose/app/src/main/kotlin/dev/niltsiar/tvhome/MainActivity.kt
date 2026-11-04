package dev.niltsiar.tvhome

import android.content.Intent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.rememberGlassState
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.feature.library.MoleculeLibraryViewModel
import dev.niltsiar.tvhome.feature.home.R as FeatureR

class MainActivity : ComponentActivity() {
    private val graph get() = (application as TvHomeApplication).graph
    private var homePresses by mutableIntStateOf(0)
    private val library: MoleculeLibraryViewModel by viewModels {
        viewModelFactory { initializer { MoleculeLibraryViewModel(graph.librarySources, graph.config) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is Home: Back has nowhere to go.
        onBackPressedDispatcher.addCallback(this) {}
        if (savedInstanceState == null) openLibraryIfAsked(intent)
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
                        glass = glass,
                        libraryOpen = { libraryOpen?.value?.open == true },
                        onOpenLibrary = { library.onOpen() },
                        homePresses = { homePresses },
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
        graph.networkMonitor.onStart()
        graph.timeChanges.onStart()
        graph.wallpaperProvider.onStart()
    }

    override fun onStop() {
        super.onStop()
        graph.appsProvider.onStop()
        graph.networkMonitor.onStop()
        graph.timeChanges.onStop()
        graph.wallpaperProvider.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (openLibraryIfAsked(intent)) return
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
        if (!graph.appLauncher.launch(app)) {
            cantOpen(app.label)
        } else if (graph.config.cinematicHome) {
            graph.lastOpened.record(app.key)
        }
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
    }
}
