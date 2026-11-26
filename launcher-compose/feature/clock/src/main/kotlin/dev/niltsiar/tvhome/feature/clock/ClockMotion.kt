package dev.niltsiar.tvhome.feature.clock

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.niltsiar.tvhome.core.designsystem.Motion
import kotlin.random.Random

internal const val MOVE_MS: Long = 60_000
internal const val FADE_OUT_MS: Int = 900
internal const val FADE_IN_MS: Int = 1_200

/** The glow's colour change: 16 steps over 8 s. Animated per frame it would redraw the full-screen gradient ~480 times. */
internal const val GLOW_STEPS: Int = 16
internal const val GLOW_STEP_MS: Long = 500

/** How the Expressive screensaver's shape takes its next turn: per frame, in steps with reduced_motion, at once with animations off. */
internal enum class ShapeChange {
    Animate,
    Step,
    Cut,
}

internal val Motion.shapeChange: ShapeChange
    get() =
        when {
            !animations -> ShapeChange.Cut
            reducedMotion -> ShapeChange.Step
            else -> ShapeChange.Animate
        }

/** The stepped shape change: 7 steps of 300 ms, the clock's fade-out and fade-in, with the third step where the clock moves. */
internal const val SHAPE_STEPS: Int = 7
internal const val SHAPE_STEP_MS: Long = 300

/** Where the clock sits as fractions of the room it has left on screen: 0.1 to 0.9 on each axis, so it never touches an edge. */
internal data class Spot(
    val x: Float,
    val y: Float,
) {
    companion object {
        fun random(random: Random): Spot = Spot(0.1f + 0.8f * random.nextFloat(), 0.1f + 0.8f * random.nextFloat())
    }
}

/** The top-left corner that puts a [content] sized clock at [spot] and keeps all of it inside [container]. */
internal fun IntSize.cornerAt(
    content: IntSize,
    spot: Spot,
): IntOffset =
    IntOffset(
        (maxOf(0, width - content.width) * spot.x).toInt(),
        (maxOf(0, height - content.height) * spot.y).toInt(),
    )

/** The glow stays centred behind the clock. */
internal fun IntSize.centerOf(
    content: IntSize,
    spot: Spot,
): Offset {
    val corner = cornerAt(content, spot)
    return Offset(corner.x + content.width / 2f, corner.y + content.height / 2f)
}

/** The glow colour [step] of [GLOW_STEPS] of the way from [from] to [to]. */
internal fun glowStep(
    from: Color,
    to: Color,
    step: Int,
): Color = lerp(from, to, step / GLOW_STEPS.toFloat())

private const val GLOW_RADIUS_OF_WIDTH = 0.55f

/** A radial gradient from [color] at 25 % opacity at the centre to nothing, over 55 % of the screen width. */
internal fun glowBrush(
    color: Color,
    center: Offset,
    screenWidth: Float,
): Brush =
    Brush.radialGradient(
        colors = listOf(color.copy(alpha = 0x40 / 255f), color.copy(alpha = 0x14 / 255f), color.copy(alpha = 0f)),
        center = center,
        radius = screenWidth * GLOW_RADIUS_OF_WIDTH,
    )
