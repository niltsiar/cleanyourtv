package dev.niltsiar.tvhome.data.inputs

import android.content.Context
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.InputsMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * The Inputs button and its menu, read the way the reference launcher reads them. TvInputManager says when to look
 * again (an input added, removed, updated or changing state); each look runs on the IO dispatcher. The callback is
 * registered only while Home is started ([onStart], [onStop]); the last menu stays until then.
 *
 * The button shows when there is something to list: the Live TV app, a source panel, a Coocaa source, or any
 * passthrough input not hidden in the TV's Settings. Streaming boxes have none.
 */
public class InputsProvider(
    context: Context,
    private val config: TvConfig,
) {
    private val appContext = context.applicationContext
    private val source = TvInputSource(appContext)
    private val coocaa = CoocaaSources(appContext)
    private val liveTv = LiveTv(appContext, config)
    private val settings = InputsSettings(config.inputSources, config.inputsConnectedOnly, config.inputsCecStateOnly)
    private val started = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    public val menu: Flow<InputsMenu> =
        started
            .transformLatest { if (it) emitAll(source.changes().map { read() }.flowOn(Dispatchers.IO)) }
            .distinctUntilChanged()

    /** Home is on screen. Main thread. */
    public fun onStart() {
        started.value = true
    }

    /** Home left the screen. Main thread. */
    public fun onStop() {
        started.value = false
    }

    private fun read(): InputsMenu {
        val inputs = source.inputs()
        val sources = if (config.coocaaSources) coocaa.list() else emptyList()
        val app = liveTv.appIntent() != null
        val panel = config.sourcePanelService != null
        return InputsMenu(
            available = app || panel || sources.isNotEmpty() || inputs.any { it.passthrough && !it.hidden },
            rows = inputRows(portRows(inputs, settings), sources, panel, app),
            panelOnly = sources.isEmpty() && panel && !app,
        )
    }
}
