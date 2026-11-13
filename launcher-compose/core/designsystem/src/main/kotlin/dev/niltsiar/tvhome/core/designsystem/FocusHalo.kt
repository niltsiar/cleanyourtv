package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp
import kotlin.math.pow

private val HaloWidth = 16.dp
private val HaloClearance = 1.dp
private const val HALO_STEPS = 8
private const val HALO_UNDER_ALPHA = 0.5f
private const val HALO_SIDE_ALPHA = 0.25f

/** Each band's share of the halo's alpha, taken from (1 - t)^1.5 at the band's middle, so the outermost band is all but gone. */
private val HaloFade = FloatArray(HALO_STEPS) { ((HALO_STEPS - it - 0.5f) / HALO_STEPS).pow(1.5f) }

/**
 * Marks a container that clips what it holds, such as a panel clipped to its shape or a scrolling row: the halo of a
 * focused [FocusSurface] inside it fades out [HaloClearance] before the container's bounds on each side.
 */
public fun Modifier.clipsHalo(): Modifier = this then HaloClipElement

private object HaloClipKey

private object HaloClipElement : ModifierNodeElement<HaloClipNode>() {
    override fun create(): HaloClipNode = HaloClipNode()

    override fun update(node: HaloClipNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "clipsHalo"
    }

    override fun hashCode(): Int = HaloClipKey.hashCode()

    override fun equals(other: Any?): Boolean = other === this
}

private class HaloClipNode :
    Modifier.Node(),
    TraversableNode {
    override val traverseKey: Any = HaloClipKey
}

/**
 * The halo glow of a focused [FocusSurface] in [ring]'s colour, white without one, drawn grown as the surface is: [scale]
 * when [focus] is 1, about [pivot]'s height or the middle. It runs up to [HaloWidth] past the ring's keyline, at 0.5
 * under the surface and 0.25 at its sides and top, in [HALO_STEPS] bands that fade out. On a side where a [clipsHalo]
 * container leaves less room, the glow on that side is narrowed to end before the container's edge, so the clip never cuts it.
 */
internal data class FocusHaloElement(
    val focus: Animatable<Float, AnimationVector1D>,
    val scale: Float,
    val pivot: ((Float) -> Float)?,
    val shape: Shape,
    val ring: ColorProducer?,
) : ModifierNodeElement<FocusHaloNode>() {
    override fun create(): FocusHaloNode = FocusHaloNode(this)

    override fun update(node: FocusHaloNode) {
        node.spec = this
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusHalo"
    }
}

internal class FocusHaloNode(
    var spec: FocusHaloElement,
) : Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode {
    private var clips: List<HaloClipNode>? = null
    private var bands: Array<Path>? = null

    override fun onDetach() {
        clips = null
    }

    // The draw does not rerun when the surface only moves; its room to a clip edge changes then.
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (spec.focus.value > 0f) invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val focus = spec.focus.value
        val progress = focus.coerceIn(0f, 1f)
        if (progress > 0f) {
            val grown = 1f + (spec.scale - 1f) * focus
            val pivot = Offset(size.width / 2f, spec.pivot?.invoke(size.height) ?: (size.height / 2f))
            scale(grown, pivot) { drawHalo(progress, grown, pivot) }
        }
        drawContent()
    }

    private fun DrawScope.drawHalo(
        progress: Float,
        grown: Float,
        pivot: Offset,
    ) {
        val reach = FocusRingReach.toPx()
        val keyline =
            spec.shape
                .createOutline(size, layoutDirection, this)
                .grownBy(reach)
                .roundRect
        val most = HaloWidth.toPx()
        val clip = clipBounds()
        val left: Float
        val top: Float
        val right: Float
        val bottom: Float
        if (clip == null) {
            left = most
            top = most
            right = most
            bottom = most
        } else {
            // The clip is laid out unscaled; the halo is drawn grown about the pivot, so the clip is moved into that space.
            val clearance = HaloClearance.toPx()
            left = (keyline.left - (pivot.x + (clip.left - pivot.x) / grown) - clearance).coerceIn(0f, most)
            top = (keyline.top - (pivot.y + (clip.top - pivot.y) / grown) - clearance).coerceIn(0f, most)
            right = (pivot.x + (clip.right - pivot.x) / grown - keyline.right - clearance).coerceIn(0f, most)
            bottom = (pivot.y + (clip.bottom - pivot.y) / grown - keyline.bottom - clearance).coerceIn(0f, most)
        }
        if (left + top + right + bottom == 0f) return
        val colour = spec.ring?.invoke() ?: Color.White
        val light =
            Brush.verticalGradient(
                listOf(colour.copy(alpha = HALO_SIDE_ALPHA), colour.copy(alpha = HALO_UNDER_ALPHA)),
                startY = size.height,
                endY = size.height + reach,
            )
        val paths = bands ?: Array(HALO_STEPS) { Path() }.also { bands = it }
        for (step in 0 until HALO_STEPS) {
            val inner = step / HALO_STEPS.toFloat()
            val outer = (step + 1) / HALO_STEPS.toFloat()
            val band = paths[step]
            band.reset()
            band.fillType = PathFillType.EvenOdd
            band.addRoundRect(keyline.outset(left * outer, top * outer, right * outer, bottom * outer))
            band.addRoundRect(keyline.outset(left * inner, top * inner, right * inner, bottom * inner))
            drawPath(band, light, alpha = progress * HaloFade[step])
        }
    }

    /** Where the [clipsHalo] containers around this surface meet, in its own unscaled space, or null with none. */
    private fun clipBounds(): Rect? {
        val found =
            clips ?: buildList {
                traverseAncestors(HaloClipKey) {
                    add(it as HaloClipNode)
                    true
                }
            }.also { clips = it }
        if (found.isEmpty()) return null
        val self = requireLayoutCoordinates()
        var bounds: Rect? = null
        for (clip in found) {
            val box = self.localBoundingBoxOf(clip.requireLayoutCoordinates(), clipBounds = false)
            bounds = bounds?.intersect(box) ?: box
        }
        return bounds
    }
}

/** This rounded rectangle pushed out by a distance per side, each corner's radius growing by the distances of its two sides. */
private fun RoundRect.outset(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): RoundRect =
    RoundRect(
        left = this.left - left,
        top = this.top - top,
        right = this.right + right,
        bottom = this.bottom + bottom,
        topLeftCornerRadius = topLeftCornerRadius.plus(left, top),
        topRightCornerRadius = topRightCornerRadius.plus(right, top),
        bottomRightCornerRadius = bottomRightCornerRadius.plus(right, bottom),
        bottomLeftCornerRadius = bottomLeftCornerRadius.plus(left, bottom),
    )

private fun CornerRadius.plus(
    x: Float,
    y: Float,
): CornerRadius = CornerRadius(this.x + x, this.y + y)
