package dev.niltsiar.tvhome.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import dev.niltsiar.tvhome.core.model.AppEntry

private const val TAG = "TvHome.Launch"
private const val VIEW_PREFIX = "view:"
private const val INPUT_PREFIX = "input:"

/** Opens an app's tile the way the reference launcher does, honouring launch_actions. */
public class AppLauncher(
    private val context: Context,
    private val launchActions: Map<String, String>,
) {
    /** True when the system accepted the launch. */
    public fun launch(app: AppEntry): Boolean {
        val intent = launchIntent(app, launchActions[app.packageName]) { context.packageManager.resolveActivity(it, 0) != null }
        if (intent == null) return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: RuntimeException) {
            // ActivityNotFoundException for a disabled app, SecurityException for an activity that is not exported.
            Log.w(TAG, "cannot open ${app.key}: $e")
            false
        }
    }
}

/**
 * The tile's own activity by explicit component, started in a new task (with the leanback category for a TV app, so a
 * running app resumes). A launch_actions value replaces it when it resolves: `view:<uri>` sends ACTION_VIEW on the
 * uri, any other value is an intent action, both limited to the app's package and in a new task without
 * RESET_TASK_IF_NEEDED, which would only bring a running task of the app forward. `input:` values belong to Inputs.
 */
internal fun launchIntent(
    app: AppEntry,
    action: String?,
    resolves: (Intent) -> Boolean,
): Intent? {
    val component = ComponentName.unflattenFromString(app.key) ?: return null
    val tile =
        Intent(Intent.ACTION_MAIN)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    if (app.leanback) tile.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    if (action == null || action.startsWith(INPUT_PREFIX)) return tile
    val uri = if (action.startsWith(VIEW_PREFIX)) action.removePrefix(VIEW_PREFIX).trim().ifEmpty { null } else null
    val custom =
        (if (uri != null) Intent(Intent.ACTION_VIEW, uri.toUri()) else Intent(action))
            .setPackage(app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return if (resolves(custom)) custom else tile
}
