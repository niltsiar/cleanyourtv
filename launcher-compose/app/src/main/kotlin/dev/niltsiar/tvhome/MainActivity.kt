package dev.niltsiar.tvhome

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.niltsiar.tvhome.core.designsystem.GlassKind
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.feature.home.R as FeatureR

class MainActivity : ComponentActivity() {
    private val graph get() = (application as TvHomeApplication).graph
    private var homePresses by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is Home: Back has nowhere to go.
        onBackPressedDispatcher.addCallback(this) {}
        setContent {
            TvTheme(glassKind = GlassKind.valueOf(BuildConfig.GLASS_KIND)) {
                HomeHost(
                    graph = graph,
                    onLaunchApp = ::launchApp,
                    onOpenSettings = ::openSettings,
                    onOpenNetwork = ::openNetwork,
                    onOpenAppInfo = ::openAppInfo,
                    onOpenNativeSettings = ::openNativeSettings,
                    onOrderNotSaved = ::orderNotSaved,
                    homePresses = { homePresses },
                )
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
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    private fun launchApp(app: AppEntry) {
        if (!graph.appLauncher.launch(app)) {
            cantOpen(app.label)
        } else if (graph.config.cinematicHome) {
            graph.lastOpened.record(app.key)
        }
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
}
