package dev.niltsiar.tvhome.service.keys

import android.Manifest
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings

private const val PREFS = "accessibility_helpers"
private const val PAUSE_MS = 1_000L

/**
 * Android takes an app's accessibility services off the enabled list when the app is force-stopped, and the remote's
 * buttons then stop working until the service is turned on again over adb. With restore_key_services on, a running
 * [KeyService] records itself here and drops the record when Android unbinds it (the user turned it off); a force stop
 * kills the process before that, so the record stays. Home starting again puts a recorded service back on the list, when
 * the installer granted WRITE_SECURE_SETTINGS. With the key off nothing is recorded and nothing is restored.
 */
public object ServiceRepair {
    internal fun connected(
        context: Context,
        service: Class<*>,
    ) = prefs(context).edit().putBoolean(service.name, true).apply()

    internal fun disconnected(
        context: Context,
        service: Class<*>,
    ) = prefs(context).edit().remove(service.name).apply()

    internal fun recorded(context: Context): Set<String> = prefs(context).all.keys

    public fun restore(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
        try {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val plan = repairPlan(current, recorded(context).map { ComponentName(context.packageName, it) }) ?: return
            Thread({ runRepair(resolver, current, plan) { Thread.sleep(it) } }, "ServiceRepair").start()
        } catch (_: RuntimeException) {
            // The setting is the system's; a refusal leaves the service off, as before.
        }
    }

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The enabled list without the recorded services, and with all of them. */
internal data class RepairPlan(
    val without: String,
    val with: String,
)

/** Null when nothing is recorded or every recorded service is on the list already. */
internal fun repairPlan(
    enabledList: String?,
    recorded: Collection<ComponentName>,
): RepairPlan? {
    if (recorded.isEmpty()) return null
    val enabled = enabledList.orEmpty().split(":").mapNotNull(ComponentName::unflattenFromString)
    if (recorded.all { it in enabled }) return null
    val others = enabled.filter { it !in recorded }.map { it.flattenToShortString() }
    return RepairPlan(
        without = others.joinToString(":"),
        with = (others + recorded.map { it.flattenToShortString() }).joinToString(":"),
    )
}

/**
 * A killed service also lands on Android's "Crashed services" list (dumpsys accessibility), and a crashed service is never
 * bound again, so putting it back on the list alone binds nothing and the switch falls back to 0 (Hisense songshan, Android
 * 11, whose standby kills third-party apps). Android drops the crashed mark when it sees the service leave the enabled list,
 * so add, remove, add: after a force stop the list is already empty, and a removal that changes nothing is not seen. Each
 * write first checks the list still reads what the previous step left, and the cycle stops otherwise: a service turned on
 * or off during a pause stays that way instead of being overwritten by a snapshot.
 */
internal fun runRepair(
    resolver: ContentResolver,
    current: String?,
    plan: RepairPlan,
    pause: (Long) -> Unit,
) {
    try {
        if (!replaceList(resolver, current, plan.with)) return
        pause(PAUSE_MS)
        if (!replaceList(resolver, plan.with, plan.without)) return
        pause(PAUSE_MS)
        if (!replaceList(resolver, plan.without, plan.with)) return
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    } catch (_: RuntimeException) {
        // The setting is the system's; a refusal leaves the service off, as before.
    }
}

/** Writes the enabled list only while it still reads [expected]; an absent list counts as empty. */
private fun replaceList(
    resolver: ContentResolver,
    expected: String?,
    updated: String,
): Boolean {
    val now = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return now.orEmpty() == expected.orEmpty() &&
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
}
