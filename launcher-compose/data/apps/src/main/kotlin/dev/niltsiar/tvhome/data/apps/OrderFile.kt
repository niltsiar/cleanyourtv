package dev.niltsiar.tvhome.data.apps

import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException

private const val TAG = "TvHome.Order"

/** The order file in the app's private files. */
internal const val ORDER_FILE_NAME = "order.txt"

/** The saved order, or null when there is none or it does not decode (then it is ignored, not deleted). */
internal fun readOrder(file: File): Order? {
    if (!file.exists()) return null
    return try {
        Order
            .decode(AtomicFile(file).readFully().toString(Charsets.UTF_8))
            .also { if (it == null) Log.w(TAG, "${file.name} is not valid; ignored") }
    } catch (e: IOException) {
        Log.w(TAG, "cannot read ${file.name}: $e")
        null
    }
}

/** Writes [order] atomically. False, with a log line, when the write failed. */
internal fun writeOrder(
    file: File,
    order: Order,
): Boolean {
    val atomic = AtomicFile(file)
    val out =
        try {
            atomic.startWrite()
        } catch (e: IOException) {
            Log.w(TAG, "cannot write ${file.name}: $e")
            return false
        }
    return try {
        out.write(order.encode().toByteArray(Charsets.UTF_8))
        atomic.finishWrite(out)
        true
    } catch (e: IOException) {
        Log.w(TAG, "cannot write ${file.name}: $e")
        atomic.failWrite(out)
        false
    }
}
