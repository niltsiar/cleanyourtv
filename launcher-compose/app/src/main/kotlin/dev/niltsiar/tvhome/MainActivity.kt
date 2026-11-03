package dev.niltsiar.tvhome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.feature.home.HomeRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as TvHomeApplication).graph
        val apps = graph.appsProvider.apps()
        setContent {
            TvTheme {
                HomeRoute(apps = apps)
            }
        }
    }
}
