package dev.niltsiar.tvhome.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect

private val MenuGap = 10.dp
private val MenuRise = 14.dp
private const val MENU_START_SCALE = 0.94f
private val PanelPadding = 14.dp
private val RowSpacing = 8.dp
private val RowHeight = 46.dp

/**
 * A panel beside its anchor rather than under it: [gap] right of the anchor, else [gap] left of it, centred on it
 * vertically. Its top stays at or under [top] when the panel fits between that and [bottomMargin] above the screen's
 * bottom, else at or under [fallbackTop]; its bottom stays [bottomMargin] above the screen's bottom.
 */
@Immutable
public class BesideAnchor(
    public val top: Dp,
    public val fallbackTop: Dp,
    public val gap: Dp = 16.dp,
    public val bottomMargin: Dp = 48.dp,
)

/**
 * An overlay panel of [rows] hanging from [anchor] (in the root's coordinates): below it, or above when it would not fit,
 * from its left edge, or from its right edge when [alignEnd]; or [beside] it. Each row is [rowHeight] tall, shows the
 * string [label] gives and carries the test tag [rowTag]; the panel's own tag is `menu`, [padding] inside its edge, and
 * the rows' corners are concentric with the panel's. It rises and fades in with a spring and takes focus on its first
 * row, and never goes above [topLimit]; focus cannot leave it.
 */
@Composable
public fun <T> GlassMenu(
    rows: List<T>,
    anchor: State<Rect>,
    alignEnd: Boolean,
    label: (T) -> Int,
    rowTag: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    topLimit: Dp = 0.dp,
    beside: BesideAnchor? = null,
    rowHeight: Dp = RowHeight,
    padding: Dp = PanelPadding,
) {
    val first = remember { FocusRequester() }
    val rowShape = remember(padding) { RoundedCornerShape(OverlayCorner - padding) }
    GlassDropdown(anchor, alignEnd, first, modifier, topLimit = topLimit, beside = beside, padding = padding) {
        rows.forEachIndexed { index, row ->
            val focus = if (index == 0) Modifier.focusRequester(first) else Modifier
            MenuItem(label(row), rowTag(row), { onPick(row) }, rowHeight, rowShape, focus)
        }
    }
}

/**
 * The overlay panel behind [GlassMenu] and the Inputs list: [content] in a column hanging from [anchor] as [GlassMenu]
 * does. The panel is as wide as its widest row, from [minWidth] to [maxWidth], and stays [edgeMargin] inside the sides of
 * the screen and never above [topLimit] (a top bar to keep clear), or goes [beside] the anchor. [padding] is the space
 * inside the panel's edge. It rises and fades in with a spring, then focus goes to [firstFocus], which the caller puts on
 * one of the rows; focus cannot leave the panel.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun GlassDropdown(
    anchor: State<Rect>,
    alignEnd: Boolean,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
    minWidth: Dp = 0.dp,
    maxWidth: Dp = Dp.Unspecified,
    edgeMargin: Dp = 0.dp,
    topLimit: Dp = 0.dp,
    beside: BesideAnchor? = null,
    padding: Dp = PanelPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = TvTheme.motion
    val reveal = remember { Animatable(if (motion.animations) 0f else 1f) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        firstFocus.requestFocus()
    }
    LaunchedEffect(Unit) { reveal.animateTo(1f, motion.springSpec(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) }
    Layout(
        content = {
            OverlaySurface(
                modifier =
                    Modifier.testTag("menu").graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = reveal.value.coerceIn(0f, 1f)
                        translationY = (1f - reveal.value) * MenuRise.toPx()
                        val scale = MENU_START_SCALE + (1f - MENU_START_SCALE) * reveal.value
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                Column(
                    modifier =
                        Modifier
                            .widthIn(min = minWidth, max = maxWidth)
                            .width(IntrinsicSize.Max)
                            .padding(padding)
                            .focusProperties { exit = { FocusRequester.Cancel } }
                            .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(RowSpacing),
                    content = content,
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val panel = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val at = anchor.value
            if (beside != null) {
                val bottom = constraints.maxHeight - beside.bottomMargin.roundToPx()
                val gap = beside.gap.roundToPx()
                panel.place(
                    besideAnchor(
                        at.roundToIntRect(),
                        panel.width,
                        panel.height,
                        constraints.maxWidth,
                        gap,
                        beside.top.roundToPx(),
                        beside.fallbackTop.roundToPx(),
                        bottom,
                    ),
                )
                return@layout
            }
            val gap = MenuGap.roundToPx()
            val margin = edgeMargin.roundToPx()
            val x =
                (if (alignEnd) at.right.toInt() - panel.width else at.left.toInt()).coerceIn(
                    margin,
                    maxOf(margin, constraints.maxWidth - panel.width - margin),
                )
            panel.place(x, dropdownTop(at.top.toInt(), at.bottom.toInt(), panel.height, constraints.maxHeight, gap, topLimit.roundToPx()))
        }
    }
}

/**
 * Where a panel [panelHeight] tall goes vertically for an anchor spanning [anchorTop] to [anchorBottom]: below it, else
 * above it, each [gap] away. When neither side has room the panel overlaps the anchor, but stays between [topLimit] and
 * the bottom of the [containerHeight] (the top first, when the panel is taller than that).
 */
internal fun dropdownTop(
    anchorTop: Int,
    anchorBottom: Int,
    panelHeight: Int,
    containerHeight: Int,
    gap: Int,
    topLimit: Int,
): Int {
    val below = anchorBottom + gap
    if (below + panelHeight <= containerHeight) return below
    val above = anchorTop - gap - panelHeight
    if (above >= topLimit) return above
    return above.coerceIn(topLimit, maxOf(topLimit, containerHeight - panelHeight))
}

/**
 * Where a panel [panelWidth] by [panelHeight] goes beside an [anchor], in a container [containerWidth] wide: [gap] right
 * of it, else [gap] left of it, centred on it vertically. Its bottom stays at or above [bottom]; its top at or under [top]
 * when the panel fits between [top] and [bottom], else at or under [fallbackTop], which wins over [bottom].
 */
internal fun besideAnchor(
    anchor: IntRect,
    panelWidth: Int,
    panelHeight: Int,
    containerWidth: Int,
    gap: Int,
    top: Int,
    fallbackTop: Int,
    bottom: Int,
): IntOffset {
    val right = anchor.right + gap
    val x = if (right + panelWidth <= containerWidth) right else (anchor.left - gap - panelWidth).coerceAtLeast(0)
    val upper = if (panelHeight <= bottom - top) top else fallbackTop
    val y = (anchor.center.y - panelHeight / 2).coerceAtMost(bottom - panelHeight).coerceAtLeast(upper)
    return IntOffset(x, y)
}

@Composable
private fun MenuItem(
    @StringRes label: Int,
    tag: String,
    onClick: () -> Unit,
    height: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    FocusPill(onClick = onClick, modifier = modifier.fillMaxWidth().height(height).testTag(tag), shape = shape) { ink ->
        BasicText(
            text = stringResource(label),
            style = TvTheme.typography.pillLabel,
            maxLines = 1,
            color = ink.content,
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}
