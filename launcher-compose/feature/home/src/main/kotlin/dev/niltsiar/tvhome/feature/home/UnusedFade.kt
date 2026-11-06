package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import kotlinx.coroutines.flow.collectLatest

private const val FADED_SATURATION = 0.55f
private const val FADED_ALPHA = 0.75f
private const val UNFADE_MS = 150

private val FadedFilter = saturation(FADED_SATURATION)

private fun saturation(amount: Float) = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(amount) })

/**
 * Draws the tile of the app [key] at 55% saturation and 75% opacity while [faded] holds it, and in full colour while it has
 * focus, over [UNFADE_MS] (at once with reduced motion). A change in [faded] shows at once. The fade is a layer's colour
 * filter and alpha, read in the layer lambda; [faded] and the focus are followed in an effect, so nothing recomposes.
 */
@Composable
internal fun Modifier.unusedFade(
    faded: State<Set<String>>?,
    key: String,
): Modifier {
    if (faded == null) return this
    val animated = TvTheme.motion.animated
    val startsFaded = Snapshot.withoutReadObservation { key in faded.value }
    val amount = remember(key) { Animatable(if (startsFaded) 1f else 0f) }
    val focused = remember { mutableStateOf(false) }
    LaunchedEffect(faded, key, animated) {
        var wasFaded = startsFaded
        snapshotFlow { (key in faded.value) to focused.value }.collectLatest { (isFaded, hasFocus) ->
            val target = if (isFaded && !hasFocus) 1f else 0f
            if (animated && isFaded == wasFaded) amount.animateTo(target, tween(UNFADE_MS)) else amount.snapTo(target)
            wasFaded = isFaded
        }
    }
    return onFocusChanged { focused.value = it.hasFocus }.graphicsLayer {
        val fade = amount.value
        if (fade > 0f) {
            alpha = 1f - (1f - FADED_ALPHA) * fade
            colorFilter = if (fade >= 1f) FadedFilter else saturation(1f - (1f - FADED_SATURATION) * fade)
        }
    }
}
