package dev.niltsiar.tvhome.core.designsystem

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Shader
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.graphics.shapes.toPath
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The hole of the look flip: a [MaterialShapes.Cookie9Sided] about an origin, through which what is drawn below shows, while a
 * picture of the page as it was covers the rest. The outline is built once, about its centre and scaled so that the largest
 * circle it holds has a radius of 1. Each frame scales it with the canvas and fills its inverse with the picture as a shader,
 * so a frame builds no path and clips to none.
 */
internal class FlipHole {
    private val outline = cookieOutline()
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var shader: BitmapShader? = null

    /** The picture the shader draws, held until [drop]. */
    var shaded: ImageBitmap? = null
        private set

    /**
     * Covers the draw scope with [picture], stretched to its size, except for the hole about [origin]: none at a [progress] of
     * 0, and at 1 one that holds the corner farthest from [origin], so that nothing of the picture is left.
     */
    fun DrawScope.drawFlip(
        picture: ImageBitmap,
        origin: Offset,
        progress: Float,
    ) {
        val shader = shaderOf(picture)
        val reach = progress * farthestCorner(origin)
        matrix.setScale(size.width / picture.width, size.height / picture.height)
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.save()
            canvas.clipRect(0f, 0f, size.width, size.height)
            if (reach <= 0f) {
                shader.setLocalMatrix(matrix)
                canvas.drawPaint(paint)
            } else {
                canvas.translate(origin.x, origin.y)
                canvas.scale(reach, reach)
                // The canvas scale applies to the shader too: undo it, so the picture stays where the page is.
                matrix.postTranslate(-origin.x, -origin.y)
                matrix.postScale(1f / reach, 1f / reach)
                shader.setLocalMatrix(matrix)
                canvas.drawPath(outline, paint)
            }
            canvas.restore()
        }
    }

    /** Lets go of the picture. */
    fun drop() {
        paint.shader = null
        shader = null
        shaded = null
    }

    private fun shaderOf(picture: ImageBitmap): BitmapShader {
        shader?.let { if (shaded === picture) return it }
        return BitmapShader(picture.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
            paint.shader = it
            shader = it
            shaded = picture
        }
    }

    private fun DrawScope.farthestCorner(origin: Offset): Float {
        val across = max(origin.x, size.width - origin.x)
        val down = max(origin.y, size.height - origin.y)
        return hypot(across, down)
    }
}

/** Cookie9Sided about its centre, the largest circle it holds 1 in radius, filled outside. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun cookieOutline(): Path {
    val polygon = MaterialShapes.Cookie9Sided
    val path = polygon.toPath()
    path.offset(-polygon.centerX, -polygon.centerY)
    val measure = PathMeasure(path, false)
    val point = FloatArray(2)
    var inner = Float.MAX_VALUE
    do {
        for (i in 0..OUTLINE_SAMPLES) {
            measure.getPosTan(measure.length * i / OUTLINE_SAMPLES, point, null)
            inner = min(inner, hypot(point[0], point[1]))
        }
    } while (measure.nextContour())
    path.transform(Matrix().apply { setScale(1f / inner, 1f / inner) })
    path.fillType = Path.FillType.INVERSE_WINDING
    return path
}
