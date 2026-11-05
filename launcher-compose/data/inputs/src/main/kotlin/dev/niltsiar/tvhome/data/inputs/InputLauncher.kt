package dev.niltsiar.tvhome.data.inputs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.InputTarget
import dev.niltsiar.tvhome.core.model.startActivitySafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "TvHome.Inputs"

/**
 * The TV's own source-selection panel, for TVs that have no TvInputManager list (TCL Android TV 9): a service, or an
 * activity on sets whose panel is a dialog (BYINTEK U4's QuickSourceActivity). `source_panel_extra` is one int extra,
 * "name=value". Null without a source_panel_service.
 */
internal fun sourcePanelIntent(config: TvConfig): Intent? {
    val service = config.sourcePanelService ?: return null
    val intent = Intent(config.sourcePanelAction.orEmpty())
    ComponentName.unflattenFromString(service)?.let(intent::setComponent)
    val extra = config.sourcePanelExtra.orEmpty()
    val equals = extra.indexOf('=')
    if (equals > 0) {
        extra
            .substring(equals + 1)
            .trim()
            .toIntOrNull()
            ?.let { intent.putExtra(extra.substring(0, equals).trim(), it) }
    }
    return intent
}

/** Opens what an Inputs menu row, or a tile with a launch_actions "input:<id>", stands for. */
public class InputLauncher(
    context: Context,
    private val config: TvConfig,
) {
    private val appContext = context.applicationContext
    private val source = TvInputSource(appContext)
    private val coocaa = CoocaaSources(appContext)
    private val liveTv = LiveTv(appContext, config)
    private val opener = InputOpener(config, appContext::startActivitySafely)

    /** True when something started. Binder calls run on the IO dispatcher; activities start on the caller's. */
    public suspend fun open(target: InputTarget): Boolean =
        when (target) {
            is InputTarget.TvInput -> openInput(target.id)
            is InputTarget.Coocaa -> withContext(Dispatchers.IO) { coocaa.open(target.name, target.index) }
            InputTarget.SourcePanel -> openSourcePanel()
            InputTarget.LiveTv -> openLiveTv()
        }

    /** Switches to a passthrough input by its TvInputManager id. */
    public suspend fun openInput(inputId: String): Boolean {
        val parentId = withContext(Dispatchers.IO) { source.parentOf(inputId) }
        return opener.open(inputId, parentId)
    }

    private suspend fun openLiveTv(): Boolean {
        val intent = withContext(Dispatchers.IO) { liveTv.intent(liveTv.tuner(source.inputs(withState = false))) } ?: return false
        return start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openSourcePanel(): Boolean {
        val intent = sourcePanelIntent(config) ?: return false
        return try {
            if (appContext.packageManager.resolveActivity(intent, 0) != null) {
                appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                appContext.startService(intent)
            }
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot open the source panel: $e")
            false
        }
    }

    private fun start(intent: Intent): Boolean =
        try {
            appContext.startActivity(intent)
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot open $intent: $e")
            false
        }
}
