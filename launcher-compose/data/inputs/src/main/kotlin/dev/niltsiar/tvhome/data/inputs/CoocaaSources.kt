package dev.niltsiar.tvhome.data.inputs

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri

private const val TAG = "TvHome.Coocaa"
private val Columns = arrayOf("sourceName", "sourceIndex", "displayName")

/** A source of a Coocaa OS TV: its [name] (HDMI, AV, VOD for the tuner, ...), [index] and the [label] its panel shows. */
internal data class CoocaaSource(
    val name: String,
    val index: Int,
    val label: String,
)

/** HDMI ports in port order, then AV, other sources, and the tuner last. */
internal fun sortedCoocaaSources(sources: List<CoocaaSource>): List<CoocaaSource> =
    sources.sortedWith { a, b -> if (a.name == b.name) a.index.compareTo(b.index) else rank(a.name).compareTo(rank(b.name)) }

private fun rank(name: String): Int =
    when (name) {
        "HDMI" -> 0
        "AV" -> 1
        "VOD" -> 3
        else -> 2
    }

/**
 * Inputs on Coocaa OS (Skyworth) TVs, which register nothing with TvInputManager. com.skyworth.tv lists the sources
 * its own Source panel shows, and switches to one and brings up its player, as picking it in that panel does.
 */
internal class CoocaaSources(
    private val context: Context,
) {
    private val sourceList = "content://com.skyworth.tv.provider/system/source_list".toUri()
    private val method = "content://com.skyworth.tv.sdk.provider/method".toUri()

    /** The sources in menu order; empty where the provider is missing. */
    fun list(): List<CoocaaSource> =
        try {
            val out = mutableListOf<CoocaaSource>()
            context.contentResolver.query(sourceList, Columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val label = cursor.getString(2)
                    out += CoocaaSource(name, cursor.getInt(1), if (label.isNullOrEmpty()) name else label)
                }
            }
            sortedCoocaaSources(out)
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot list the Coocaa sources: $e")
            emptyList()
        }

    /** True when com.skyworth.tv switched to the source. */
    fun open(
        name: String,
        index: Int,
    ): Boolean =
        try {
            val extras =
                Bundle().apply {
                    putString("name", name)
                    putInt("index", index)
                }
            context.contentResolver.call(method, "METHOD_GOTO_SOURCE", null, extras)?.getBoolean("ret") ?: false
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot open the Coocaa source $name: $e")
            false
        }
}
