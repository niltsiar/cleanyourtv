package dev.niltsiar.tvhome

import android.content.Intent
import android.content.pm.PackageManager
import dev.niltsiar.tvhome.core.model.LauncherIntents

/** What an intent sent to MainActivity asks of Home. */
internal enum class HomeRequest {
    /** [LauncherIntents.APP_LIBRARY], from the "library" action word of app_keys. */
    OpenLibrary,

    /** [LauncherIntents.VIEW_INPUTS], forwarded by InputsRequestActivity: open the Inputs list. */
    ShowInputs,

    /** [launcherSettingsAction]: open the Launcher settings page. */
    OpenLauncherSettings,

    /** The Home key (CATEGORY_HOME) while Home is already the front task. */
    HomeKey,
    None,
}

/** The action that opens Launcher settings. It follows the package, so a launcherApplicationId build answers to its own. */
internal fun launcherSettingsAction(packageName: String): String = "$packageName.action.LAUNCHER_SETTINGS"

/**
 * The action wins over the category: the key service sends its actions through the Home intent. An intent the system
 * re-delivers from history (after process death, stateNotNeeded gives no saved state) carries no new request.
 */
internal fun homeRequestOf(
    intent: Intent?,
    packageName: String,
): HomeRequest {
    val replayed = intent != null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
    return when {
        !replayed && intent?.action == launcherSettingsAction(packageName) -> HomeRequest.OpenLauncherSettings
        !replayed && intent?.action == LauncherIntents.APP_LIBRARY -> HomeRequest.OpenLibrary
        !replayed && intent?.action == LauncherIntents.VIEW_INPUTS -> HomeRequest.ShowInputs
        intent?.hasCategory(Intent.CATEGORY_HOME) == true -> HomeRequest.HomeKey
        else -> HomeRequest.None
    }
}

/**
 * The state to write for InputsRequestActivity when handle_view_inputs is [on], or null when [current] already is it.
 * Off is DEFAULT, not DISABLED: the manifest keeps the activity disabled.
 */
internal fun viewInputsHandlerChange(
    on: Boolean,
    current: Int,
): Int? {
    val want = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    return want.takeIf { it != current }
}
