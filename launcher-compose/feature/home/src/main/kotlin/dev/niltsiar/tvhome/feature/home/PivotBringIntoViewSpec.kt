package dev.niltsiar.tvhome.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Scrolls a focused item so its leading edge rests at [pivotFraction] of the viewport, which keeps
 * it clear of the glass bar. An item too tall to fit there is aligned to the trailing edge instead.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class PivotBringIntoViewSpec(
    private val pivotFraction: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val target = pivotFraction * containerSize
        val leadingTarget = if (containerSize - target < size) containerSize - size else target
        return offset - leadingTarget
    }
}
