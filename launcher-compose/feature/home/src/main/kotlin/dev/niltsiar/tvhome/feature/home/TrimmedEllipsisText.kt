package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.niltsiar.tvhome.core.designsystem.MarqueeLine
import dev.niltsiar.tvhome.core.designsystem.TvTheme

private val Ellipsis = Char(0x2026).toString()

/** A name cut for [width] pixels of room. */
private data class Cut(
    val width: Int,
    val text: String,
)

/**
 * [text] in at most [maxLines] lines. Where it does not fit, it ends in the single ellipsis character right after its last
 * visible character: Compose's own ellipsis can follow a space. [color], when given, is read as the text is drawn. With
 * [scrolling], a cut text shows whole on one line, scrolling sideways, while [scrolling] is true and the theme's motion is
 * animated ([MarqueeLine]).
 */
@Composable
internal fun TrimmedEllipsisText(
    text: String,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
    color: ColorProducer? = null,
    scrolling: (() -> Boolean)? = null,
) {
    var cut by remember(text, style, maxLines) { mutableStateOf<Cut?>(null) }
    if (scrolling == null) {
        CutText(text, style, maxLines, cut, { cut = it }, color, modifier)
        return
    }
    val scroll = scrolling.takeIf { cut != null && TvTheme.motion.animated }
    Box(modifier) {
        CutText(
            text,
            style,
            maxLines,
            cut,
            { cut = it },
            color,
            if (scroll != null) Modifier.graphicsLayer { alpha = if (scroll()) 0f else 1f } else Modifier,
        )
        if (scroll != null) MarqueeLine(text, style, scroll, Modifier.matchParentSize(), color)
    }
}

@Composable
private fun CutText(
    text: String,
    style: TextStyle,
    maxLines: Int,
    cut: Cut?,
    onCut: (Cut?) -> Unit,
    color: ColorProducer?,
    modifier: Modifier = Modifier,
) {
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
                onCut(Cut(width, text.substring(0, layout.getLineEnd(last, visibleEnd = true)).trimEnd() + Ellipsis))
            } else if (cut?.width != width) {
                onCut(null)
            }
        },
    )
}
