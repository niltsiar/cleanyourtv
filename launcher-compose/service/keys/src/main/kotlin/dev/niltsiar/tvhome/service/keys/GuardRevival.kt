package dev.niltsiar.tvhome.service.keys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import android.service.notification.NotificationListenerService

/**
 * Starts this launcher's process after the firmware has killed it. A notification listener keeps its approval across a
 * force stop, and the system binds it again once the process is gone; that rebind is what brings the key service back on
 * TCL and Xiaomi China. No notification is read. Enable with
 * `cmd notification allow_listener dev.niltsiar.tvhome/.service.keys.GuardRevival`.
 */
public class GuardRevival : NotificationListenerService() {
    private var unlocked: BroadcastReceiver? = null

    override fun onListenerConnected() {
        val users = getSystemService(UserManager::class.java)
        if (users.isUserUnlocked) {
            GuardKeep.ensureKeyService(this)
            return
        }
        if (unlocked != null) return
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    stopWaiting()
                    GuardKeep.ensureKeyService(this@GuardRevival)
                }
            }
        unlocked = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        // The unlock may have happened between the check and the registration.
        if (users.isUserUnlocked) {
            stopWaiting()
            GuardKeep.ensureKeyService(this)
        }
    }

    private fun stopWaiting() {
        unlocked?.let(::unregisterReceiver)
        unlocked = null
    }

    override fun onDestroy() {
        stopWaiting()
        super.onDestroy()
    }
}
