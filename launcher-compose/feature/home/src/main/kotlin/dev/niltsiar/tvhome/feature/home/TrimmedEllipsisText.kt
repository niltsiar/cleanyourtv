package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

private val Ellipsis = Char(0x2026).toString()

/** A name cut for [width] pixels of room. */
private data class Cut(
    val width: Int,
    val text: String,
)

/**
 * [text] in at most [maxLines] lines. Where it does not fit, it ends in the single ellipsis character right after its last
 * visible character: Compose's own ellipsis can follow a space. [color], when given, is read as the text is drawn.
 */
@Composable
internal fun TrimmedEllipsisText(
    text: String,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
    color: ColorProducer? = null,
) {
    var cut by remember(text, style, maxLines) { mutableStateOf<Cut?>(null) }
    BasicText(
        text = cut?.text ?: text,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        color = color,
        onTextLayout = { layout ->
            val width = layout.layoutInput.constraints.maxWidth
            val last = layout.lineCount - 1
            if (layout.isLineEllipsized(last)) {
                cut = Cut(width, text.substring(0, layout.getLineEnd(last, visibleEnd = true)).trimEnd() + Ellipsis)
            } else if (cut?.width != width) {
                cut = null
            }
        },
    )
}
