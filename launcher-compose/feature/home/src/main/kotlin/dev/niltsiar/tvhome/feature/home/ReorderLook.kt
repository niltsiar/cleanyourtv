package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.FocusRingReach
import dev.niltsiar.tvhome.core.designsystem.TILE_FOCUSED_SCALE
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

private const val DIMMED_ALPHA = 0.65f

private const val WIGGLE_DEGREES = 1.6f
private const val WIGGLE_MS = 150
private const val DIM_MS = 140
private val TrayRimClear = 8.dp
private val LiftShadow = 20.dp
private val LiftShadowColor = Color.Black.copy(alpha = 0.55f)

/**
 * How far a carried tile lifts: its ring, [FocusRingReach] past the tile, stays [TrayRimClear] under the tray's rim, a
 * [TileGap] above the tile.
 */
private val LiftHeight = TileGap - FocusRingReach - TrayRimClear

/**
 * A move arrow: a round [ArrowDisc] of black at 0.60 centred on the outer edge of a carried tile's ring, so that half its
 * width lies under the ring and tile and the other half, [ArrowDisc] / 2 past the ring, leaves the rest of the [TileGap]
 * (4 dp) to the next tile. On it stands a white chevron at 0.95, [ChevronHeight] tall and [ChevronDepth] deep, drawn with a
 * round-capped [ChevronStroke] stroke; the disc keeps it readable, so the chevron has no outline of its own.
 */
private val ArrowDisc = 22.dp
internal val ArrowDiscColor = Color.Black.copy(alpha = 0.60f)
private val ChevronHeight = 12.dp
private val ChevronDepth = 6.dp
private val ChevronStroke = 2.5.dp
internal val ChevronColor = Color.White.copy(alpha = 0.95f)

/**
 * A tile's look in reorder and edit-dock modes: dimmed unless it is the carried one, which lifts, is drawn at its rest size
 * (a focused tile is grown), casts a shadow in its [shape], wiggles a little and shows a move arrow on its ring beside each
 * edge it can still move towards; it settles back with a spring when it is put down. No other tile moves. The
 * animated values are read in the layer and draw lambdas only.
 */
@Composable
internal fun Modifier.reorderLook(
    key: String,
    input: HomeInput,
    shape: Shape,
    growthPivot: ((Float) -> Float)? = null,
): Modifier {
    val motion = TvTheme.motion
    // A tile built mid-session (it crossed from the grid into the dock) starts where it ends up.
    val dim = remember { Animatable(Snapshot.withoutReadObservation { if (key in input.dimmed.value) DIMMED_ALPHA else 1f }) }
    val lift = remember { Animatable(Snapshot.withoutReadObservation { if (input.carried.value == key) 1f else 0f }) }
    val wiggle = remember { Animatable(0f) }
    val left = remember { Animatable(input.badgeAtStart(key, input.canMoveLeft)) }
    val right = remember { Animatable(input.badgeAtStart(key, input.canMoveRight)) }
    val grown = motion.focusScale(TILE_FOCUSED_SCALE)
    LaunchedEffect(key, input, motion) {
        launch {
            snapshotFlow { if (key in input.dimmed.value) DIMMED_ALPHA else 1f }.collect { dim.animateTo(it, motion.tweenSpec(DIM_MS)) }
        }
        launch {
            snapshotFlow { input.carried.value == key }.collectLatest { carried ->
                coroutineScope {
                    val liftSpec = motion.springSpec<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
                    val sideSpec = motion.tweenSpec<Float>(DIM_MS)
                    if (carried) {
                        launch { lift.animateTo(1f, liftSpec) }
                        launch { snapshotFlow { input.canMoveLeft.value }.collect { left.animateTo(if (it) 1f else 0f, sideSpec) } }
                        launch { snapshotFlow { input.canMoveRight.value }.collect { right.animateTo(if (it) 1f else 0f, sideSpec) } }
                        if (motion.animated) {
                            launch {
                                wiggle.snapTo(-1f)
                                wiggle.animateTo(1f, infiniteRepeatable(tween(WIGGLE_MS), RepeatMode.Reverse))
                            }
                        }
                    } else {
                        launch { wiggle.animateTo(0f, liftSpec) }
                        launch { lift.animateTo(0f, liftSpec) }
                        launch { left.animateTo(0f, sideSpec) }
                        launch { right.animateTo(0f, sideSpec) }
                    }
                }
            }
        }
    }
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = dim.value.coerceIn(0f, 1f)
        val raised = lift.value
        scaleX = 1f + (1f / grown - 1f) * raised
        scaleY = scaleX
        // A growth anchored under the tile's centre, cancelled about the centre, would leave the tile this much higher than at rest.
        val regrown = growthPivot?.let { (it(size.height) - size.height / 2f) * (1f - 1f / grown) } ?: 0f
        translationY = (regrown - LiftHeight.toPx()) * raised
        rotationZ = WIGGLE_DEGREES * wiggle.value * raised
        shadowElevation = LiftShadow.toPx() * raised.coerceIn(0f, 1f)
        this.shape = shape
        ambientShadowColor = LiftShadowColor
        spotShadowColor = LiftShadowColor
    }.drawWithCache {
        onDrawWithContent {
            val lifted = lift.value.coerceIn(0f, 1f)
            val onLeft = lifted * left.value.coerceIn(0f, 1f)
            val onRight = lifted * right.value.coerceIn(0f, 1f)
            if (onLeft == 0f && onRight == 0f) {
                drawContent()
                return@onDrawWithContent
            }
            // The layer is scaled by the lift: draw in the tile's own measures by undoing that.
            val raised = 1f + (1f / grown - 1f) * lift.value
            val centre = ringReach(size.width, grown * raised)
            val middle = size.height / 2f
            val pivot = Offset(size.width / 2f, middle)
            val leftAt = Offset(-centre, middle)
            val rightAt = Offset(size.width + centre, middle)
            // The discs go under the tile, so that its ring is drawn over them; the chevrons stay on top.
            scale(1f / raised, pivot) {
                if (onLeft > 0f) drawArrowDisc(leftAt, alpha = onLeft)
                if (onRight > 0f) drawArrowDisc(rightAt, alpha = onRight)
            }
            drawContent()
            scale(1f / raised, pivot) {
                if (onLeft > 0f) drawChevron(leftAt, pointsLeft = true, alpha = onLeft)
                if (onRight > 0f) drawChevron(rightAt, pointsLeft = false, alpha = onRight)
            }
        }
    }
}

/**
 * How far outside the edge of a tile [width] wide, drawn [scale] times its size, its ring's outer edge stands: [FocusRingReach]
 * x [scale] past the art. A carried tile's arrow is centred there.
 */
internal fun Density.ringReach(
    width: Float,
    scale: Float,
): Float = width * (scale - 1f) / 2f + FocusRingReach.toPx() * scale

private fun HomeInput.badgeAtStart(
    key: String,
    side: State<Boolean>,
): Float = Snapshot.withoutReadObservation { if (carried.value == key && side.value) 1f else 0f }

/** A move arrow's disc centred on [center], at [alpha]. */
internal fun DrawScope.drawArrowDisc(
    center: Offset,
    alpha: Float,
) = drawCircle(ArrowDiscColor, radius = ArrowDisc.toPx() / 2f, center = center, alpha = alpha)

/** A move arrow's chevron centred on [center], pointing left or right, at [alpha]; it stands on [drawArrowDisc]. */
internal fun DrawScope.drawChevron(
    center: Offset,
    pointsLeft: Boolean,
    alpha: Float,
) {
    val half = ChevronHeight.toPx() / 2f
    val tip = if (pointsLeft) -ChevronDepth.toPx() / 2f else ChevronDepth.toPx() / 2f
    val path =
        Path().apply {
            moveTo(center.x - tip, center.y - half)
            lineTo(center.x + tip, center.y)
            lineTo(center.x - tip, center.y + half)
        }
    drawPath(path, ChevronColor, alpha = alpha, style = Stroke(ChevronStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
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
