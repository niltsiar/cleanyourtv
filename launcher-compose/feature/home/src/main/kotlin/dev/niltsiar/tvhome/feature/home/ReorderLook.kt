package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val DIMMED_ALPHA = 0.45f
private const val LIFT_SCALE = 0.08f
private const val WIGGLE_DEGREES = 1.6f
private const val WIGGLE_MS = 150
private const val DIM_MS = 140
private val LiftHeight = 10.dp

/**
 * A tile's look in reorder and edit-dock modes: dimmed unless it is the carried one, which lifts, grows and wiggles
 * a little, and settles back with a spring when it is put down. The animated values are read in the layer lambda only.
 */
@Composable
internal fun Modifier.reorderLook(
    key: String,
    input: HomeInput,
): Modifier {
    val motion = TvTheme.motion
    // A tile built mid-session (it crossed from the grid into the dock) starts where it ends up.
    val dim = remember { Animatable(Snapshot.withoutReadObservation { if (key in input.dimmed.value) DIMMED_ALPHA else 1f }) }
    val lift = remember { Animatable(Snapshot.withoutReadObservation { if (input.carried.value == key) 1f else 0f }) }
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(key, input, motion) {
        launch {
            snapshotFlow { if (key in input.dimmed.value) DIMMED_ALPHA else 1f }.collect { dim.animateTo(it, motion.tweenSpec(DIM_MS)) }
        }
        launch {
            snapshotFlow { input.carried.value == key }.collectLatest { carried ->
                coroutineScope {
                    val liftSpec = motion.springSpec<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
                    if (carried) {
                        launch { lift.animateTo(1f, liftSpec) }
                        if (motion.animated) {
                            launch {
                                wiggle.snapTo(-1f)
                                wiggle.animateTo(1f, infiniteRepeatable(tween(WIGGLE_MS), RepeatMode.Reverse))
                            }
                        }
                    } else {
                        launch { wiggle.animateTo(0f, liftSpec) }
                        launch { lift.animateTo(0f, liftSpec) }
                    }
                }
            }
        }
    }
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = dim.value.coerceIn(0f, 1f)
        val raised = lift.value
        scaleX = 1f + LIFT_SCALE * raised
        scaleY = scaleX
        translationY = -LiftHeight.toPx() * raised
        rotationZ = WIGGLE_DEGREES * wiggle.value * raised
    }
}

/**
 * Slides a dock tile from its old place to its new one when its [index] in the row changes. The row is not lazy, so
 * nothing else animates its tiles; they are one slot plus [gap] apart.
 */
@Composable
internal fun Modifier.slideToIndex(
    index: Int,
    gap: Dp,
): Modifier {
    val motion = TvTheme.motion
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val last = remember { intArrayOf(index) }
    // After composition and before layout, so the first frame already shows the tile at its old place.
    SideEffect {
        val from = last[0]
        if (from != index) {
            last[0] = index
            if (motion.animations) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offset.snapTo(offset.value + (from - index))
                    offset.animateTo(0f, motion.springSpec(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
    }
    return graphicsLayer { translationX = offset.value * (size.width + gap.toPx()) }
}
