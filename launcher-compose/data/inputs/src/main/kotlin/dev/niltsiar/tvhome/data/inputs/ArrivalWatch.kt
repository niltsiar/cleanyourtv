package dev.niltsiar.tvhome.data.inputs

import dev.niltsiar.tvhome.core.model.InputArrival
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputRow

/**
 * Follows which HDMI ports report a device, reading by reading, from the first one after Home came on screen. The
 * first reading is only the starting point: a device already connected then is not an arrival. After it, a port that
 * is connected and was not at the reading before is, named as its Inputs row names it. [update] gives the latest
 * arrival while its port stays connected, and null once it leaves.
 *
 * It reads the rows [portEntries] makes, so it sees the states the Inputs menu shows: a TV that reports every port
 * connected has none to tell, and with inputs_cec_state_only only a CEC device's own state counts.
 */
internal class ArrivalWatch(
    private val nextToken: () -> Int,
) {
    private var seen: Set<String>? = null
    private var arrival: Pair<String, InputArrival>? = null

    fun update(entries: List<Pair<String?, InputRow>>): InputArrival? {
        val connected = LinkedHashMap<String, InputRow.Port>()
        for ((port, row) in entries) {
            if (port != null && row is InputRow.Port && row.connection == InputConnection.Connected) connected.putIfAbsent(port, row)
        }
        val before = seen
        seen = connected.keys.toSet()
        if (before == null) return null
        val fresh = connected.keys.lastOrNull { it !in before }
        val held = arrival
        arrival =
            when {
                fresh != null -> connected.getValue(fresh).let { fresh to InputArrival(it.label, it.target, nextToken()) }
                held == null -> null
                else -> connected[held.first]?.let { held.first to held.second.copy(label = it.label, target = it.target) }
            }
        return arrival?.second
    }
}
