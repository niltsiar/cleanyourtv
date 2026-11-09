package dev.niltsiar.tvhome.data.inputs

import android.content.Context
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.InputsMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Inputs button and its menu, read the way the reference launcher reads them. TvInputManager says when to look
 * again (an input added, removed, updated or changing state); each look runs on the IO dispatcher. The callback is
 * registered only while Home is started ([onStart], [onStop]); the last menu stays until then.
 *
 * The button shows when there is something to list: the Live TV app, a source panel, a Coocaa source, or any
 * passthrough input not hidden in the TV's Settings. Streaming boxes have none.
 *
 * With input_arrival the menu also carries the device that was switched on since Home came on screen ([ArrivalWatch]).
 */
public class InputsProvider(
    context: Context,
    private val config: StateFlow<TvConfig>,
) {
    private val appContext = context.applicationContext
    private val source = TvInputSource(appContext)
    private val coocaa = CoocaaSources(appContext)
    private val liveTv = LiveTv(appContext, config)
    private val started = MutableStateFlow(false)
    private val arrivals = AtomicInteger()

    @OptIn(ExperimentalCoroutinesApi::class)
    public val menu: Flow<InputsMenu> =
        started
            .transformLatest { if (it) emitAll(readings()) }
            .distinctUntilChanged()

    /** Home is on screen. Main thread. */
    public fun onStart() {
        started.value = true
    }

    /** Home left the screen. Main thread. */
    public fun onStop() {
        started.value = false
    }

    /**
     * Each look at the TV's inputs while Home is started; a new start, or input_arrival turning on or off, begins a new
     * [ArrivalWatch].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun readings(): Flow<InputsMenu> =
        config.map { it.inputArrival }.distinctUntilChanged().flatMapLatest { arrival ->
            val watch = if (arrival) ArrivalWatch(arrivals::incrementAndGet) else null
            source.changes().map { read(watch) }.flowOn(Dispatchers.IO)
        }

    private fun read(watch: ArrivalWatch?): InputsMenu {
        val current = config.value
        val inputs = source.inputs()
        val sources = if (current.coocaaSources) coocaa.list() else emptyList()
        val app = liveTv.appIntent() != null
        val panel = current.sourcePanelService != null
        val settings = InputsSettings(current.inputSources, current.inputsConnectedOnly, current.inputsCecStateOnly)
        val ports = portEntries(inputs, settings)
        return InputsMenu(
            available = app || panel || sources.isNotEmpty() || inputs.any { it.passthrough && !it.hidden },
            rows = inputRows(ports.map { it.second }, sources, panel, app),
            panelOnly = sources.isEmpty() && panel && !app,
            arrival = watch?.update(ports),
        )
    }
}
