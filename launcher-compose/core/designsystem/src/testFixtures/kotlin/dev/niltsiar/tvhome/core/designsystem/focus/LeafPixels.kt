package dev.niltsiar.tvhome.core.designsystem.focus

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Each tagged leaf on screen, a tagged node with no tagged node under it, with [ownPixels] inside its bounds. The key is the
 * tag and, for a tag that repeats, its place among the nodes with that tag in the order of the tree. It fails when the
 * screen has no tagged leaf.
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
        if (bounds.width.roundToInt() <= 0 || bounds.height.roundToInt() <= 0) continue
        leaves[if (place == 1) tag else "$tag ($place)"] = image.ownPixels(bounds)
    }
    check(leaves.isNotEmpty()) { "No tagged leaf on screen, so there is nothing to compare" }
    return leaves
}

/**
 * What a leaf at [bounds] (on the screen) draws itself over its ground: the ground is the commonest colour of a ring
 * [GROUND_RING] px wide just outside the bounds, the ink the pixel inside them farthest from the ground, and each other
 * pixel that is not ground is told by its place and its share of the way from the ground to the ink, in 32nds. A leaf
 * whose ink and shape stay while the look changes only its ground gives the same list in both looks; one that shows
 * nothing over its ground gives an empty list.
 */
public fun PixelMap.ownPixels(bounds: Rect): List<Int> {
    val left = bounds.left.roundToInt()
    val top = bounds.top.roundToInt()
    val right = bounds.right.roundToInt()
    val bottom = bounds.bottom.roundToInt()
    val ring = mutableMapOf<Int, Int>()
    for (y in top - GROUND_RING until bottom + GROUND_RING) {
        for (x in left - GROUND_RING until right + GROUND_RING) {
            val inside = x in left until right && y in top until bottom
            if (!inside && x in 0 until width && y in 0 until height) ring.merge(this[x, y].toArgb(), 1, Int::plus)
        }
    }
    val across = right - left
    val pixels = IntArray(across * (bottom - top)) { this[left + it % across, top + it / across].toArgb() }
    val ground = ring.maxByOrNull { it.value }?.key ?: return pixels.toList()
    val ink = pixels.maxBy { distance(it, ground) }
    if (distance(ink, ground) <= GROUND_TOLERANCE) return emptyList()
    val reach = (0..16 step 8).sumOf { shift -> (channel(ink, shift) - channel(ground, shift)).let { it * it } }.toDouble()
    return buildList {
        add(ink)
        pixels.forEachIndexed { index, pixel ->
            if (distance(pixel, ground) > GROUND_TOLERANCE) {
                val along =
                    (0..16 step 8).sumOf { shift ->
                        (channel(pixel, shift) - channel(ground, shift)) *
                            (channel(ink, shift) - channel(ground, shift))
                    }
                add(index)
                add((along / reach * SHARES).roundToInt())
            }
        }
    }
}

private fun channel(
    argb: Int,
    shift: Int,
): Int = argb shr shift and 0xFF

/** The largest gap between two colours' channels. */
private fun distance(
    a: Int,
    b: Int,
): Int = (0..16 step 8).maxOf { shift -> abs(channel(a, shift) - channel(b, shift)) }

/** How wide the ring round a leaf is whose commonest colour is the leaf's ground, in px. */
private const val GROUND_RING = 2

/** How far a pixel's channels may be from the ground and still count as ground. */
private const val GROUND_TOLERANCE = 4

/** The steps a pixel's share of the ink is told in. */
private const val SHARES = 32.0

/**
 * The keys of [glass]'s leaves that draw the same pixels in [expressive]. A leaf only one of them shows is not compared, nor
 * one that draws nothing over its ground in either, as under an opaque panel.
 */
public fun sameInBothLooks(
    glass: Map<String, List<Int>>,
    expressive: Map<String, List<Int>>,
): List<String> =
    glass.filter { (key, pixels) -> pixels.isNotEmpty() && expressive[key]?.let { sameOwnPixels(pixels, it) } == true }.keys.toList()

/**
 * Whether two [ownPixels] lists show the same leaf: the same ink, and each pixel's share of it within [SHARE_SLACK] of the
 * other's, a pixel missing from one list counting as no share. Text drawn over another ground takes a slightly other edge.
 */
public fun sameOwnPixels(
    a: List<Int>,
    b: List<Int>,
): Boolean {
    if (a.firstOrNull() != b.firstOrNull()) return false
    val shares = { list: List<Int> -> (1 until list.size step 2).associate { list[it] to list[it + 1] } }
    val first = shares(a)
    val second = shares(b)
    return (first.keys + second.keys).all { abs((first[it] ?: 0) - (second[it] ?: 0)) <= SHARE_SLACK }
}

/** How many 32nds a pixel's share of the ink may move between two grounds for the leaf to count as the same. */
private const val SHARE_SLACK = 3

private fun SemanticsNode.hasTaggedDescendant(): Boolean =
    children.any { SemanticsProperties.TestTag in it.config || it.hasTaggedDescendant() }
