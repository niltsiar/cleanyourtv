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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.roundToIntRect
import kotlin.math.ceil
import kotlin.math.roundToInt

private val MenuGap = 10.dp
private val MenuRise = 14.dp
private const val MENU_START_SCALE = 0.94f
private val PanelPadding = 14.dp
private val RowSpacing = 8.dp
private val RowHeight = 46.dp
private const val TITLE_ALPHA = 0.75f
private val TitlePadding = 8.dp

/** Rounding a tile's width to whole pixels can leave a panel a pixel or two past the column edge it should end on. */
private const val EDGE_SLACK_PX = 2

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
 * row, and never goes above [topLimit]; focus cannot leave it. A [title] is a header row over the rows, tagged
 * `menu:title`. [above], [anchorGap], [columnGap] and [edgeMargin] are [GlassDropdown]'s.
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
    title: String? = null,
    above: Boolean = false,
    anchorGap: Dp = MenuGap,
    columnGap: Dp? = null,
    edgeMargin: Dp = 0.dp,
) {
    val first = remember { FocusRequester() }
    val rowShape = remember(padding) { RoundedCornerShape(OverlayCorner - padding) }
    GlassDropdown(
        anchor,
        alignEnd,
        first,
        modifier,
        edgeMargin = edgeMargin,
        topLimit = topLimit,
        beside = beside,
        padding = padding,
        above = above,
        anchorGap = anchorGap,
        columnGap = columnGap,
    ) {
        if (title != null) MenuTitle(title)
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
 * one of the rows; focus cannot leave the panel. With [above] the panel sits 10 dp above the anchor when it fits
 * there, before it tries below; [anchorGap] is that distance, 10 dp by default. With [bottomLimit] (in the root's
 * coordinates) a panel that hangs below the anchor ends at or above it, and scrolls when its content is taller. With
 * [columnGap] the anchor is one column of a grid whose columns are [columnGap] apart: the panel is a whole number of
 * columns wide, plus the gaps between them, and starts at the anchor's left edge (or ends at its right edge when it
 * would pass [edgeMargin] from the screen's side); [beside] it, [BesideAnchor.gap] should be [columnGap] so that the
 * panel starts on the next column and ends on the one before.
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
    above: Boolean = false,
    anchorGap: Dp = MenuGap,
    bottomLimit: Dp = Dp.Unspecified,
    columnGap: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = TvTheme.motion
    val scroll = rememberScrollState()
    val columnWidth by remember(anchor) { derivedStateOf { anchor.value.width } }
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
                            .then(if (columnGap != null) Modifier.fillMaxWidth() else Modifier)
                            .widthIn(min = minWidth, max = maxWidth)
                            .width(IntrinsicSize.Max)
                            .then(if (bottomLimit.isSpecified) Modifier.verticalScroll(scroll) else Modifier)
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
        val measurable = measurables.single()
        val columns = columnGap?.roundToPx()
        val limits =
            if (columns == null) {
                constraints.copy(minWidth = 0, minHeight = 0)
            } else {
                val content = measurable.maxIntrinsicWidth(constraints.maxHeight)
                val width = if (columnWidth > 0f) columnsWidth(content, columnWidth, columns) else content
                width.coerceAtMost(constraints.maxWidth).let { constraints.copy(minWidth = it, maxWidth = it, minHeight = 0) }
            }
        val gap = anchorGap.roundToPx()
        val limited = bottomLimit.isSpecified && beside == null
        val room = if (limited) bottomLimit.roundToPx() - anchor.value.bottom.toInt() - gap else Int.MAX_VALUE
        val panel = measurable.measure(limits.copy(maxHeight = minOf(limits.maxHeight, maxOf(room, 0))))
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
            val margin = edgeMargin.roundToPx()
            val end = alignEnd || (columns != null && at.left.toInt() + panel.width > constraints.maxWidth - margin + EDGE_SLACK_PX)
            val x =
                (if (end) at.right.toInt() - panel.width else at.left.toInt()).coerceIn(
                    margin,
                    maxOf(margin, constraints.maxWidth - panel.width - margin),
                )
            val y = dropdownTop(at.top.toInt(), at.bottom.toInt(), panel.height, constraints.maxHeight, gap, topLimit.roundToPx(), above)
            panel.place(x, y)
        }
    }
}

/**
 * Where a panel [panelHeight] tall goes vertically for an anchor spanning [anchorTop] to [anchorBottom]: below it, else
 * above it, each [gap] away; with [preferAbove], above it first. When neither side has room the panel overlaps the
 * anchor, but stays between [topLimit] and the bottom of the [containerHeight] (the top first, when the panel is taller
 * than that).
 */
internal fun dropdownTop(
    anchorTop: Int,
    anchorBottom: Int,
    panelHeight: Int,
    containerHeight: Int,
    gap: Int,
    topLimit: Int,
    preferAbove: Boolean = false,
): Int {
    val above = anchorTop - gap - panelHeight
    if (preferAbove && above >= topLimit) return above
    val below = anchorBottom + gap
    if (below + panelHeight <= containerHeight) return below
    if (above >= topLimit) return above
    return above.coerceIn(topLimit, maxOf(topLimit, containerHeight - panelHeight))
}

/**
 * The width of the fewest columns, each [column] wide with [gap] between them, that hold [content]: at least one column,
 * and a whole number of them plus the gaps between.
 */
internal fun columnsWidth(
    content: Int,
    column: Float,
    gap: Int,
): Int {
    val count = ceil((content + gap) / (column + gap)).toInt().coerceAtLeast(1)
    return (count * column + (count - 1) * gap).roundToInt()
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
private fun MenuTitle(text: String) {
    val typography = TvTheme.typography
    val style = remember(typography) { typography.chipKey.copy(color = typography.chipKey.color.copy(alpha = TITLE_ALPHA)) }
    BasicText(
        text,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("menu:title").fillMaxWidth().padding(horizontal = 16.dp, vertical = TitlePadding),
    )
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
