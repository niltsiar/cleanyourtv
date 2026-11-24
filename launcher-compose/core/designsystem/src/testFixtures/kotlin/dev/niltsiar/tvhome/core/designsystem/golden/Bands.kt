package dev.niltsiar.tvhome.core.designsystem.golden

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

/** A fixed test picture for golden scenes: [width] x [height] pixels of [colours] in vertical bands, left to right. */
public fun bands(
    width: Int,
    height: Int,
    colours: List<Color>,
): ImageBitmap =
    ImageBitmap(width, height).also { image ->
        val canvas = Canvas(image)
        val paint = Paint()
        val band = width.toFloat() / colours.size
        colours.forEachIndexed { i, colour ->
            paint.color = colour
            canvas.drawRect(Rect(i * band, 0f, (i + 1) * band, height.toFloat()), paint)
        }
    }
