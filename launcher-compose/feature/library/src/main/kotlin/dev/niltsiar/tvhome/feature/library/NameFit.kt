package dev.niltsiar.tvhome.feature.library

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.max

private const val FLOOR = 0.8f
private const val STEP = 1f

/**
 * Sizes a name from [start] down in 1 sp steps to 80% of it, and keeps the largest at which it shows in [NAME_LINES] lines
 * with no line ending inside a word. Where none does, the name keeps [start].
 */
internal data class WholeWords(
    val start: TextUnit,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(
        constraints: Constraints,
        text: AnnotatedString,
    ): TextUnit = wholeWordsSize(start, text.text) { performLayout(constraints, text, it) } ?: start
}

/** The largest size from [start] down to its floor at which [layoutAt] shows [text] in whole words, or null. */
internal fun wholeWordsSize(
    start: TextUnit,
    text: String,
    layoutAt: (TextUnit) -> TextLayoutResult,
): TextUnit? {
    val floor = start.value * FLOOR
    var size = start.value
    while (true) {
        if (layoutAt(size.sp).showsWholeWords(text)) return size.sp
        if (size <= floor) return null
        size = max(size - STEP, floor)
    }
}

private fun TextLayoutResult.showsWholeWords(text: String): Boolean {
    if (lineCount > NAME_LINES || isLineEllipsized(lineCount - 1)) return false
    return (0 until lineCount - 1).none { line ->
        val end = getLineEnd(line)
        text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
    }
}
