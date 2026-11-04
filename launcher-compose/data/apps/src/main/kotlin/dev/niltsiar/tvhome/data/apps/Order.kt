package dev.niltsiar.tvhome.data.apps

private const val MAGIC = "tvhome-order 1"
private const val DOCK = "dock "
private const val DOCKED = "docked "

/**
 * The owner's tile order, kept in order.txt: [keys] are tile keys (AppEntry.key), first to last. The file also records
 * the dock_apps it was saved under ([dock]), and a saved order is ignored once dock_apps differs, so a pushed theme's
 * dock takes effect. [docked] says how many of the first keys are in the dock, or is -1 when dock_apps decides.
 *
 * Text, UTF-8: `tvhome-order 1`, `dock <dock_apps, comma-joined>`, optionally `docked <n>`, then one key per line.
 */
internal data class Order(
    val dock: String,
    val keys: List<String>,
    val docked: Int = -1,
) {
    /** Rank of each saved key (0 first) while this order applies to [dockApps], else empty. */
    fun ranks(dockApps: List<String>): Map<String, Int> =
        if (appliesTo(dockApps)) keys.withIndex().associate { (index, key) -> key to index } else emptyMap()

    /** The saved dock size while this order applies to [dockApps] and recorded one, else -1. */
    fun dockedFor(dockApps: List<String>): Int = if (appliesTo(dockApps)) docked else -1

    private fun appliesTo(dockApps: List<String>) = dock == dockKey(dockApps)

    fun encode(): String =
        buildString {
            append(MAGIC).append('\n')
            append(DOCK).append(dock).append('\n')
            if (docked >= 0) append(DOCKED).append(docked).append('\n')
            keys.forEach { append(it).append('\n') }
        }

    companion object {
        fun dockKey(dockApps: List<String>): String = dockApps.joinToString(",")

        /** The order in [text], or null when it is not one: wrong header, a blank or duplicate key, `docked` past the keys. */
        fun decode(text: String): Order? {
            val lines = text.split('\n')
            if (lines.size < 2 || lines[0] != MAGIC || !lines[1].startsWith(DOCK)) return null
            var docked = -1
            var first = 2
            if (lines.size > 2 && lines[2].startsWith(DOCKED)) {
                docked = lines[2].removePrefix(DOCKED).toIntOrNull()?.takeIf { it >= 0 } ?: return null
                first = 3
            }
            val keys = LinkedHashSet<String>()
            for (index in first until lines.size) {
                val key = lines[index]
                if (key.isEmpty()) {
                    if (index == lines.lastIndex) break
                    return null
                }
                if (!keys.add(key)) return null
            }
            if (docked > keys.size) return null
            return Order(lines[1].removePrefix(DOCK), keys.toList(), docked)
        }
    }
}
