package dev.niltsiar.tvhome.service.media

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** The secure setting that lists the allowed notification listeners; Settings.Secure keeps its constant hidden. */
private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

/** Whether Home may read media sessions through [NowPlayingListener], and the switch that turns the listener on or off. */
public object NowPlayingAccess {
    /** The listener to pass to `MediaSessionManager.getActiveSessions`. */
    public fun component(context: Context): ComponentName = ComponentName(context, NowPlayingListener::class.java)

    /**
     * False on a low-RAM TV up to Android 10: the system binds no notification listener there, and TV settings hides
     * Notification access.
     */
    public fun available(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || !context.getSystemService(ActivityManager::class.java).isLowRamDevice

    /** True when the person or `cmd notification allow_listener` gave the listener notification access. */
    public fun granted(context: Context): Boolean {
        val component = component(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        }
        val allowed = Settings.Secure.getString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS) ?: return false
        return allowed.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    /**
     * Turns the listener on or off. Off is DEFAULT, not DISABLED: the manifest keeps it disabled. Written only when the state
     * differs, so an ordinary start does not change the package. A package manager call: run it off the main thread.
     */
    public fun setEnabled(
        context: Context,
        on: Boolean,
    ) {
        val packages = context.packageManager
        val component = component(context)
        val want = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        if (packages.getComponentEnabledSetting(component) != want) {
            packages.setComponentEnabledSetting(component, want, PackageManager.DONT_KILL_APP)
        }
    }
}
