package dev.niltsiar.tvhome.service.keys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Opt in to leaving the key service while the screen is off. Protected by WRITE_SECURE_SETTINGS, which only the adb shell
 * holds:
 * `adb shell am broadcast -a dev.niltsiar.tvhome.GUARD -n dev.niltsiar.tvhome/.service.keys.GuardConfig --ez guard_off_in_standby true`
 */
public class GuardConfig : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.hasExtra(GuardKeep.EXTRA_OFF_IN_STANDBY)) {
            GuardKeep.setOffInStandby(context, intent.getBooleanExtra(GuardKeep.EXTRA_OFF_IN_STANDBY, false))
            GuardKeep.ensureKeyService(context)
        }
        resultData = "${GuardKeep.EXTRA_OFF_IN_STANDBY}=${GuardKeep.offInStandby(context)}"
    }
}
