package dev.niltsiar.tvhome.data.inputs

import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputRow
import dev.niltsiar.tvhome.core.model.InputTarget
import java.util.Locale

/** The kinds of TV input the menu tells apart. */
internal enum class PortType {
    Hdmi,
    Tuner,
    Other,
}

/** TvInputManager's input state; [None] is no state worth showing. */
internal enum class PortState {
    None,
    Connected,
    Standby,
    Disconnected,
}

/** One input as TvInputManager reports it. [parentId] is the HDMI port a CEC device sits on. */
internal data class TvInput(
    val id: String,
    val parentId: String?,
    val type: PortType,
    val system: Boolean,
    val passthrough: Boolean,
    val hidden: Boolean,
    val label: String?,
    val customLabel: String?,
    val state: PortState,
)

/** The theme keys that shape the rows: input_sources, inputs_connected_only and inputs_cec_state_only. */
internal data class InputsSettings(
    val inputSources: List<String> = emptyList(),
    val connectedOnly: Boolean = false,
    val cecStateOnly: Boolean = false,
)

/**
 * Which rows inputs_connected_only folds behind "More inputs". An AV jack has no hotplug detection, so it always folds.
 * An HDMI port or CEC device stays only while it reports Connected: a port with nothing on it can report standby
 * (TCL), and so does a device asleep, so both fold. A row with no state to show stays.
 */
internal fun inputFolds(
    hdmi: Boolean,
    state: PortState,
): Boolean = !hdmi || (state != PortState.None && state != PortState.Connected)

private fun PortState.connection(): InputConnection =
    when (this) {
        PortState.Connected -> InputConnection.Connected
        PortState.Disconnected -> InputConnection.Disconnected
        else -> InputConnection.Unknown
    }

/**
 * The rows for the passthrough inputs: HDMI ports first, then the rest, each group in label order, with a line before
 * the first non-HDMI row. Left out: inputs hidden in the TV's Settings, inputs mapped to "-" in input_sources, and
 * the CEC devices' own entries. A CEC device takes its port's place as "Device · HDMI 1", and a bare port is listed
 * only when nothing on it speaks CEC. A name given in Settings > Inputs goes before the port's.
 *
 * Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in, so the HDMI state
 * is dropped then; a TV with every port really in use loses it too. inputs_cec_state_only drops port states for TVs
 * that put them on the wrong port (TCL UnionTV), and a CEC device row keeps its own.
 */
internal fun portRows(
    all: List<TvInput>,
    settings: InputsSettings,
): List<InputRow> = portEntries(all, settings).map { it.second }

/** [portRows], each row with the id of the HDMI port it stands for: the port itself, or the port a CEC device sits on. */
internal fun portEntries(
    all: List<TvInput>,
    settings: InputsSettings,
): List<Pair<String?, InputRow>> {
    val inputs =
        all.sortedWith(
            compareBy<TvInput> { it.type != PortType.Hdmi }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label.orEmpty() },
        )
    val ids = all.mapTo(HashSet()) { it.id }

    fun TvInput.onPort() = parentId != null && parentId in ids
    val devices = inputs.filter { it.onPort() && it.passthrough && !it.hidden }.groupBy { it.parentId }
    val ports = inputs.filter { it.passthrough && it.type == PortType.Hdmi && !it.onPort() }
    val stateBogus = settings.cecStateOnly || (ports.size > 1 && ports.all { it.state == PortState.Connected })
    val rows = mutableListOf<Pair<String?, InputRow>>()
    var sawNonHdmi = false
    for (input in inputs) {
        if (!input.passthrough || input.hidden || input.onPort()) continue
        if (mappedSource(settings.inputSources, input.id) == HIDDEN_SOURCE) continue
        val hdmi = input.type == PortType.Hdmi
        var label = input.label ?: input.id
        if (hdmi && "HDMI" !in label.uppercase(Locale.ROOT)) label = "HDMI $label"
        val custom = tidyInputName(input.customLabel)
        val named = !custom.isNullOrEmpty() && custom != label
        if (!hdmi && rows.isNotEmpty() && !sawNonHdmi) rows += null to InputRow.Separator(folded = settings.connectedOnly)
        if (!hdmi) sawNonHdmi = true
        val onThisPort = devices[input.id]
        if (onThisPort != null) {
            for (device in onThisPort) {
                val own = tidyInputName(device.customLabel)
                val name =
                    if (!own.isNullOrEmpty()) {
                        own
                    } else if (named) {
                        custom
                    } else {
                        device.label
                    }
                val state = if (stateBogus && !settings.cecStateOnly) PortState.None else device.state
                rows +=
                    input.id to
                    InputRow.Port(
                        label = if (name.isNullOrEmpty()) label else "$name · $label",
                        icon = InputIcon.Hdmi,
                        connection = state.connection(),
                        target = InputTarget.TvInput(device.id),
                        folded = settings.connectedOnly && inputFolds(hdmi = true, state),
                    )
            }
            continue
        }
        if (named) label = "$custom · $label"
        // Analog AV jacks have no hotplug detection, and some TVs report them as always connected.
        val state = if (!hdmi || stateBogus) PortState.None else input.state
        rows +=
            (if (hdmi) input.id else null) to
            InputRow.Port(
                label = label,
                icon = if (hdmi) InputIcon.Hdmi else InputIcon.Av,
                connection = state.connection(),
                target = InputTarget.TvInput(input.id),
                folded = settings.connectedOnly && inputFolds(hdmi, state),
            )
    }
    return rows
}

/**
 * The whole menu: [ports] with "More inputs" after them where rows fold; the Coocaa sources where TvInputManager
 * listed nothing; one "Inputs" row for the TV's source panel where that is still empty and a panel is set; and
 * the Live TV row last, behind a line.
 */
internal fun inputRows(
    ports: List<InputRow>,
    coocaa: List<CoocaaSource>,
    sourcePanel: Boolean,
    liveTv: Boolean,
): List<InputRow> =
    buildList {
        addAll(ports)
        if (ports.any { (it as? InputRow.Port)?.folded == true || (it as? InputRow.Separator)?.folded == true }) add(InputRow.More)
        if (isEmpty()) {
            for (source in coocaa) {
                val icon =
                    when (source.name) {
                        "HDMI" -> InputIcon.Hdmi
                        "VOD" -> InputIcon.LiveTv
                        else -> InputIcon.Av
                    }
                add(InputRow.Port(source.label, icon, InputConnection.Unknown, InputTarget.Coocaa(source.name, source.index)))
            }
        }
        if (isEmpty() && sourcePanel) add(InputRow.Panel)
        if (liveTv) {
            if (isNotEmpty()) add(InputRow.Separator())
            add(InputRow.LiveTv)
        }
    }
