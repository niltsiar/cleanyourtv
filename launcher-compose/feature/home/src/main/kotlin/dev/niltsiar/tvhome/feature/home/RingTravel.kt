package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.niltsiar.tvhome.core.designsystem.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val RING_TRAVEL_MS = 220

/**
 * The colour the focus ring shows while it moves from one tile to the next. A tile that takes focus straight from
 * another one starts at the colour the old ring had and blends to its own in OKLab over [RING_TRAVEL_MS]; one that takes
 * it from anywhere else ([reset]: the top bar, a menu, the App library, another app) starts at its own. The ring of a
 * tile that loses focus mid-blend fades out in the colour it had reached. Only the ring's draw reads [colourFor], so a
 * move recomposes nothing.
 */
@Stable
internal class RingTravel {
    private var key: String? = null
    private var from = Color.White
    private var to = Color.White
    private var job: Job? = null
    private val progress = mutableFloatStateOf(1f)
    private var leaving: String? = null
    private var leavingColour = Color.White

    /** The ring of tile [key], whose own colour is [own]: its colour now, blended while it travels. */
    fun colourFor(
        key: String,
        own: Color,
    ): Color =
        when (key) {
            this.key -> blend(own)
            leaving -> leavingColour
            else -> own
        }

    private fun blend(own: Color): Color = progress.floatValue.let { if (it >= 1f) own else lerp(from, own, it) }

    /** Tile [key] took focus; its ring is [own]. The blend runs in [scope]. */
    fun arrive(
        key: String,
        own: Color,
        motion: Motion,
        scope: CoroutineScope,
    ) {
        val start = if (this.key != null) blend(to) else own
        if (this.key != null && this.key != key) holdLeaving(start)
        job?.cancel()
        this.key = key
        from = start
        to = own
        if (start == own || !motion.animations) {
            progress.floatValue = 1f
            return
        }
        progress.floatValue = 0f
        job =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                animate(0f, 1f, animationSpec = motion.tweenSpec(RING_TRAVEL_MS)) { value, _ -> progress.floatValue = value }
            }
    }

    /** Focus left the tiles: the next ring starts at its own colour. */
    fun reset() {
        if (key != null) holdLeaving(blend(to))
        job?.cancel()
        key = null
        progress.floatValue = 1f
    }

    /** The tile that has the ring now keeps [colour] when it loses it, if it has not reached its own yet. */
    private fun holdLeaving(colour: Color) {
        leaving = key.takeIf { progress.floatValue < 1f }
        leavingColour = colour
    }
}
