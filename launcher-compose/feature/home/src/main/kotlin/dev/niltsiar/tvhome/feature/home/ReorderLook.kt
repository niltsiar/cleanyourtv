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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.FocusRingReach
import dev.niltsiar.tvhome.core.designsystem.TILE_FOCUSED_SCALE
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

private const val DIMMED_ALPHA = 0.65f

/** The carried tile has focus, so FocusSurface's 1.08 applies inside this layer too. */
private const val LIFT_SCALE = 0.08f
private const val WIGGLE_DEGREES = 1.6f
private const val WIGGLE_MS = 150
private const val DIM_MS = 140
private const val APART_MS = 200
private val LiftHeight = 8.dp
private val LiftShadow = 20.dp
private val LiftShadowColor = Color.Black.copy(alpha = 0.55f)
private val ChevronDepth = 7.dp
private val ChevronStroke = 2.5.dp
private val ChevronOutline = 1.25.dp
private val ChevronThickness = ChevronStroke + ChevronOutline * 2
private val ChevronClear = 6.dp
private val ChevronColor = Color.White
private val ChevronOutlineColor = Color.Black.copy(alpha = 0.55f)

/**
 * A tile's look in reorder and edit-dock modes: dimmed unless it is the carried one, which lifts, grows, casts a shadow
 * in its [shape], wiggles a little and shows a white chevron in the gutter beside each edge it can still move towards,
 * clear of its ring and of the next tile; it settles back with a spring when it is put down. The tiles of its row move
 * apart from it as far as the chevrons need ([ChevronGutter.extra]). The animated values are read in the layer and draw
 * lambdas only.
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
    val left = remember { Animatable(input.badgeAtStart(key, input.canMoveLeft)) }
    val right = remember { Animatable(input.badgeAtStart(key, input.canMoveRight)) }
    val apart = remember { Animatable(Snapshot.withoutReadObservation { input.apart.value[key]?.toFloat() ?: 0f }) }
    LaunchedEffect(key, input, motion) {
        launch {
            snapshotFlow { if (key in input.dimmed.value) DIMMED_ALPHA else 1f }.collect { dim.animateTo(it, motion.tweenSpec(DIM_MS)) }
        }
        launch {
            snapshotFlow { input.apart.value[key]?.toFloat() ?: 0f }.collect { apart.animateTo(it, motion.tweenSpec(APART_MS)) }
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
        scaleX = 1f + LIFT_SCALE * raised
        scaleY = scaleX
        translationY = -LiftHeight.toPx() * raised
        rotationZ = WIGGLE_DEGREES * wiggle.value * raised
        translationX =
            apart.value * chevronGutter(size.width, motion.focusScale(TILE_FOCUSED_SCALE) * (1f + LIFT_SCALE)).extra(TileGap.toPx())
        shadowElevation = LiftShadow.toPx() * raised.coerceIn(0f, 1f)
        this.shape = shape
        ambientShadowColor = LiftShadowColor
        spotShadowColor = LiftShadowColor
    }.drawWithCache {
        val stroke = Stroke(ChevronStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val outline = Stroke(ChevronThickness.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val grown = motion.focusScale(TILE_FOCUSED_SCALE)
        onDrawWithContent {
            drawContent()
            val lifted = lift.value.coerceIn(0f, 1f)
            val onLeft = lifted * left.value.coerceIn(0f, 1f)
            val onRight = lifted * right.value.coerceIn(0f, 1f)
            if (onLeft == 0f && onRight == 0f) return@onDrawWithContent
            // The layer is scaled by the lift: draw in the tile's own measures by undoing that.
            val raised = 1f + LIFT_SCALE * lift.value
            val gutter = chevronGutter(size.width, grown * raised)
            scale(1f / raised, Offset(size.width / 2f, size.height / 2f)) {
                val middle = size.height / 2f
                if (onLeft > 0f) {
                    val chevron = chevron(Offset(-gutter.centre, middle), ChevronDepth.toPx(), pointsLeft = true)
                    drawPath(chevron, ChevronOutlineColor, alpha = onLeft, style = outline)
                    drawPath(chevron, ChevronColor, alpha = onLeft, style = stroke)
                }
                if (onRight > 0f) {
                    val chevron = chevron(Offset(size.width + gutter.centre, middle), ChevronDepth.toPx(), pointsLeft = false)
                    drawPath(chevron, ChevronOutlineColor, alpha = onRight, style = outline)
                    drawPath(chevron, ChevronColor, alpha = onRight, style = stroke)
                }
            }
        }
    }
}

/**
 * Where a chevron sits beside a carried tile whose art, [width] wide, is [scale] times its size. The ring reaches
 * [FocusRingReach] x [scale] past the art; the chevron, [ChevronDepth] wide and [ChevronThickness] wider with its outline,
 * stands [ChevronClear] past the ring, and the next tile [ChevronClear] past it. [ChevronGutter.centre] is the chevron's
 * distance outside the tile's edge, and [ChevronGutter.reach] the distance at which the next tile must start.
 */
private fun Density.chevronGutter(
    width: Float,
    scale: Float,
): ChevronGutter {
    val ringEnd = width * (scale - 1f) / 2f + FocusRingReach.toPx() * scale
    val footprint = (ChevronDepth + ChevronThickness).toPx()
    val clear = ChevronClear.toPx()
    return ChevronGutter(centre = ringEnd + clear + footprint / 2f, reach = ringEnd + 2f * clear + footprint)
}

/** How far from a tile's edge a chevron stands ([centre]), and how far the next tile must start for it to be [ChevronClear] clear ([reach]). */
private data class ChevronGutter(
    val centre: Float,
    val reach: Float,
) {
    /** How far the next tile must move out, when it starts [gap] from the edge. */
    fun extra(gap: Float): Float = max(0f, reach - gap)
}

private fun HomeInput.badgeAtStart(
    key: String,
    side: State<Boolean>,
): Float = Snapshot.withoutReadObservation { if (carried.value == key && side.value) 1f else 0f }

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
