package dev.niltsiar.tvhome.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.InputTarget

private const val TAG = "TvHome.Launch"
private const val VIEW_PREFIX = "view:"
private const val INPUT_PREFIX = "input:"

/** What Home's own launch of a tile came to; the caller turns it into a screen change or a toast. */
public sealed interface LaunchResult {
    /** The tile's launch_actions value is "input:<id>": nothing was started, the caller switches to [target]. */
    public data class SwitchInput(
        val target: InputTarget.TvInput,
    ) : LaunchResult

    public data object Opened : LaunchResult

    public data object CantOpen : LaunchResult
}

/**
 * Opens an app's tile the way the reference launcher does, honouring launch_actions. With [recordOpened] (the cinematic
 * Home) a tile that opened is noted in [lastOpened].
 */
public class AppLauncher(
    private val context: Context,
    private val launchActions: Map<String, String>,
    private val lastOpened: LastOpened,
    private val recordOpened: Boolean,
) {
    /** The TV input a tile's launch_actions "input:<id>" switches to instead of opening the app; null for any other tile. */
    internal fun inputId(app: AppEntry): String? = inputIdOf(launchActions[app.packageName])

    /** Opens a tile that Home itself shows: an "input:<id>" tile switches input, any other starts the app. */
    public fun open(app: AppEntry): LaunchResult {
        inputId(app)?.let { return LaunchResult.SwitchInput(InputTarget.TvInput(it)) }
        if (!launch(app)) return LaunchResult.CantOpen
        if (recordOpened) lastOpened.record(app.key)
        return LaunchResult.Opened
    }

    /** True when the system accepted the launch. The library passes false for [useLaunchAction]: it opens the app itself. */
    public fun launch(
        app: AppEntry,
        useLaunchAction: Boolean = true,
    ): Boolean {
        val intent =
            launchIntent(app, if (useLaunchAction) launchActions[app.packageName] else null) {
                context.packageManager.resolveActivity(it, 0) !=
                    null
            }
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
 * The TV input id of a launch_actions value "input:<id>", or null when the value is not one. An empty id ("input:"
 * alone) is none: the tile launches normally rather than switching to nothing.
 */
internal fun inputIdOf(action: String?): String? =
    action
        ?.takeIf { it.startsWith(INPUT_PREFIX) }
        ?.removePrefix(INPUT_PREFIX)
        ?.trim()
        ?.ifEmpty { null }

/**
 * The tile's own activity by explicit component, started in a new task (with the leanback category for a TV app, so a
 * running app resumes). A launch_actions value replaces it when it resolves: `view:<uri>` sends ACTION_VIEW on the
 * uri, any other value is an intent action, both limited to the app's package and in a new task without
 * RESET_TASK_IF_NEEDED, which would only bring a running task of the app forward. `input:` values belong to [inputIdOf].
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
