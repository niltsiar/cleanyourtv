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

class MainActivity : ComponentActivity() {
    private val graph get() = (application as TvHomeApplication).graph
    private var homePresses by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is Home: Back has nowhere to go.
        onBackPressedDispatcher.addCallback(this) {}
        setContent {
            TvTheme(glassKind = GlassKind.valueOf(BuildConfig.GLASS_KIND)) {
                HomeHost(graph = graph, onLaunchApp = ::launchApp, homePresses = { homePresses })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        graph.appsProvider.onStart()
    }

    override fun onStop() {
        super.onStop()
        graph.appsProvider.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    private fun launchApp(app: AppEntry) {
        if (!graph.appLauncher.launch(app)) Toast.makeText(this, getString(R.string.cant_open, app.label), Toast.LENGTH_SHORT).show()
    }
}
