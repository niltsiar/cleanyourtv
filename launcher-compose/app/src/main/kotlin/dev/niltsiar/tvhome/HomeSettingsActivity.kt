package dev.niltsiar.tvhome

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import dev.niltsiar.tvhome.feature.settings.R as SettingsR

/**
 * Lets the person change Home through the system's picker, without a shell. The picker is translucent on Android 14
 * (permissioncontroller), so this stays behind it as a black backdrop: over Home, the picker's text sat on the tiles.
 * It finishes when the picker closes.
 */
class HomeSettingsActivity : Activity() {
    private var left = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            left = true
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            val label = applicationInfo.loadLabel(packageManager)
            Toast.makeText(this, getString(SettingsR.string.home_settings_help, label), Toast.LENGTH_LONG).show()
        } catch (e: ActivityNotFoundException) {
            notAvailable()
        } catch (e: SecurityException) {
            notAvailable()
        }
    }

    override fun onPause() {
        super.onPause()
        left = true
    }

    override fun onResume() {
        super.onResume()
        if (left) finish()
    }

    private fun notAvailable() {
        Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show()
        finish()
    }
}
