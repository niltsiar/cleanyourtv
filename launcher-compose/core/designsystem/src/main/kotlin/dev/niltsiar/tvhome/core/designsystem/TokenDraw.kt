package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp

/**
 * [shape] filled with the colour [colour] gives, as `background` fills it, read in the draw phase: a change of look or seed
 * redraws it and recomposes nothing.
 */
public fun Modifier.tokenBackground(
    colour: ColorProducer,
    shape: Shape,
): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind { drawOutline(outline, colour()) }
    }

/**
 * A line [width] wide inside the edge of [shape], in the colour [colour] gives, as `border` draws it, read in the draw
 * phase.
 */
public fun Modifier.tokenBorder(
    width: Dp,
    colour: ColorProducer,
    shape: Shape,
): Modifier =
    drawWithCache {
        val stroke = width.toPx()
        val half = Offset(stroke / 2f, stroke / 2f)
        val inset = Size(size.width - stroke, size.height - stroke)
        when (val outline = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Rounded -> {
                val corner = outline.roundRect.topLeftCornerRadius
                val inner = CornerRadius(maxOf(0f, corner.x - stroke / 2f), maxOf(0f, corner.y - stroke / 2f))
                val style = Stroke(stroke)
                onDrawWithContent {
                    drawContent()
                    drawRoundRect(colour(), topLeft = half, size = inset, cornerRadius = inner, style = style)
                }
            }

            is Outline.Rectangle -> {
                val style = Stroke(stroke)
                onDrawWithContent {
                    drawContent()
                    drawRect(colour(), topLeft = half, size = inset, style = style)
                }
            }

            // Twice the width, clipped to the shape, leaves the inner half.
            is Outline.Generic -> {
                val style = Stroke(stroke * 2f)
                onDrawWithContent {
                    drawContent()
                    clipPath(outline.path) { drawPath(outline.path, colour(), style = style) }
                }
            }
        }
    }
