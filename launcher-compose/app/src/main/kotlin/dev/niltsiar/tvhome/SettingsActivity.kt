package dev.niltsiar.tvhome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.niltsiar.tvhome.feature.settings.MoleculeSettingsViewModel
import dev.niltsiar.tvhome.feature.settings.R as SettingsR

/** Opens [SettingsActivity] and answers whether it ended with the Reorder row, which asks Home to start reorder mode. */
internal object LauncherSettingsPage : ActivityResultContract<Unit, Boolean>() {
    private const val EXTRA_REORDER = "reorder"

    override fun createIntent(
        context: Context,
        input: Unit,
    ): Intent = Intent(context, SettingsActivity::class.java)

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Boolean = resultCode == Activity.RESULT_OK && intent?.getBooleanExtra(EXTRA_REORDER, false) == true

    fun reorderResult(): Intent = Intent().putExtra(EXTRA_REORDER, true)
}

/** Launcher settings, a window of their own over Home: Back returns to it, and Home follows the choices when it is in front again. */
class SettingsActivity : ComponentActivity() {
    private val graph get() = (application as TvHomeApplication).graph
    private val viewModel: MoleculeSettingsViewModel by viewModels {
        viewModelFactory { initializer { MoleculeSettingsViewModel(graph.configRepository, graph.settingsSources) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent {
            SettingsHost(
                graph = graph,
                viewModel = viewModel,
                onOpenTvSettings = ::openTvSettings,
                onReorder = ::reorder,
                onSaveFailed = { Toast.makeText(this, SettingsR.string.settings_save_failed, Toast.LENGTH_LONG).show() },
            )
        }
    }

    private fun reorder() {
        setResult(RESULT_OK, LauncherSettingsPage.reorderResult())
        finish()
    }

    private fun openTvSettings() {
        if (!graph.systemSettings.openSettings()) Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show()
    }
}
