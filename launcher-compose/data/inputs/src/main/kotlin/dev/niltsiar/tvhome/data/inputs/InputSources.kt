package dev.niltsiar.tvhome.data.inputs

/** What `input_sources` says about an input that is not a number. */
internal const val HIDDEN_SOURCE: Int = -1

/**
 * `input_sources` maps the framework input whose id ends in /HW5 to a vendor source number ("HW5=23"); "HW8=-"
 * leaves that input out of the Inputs menu (a port the board reports but the set does not have). The number, -1
 * ([HIDDEN_SOURCE]) for a left-out input, or null when the input is not mapped.
 */
internal fun mappedSource(
    mappings: List<String>,
    inputId: String,
): Int? {
    val input = inputId.substringAfterLast('/')
    for (mapping in mappings) {
        val equals = mapping.indexOf('=')
        if (equals <= 0 || mapping.substring(0, equals).trim() != input) continue
        val value = mapping.substring(equals + 1).trim()
        return if (value == "-") HIDDEN_SOURCE else value.toIntOrNull()
    }
    return null
}

/**
 * True when `input_action` names an activity ("package/.Activity", "package/package.Activity") rather than an intent
 * action. Actions never contain a slash, so the two forms cannot be confused.
 */
internal fun isComponent(inputAction: String?): Boolean {
    if (inputAction == null) return false
    val slash = inputAction.indexOf('/')
    return slash > 0 && slash < inputAction.length - 1 && inputAction.indexOf('/', slash + 1) < 0
}
