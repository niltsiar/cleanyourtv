package dev.niltsiar.tvhome.data.apps

import android.util.AtomicFile
import android.util.Log
import dev.niltsiar.tvhome.core.model.InputIcon
import dev.niltsiar.tvhome.core.model.InputTile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

private const val TAG = "TvHome.InputPins"

internal const val MAX_PINS: Int = 64
private const val MAX_PIN_BYTES = MAX_PINS * 1024
private const val MAX_ID_LENGTH = 255
private const val NO_PORT = "-"

/** The inputs pinned to Home, in the order they were pinned, one per line: `<id>\t<port or ->\t<icon>\t<label>`. */
internal const val INPUT_PINS_FILE = "input_tiles.txt"

/** An input pinned to Home: its [tile], and the [label] its Inputs row read when it was pinned. */
internal data class InputPin(
    val tile: InputTile,
    val label: String,
)

/** One pin per line, in list order. Throws for an id or port that is not an input id, or more than [MAX_PINS] pins. */
internal fun encodePins(pins: List<InputPin>): String {
    require(pins.size <= MAX_PINS) { "Too many pins" }
    return pins.joinToString("") { (tile, label) ->
        val port = tile.port
        require(isValidInputId(tile.id) && (port == null || isValidInputId(port))) { "Invalid input id" }
        "${tile.id}\t${port ?: NO_PORT}\t${tile.icon.name}\t${cleanLabel(label, tile.id)}\n"
    }
}

/**
 * The pins in [text]. A label is cleaned as an app's is. Throws for a line without four fields, an id, port or icon
 * that is not one, an id pinned twice, or too many lines.
 */
internal fun decodePins(text: String): List<InputPin> {
    val ids = HashSet<String>()
    val lines = text.split('\n').let { if (it.last().isEmpty()) it.dropLast(1) else it }
    require(lines.size <= MAX_PINS) { "Too many pins" }
    return lines.map { line ->
        val fields = line.split('\t', limit = 4)
        require(fields.size == 4) { "Invalid pin file" }
        val (id, port, iconName, label) = fields
        val icon = InputIcon.entries.firstOrNull { it.name == iconName }
        require(isValidInputId(id) && ids.add(id) && (port == NO_PORT || isValidInputId(port)) && icon != null) { "Invalid pin file" }
        InputPin(InputTile(id, port.takeIf { it != NO_PORT }, icon), cleanLabel(label, id))
    }
}

/** A TvInputManager id: a component in short form and an optional suffix, so it always has a slash, with no blanks. */
internal fun isValidInputId(id: String): Boolean =
    id.length in 3..MAX_ID_LENGTH && '/' in id && id.none { it.isWhitespace() || it.isISOControl() }

/** The pin file in the app's private files. Changes run one at a time: each reads the file and writes it back. */
internal class InputPinStore(
    private val dir: File,
) {
    private val file get() = AtomicFile(File(dir, INPUT_PINS_FILE))

    /** The pins, none when the file is missing or unreadable. */
    fun load(): List<InputPin> =
        try {
            readStrict()
        } catch (e: IOException) {
            Log.w(TAG, "cannot read input pins: $e")
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "cannot read input pins: $e")
            emptyList()
        }

    /**
     * Pins [tile] under [label] at the end, or in its place when its id is pinned already. False when the file could not
     * be read or written, or the pin would pass [MAX_PINS]; an unreadable file is left as it is.
     */
    @Synchronized
    fun pin(
        tile: InputTile,
        label: String,
    ): Boolean =
        change { pins ->
            val pin = InputPin(tile, label)
            val index = pins.indexOfFirst { it.tile.id == tile.id }
            if (index < 0) pins + pin else pins.toMutableList().apply { set(index, pin) }
        }

    /** Takes the pin of [id] out. False when the file could not be read or written. */
    @Synchronized
    fun unpin(id: String): Boolean = change { pins -> pins.filter { it.tile.id != id } }

    private fun change(edit: (List<InputPin>) -> List<InputPin>): Boolean {
        val bytes =
            try {
                encodePins(edit(readStrict())).toByteArray(Charsets.UTF_8)
            } catch (e: IOException) {
                Log.w(TAG, "keeping input pins unchanged: $e")
                return false
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "keeping input pins unchanged: $e")
                return false
            }
        val atomic = file
        val out =
            try {
                atomic.startWrite()
            } catch (e: IOException) {
                Log.w(TAG, "cannot save input pins: $e")
                return false
            }
        return try {
            out.write(bytes)
            atomic.finishWrite(out)
            true
        } catch (e: IOException) {
            atomic.failWrite(out)
            Log.w(TAG, "cannot save input pins: $e")
            false
        }
    }

    private fun readStrict(): List<InputPin> {
        val atomic = file
        val base = atomic.baseFile
        if (!base.exists() && !File("$base.bak").exists()) return emptyList()
        atomic.openRead().use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (bytes.size() + count > MAX_PIN_BYTES) throw IOException("Pin file too large")
                bytes.write(buffer, 0, count)
            }
            return decodePins(String(bytes.toByteArray(), Charsets.UTF_8))
        }
    }
}
