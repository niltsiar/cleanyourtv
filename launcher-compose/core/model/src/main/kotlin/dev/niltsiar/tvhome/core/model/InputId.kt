package dev.niltsiar.tvhome.core.model

/** The prefix of an app_keys target or a launch_actions value that switches to a TV input. */
public const val INPUT_PREFIX: String = "input:"

/**
 * The TV input id of an "input:<id>" value, or null when the value is not one. An empty id ("input:" alone) is none:
 * the tile launches normally, the key opens an app, rather than switching to nothing.
 */
public fun inputIdOf(value: String?): String? =
    value
        ?.takeIf { it.startsWith(INPUT_PREFIX) }
        ?.removePrefix(INPUT_PREFIX)
        ?.trim()
        ?.ifEmpty { null }
