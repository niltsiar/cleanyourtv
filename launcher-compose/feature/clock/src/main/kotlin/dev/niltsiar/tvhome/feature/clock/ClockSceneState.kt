package dev.niltsiar.tvhome.feature.clock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.designsystem.ScreensaverGlows
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * What moves in the screensaver, kept in state that only the layout and draw phases read, so nothing recomposes while it
 * runs for hours. Every [MOVE_MS] the clock fades out, jumps to another [spot] and fades back in, and the glow takes the
 * next colour of [ScreensaverGlows], or in Expressive the shape behind the clock takes its next [turn].
 */
@Stable
internal class ClockSceneState(
    private val random: Random,
    initialFade: Float = 0f,
    initialSpot: Spot = Spot.random(random),
) {
    /** The clock's opacity; read in the draw phase. */
    val fade = Animatable(initialFade)

    var spot by mutableStateOf(initialSpot)
        private set

    private var paletteIndex = random.nextInt(ScreensaverGlows.size)

    var glow by mutableStateOf(ScreensaverGlows[paletteIndex])
        private set

    /** Which shape and colour of their sets the Expressive screensaver shows behind the clock: one more each minute. */
    var turn by mutableIntStateOf(paletteIndex)
        private set

    /** How far the shape is through its change from the last [turn] (0) to this one (1); read in the draw phase. */
    val change = Animatable(1f)

    /** Plays the scene until cancelled. [cycleGlow] false keeps the glow colour; [shapes] null keeps the shape. */
    suspend fun run(
        cycleGlow: Boolean,
        shapes: ShapeChange? = null,
    ) {
        change.snapTo(1f)
        spot = Spot.random(random)
        fade.animateTo(1f, tween(FADE_IN_MS))
        while (true) {
            delay(MOVE_MS)
            coroutineScope {
                if (cycleGlow) launch { shiftGlow() }
                if (shapes == ShapeChange.Animate || shapes == ShapeChange.Step) launch { changeShape(shapes) }
                fade.animateTo(0f, tween(FADE_OUT_MS))
                spot = Spot.random(random)
                if (shapes == ShapeChange.Cut) turn++
                fade.animateTo(1f, tween(FADE_IN_MS))
            }
        }
    }

    /** The next [turn], over the clock's fade-out and fade-in: per frame, or in [SHAPE_STEPS] steps. */
    private suspend fun changeShape(how: ShapeChange) {
        turn++
        change.snapTo(0f)
        if (how == ShapeChange.Animate) {
            change.animateTo(1f, tween(FADE_OUT_MS + FADE_IN_MS, easing = LinearEasing))
        } else {
            for (step in 1..SHAPE_STEPS) {
                delay(SHAPE_STEP_MS)
                change.snapTo(step / SHAPE_STEPS.toFloat())
            }
        }
    }

    private suspend fun shiftGlow() {
        val from = glow
        paletteIndex = (paletteIndex + 1) % ScreensaverGlows.size
        val to = ScreensaverGlows[paletteIndex]
        for (step in 1..GLOW_STEPS) {
            delay(GLOW_STEP_MS)
            glow = glowStep(from, to, step)
        }
    }
}

internal fun Color.withFade(fade: Float): Color = copy(alpha = alpha * fade)
