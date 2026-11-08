package dev.niltsiar.tvhome.service.keys

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings

private const val PREFS = "guard"
private const val KEY_OFF = "off_in_standby"

internal enum class WakeStep { Idle, WaitForScreenOn, PutBack }

/** The standby exit runs only for an opt-in whose installer also granted WRITE_SECURE_SETTINGS: without it the service could not come back. */
internal fun leavesInStandby(
    optedIn: Boolean,
    permitted: Boolean,
): Boolean = optedIn && permitted

/** What to do when something asks for the key service to be listed: nothing, wait for the screen, or write the list now. */
internal fun wakeStep(
    optedIn: Boolean,
    permitted: Boolean,
    interactive: Boolean,
): WakeStep =
    when {
        !leavesInStandby(optedIn, permitted) -> WakeStep.Idle
        !interactive -> WakeStep.WaitForScreenOn
        else -> WakeStep.PutBack
    }

/** [list] with [service] added at the end and every other entry kept in order; null when [service] is listed already. */
internal fun withService(
    list: String?,
    service: ComponentName,
): String? {
    val entries = if (list == null || list == "null") emptyList() else list.split(":").filter { it.isNotEmpty() }
    if (entries.any { ComponentName.unflattenFromString(it) == service }) return null
    return (entries + service.flattenToShortString()).joinToString(":")
}

/**
 * Keeps [KeyService] on the accessibility list for TVs where it blocks the power key or the firmware kills it. TCL and
 * Xiaomi China start their own launcher at boot and force-stop a third-party one, which also drops its accessibility
 * service; [GuardRevival] is bound again by the system afterwards and calls [ensureKeyService]. With the opt-in
 * `guard_off_in_standby` the service takes itself off the list while the screen is off: on a TCL tc8000_ay30a2 the
 * remote's power key never woke the set while an accessibility service of this app stayed bound.
 */
internal object GuardKeep {
    const val EXTRA_OFF_IN_STANDBY = "guard_off_in_standby"

    private var screenOn: BroadcastReceiver? = null

    /** False before the unlock: the flag is in credential-protected storage. */
    fun offInStandby(context: Context): Boolean {
        val users = context.getSystemService(UserManager::class.java)
        if (!users.isUserUnlocked) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OFF, false)
    }

    fun setOffInStandby(
        context: Context,
        off: Boolean,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OFF, off)
            .commit()
    }

    fun permitted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Puts [KeyService] back on the enabled list when the opt-in and the permission allow it. Other services stay. */
    fun ensureKeyService(context: Context) {
        // The service may be bound already; arming it only at connect would miss an opt-in that arrives later (Philips 65PUS8102).
        PowerMenu.bound()?.applyStandbyOptIn()
        val interactive = context.getSystemService(PowerManager::class.java).isInteractive
        when (wakeStep(offInStandby(context), permitted(context), interactive)) {
            WakeStep.Idle -> Unit

            // Binding the service again while the screen is off would sit on the power key.
            WakeStep.WaitForScreenOn -> watchScreenOn(context.applicationContext)

            WakeStep.PutBack -> putBack(context)
        }
    }

    /** Leaves for standby and lists the service again at the next SCREEN_ON. The process has to stay up. */
    fun stepOut(service: KeyService) {
        watchScreenOn(service.applicationContext)
        service.disableSelf()
    }

    /** Drops the pending screen-on watch; tests start each case from none. */
    fun reset() {
        screenOn = null
    }

    private fun putBack(context: Context) {
        try {
            val resolver = context.contentResolver
            val list = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            withService(list, ComponentName(context, KeyService::class.java))?.let {
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, it)
            }
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (_: RuntimeException) {
            // The settings are the system's; a refusal leaves the service as it was.
        }
    }

    private fun watchScreenOn(app: Context) {
        if (screenOn != null) return
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (screenOn !== this) return
                    screenOn = null
                    app.unregisterReceiver(this)
                    ensureKeyService(app)
                }
            }
        screenOn = receiver
        app.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }
}
