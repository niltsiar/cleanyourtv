package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ScrollCuePillWidth = 36.dp

/** The height of the pill that [drawScrollDownCue] draws; it is 36 dp wide. */
public val ScrollCuePillHeight: Dp = 20.dp

/** The alpha of the theme's text colour for a panel's scroll cue chevron, and for the pill under the down one. */
public const val SCROLL_CUE_ALPHA: Float = 0.55f
public const val SCROLL_CUE_PILL_ALPHA: Float = 0.15f

private val CueStroke = 2.dp
private val DownWidth = 18.dp
private val DownHeight = 9.dp

/**
 * The cue that more lies below a scrolled view: a down chevron in a 36 dp x [ScrollCuePillHeight] pill, centred on
 * the scope's middle and [centreY]. The caller picks the colours, [chevron] and [pill], for the ground it draws on.
 */
public fun DrawScope.drawScrollDownCue(
    centreY: Float,
    chevron: Color,
    pill: Color,
) {
    val size = Size(ScrollCuePillWidth.toPx(), ScrollCuePillHeight.toPx())
    drawRoundRect(pill, Offset((this.size.width - size.width) / 2f, centreY - size.height / 2f), size, CornerRadius(size.height / 2f))
    drawCueChevron(chevron, centreY, DownWidth.toPx(), DownHeight.toPx(), down = true)
}

/** A round-capped chevron [width] wide and [height] tall, centred on the scope's middle and [centreY], pointing down or up. */
public fun DrawScope.drawCueChevron(
    color: Color,
    centreY: Float,
    width: Float,
    height: Float,
    down: Boolean,
) {
    val centreX = size.width / 2f
    val halfWidth = width / 2f
    val halfHeight = height / 2f * if (down) 1f else -1f
    val path =
        Path().apply {
            moveTo(centreX - halfWidth, centreY - halfHeight)
            lineTo(centreX, centreY + halfHeight)
            lineTo(centreX + halfWidth, centreY - halfHeight)
        }
    drawPath(path, color, style = Stroke(CueStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
