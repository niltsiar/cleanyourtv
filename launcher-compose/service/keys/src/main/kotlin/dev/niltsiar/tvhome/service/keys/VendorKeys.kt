package dev.niltsiar.tvhome.service.keys

import android.content.Context
import android.view.KeyEvent
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.data.system.SystemSettings

/** Keycodes from here up are vendor keys, which reach only the focused window and never the key service. */
private const val FIRST_VENDOR_KEYCODE = 1000

/**
 * The remote keys that Home answers itself, for a set where no key service runs or a key never reaches it: app_keys with a
 * vendor keycode (TCL's Netflix button is 4062), with their "hold:" entries, and the Settings buttons of
 * settings_key_keycodes. The caller passes only the keys that arrive while Home is in front. [openSettings] opens the TV's
 * settings.
 */
public class VendorKeys(
    context: Context,
    config: TvConfig,
    settings: SystemSettings,
    private val openSettings: () -> Unit,
) {
    private val keys = AppKeys(config.appKeys)
    private val settingsKeycodes = config.settingsKeyKeycodes
    private val launcher = TargetLauncher(context, settings, performGlobal = null, liveTvPackage = config.liveTvPackage)

    /** True when the key was taken, so Home does not handle it. */
    public fun handle(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code >= FIRST_VENDOR_KEYCODE &&
            handleAppKey(event, keys.keycodes[code], keys.holdKeycodes[code], this, launcher::launch)
        ) {
            return true
        }
        if (code !in settingsKeycodes) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openSettings()
        return true
    }

    /**
     * Home is leaving the front: no UP reaches a paused Home, so a hold it saw go down would still open its app later. Only
     * Home's own share is dropped.
     */
    public fun release() {
        SharedPresses.release(this)
    }
}
