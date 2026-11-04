package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Scrolls a focused item so its leading edge rests [pivot] pixels from the viewport start, which keeps
 * it clear of the glass bar. An item too tall to fit there is aligned to the trailing edge instead.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class PivotBringIntoViewSpec(
    private val pivot: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val leadingTarget = if (containerSize - pivot < size) containerSize - size else pivot
        return offset - leadingTarget
    }
}
