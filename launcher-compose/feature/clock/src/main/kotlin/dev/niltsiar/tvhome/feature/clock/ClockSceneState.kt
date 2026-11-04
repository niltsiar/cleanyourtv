package dev.niltsiar.tvhome.feature.clock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * What moves in the screensaver, kept in state that only the layout and draw phases read, so nothing recomposes while it
 * runs for hours. Every [MOVE_MS] the clock fades out, jumps to another [spot] and fades back in, and the glow takes the
 * next colour of [GlowPalette].
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

    private var paletteIndex = random.nextInt(GlowPalette.size)

    var glow by mutableStateOf(GlowPalette[paletteIndex])
        private set

    /** Plays the scene until cancelled. [cycleGlow] false keeps the glow colour. */
    suspend fun run(cycleGlow: Boolean) {
        spot = Spot.random(random)
        fade.animateTo(1f, tween(FADE_IN_MS))
        while (true) {
            delay(MOVE_MS)
            coroutineScope {
                if (cycleGlow) launch { shiftGlow() }
                fade.animateTo(0f, tween(FADE_OUT_MS))
                spot = Spot.random(random)
                fade.animateTo(1f, tween(FADE_IN_MS))
            }
        }
    }

    private suspend fun shiftGlow() {
        val from = glow
        paletteIndex = (paletteIndex + 1) % GlowPalette.size
        val to = GlowPalette[paletteIndex]
        for (step in 1..GLOW_STEPS) {
            delay(GLOW_STEP_MS)
            glow = glowStep(from, to, step)
        }
    }
}

internal fun Color.withFade(fade: Float): Color = copy(alpha = alpha * fade)
