package dev.niltsiar.tvhome.feature.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.designsystem.BesideAnchor
import dev.niltsiar.tvhome.core.designsystem.FocusPill
import dev.niltsiar.tvhome.core.designsystem.GlassMenu
import dev.niltsiar.tvhome.core.designsystem.OkHold
import dev.niltsiar.tvhome.core.designsystem.OverlaySurface
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.eatsOkUp
import dev.niltsiar.tvhome.core.designsystem.menuReleased
import dev.niltsiar.tvhome.core.model.LibraryArt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val PanelMaxWidth = 800.dp
private val PanelMargin = 32.dp
private val PanelEdge = 16.dp
private val EmptyPanelHeight = 282.dp
private val PanelSide = 12.dp
private val PanelTop = 8.dp
private val PanelBottom = 12.dp
private val HeaderTitleHeight = 48.dp
private val HeaderHintHeight = 28.dp
private val PanelRise = 56.dp
private const val PANEL_START_SCALE = 0.92f

/** The grid: [COLUMNS] cards across and [ROWS] rows in view, [CardGap] apart. */
private const val COLUMNS = 6
private const val ROWS = 3
private val CardGap = 20.dp

/** A focused card's growth and ring reach 13 dp past its sides, and the grid clips at its edges. */
private val GridEdge = 13.dp

private val PanelHeight =
    PanelTop + HeaderTitleHeight + HeaderHintHeight + GridEdge * 2 + CardHeight * ROWS + CardGap * (ROWS - 1) + PanelBottom
private val MenuEdge = 48.dp

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
    val gridState = rememberLazyGridState()
    val cardFocus = remember { CardFocus() }
    val chip = remember { ChipFocus() }
    val scope = rememberCoroutineScope()
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .testTag("library")
                .onPreviewKeyEvent { hold.eatsOkUp(it) || (layout.leastUsed && it.switchesOrder(actions)) }
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        OverlaySurface(
            modifier =
                Modifier
                    .padding(horizontal = PanelMargin, vertical = PanelEdge)
                    .widthIn(max = PanelMaxWidth)
                    .fillMaxWidth()
                    .heightIn(max = if (empty) EmptyPanelHeight else PanelHeight)
                    .fillMaxHeight()
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
                        .padding(start = PanelSide, top = PanelTop, end = PanelSide, bottom = PanelBottom),
            ) {
                Header(state, layout, actions, chip, empty) {
                    val cards = state().cards
                    val index = cards.indexOfFirst { it.app.key == state().focusedKey }.coerceAtLeast(0)
                    cards.getOrNull(index)?.let { scope.launch { cardFocus.arrive(gridState, index, it.app.key) } }
                }
                if (empty) Empty() else Cards(state, layout, actions, art, entrance, hold, gridState, cardFocus, chip) { anchor.value = it }
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

/** The MENU key switches the order when it is released, as it opens a tile menu in Home; its press is eaten too. */
private fun KeyEvent.switchesOrder(actions: LibraryActions): Boolean {
    if (key != Key.Menu) return false
    if (type == KeyEventType.KeyUp && menuReleased(isUp = true, flags = nativeKeyEvent.flags)) actions.onOrder()
    return true
}

@Composable
private fun Header(
    state: () -> LibraryUiState,
    layout: LibraryLayout,
    actions: LibraryActions,
    chip: ChipFocus,
    empty: Boolean,
    onChipDown: () -> Unit,
) {
    val count by remember { derivedStateOf { state().cards.size } }
    val hint by remember { derivedStateOf { state().hint } }
    val order by remember { derivedStateOf { state().order } }
    val noUsage by remember { derivedStateOf { state().noUsageYet } }
    val typography = TvTheme.typography
    val title = remember(typography) { typography.shelfTitle.copy(fontSize = 26.sp) }
    val countStyle = remember(typography) { typography.date.copy(fontSize = 18.sp) }
    val muted = remember(typography) { typography.date.copy(fontSize = 14.sp) }
    var countWidth by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.height(HeaderTitleHeight), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                stringResource(if (layout.selection) R.string.library_title_choose else R.string.app_library),
                style = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (layout.leastUsed) {
                val end = Modifier.padding(end = ChipEnd)
                if (empty) {
                    BasicText(stringResource(order.label()), style = countStyle, maxLines = 1, modifier = end)
                } else {
                    OrderChip(stringResource(order.label()), actions.onOrder, chip, onChipDown, end)
                }
            }
            BasicText(
                pluralStringResource(R.plurals.library_app_count, count, count),
                style = countStyle,
                maxLines = 1,
                modifier = Modifier.onSizeChanged { countWidth = it.width },
            )
        }
        if (!empty) {
            val other = stringResource(order.other().label())
            val keys =
                if (layout.leastUsed && chip.focused) {
                    stringResource(R.string.library_hint_select, stringResource(android.R.string.ok), other)
                } else if (layout.leastUsed) {
                    "${hintText(hint)} · ${stringResource(R.string.library_hint_order, other)}"
                } else {
                    hintText(hint)
                }
            Row(Modifier.height(HeaderHintHeight), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    keys,
                    style = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("library:hint"),
                )
                if (noUsage) {
                    // Ends where the order chip does: the count and the gap after the chip in from the header's end.
                    val inset = ChipEnd + with(LocalDensity.current) { countWidth.toDp() }
                    BasicText(
                        stringResource(R.string.library_no_usage_yet),
                        style = muted,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 12.dp, end = inset).testTag("library:status"),
                    )
                }
            }
        }
    }
}

/** Whether the order chip has focus: a card's focus request waits while it does, so a new order leaves focus where it is. */
private class ChipFocus {
    val requester = FocusRequester()
    var focused by mutableStateOf(false)
}

/**
 * The order's name as a chip, with a sort glyph before it: OK switches the order, as the MENU key does. It fills like a
 * focused menu row. Up from the first row of cards reaches it and Down returns to the card that had focus, through
 * [onDown]; no other key leaves it.
 */
@Composable
private fun OrderChip(
    label: String,
    onClick: () -> Unit,
    focus: ChipFocus,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = TvTheme.typography
    val rest = TvTheme.colors.onSurface
    FocusPill(
        onClick = onClick,
        shape = CircleShape,
        modifier =
            modifier
                .height(ChipHeight)
                .testTag("library:order")
                .focusRequester(focus.requester)
                .focusProperties {
                    up = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }.onFocusChanged { focus.focused = it.isFocused }
                .onPreviewKeyEvent {
                    if (it.key != Key.DirectionDown) return@onPreviewKeyEvent false
                    if (it.type == KeyEventType.KeyDown) onDown()
                    true
                },
        background = {
            Box(
                Modifier
                    .matchParentSize()
                    .background(rest.copy(alpha = CHIP_FILL_ALPHA), CircleShape)
                    .border(1.dp, rest.copy(alpha = CHIP_RIM_ALPHA), CircleShape),
            )
        },
    ) { ink ->
        Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(ChipGlyph).testTag("library:order:glyph").drawBehind { drawSortArrows(ink.content(), size.width) })
            Spacer(Modifier.width(ChipGlyphGap))
            BasicText(label, style = typography.chipLabel, maxLines = 1, color = ink.content)
        }
    }
}

/** Two opposed arrows, the first up and the second down, filling a square of [side]. */
private fun DrawScope.drawSortArrows(
    color: Color,
    side: Float,
) {
    val arrows =
        Path().apply {
            // Each arrow: its x, the end of its tail and its tip, in fractions of the side.
            listOf(Triple(0.27f, 0.9f, 0.1f), Triple(0.73f, 0.1f, 0.9f)).forEach { (x, tail, tip) ->
                val arm = if (tail > tip) tip + 0.2f else tip - 0.2f
                moveTo(x * side, tail * side)
                lineTo(x * side, tip * side)
                moveTo((x - 0.17f) * side, arm * side)
                lineTo(x * side, tip * side)
                lineTo((x + 0.17f) * side, arm * side)
            }
        }
    drawPath(arrows, color, style = Stroke(ChipGlyphStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private val ChipEnd = 16.dp
private val ChipHeight = 32.dp
private val ChipGlyph = 14.dp
private val ChipGlyphGap = 6.dp
private val ChipGlyphStroke = 1.5.dp
private const val CHIP_FILL_ALPHA = 0.10f
private const val CHIP_RIM_ALPHA = 0.20f

@StringRes
private fun LibraryOrder.label(): Int =
    when (this) {
        LibraryOrder.Usual -> R.string.library_order_usual
        LibraryOrder.LeastUsed -> R.string.library_order_least_used
    }

private fun LibraryOrder.other(): LibraryOrder = if (this == LibraryOrder.Usual) LibraryOrder.LeastUsed else LibraryOrder.Usual

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
    gridState: LazyGridState,
    focus: CardFocus,
    chip: ChipFocus,
    anchor: (Rect) -> Unit,
) {
    val density = LocalDensity.current
    val windowWidth = LocalWindowInfo.current.containerSize.width
    // The narrowest a column comes out: the grid shares what is left of its width in whole pixels.
    val cardWidthPx =
        with(density) {
            val panel = minOf(PanelMaxWidth.roundToPx(), windowWidth - PanelMargin.roundToPx() * 2)
            (panel - PanelSide.roundToPx() * 2 - GridEdge.roundToPx() * 2 - CardGap.roundToPx() * (COLUMNS - 1)) / COLUMNS
        }
    val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pitch = with(density) { (CardHeight + CardGap).toPx() }
    val bringIntoView = remember(gridState, density) { RowBringIntoViewSpec(gridState, with(density) { GridEdge.toPx() }) }
    // Read here, not in the grid's content, so a focus move does not rebuild what the grid lists.
    val cards = remember(state) { derivedStateOf { state().cards } }.value
    LaunchedEffect(gridState) {
        snapshotFlow { state().focusRequest }.filterNotNull().collect { request ->
            val index = state().cards.indexOfFirst { it.app.key == request.key }
            if (index < 0 || chip.focused) return@collect
            focus.arrive(gridState, index, request.key)
        }
    }
    Box(Modifier.fillMaxWidth().fillMaxHeight()) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                state = gridState,
                contentPadding = PaddingValues(GridEdge),
                horizontalArrangement = Arrangement.spacedBy(CardGap),
                verticalArrangement = Arrangement.spacedBy(CardGap),
                modifier =
                    Modifier.fillMaxSize().testTag("library:cards").onPreviewKeyEvent { event ->
                        val move = event.gridMove(rtl) ?: return@onPreviewKeyEvent false
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val from = cards.indexOfFirst { it.app.key == state().focusedKey }
                        val to = if (from < 0) null else gridNeighbour(from, move, cards.size, COLUMNS)
                        if (to != null) {
                            val sign = if (to > from) 1f else -1f
                            scope.launch { focus.moveTo(gridState, to, cards[to].app.key, sign * pitch) }
                        } else if (layout.leastUsed && move == GridMove.Up && from in 0 until COLUMNS) {
                            chip.requester.requestFocus()
                        }
                        true
                    },
            ) {
                itemsIndexed(cards, key = { _, card -> card.app.key }) { index, card ->
                    LibraryCardView(
                        card = card,
                        index = index,
                        widthPx = cardWidthPx,
                        layout = layout,
                        art = art,
                        measurer = measurer,
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
        ScrollCue(gridState)
    }
}

private val CueWidth = 14.dp
private val CueHeight = 7.dp
private val CueStroke = 2.dp
private const val CUE_ALPHA = 0.55f

/**
 * A chevron under the grid while cards lie below the view, and one in its top padding while cards lie above. It is drawn
 * from the grid's scroll state in the draw phase: two short strokes, no layer, nothing recomposes with a focus move.
 */
@Composable
private fun ScrollCue(grid: LazyGridState) {
    val ink = TvTheme.colors.onSurface
    Box(
        Modifier.fillMaxSize().drawBehind {
            if (grid.canScrollBackward) drawChevron(ink, GridEdge.toPx() / 2f, down = false)
            if (grid.canScrollForward) drawChevron(ink, size.height + PanelBottom.toPx() / 2f, down = true)
        },
    )
}

private fun DrawScope.drawChevron(
    ink: Color,
    centreY: Float,
    down: Boolean,
) {
    val centreX = size.width / 2f
    val halfWidth = CueWidth.toPx() / 2f
    val halfHeight = CueHeight.toPx() / 2f * if (down) 1f else -1f
    val path =
        Path().apply {
            moveTo(centreX - halfWidth, centreY - halfHeight)
            lineTo(centreX, centreY + halfHeight)
            lineTo(centreX + halfWidth, centreY - halfHeight)
        }
    drawPath(path, ink.copy(alpha = CUE_ALPHA), style = Stroke(CueStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** The grid's move for an arrow key, null for any other key. Left and right swap in a right-to-left layout. */
private fun KeyEvent.gridMove(rtl: Boolean): GridMove? =
    when (key) {
        Key.DirectionUp -> GridMove.Up
        Key.DirectionDown -> GridMove.Down
        Key.DirectionLeft -> if (rtl) GridMove.Right else GridMove.Left
        Key.DirectionRight -> if (rtl) GridMove.Left else GridMove.Right
        else -> null
    }

/**
 * Focuses the card at [index], whose key is [key]. A card in a row that is not laid out yet waits for the grid to scroll
 * [scroll] pixels, so the focus moves to a card the grid has attached.
 */
private suspend fun CardFocus.moveTo(
    grid: LazyGridState,
    index: Int,
    key: String,
    scroll: Float,
) {
    coroutineScope {
        if (grid.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            launch { grid.animateScrollBy(scroll) }
            snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
        }
        request(key)
    }
}

/**
 * Keeps the focused card [margin] pixels inside the top and bottom of the grid, so its ring shows whole: the grid scrolls
 * the one row that brings it there and no more.
 */
@OptIn(ExperimentalFoundationApi::class)
private class RowBringIntoViewSpec(
    private val grid: LazyGridState,
    private val margin: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        // The offset comes with the focused card's growth in it, the size without; the card's slot in the grid has neither.
        val info = grid.layoutInfo
        val center = offset + size / 2f
        val slot = info.visibleItemsInfo.firstOrNull { center - (it.offset.y - info.viewportStartOffset) in 0f..it.size.height.toFloat() }
        val start = slot?.let { (it.offset.y - info.viewportStartOffset).toFloat() } ?: offset
        val end = slot?.let { start + it.size.height } ?: (offset + size)
        return when {
            end > containerSize - margin -> end - (containerSize - margin)
            start < margin -> start - margin
            else -> 0f
        }
    }
}

/** The focus requesters of the cards, by key. */
private class CardFocus {
    private val requesters = HashMap<String, FocusRequester>()

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /** Focuses the card at [index], whose key is [key], once the grid has scrolled it in and attached it. */
    suspend fun arrive(
        grid: LazyGridState,
        index: Int,
        key: String,
    ) {
        if (grid.layoutInfo.visibleItemsInfo.none { it.index == index }) grid.scrollToItem(index)
        withFrameNanos { }
        withFrameNanos { }
        request(key)
    }

    /** Focuses the card; nothing happens when it is not built (any more). */
    fun request(key: String) {
        try {
            requesters[key]?.requestFocus()
        } catch (e: IllegalStateException) {
            // The card is gone by now.
        }
    }
}
