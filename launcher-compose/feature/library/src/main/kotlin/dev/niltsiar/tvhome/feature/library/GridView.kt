package dev.niltsiar.tvhome.feature.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import dev.niltsiar.tvhome.core.designsystem.FocusRingReach
import dev.niltsiar.tvhome.core.designsystem.LookState
import dev.niltsiar.tvhome.core.designsystem.growsByOutset

/**
 * The part of the grid the library shows, [edgeX] and [edgeY] around the cards. In Expressive a focused card's capsule grows
 * [FocusOutset] past the card and its band [FocusRingReach] past that, further than [edgeY]: the grid then lays out
 * [overhang] past the view at its top and bottom, so its clip lets that shape through, and a card without focus cuts itself
 * at the view's edge ([cutAtView]). The look is read in layout and draw.
 */
internal class GridView(
    private val look: LookState,
    private val edgeX: Dp,
    val edgeY: Dp,
) {
    /** The view's coordinates, set when the grid is placed. */
    lateinit var coordinates: LayoutCoordinates

    /** How far the grid lays out past the view at its top and bottom: 0 unless a focused card's shape reaches past [edgeY]. */
    fun overhang(): Dp = if (look.growsByOutset(FocusOutset)) (FocusOutset + FocusRingReach - edgeY).coerceAtLeast(0.dp) else 0.dp

    /** The [overhang] in whole pixels, as the grid rounds its [padding], so the cards stay where they are in the view. */
    fun overhangPx(density: Density): Int = with(density) { (edgeY + overhang()).roundToPx() - edgeY.roundToPx() }

    /** The grid's content padding: [edgeY] and the [overhang] at the top and bottom. */
    val padding: PaddingValues =
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = edgeX

            override fun calculateTopPadding(): Dp = edgeY + overhang()

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = edgeX

            override fun calculateBottomPadding(): Dp = edgeY + overhang()
        }
}

/** For the grid of [view]: lays it out [GridView.overhang] past the view at the top and bottom, with the view's size. */
internal fun Modifier.overhanging(view: GridView): Modifier =
    onPlaced { view.coordinates = it }.layout { measurable, constraints ->
        val overhang = view.overhangPx(this)
        val placeable = measurable.measure(constraints.offset(vertical = 2 * overhang))
        layout(placeable.width, placeable.height - 2 * overhang) { placeable.place(0, -overhang) }
    }

/**
 * Cuts what a card of [view]'s grid draws at the view's top and bottom while the grid overhangs it, as the grid's own clip
 * does without the overhang; a card that [focused] says has focus is left to the grid's clip.
 */
internal fun Modifier.cutAtView(
    view: GridView,
    focused: () -> Boolean,
): Modifier = this then CutAtViewElement(view, focused)

private data class CutAtViewElement(
    val view: GridView,
    val focused: () -> Boolean,
) : ModifierNodeElement<CutAtViewNode>() {
    override fun create() = CutAtViewNode(view, focused)

    override fun update(node: CutAtViewNode) {
        node.view = view
        node.focused = focused
        node.invalidateDraw()
    }
}

private class CutAtViewNode(
    var view: GridView,
    var focused: () -> Boolean,
) : Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode {
    /** The view's top and bottom in the card's pixels, held at one card height past the card, as it draws no further. */
    private var top = 0f
    private var bottom = 0f

    /** Reads where the view's edges are from the card; true when they moved where they could cut it. */
    private fun measureCut(card: LayoutCoordinates): Boolean {
        val y = view.coordinates.localPositionOf(card, Offset.Zero).y
        val height = card.size.height.toFloat()
        val newTop = (-y).coerceAtLeast(-height)
        val newBottom = (view.coordinates.size.height - y).coerceAtMost(2f * height)
        val moved = newTop != top || newBottom != bottom
        top = newTop
        bottom = newBottom
        return moved
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (view.overhang() != 0.dp && measureCut(coordinates)) invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        if (view.overhang() == 0.dp || focused()) {
            drawContent()
            return
        }
        measureCut(requireLayoutCoordinates())
        clipRect(left = -size.width, top = top, right = 2f * size.width, bottom = bottom) { this@draw.drawContent() }
    }
}
