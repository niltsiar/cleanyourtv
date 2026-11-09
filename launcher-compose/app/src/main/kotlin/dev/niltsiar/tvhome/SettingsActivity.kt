package dev.niltsiar.tvhome

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.niltsiar.tvhome.feature.settings.MoleculeSettingsViewModel
import dev.niltsiar.tvhome.feature.settings.R as SettingsR

/** Launcher settings, a window of their own over Home: Back returns to it, and Home follows the choices when it is in front again. */
class SettingsActivity : ComponentActivity() {
    private val graph get() = (application as TvHomeApplication).graph
    private val viewModel: MoleculeSettingsViewModel by viewModels {
        viewModelFactory { initializer { MoleculeSettingsViewModel(graph.configRepository) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent {
            SettingsHost(
                graph = graph,
                viewModel = viewModel,
                onOpenTvSettings = ::openTvSettings,
                onSaveFailed = { Toast.makeText(this, SettingsR.string.settings_save_failed, Toast.LENGTH_LONG).show() },
            )
        }
    }

    private fun openTvSettings() {
        if (!graph.systemSettings.openSettings()) Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show()
    }
}
