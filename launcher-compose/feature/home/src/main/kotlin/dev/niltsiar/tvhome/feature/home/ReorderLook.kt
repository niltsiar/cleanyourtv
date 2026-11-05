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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val DIMMED_ALPHA = 0.70f

/** The carried tile has focus, so FocusSurface's 1.08 applies inside this layer: together they make 1.12. */
private const val LIFT_SCALE = 1.12f / 1.08f - 1f
private const val WIGGLE_DEGREES = 1.6f
private const val WIGGLE_MS = 150
private const val DIM_MS = 140
private val LiftHeight = 10.dp
private val LiftShadow = 20.dp
private val LiftShadowColor = Color.Black.copy(alpha = 0.55f)
private val BadgeRadius = 12.dp
private val ChevronHalf = 5.dp
private val ChevronStroke = 2.dp
private val ChevronColor = Color(0xFF0B0D12)

/**
 * A tile's look in reorder and edit-dock modes: dimmed unless it is the carried one, which lifts, grows, casts a shadow
 * in its [shape], wiggles a little and shows a white chevron badge on its left and right edges; it settles back with a
 * spring when it is put down. The animated values are read in the layer and draw lambdas only.
 */
@Composable
internal fun Modifier.reorderLook(
    key: String,
    input: HomeInput,
    shape: Shape,
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
        shadowElevation = LiftShadow.toPx() * raised.coerceIn(0f, 1f)
        this.shape = shape
        ambientShadowColor = LiftShadowColor
        spotShadowColor = LiftShadowColor
    }.drawWithCache {
        val radius = BadgeRadius.toPx()
        val half = ChevronHalf.toPx()
        val stroke = Stroke(ChevronStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val middle = size.height / 2f
        val left = chevron(Offset(0f, middle), half, pointsLeft = true)
        val right = chevron(Offset(size.width, middle), half, pointsLeft = false)
        onDrawWithContent {
            drawContent()
            val shown = lift.value.coerceIn(0f, 1f)
            if (shown > 0f) {
                drawCircle(Color.White, radius, Offset(0f, middle), alpha = shown)
                drawCircle(Color.White, radius, Offset(size.width, middle), alpha = shown)
                drawPath(left, ChevronColor, alpha = shown, style = stroke)
                drawPath(right, ChevronColor, alpha = shown, style = stroke)
            }
        }
    }
}

/** A chevron centred on [center], [half] tall above and below it, pointing left or right. */
private fun chevron(
    center: Offset,
    half: Float,
    pointsLeft: Boolean,
): Path {
    val tip = if (pointsLeft) -half / 2f else half / 2f
    return Path().apply {
        moveTo(center.x - tip, center.y - half)
        lineTo(center.x + tip, center.y)
        lineTo(center.x - tip, center.y + half)
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
