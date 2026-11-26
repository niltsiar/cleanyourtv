package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** The line's stroke, the wave's height from its middle to a crest, its length, and the gap between the wave and the track. */
private val WaveStroke = 3.dp
private val WaveAmplitude = 2.dp
private val Wavelength = 16.dp
private val WaveGap = 4.dp

/** How far a [ProgressWave] reaches above and below its middle: a crest and half the stroke. */
public val ProgressWaveReach: Dp = WaveAmplitude + WaveStroke / 2

/** The wave's points per wavelength. */
private const val WAVE_SAMPLES = 24

/**
 * A determinate wavy progress line at rest, as Material 3 Expressive's linear wavy indicator draws one: a wave from the
 * start to the progress and, after a gap, a flat track to the end, with round caps, all inside the width. It stands
 * still. Its paths are built when the width, the progress or the density changes and kept, so drawing it again
 * allocates nothing.
 */
public class ProgressWave {
    private val wave = Path()
    private val track = Path()
    private var builtWidth = -1f
    private var builtProgress = -1f
    private var stroke = Stroke(0f)

    /**
     * Draws the line [width] pixels long from [left], its middle at [y], [progress] of the way along (0 to 1): the wave
     * in [waveColour], the track in [trackColour].
     */
    public fun DrawScope.drawProgressWave(
        left: Float,
        y: Float,
        width: Float,
        progress: Float,
        waveColour: Color,
        trackColour: Color,
    ) {
        val done = progress.coerceIn(0f, 1f)
        if (width != builtWidth || done != builtProgress || WaveStroke.toPx() != stroke.width) build(width, done)
        translate(left, y) {
            drawPath(wave, waveColour, style = stroke)
            drawPath(track, trackColour, style = stroke)
        }
    }

    private fun DrawScope.build(
        width: Float,
        done: Float,
    ) {
        builtWidth = width
        builtProgress = done
        stroke = Stroke(WaveStroke.toPx(), cap = StrokeCap.Round)
        val cap = stroke.width / 2f
        val start = cap
        val end = width - cap
        val waveEnd = start + done * (end - start)
        wave.rewind()
        if (done > 0f) {
            val amplitude = WaveAmplitude.toPx()
            val length = Wavelength.toPx()
            val step = length / WAVE_SAMPLES
            wave.moveTo(start, 0f)
            var x = start + step
            while (x < waveEnd) {
                wave.lineTo(x, amplitude * sin(2f * PI.toFloat() * (x - start) / length))
                x += step
            }
            wave.lineTo(waveEnd, amplitude * sin(2f * PI.toFloat() * (waveEnd - start) / length))
        }
        track.rewind()
        val trackStart = if (done > 0f) waveEnd + 2f * cap + WaveGap.toPx() else start
        if (trackStart < end) {
            track.moveTo(trackStart, 0f)
            track.lineTo(end, 0f)
        }
    }
}
