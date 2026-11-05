package dev.niltsiar.tvhome.feature.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.designsystem.BesideAnchor
import dev.niltsiar.tvhome.core.designsystem.GlassMenu
import dev.niltsiar.tvhome.core.designsystem.OkHold
import dev.niltsiar.tvhome.core.designsystem.OverlaySurface
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.eatsOkUp
import dev.niltsiar.tvhome.core.model.LibraryArt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

private val PanelMaxWidth = 800.dp
private val PanelMargin = 32.dp
private val PanelHeight = 282.dp
private val PanelSide = 12.dp
private val PanelRise = 56.dp
private const val PANEL_START_SCALE = 0.92f

/** A focused card's growth and ring reach 13 dp past its sides, and the row clips at its sides. */
private val RowPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)
private val CardGap = 16.dp
private const val WHOLE_CARDS = 5

/** How much of the next card shows at an edge of the row, under that edge's fade. */
private val Peek = 40.dp
private val EdgeFade = 48.dp

/** The right edge's fade is narrower, so it fades only the outer part of the next card's peek. */
private val RightFade = 40.dp
private val MenuEdge = 48.dp
private val HighlightGrow = 6.dp
private val HighlightRadius = 20.dp
private const val HIGHLIGHT_STIFFNESS = 260f
private const val HIGHLIGHT_DAMPING = 0.58f

/**
 * The App library over Home, for Home's overlay host, which dims what is behind it. It is composed whenever the
 * library is [LibraryUiState.open]: a Back handler from then on, and the panel once the catalogue has loaded.
 */
@Composable
public fun LibraryOverlay(
    state: () -> LibraryUiState,
    layout: LibraryLayout,
    actions: LibraryActions,
    art: suspend (String) -> LibraryArt?,
    modifier: Modifier = Modifier,
) {
    val open by remember { derivedStateOf { state().open } }
    val shown by remember { derivedStateOf { state().shown } }
    val currentActions = rememberUpdatedState(actions)
    BackHandler(enabled = open) { currentActions.value.onBack() }
    if (shown) LibraryScreen(state, layout, actions, art, modifier)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun LibraryScreen(
    state: () -> LibraryUiState,
    layout: LibraryLayout,
    actions: LibraryActions,
    art: suspend (String) -> LibraryArt?,
    modifier: Modifier = Modifier,
) {
    val reveal = remember { Animatable(if (layout.animated) 0f else 1f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow))
    }
    val hold = remember { OkHold() }
    val anchor = remember { mutableStateOf(Rect.Zero) }
    val entrance = remember { CardEntrance() }
    LaunchedEffect(entrance) {
        delay(STAGGER_WINDOW_MS)
        entrance.staggering = false
    }
    val menu by remember { derivedStateOf { state().menu } }
    val empty by remember { derivedStateOf { state().cards.isEmpty() } }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .testTag("library")
                .onPreviewKeyEvent { hold.eatsOkUp(it) }
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        OverlaySurface(
            modifier =
                Modifier
                    .padding(horizontal = PanelMargin)
                    .widthIn(max = PanelMaxWidth)
                    .fillMaxWidth()
                    .height(PanelHeight)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = reveal.value.coerceIn(0f, 1f)
                        translationY = (1f - reveal.value) * PanelRise.toPx()
                        val scale = PANEL_START_SCALE + (1f - PANEL_START_SCALE) * reveal.value
                        scaleX = scale
                        scaleY = scale
                    },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = PanelSide, top = 8.dp, end = PanelSide, bottom = 12.dp),
            ) {
                Header(state, showHint = !empty)
                if (empty) Empty() else Cards(state, layout, actions, art, entrance, hold) { anchor.value = it }
            }
        }
        menu?.let { open ->
            GlassMenu(
                rows = open.rows,
                anchor = anchor,
                alignEnd = false,
                label = LibraryRow::label,
                rowTag = { "menu:${it.name}" },
                onPick = actions.onPick,
                beside = BesideAnchor(top = MenuEdge, fallbackTop = MenuEdge),
            )
        }
    }
}

@StringRes
private fun LibraryRow.label(): Int =
    when (this) {
        LibraryRow.Open -> R.string.library_open
        LibraryRow.AddToHome -> R.string.library_add_home
        LibraryRow.RemoveFromHome -> R.string.library_remove_shortcut
        LibraryRow.AppInfo -> R.string.library_app_info
    }

@Composable
private fun Header(
    state: () -> LibraryUiState,
    showHint: Boolean,
) {
    val count by remember { derivedStateOf { state().cards.size } }
    val hint by remember { derivedStateOf { state().hint } }
    val typography = TvTheme.typography
    val title = remember(typography) { typography.shelfTitle.copy(fontSize = 26.sp) }
    val countStyle = remember(typography) { typography.date.copy(fontSize = 18.sp) }
    val muted = remember(typography) { typography.date.copy(fontSize = 14.sp) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.app_library), style = title, maxLines = 1, modifier = Modifier.weight(1f))
            BasicText(pluralStringResource(R.plurals.library_app_count, count, count), style = countStyle, maxLines = 1)
        }
        if (showHint) BasicText(hintText(hint), style = muted, maxLines = 1, modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun hintText(hint: LibraryHint): String =
    when (hint) {
        LibraryHint.Open -> {
            stringResource(R.string.library_hint_open)
        }

        LibraryHint.Add -> {
            stringResource(R.string.library_hint_add)
        }

        LibraryHint.SelectAdd -> {
            stringResource(R.string.library_hint_select, stringResource(android.R.string.ok), stringResource(R.string.library_add_home))
        }

        LibraryHint.SelectRemove -> {
            stringResource(
                R.string.library_hint_select,
                stringResource(android.R.string.ok),
                stringResource(R.string.library_remove_shortcut),
            )
        }
    }

/** With nothing to list the panel itself takes focus, so Home behind it gets no key. */
@Composable
private fun Empty() {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestFocus()
    }
    Box(Modifier.fillMaxSize().focusRequester(focus).focusable(), contentAlignment = Alignment.Center) {
        BasicText(
            stringResource(R.string.library_empty),
            style = TvTheme.typography.pillLabel,
            modifier = Modifier.testTag("library:empty"),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Cards(
    state: () -> LibraryUiState,
    layout: LibraryLayout,
    actions: LibraryActions,
    art: suspend (String) -> LibraryArt?,
    entrance: CardEntrance,
    hold: OkHold,
    anchor: (Rect) -> Unit,
) {
    val listState = rememberLazyListState()
    val focus = remember { CardFocus() }
    val density = LocalDensity.current
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val window = with(density) { windowWidth.toDp() }
    val rowWidth = minOf(PanelMaxWidth, window - PanelMargin * 2) - PanelSide * 2
    val cardWidth = (rowWidth - RowPadding.calculateLeftPadding(LayoutDirection.Ltr) - Peek) / WHOLE_CARDS - CardGap
    val bringIntoView = remember(listState, density) { PeekBringIntoViewSpec(listState, with(density) { (Peek + CardGap).toPx() }) }
    LaunchedEffect(listState) {
        snapshotFlow { state().focusRequest }.filterNotNull().collect { request ->
            val index = state().cards.indexOfFirst { it.app.key == request.key }
            if (index < 0) return@collect
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
            withFrameNanos { }
            withFrameNanos { }
            focus.request(request.key)
        }
    }
    Box(Modifier.fillMaxWidth().fillMaxHeight()) {
        Highlight(listState, layout, cardWidth, focusedIndex = { state().let { s -> s.cards.indexOfFirst { it.app.key == s.focusedKey } } })
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
            LazyRow(
                state = listState,
                contentPadding = RowPadding,
                horizontalArrangement = Arrangement.spacedBy(CardGap),
                modifier = Modifier.fillMaxSize().rowEdges(listState).testTag("library:cards"),
            ) {
                val cards = state().cards
                itemsIndexed(cards, key = { _, card -> card.app.key }) { index, card ->
                    LibraryCardView(
                        card = card,
                        index = index,
                        width = cardWidth,
                        layout = layout,
                        art = art,
                        entrance = entrance,
                        actions = actions,
                        hold = hold,
                        anchor = anchor,
                        requester = focus.requester(card.app.key),
                        modifier =
                            Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = TvTheme.motion.placementSpec,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Keeps the focused card [margin] pixels inside both ends of the row, so a whole neighbour never hides under an edge
 * fade and the next card peeks. Where less than [margin] of the row is left to scroll, it scrolls to the row's end.
 */
@OptIn(ExperimentalFoundationApi::class)
private class PeekBringIntoViewSpec(
    private val list: LazyListState,
    private val margin: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        // The offset comes with the focused card's growth in it, the size without; the card's slot in the row has neither.
        val info = list.layoutInfo
        val center = offset + size / 2f
        val slot = info.visibleItemsInfo.firstOrNull { center - (it.offset - info.viewportStartOffset) in 0f..it.size.toFloat() }
        val start = slot?.let { (it.offset - info.viewportStartOffset).toFloat() } ?: offset
        val end = slot?.let { start + it.size } ?: (offset + size)
        val trailing = end - (containerSize - margin)
        val leading = start - margin
        return when {
            trailing > 0f -> list.aheadRoom().let { room -> if (room - trailing < margin) room else trailing }
            leading < 0f -> list.backRoom().let { room -> if (room + leading < margin) -room else leading }
            else -> 0f
        }
    }
}

/** How far the row can still scroll back, in pixels: infinite while its first card is not laid out. */
private fun LazyListState.backRoom(): Float {
    val info = layoutInfo
    val first = info.visibleItemsInfo.firstOrNull() ?: return 0f
    if (first.index != 0) return Float.POSITIVE_INFINITY
    return (info.viewportStartOffset + info.beforeContentPadding - first.offset).toFloat()
}

/** How far the row can still scroll on, in pixels: infinite while its last card is not laid out, 0 for a row that fits. */
private fun LazyListState.aheadRoom(): Float {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return 0f
    if (last.index != info.totalItemsCount - 1) return Float.POSITIVE_INFINITY
    return (last.offset + last.size - (info.viewportEndOffset - info.afterContentPadding)).toFloat().coerceAtLeast(0f)
}

/**
 * The row's ends, drawn as masks in the draw phase: an [EdgeFade] on the left and a [RightFade] on the right, at each
 * end the row can scroll towards, coming in over the first [EdgeFade] of scrolling. The cards' rings reach above and
 * below the row, so only the sides are cut.
 */
private fun Modifier.rowEdges(list: LazyListState): Modifier =
    drawWithCache {
        val fade = EdgeFade.toPx()
        val rightFade = RightFade.toPx()
        val layerPaint = Paint()
        onDrawWithContent {
            val left = (list.backRoom() / fade).coerceIn(0f, 1f)
            val right = (list.aheadRoom() / fade).coerceIn(0f, 1f)
            val bodyLeft =
                if (left > 0f) {
                    val mask =
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 1f - left),
                            1f to Color.Black,
                            startX = 0f,
                            endX = fade,
                        )
                    drawMasked(0f, fade, mask, layerPaint)
                    fade
                } else {
                    0f
                }
            val rightStart = size.width - rightFade
            val bodyRight =
                if (right > 0f) {
                    val mask =
                        Brush.horizontalGradient(
                            0f to Color.Black,
                            1f to Color.Black.copy(alpha = 1f - right),
                            startX = rightStart,
                            endX = size.width,
                        )
                    drawMasked(rightStart, size.width, mask, layerPaint)
                    rightStart
                } else {
                    size.width
                }
            clipRect(left = bodyLeft, top = -size.height, right = bodyRight, bottom = size.height * 2) {
                this@onDrawWithContent.drawContent()
            }
        }
    }

/** The content between [left] and [right], its alpha multiplied by [mask]'s in a layer of that strip only. */
private fun ContentDrawScope.drawMasked(
    left: Float,
    right: Float,
    mask: Brush,
    paint: Paint,
) {
    if (right <= left) return
    val strip = Rect(left, -size.height, right, size.height * 2)
    val canvas = drawContext.canvas
    canvas.saveLayer(strip, paint)
    clipRect(strip.left, strip.top, strip.right, strip.bottom) { this@drawMasked.drawContent() }
    drawRect(mask, topLeft = strip.topLeft, size = strip.size, blendMode = BlendMode.DstIn)
    canvas.restore()
}

/**
 * A rounded square in the surface's text colour behind the focused card. It slides to the next card on a spring that
 * overshoots a little and is kept in the row's own coordinates, so scrolling carries it along. Its position is read in
 * the layer lambda.
 */
@Composable
private fun Highlight(
    listState: LazyListState,
    layout: LibraryLayout,
    cardWidth: Dp,
    focusedIndex: () -> Int,
) {
    val density = LocalDensity.current
    // The row lays its cards out at whole pixels.
    val stride = with(density) { (cardWidth.roundToPx() + CardGap.roundToPx()).toFloat() }
    val start = with(density) { RowPadding.calculateLeftPadding(LayoutDirection.Ltr).roundToPx().toFloat() }
    val ink = TvTheme.colors.onSurface
    val grow = with(density) { HighlightGrow.toPx() }
    val left = remember { Animatable(0f) }
    val visible = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow(focusedIndex).filter { it >= 0 }.collect { index ->
            val target = start + index * stride - grow
            if (visible.value == 0f || !layout.animated) {
                left.snapTo(target)
                if (layout.animated) visible.animateTo(1f, tween(HIGHLIGHT_FADE_MS)) else visible.snapTo(1f)
            } else {
                left.animateTo(target, spring(dampingRatio = HIGHLIGHT_DAMPING, stiffness = HIGHLIGHT_STIFFNESS))
            }
        }
    }
    val width = cardWidth + HighlightGrow * 2
    val height = CardHeight + HighlightGrow * 2
    Box(
        Modifier
            .padding(top = RowPadding.calculateTopPadding() - HighlightGrow)
            .size(width, height)
            .graphicsLayer {
                alpha = visible.value.coerceIn(0f, 1f)
                translationX = left.value - (listState.firstVisibleItemIndex * stride + listState.firstVisibleItemScrollOffset)
            }.drawBehind {
                drawRoundRect(
                    Brush.verticalGradient(listOf(ink.copy(alpha = 0.22f), ink.copy(alpha = 0.06f))),
                    cornerRadius = CornerRadius(HighlightRadius.toPx()),
                )
                drawRoundRect(
                    ink.copy(alpha = 0.3f),
                    cornerRadius = CornerRadius(HighlightRadius.toPx()),
                    style = Stroke(1.dp.toPx()),
                )
            },
    )
}

private const val HIGHLIGHT_FADE_MS = 160

/** The focus requesters of the cards, by key. */
private class CardFocus {
    private val requesters = HashMap<String, FocusRequester>()

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /** Focuses the card; nothing happens when it is not built (any more). */
    fun request(key: String) {
        try {
            requesters[key]?.requestFocus()
        } catch (e: IllegalStateException) {
            // The card is gone by now.
        }
    }
}
