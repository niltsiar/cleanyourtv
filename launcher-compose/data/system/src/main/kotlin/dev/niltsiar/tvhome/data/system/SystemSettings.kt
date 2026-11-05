package dev.niltsiar.tvhome.data.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.net.toUri
import dev.niltsiar.tvhome.core.model.startActivitySafely
import dev.niltsiar.tvhome.core.model.tvLaunchIntent

private val AospTvSettings = ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings")

/**
 * Opens the TV's system settings the way the reference launcher does. [fallbackComponents] are vendor settings
 * activities ("package/class") tried in order when no standard handler is usable. [vpnApp] is the package the
 * network button opens instead of Wi-Fi settings, when it is installed.
 */
public class SystemSettings internal constructor(
    private val context: Context,
    private val fallbackComponents: List<String>,
    private val vpnApp: String?,
) {
    /** With the vendor settings activities this module ships. */
    public constructor(context: Context, vpnApp: String?) :
        this(context, context.resources.getStringArray(R.array.settings_fallback_components).toList(), vpnApp)

    /** True when the system accepted the intent. */
    public fun openSettings(): Boolean = open(intent(Settings.ACTION_SETTINGS))

    /** The VPN app when one is set and launchable, else Wi-Fi settings. */
    public fun openNetwork(): Boolean = open(vpnAppIntent() ?: intent(Settings.ACTION_WIFI_SETTINGS))

    /** True when the system accepted the intent. Always Wi-Fi settings: the remote's "wifi" key must not open the VPN app. */
    public fun openWifiSettings(): Boolean = open(intent(Settings.ACTION_WIFI_SETTINGS))

    /** True when the TV has an App info page to open; CVTE's settings app has none. */
    public fun hasAppInfo(packageName: String): Boolean =
        context.packageManager.resolveActivity(appInfoIntent(packageName), PackageManager.MATCH_DEFAULT_ONLY) != null

    /** True when the system accepted the intent. */
    public fun openAppInfo(packageName: String): Boolean = open(appInfoIntent(packageName))

    /** True when a vendor settings app from the fallback list is installed: it has pages the standard settings lack. */
    public fun hasNativeSettings(): Boolean = vendorIntent() != null

    /** True when the system accepted the intent. */
    public fun openNativeSettings(): Boolean = vendorIntent()?.let(::open) ?: false

    /**
     * The standard settings intent, or, when nothing usable answers it, the first vendor entry of [fallbackComponents]
     * whose package is installed, else the AOSP TV settings app's main screen where it is installed. Fire OS
     * resolves the standard actions to a compliance stub that opens nothing (CTSDummy), which counts as missing.
     */
    internal fun intent(action: String): Intent {
        val pm = context.packageManager
        val standard = Intent(action)
        val resolved = pm.resolveActivity(standard, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolved != null && !resolved.activityInfo.name.contains("CTSDummy")) return standard
        vendorIntent()?.let { return it }
        return if (isInstalled(AospTvSettings.packageName)) Intent().setComponent(AospTvSettings) else standard
    }

    private fun vendorIntent(): Intent? =
        fallbackComponents
            .asSequence()
            .mapNotNull(ComponentName::unflattenFromString)
            .firstOrNull { isInstalled(it.packageName) }
            ?.let { Intent().setComponent(it) }

    private fun appInfoIntent(packageName: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

    private fun vpnAppIntent(): Intent? = vpnApp?.let(context.packageManager::tvLaunchIntent)

    private fun isInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    private fun open(intent: Intent): Boolean = context.startActivitySafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
