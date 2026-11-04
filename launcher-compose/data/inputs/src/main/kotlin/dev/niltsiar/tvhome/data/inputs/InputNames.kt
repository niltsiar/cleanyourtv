package dev.niltsiar.tvhome.data.inputs

import java.util.Locale

private val PhilipsType = Regex("[A-Z][A-Z0-9_]{2,}")

/**
 * Philips (TP Vision) saves an input's name from its Sources menu as "Name&TYPE", the type picking the icon there
 * ("Xbox&GAME_CONSOLE", "Ordinateur&COMPUTER"), and an unnamed port as the text "null". Shown as is, the Inputs
 * menu would read "Xbox&GAME_CONSOLE · HDMI 1" and "null · HDMI 4". This gives "🎮 Xbox" and no name; the TV's own
 * setting is left as it is. Null for no name.
 */
internal fun tidyInputName(raw: CharSequence?): String? {
    val text = raw?.toString()?.trim().orEmpty()
    if (text.isEmpty() || text == "null") return null
    val amp = text.lastIndexOf('&')
    // Only a type in Philips' form is taken off, so "Tom & Jerry" stays.
    if (amp < 0 || !PhilipsType.matches(text.substring(amp + 1))) return text
    val name = text.substring(0, amp).trim()
    val emoji = typeEmoji(text.substring(amp + 1))
    return when {
        name.isEmpty() -> emoji
        emoji == null -> name
        else -> "$emoji $name"
    }
}

private fun typeEmoji(type: String): String? {
    val t = type.uppercase(Locale.ROOT)
    return when {
        "GAME" in t -> "🎮"
        "COMPUTER" in t || t == "PC" -> "💻"
        "BLU" in t || "DVD" in t || "DISC" in t -> "📀"
        "BOX" in t || "STB" in t || "SAT" in t || "CABLE" in t -> "📡"
        "AUDIO" in t || "SOUND" in t || "AMP" in t -> "🔊"
        "CAM" in t -> "📷"
        else -> null
    }
}
