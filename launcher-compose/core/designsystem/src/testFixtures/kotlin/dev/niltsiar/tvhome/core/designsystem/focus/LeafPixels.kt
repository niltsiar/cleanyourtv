package dev.niltsiar.tvhome.core.designsystem.focus

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import kotlin.math.roundToInt

/**
 * Each tagged leaf on screen, a tagged node with no tagged node under it, with the screen's pixels inside its bounds. The
 * key is the tag and, for a tag that repeats, its place among the nodes with that tag in the order of the tree. It fails
 * when the screen has no tagged leaf.
 */
public fun SemanticsNodeInteractionsProvider.taggedLeafPixels(): Map<String, List<Int>> {
    val image = onRoot().captureToImage().toPixelMap()
    val screen = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())
    val seen = mutableMapOf<String, Int>()
    val leaves = mutableMapOf<String, List<Int>>()
    for (node in onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag), useUnmergedTree = true).fetchSemanticsNodes()) {
        val tag = node.config[SemanticsProperties.TestTag]
        val place = seen.merge(tag, 1, Int::plus)!!
        if (node.hasTaggedDescendant()) continue
        val bounds = node.boundsInRoot.intersect(screen)
        val left = bounds.left.roundToInt()
        val top = bounds.top.roundToInt()
        val right = bounds.right.roundToInt()
        val bottom = bounds.bottom.roundToInt()
        if (right <= left || bottom <= top) continue
        val key = if (place == 1) tag else "$tag ($place)"
        leaves[key] = buildList { for (y in top until bottom) for (x in left until right) add(image[x, y].toArgb()) }
    }
    check(leaves.isNotEmpty()) { "No tagged leaf on screen, so there is nothing to compare" }
    return leaves
}

/** The keys of [glass]'s leaves that draw the same pixels in [expressive]; a leaf only one of them shows is not compared. */
public fun sameInBothLooks(
    glass: Map<String, List<Int>>,
    expressive: Map<String, List<Int>>,
): List<String> = glass.filter { (key, pixels) -> expressive[key] == pixels }.keys.toList()

private fun SemanticsNode.hasTaggedDescendant(): Boolean =
    children.any { SemanticsProperties.TestTag in it.config || it.hasTaggedDescendant() }
