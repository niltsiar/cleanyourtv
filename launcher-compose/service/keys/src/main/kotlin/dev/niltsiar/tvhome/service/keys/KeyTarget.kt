package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService

/** The action words of app_keys. "ignore" does nothing on purpose: the button is swallowed, for a shortcut pressed by accident. */
internal enum class ActionWord(
    val word: String,
) {
    Back("back"),
    Home("home"),
    Recents("recents"),
    Mute("mute"),
    Sleep("sleep"),
    Screensaver("screensaver"),
    Settings("settings"),
    Wifi("wifi"),
    Library("library"),
    Ignore("ignore"),
}

/** What an app_keys target stands for. */
internal sealed interface KeyTarget {
    data class Action(
        val action: ActionWord,
    ) : KeyTarget

    /** `inputs`: Home's Inputs list. */
    data object Inputs : KeyTarget

    /** `guide:<package>`: that Live TV app's program guide. */
    data class Guide(
        val packageName: String,
    ) : KeyTarget

    /** `input:<id>`: switch to that TV input. */
    data class Input(
        val id: String,
    ) : KeyTarget

    /** `package/.Activity`: that activity rather than the app's launcher entry. */
    data class Activity(
        val component: String,
    ) : KeyTarget

    /** A package: its TV launcher entry, else its phone one. */
    data class App(
        val packageName: String,
    ) : KeyTarget
}

private const val INPUT_PREFIX = "input:"
private const val GUIDE_PREFIX = "guide:"

/** The TV input id of an "input:<id>" target; null for any other target or an empty id. */
internal fun inputId(target: String): String? =
    target
        .takeIf { it.startsWith(INPUT_PREFIX) }
        ?.removePrefix(INPUT_PREFIX)
        ?.trim()
        ?.ifEmpty { null }

/**
 * True for a "package/.Activity" target. An input id has slashes too, and ComponentName.unflattenFromString() would read
 * "input:com.tcl.tvinput" as its package, so an "input:" target never counts.
 */
internal fun isActivity(target: String): Boolean = inputId(target) == null && target.indexOf('/') > 0

internal fun parseTarget(target: String): KeyTarget {
    ActionWord.entries.firstOrNull { it.word == target }?.let { return KeyTarget.Action(it) }
    if (target == "inputs") return KeyTarget.Inputs
    if (target.startsWith(GUIDE_PREFIX)) {
        target
            .removePrefix(GUIDE_PREFIX)
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { return KeyTarget.Guide(it) }
    }
    inputId(target)?.let { return KeyTarget.Input(it) }
    return if (isActivity(target)) KeyTarget.Activity(target) else KeyTarget.App(target)
}

/**
 * The accessibility global action for an action word, or null when it is not one. "sleep" is GLOBAL_ACTION_LOCK_SCREEN,
 * which puts a TV in standby (as the Sleep Timer add-on does); Android has it from 28.
 */
internal fun globalAction(
    action: ActionWord,
    sdk: Int,
): Int? =
    when (action) {
        ActionWord.Back -> AccessibilityService.GLOBAL_ACTION_BACK
        ActionWord.Home -> AccessibilityService.GLOBAL_ACTION_HOME
        ActionWord.Recents -> AccessibilityService.GLOBAL_ACTION_RECENTS
        ActionWord.Sleep -> if (sdk >= 28) AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN else null
        else -> null
    }
