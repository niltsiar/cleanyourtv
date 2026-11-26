package dev.niltsiar.tvhome

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.ResourceFont

/**
 * The style of the [index]th text tagged [tag] as it was laid out, with the colour it is drawn in. The hosts' tests read
 * the look from it: under Robolectric's legacy graphics, which `:app` runs on, a captured image is empty.
 */
internal fun SemanticsNodeInteractionsProvider.textStyle(
    tag: String,
    index: Int = 0,
): TextStyle {
    val node = onAllNodesWithTag(tag, useUnmergedTree = true)[index].fetchSemanticsNode()
    val results = mutableListOf<TextLayoutResult>()
    checkNotNull(node.config[SemanticsActions.GetTextLayoutResult].action).invoke(results)
    return results.single().layoutInput.style
}

/** The ROND axis of the [index]th text tagged [tag]: Expressive sets every type role to 100. */
internal fun SemanticsNodeInteractionsProvider.roundness(
    tag: String,
    index: Int = 0,
): Float {
    val font = (textStyle(tag, index).fontFamily as FontListFontFamily).fonts.single() as ResourceFont
    return font.variationSettings.settings
        .single { it.axisName == "ROND" }
        .toVariationValue(null)
}
