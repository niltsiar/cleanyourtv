package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.expressive.TilePainter
import dev.niltsiar.tvhome.core.designsystem.expressive.drawExpressiveHold

/**
 * The hold cue of a [FocusSurface]: while OK is held on it, an arc in [ring]'s colour, [TvElementColors.ring] of [colours]
 * without one, covers the gap
 * and the ring ([RingBand]) from the top centre clockwise, as far round as the hold is to the menu. It reads the progress
 * from the [holdOk] earlier in the chain and is drawn grown as the ring is: [scale] when [focus] is 1, less while a
 * [press] [dips] it, about [pivot]'s height or the middle. In [Look.Expressive] it is a thin line in black or white,
 * whichever reads on the band, along the middle of the band [painter] drew.
 */
internal data class HoldArcElement(
    val focus: Animatable<Float, AnimationVector1D>,
    val scale: Float,
    val pivot: ((Float) -> Float)?,
    val shape: Shape,
    val ring: ColorProducer?,
    val colours: TvElementColors,
    val press: Animatable<Float, AnimationVector1D>?,
    val dips: Boolean,
    val look: LookState,
    val painter: TilePainter,
) : ModifierNodeElement<HoldArcNode>() {
    override fun create(): HoldArcNode = HoldArcNode(this)

    override fun update(node: HoldArcNode) {
        node.spec = this
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "holdArc"
    }
}

internal class HoldArcNode(
    var spec: HoldArcElement,
) : Modifier.Node(),
    DrawModifierNode,
    ModifierLocalModifierNode {
    private val outline = Path()
    private val arc = Path()
    private val measure = PathMeasure()

    override fun ContentDrawScope.draw() {
        drawContent()
        val held = ModifierLocalHeldOk.current?.invoke() ?: 0f
        val focus = spec.focus.value
        if (held <= 0f || focus <= 0f) return
        val grown = (1f + (spec.scale - 1f) * focus) * dipOf(spec.press, spec.dips)
        scale(grown, Offset(size.width / 2f, spec.pivot?.invoke(size.height) ?: (size.height / 2f))) { drawArc(held) }
    }

    private fun DrawScope.drawArc(held: Float) {
        val colour = spec.ring?.invoke() ?: spec.colours.ring
        if (spec.look.look == Look.Expressive) {
            drawExpressiveHold(spec.painter, spec.look.tile, spec.shape, spec.focus.value, held, colour)
            return
        }
        val band = RingBand.toPx()
        outline.rewind()
        outline.addFromTopCentre(
            spec.shape
                .createOutline(size, layoutDirection, this)
                .grownBy(band / 2f)
                .roundRect,
        )
        measure.setPath(outline, false)
        arc.rewind()
        measure.getSegment(0f, measure.length * held, arc, true)
        drawPath(arc, colour, style = Stroke(band))
    }
}

/** [r] as one closed contour that starts at the middle of its top edge and runs clockwise. */
internal fun Path.addFromTopCentre(r: RoundRect) {
    moveTo((r.left + r.right) / 2f, r.top)
    lineTo(r.right - r.topRightCornerRadius.x, r.top)
    arcTo(Rect(r.right - 2f * r.topRightCornerRadius.x, r.top, r.right, r.top + 2f * r.topRightCornerRadius.y), 270f, 90f, false)
    lineTo(r.right, r.bottom - r.bottomRightCornerRadius.y)
    arcTo(
        Rect(r.right - 2f * r.bottomRightCornerRadius.x, r.bottom - 2f * r.bottomRightCornerRadius.y, r.right, r.bottom),
        0f,
        90f,
        false,
    )
    lineTo(r.left + r.bottomLeftCornerRadius.x, r.bottom)
    arcTo(Rect(r.left, r.bottom - 2f * r.bottomLeftCornerRadius.y, r.left + 2f * r.bottomLeftCornerRadius.x, r.bottom), 90f, 90f, false)
    lineTo(r.left, r.top + r.topLeftCornerRadius.y)
    arcTo(Rect(r.left, r.top, r.left + 2f * r.topLeftCornerRadius.x, r.top + 2f * r.topLeftCornerRadius.y), 180f, 90f, false)
    close()
}
