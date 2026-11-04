package dev.niltsiar.tvhome.data.inputs

import java.util.Locale

private val HardwareTuner = Regex(".*/HW\\d+$")

/**
 * The tuner input Live TV opens: of the system apps' tuners, restricted to [liveTvInputId] when one is set (and
 * [rootChannel] is not), the hardware tuner (the framework names those ".../HW<n>") over a software one (AirPlay
 * registers one, TCL G05), a digital one over an analog one (MediaTek's AnalogInputService sorts first, and Live TV
 * then opens a dead ATV source), then the lowest id. Null when there is none.
 */
internal fun chooseTuner(
    inputs: List<TvInput>,
    liveTvInputId: String?,
    rootChannel: Boolean,
): String? =
    inputs
        .filter { it.type == PortType.Tuner && it.system && (liveTvInputId == null || rootChannel || liveTvInputId == it.id) }
        .minWithOrNull(
            compareBy<TvInput> { !HardwareTuner.matches(it.id) }
                .thenBy { "analog" in it.id.lowercase(Locale.ROOT) }
                .thenBy { it.id },
        )?.id
