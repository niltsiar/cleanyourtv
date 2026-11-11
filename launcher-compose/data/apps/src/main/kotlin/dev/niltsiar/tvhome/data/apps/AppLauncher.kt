package dev.niltsiar.tvhome.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppOpened
import dev.niltsiar.tvhome.core.model.INPUT_PREFIX
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.inputIdOf
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "TvHome.Launch"
private const val VIEW_PREFIX = "view:"

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
 * Opens an app's tile the way the reference launcher does, honouring launch_actions. With the cinematic Home, or with
 * library_least_used or unused_fade_days ([recordsUsage]), every app the launcher opened is noted in [lastOpened]: a tile,
 * an app from the library, and through [opened] an app a remote key or Open on start started. Every tile that opened is
 * noted in [launchLog], which keeps nothing unless habit_hint is on. The choices are read at each launch, so turning one
 * on or off takes effect at the next.
 */
public class AppLauncher(
    private val context: Context,
    private val config: StateFlow<TvConfig>,
    private val lastOpened: LastOpened,
    private val launchLog: LaunchLog,
) : AppOpened {
    private val records: Boolean get() = config.value.let { it.cinematicHome || it.recordsUsage }

    /** The TV input a tile's launch_actions "input:<id>" switches to instead of opening the app; null for any other tile. */
    internal fun inputId(app: AppEntry): String? = inputIdOf(config.value.launchActions[app.packageName])

    /** Opens a tile that Home itself shows: an "input:<id>" tile switches input, any other starts the app. */
    public fun open(app: AppEntry): LaunchResult {
        inputId(app)?.let { return LaunchResult.SwitchInput(InputTarget.TvInput(it)) }
        if (!launch(app)) return LaunchResult.CantOpen
        if (records) lastOpened.record(app.key)
        launchLog.record(app.packageName)
        return LaunchResult.Opened
    }

    /** Opens an app from the App library, which ignores launch_actions. True when the system accepted the launch. */
    public fun openFromLibrary(app: AppEntry): Boolean {
        if (!launch(app, useLaunchAction = false)) return false
        if (records) lastOpened.record(app.key)
        return true
    }

    /**
     * Notes an app the launcher started outside Home and the library, once the system accepted [intent]. The key is the
     * activity the intent names, or the one it resolves to, so the leanback entry of a package's launch intent gets the
     * key of that package's Home tile.
     */
    override fun opened(intent: Intent) {
        if (!records) return
        val component =
            intent.component
                ?: context.packageManager
                    .resolveActivity(intent, 0)
                    ?.activityInfo
                    ?.let { ComponentName(it.packageName, it.name) }
                ?: return
        lastOpened.record(component.flattenToShortString())
    }

    /** True when the system accepted the launch. */
    public fun launch(
        app: AppEntry,
        useLaunchAction: Boolean = true,
    ): Boolean {
        val intent =
            launchIntent(app, if (useLaunchAction) config.value.launchActions[app.packageName] else null) {
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
