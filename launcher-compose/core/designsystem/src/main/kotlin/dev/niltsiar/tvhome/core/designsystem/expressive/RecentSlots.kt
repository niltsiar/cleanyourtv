package dev.niltsiar.tvhome.core.designsystem.expressive

/**
 * The values of the last keys kept, one per slot of [values], so a value read in every frame is built once: [find] scans
 * the keys, and [keep] puts a new value in the oldest slot.
 */
internal class RecentSlots<V : Any>(
    private val values: Array<V?>,
) {
    private val keys = LongArray(values.size)
    private var next = 0

    fun find(key: Long): V? {
        for (i in keys.indices) {
            val kept = values[i]
            if (kept != null && keys[i] == key) return kept
        }
        return null
    }

    fun keep(
        key: Long,
        value: V,
    ): V {
        keys[next] = key
        values[next] = value
        next = (next + 1) % keys.size
        return value
    }
}
