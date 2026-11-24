package dev.niltsiar.tvhome.core.designsystem.focus

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isFocused

/**
 * What a key script saw: the focused node's tag ([focusedTag]) before the first step and after each one, and every tagged
 * node's bounds ([taggedBounds]) at the same moments.
 */
public data class FocusTrace(
    public val tags: List<String>,
    public val bounds: List<List<Pair<String, Rect>>>,
)

/** The test tag of the focused node or of its nearest tagged ancestor; without one, "cd:" and its content description. */
public fun SemanticsNodeInteractionsProvider.focusedTag(): String {
    var node: SemanticsNode? = onNode(isFocused()).fetchSemanticsNode()
    while (node != null) {
        node.config.getOrNull(SemanticsProperties.TestTag)?.let { return it }
        node.config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()
            ?.let { return "cd:$it" }
        node = node.parent
    }
    error("the focused node has neither a tag nor a content description")
}

/** Every tagged node's tag and bounds in the root, in the order of the tree. */
public fun SemanticsNodeInteractionsProvider.taggedBounds(): List<Pair<String, Rect>> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.TestTag] to it.boundsInRoot }

/** Runs [steps] in order, each one a key press that settles before it returns, and records the trace. */
public fun SemanticsNodeInteractionsProvider.trace(steps: List<() -> Unit>): FocusTrace {
    val tags = mutableListOf(focusedTag())
    val bounds = mutableListOf(taggedBounds())
    for (step in steps) {
        step()
        tags += focusedTag()
        bounds += taggedBounds()
    }
    return FocusTrace(tags, bounds)
}
