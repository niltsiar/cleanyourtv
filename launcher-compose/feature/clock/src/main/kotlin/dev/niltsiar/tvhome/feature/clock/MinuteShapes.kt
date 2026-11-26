package dev.niltsiar.tvhome.feature.clock

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.niltsiar.tvhome.core.designsystem.LookColors
import dev.niltsiar.tvhome.core.designsystem.Medallion
import dev.niltsiar.tvhome.core.designsystem.ScreensaverShape
import kotlin.math.max

private val SHAPE_SIDE = 440.dp

/** The size a shape has shrunk to as it leaves, and grows from as it comes. */
private const val SHAPE_SHRUNK = 0.6f

/** Where the clock moves in [ClockSceneState.change]: the end of its fade-out. */
private const val HANDOFF = FADE_OUT_MS / (FADE_OUT_MS + FADE_IN_MS).toFloat()

/**
 * The Expressive screensaver's shape behind the clock. Each shape's path is built once per size and kept; a change of
 * turn shrinks and fades the old shape with the clock's fade-out and grows the new one in with its fade-in, by the
 * canvas transform and the paint's alpha alone. Over a picture the shape is a window onto it: the picture fills the shape
 * through one shader, built once per picture, whose matrix undoes the shape's transform.
 */
internal class MinuteShapes {
    private val shapes = ScreensaverShape.entries.map { Medallion(it) }

    private var shaderPicture: ImageBitmap? = null
    private lateinit var shader: Shader
    private lateinit var brush: ShaderBrush
    private val matrix = android.graphics.Matrix()
    private var placedLeft = Float.NaN
    private var placedTop = Float.NaN
    private var placedScale = Float.NaN
    private var placedSize = Size.Unspecified

    /** Draws [turn]'s shape, [change] of the way in from the last turn's, centred on [center]: in its house colour, or filled with [picture]. */
    fun DrawScope.drawShape(
        turn: Int,
        change: Float,
        center: Offset,
        colours: LookColors,
        picture: ImageBitmap?,
    ) {
        val leaving = change < HANDOFF
        val shown = if (leaving) turn - 1 else turn
        // The clock's own fade, from FastOutSlowIn tweens on each side of the handoff: 1 at rest, 0 where it moves.
        val presence =
            if (leaving) {
                1f - FastOutSlowInEasing.transform(change / HANDOFF)
            } else {
                FastOutSlowInEasing.transform((change - HANDOFF) / (1f - HANDOFF))
            }
        if (presence <= 0f) return
        val scale = lerp(SHAPE_SHRUNK, 1f, presence)
        val side = SHAPE_SIDE.toPx()
        val path = shapes[shown.mod(shapes.size)].path(side)
        val left = center.x - side * scale / 2f
        val top = center.y - side * scale / 2f
        val brush = picture?.let { pictureBrush(it, left, top, scale) }
        withTransform({
            translate(left, top)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            if (brush == null) drawPath(path, colours.screensaverShape(shown).withFade(presence)) else drawPath(path, brush, presence)
        }
    }

    /** The picture cropped to fill the screen, as seen through a shape drawn at [left], [top] and [scale]. */
    private fun DrawScope.pictureBrush(
        picture: ImageBitmap,
        left: Float,
        top: Float,
        scale: Float,
    ): ShaderBrush {
        val newPicture = picture !== shaderPicture
        if (newPicture) {
            shaderPicture = picture
            shader = ImageShader(picture)
            brush = ShaderBrush(shader)
        }
        if (newPicture || left != placedLeft || top != placedTop || scale != placedScale || size != placedSize) {
            placedLeft = left
            placedTop = top
            placedScale = scale
            placedSize = size
            val crop = max(size.width / picture.width, size.height / picture.height)
            matrix.setScale(crop, crop)
            matrix.postTranslate((size.width - picture.width * crop) / 2f - left, (size.height - picture.height * crop) / 2f - top)
            matrix.postScale(1f / scale, 1f / scale)
            shader.setLocalMatrix(matrix)
        }
        return brush
    }
}
