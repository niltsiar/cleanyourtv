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

    /** The Home key (CATEGORY_HOME) while Home is already the front task. */
    HomeKey,
    None,
}

/** The action wins over the category: the key service sends its actions through the Home intent. */
internal fun homeRequestOf(intent: Intent?): HomeRequest =
    when {
        intent?.action == LauncherIntents.APP_LIBRARY -> HomeRequest.OpenLibrary
        intent?.action == LauncherIntents.VIEW_INPUTS -> HomeRequest.ShowInputs
        intent?.hasCategory(Intent.CATEGORY_HOME) == true -> HomeRequest.HomeKey
        else -> HomeRequest.None
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
