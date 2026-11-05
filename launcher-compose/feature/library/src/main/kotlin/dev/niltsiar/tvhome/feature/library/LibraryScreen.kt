package dev.niltsiar.tvhome.feature.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.designsystem.GlassMenu
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.designsystem.GlassSurface
import dev.niltsiar.tvhome.core.designsystem.OkHold
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.eatsOkUp
import dev.niltsiar.tvhome.core.model.LibraryArt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

private const val DIM = 0.55f
private val PanelMaxWidth = 800.dp
private val PanelMargin = 32.dp
private val PanelHeight = 300.dp
private val PanelShape = RoundedCornerShape(26.dp)
private val PanelRise = 56.dp
private const val PANEL_START_SCALE = 0.92f
private val RowPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
private val CardGap = 16.dp
private val HighlightGrow = 6.dp
private val HighlightRadius = 20.dp
private const val HIGHLIGHT_STIFFNESS = 260f
private const val HIGHLIGHT_DAMPING = 0.58f

/**
 * The App library over Home. It is composed whenever the library is [LibraryUiState.open]: a Back handler from then on,
 * and the panel once the catalogue has loaded. [glass] is Home's, so the panel glasses what is behind it.
 */
@Composable
public fun LibraryOverlay(
    state: () -> LibraryUiState,
    layout: LibraryLayout,
    actions: LibraryActions,
    glass: GlassState,
    art: suspend (String) -> LibraryArt?,
    modifier: Modifier = Modifier,
) {
    val open by remember { derivedStateOf { state().open } }
    val shown by remember { derivedStateOf { state().shown } }
    val currentActions = rememberUpdatedState(actions)
    BackHandler(enabled = open) { currentActions.value.onBack() }
    if (shown) LibraryScreen(state, layout, actions, glass, art, modifier)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun LibraryScreen(
    state: () -> LibraryUiState,
    layout: LibraryLayout,
    actions: LibraryActions,
    glass: GlassState,
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
                .drawBehind { drawRect(Color.Black, alpha = DIM * reveal.value.coerceIn(0f, 1f)) }
                .onPreviewKeyEvent { hold.eatsOkUp(it) }
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            glass = glass,
            shape = PanelShape,
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
                        .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Header(state, showHint = !empty)
                if (empty) Empty() else Cards(state, layout, actions, art, entrance, hold) { anchor.value = it }
            }
        }
        menu?.let { open ->
            GlassMenu(
                glass = glass,
                rows = open.rows,
                anchor = anchor,
                alignEnd = false,
                label = LibraryRow::label,
                rowTag = { "menu:${it.name}" },
                onPick = actions.onPick,
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
    val muted = remember(typography) { typography.date.copy(fontSize = 14.sp) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.app_library), style = title, maxLines = 1, modifier = Modifier.weight(1f))
            BasicText(pluralStringResource(R.plurals.library_app_count, count, count), style = muted, maxLines = 1)
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
        Highlight(listState, layout, focusedIndex = { state().let { s -> s.cards.indexOfFirst { it.app.key == s.focusedKey } } })
        LazyRow(
            state = listState,
            contentPadding = RowPadding,
            horizontalArrangement = Arrangement.spacedBy(CardGap),
            modifier = Modifier.fillMaxSize().testTag("library:cards"),
        ) {
            val cards = state().cards
            itemsIndexed(cards, key = { _, card -> card.app.key }) { index, card ->
                LibraryCardView(
                    card = card,
                    index = index,
                    layout = layout,
                    art = art,
                    entrance = entrance,
                    actions = actions,
                    hold = hold,
                    anchor = anchor,
                    requester = focus.requester(card.app.key),
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = TvTheme.motion.placementSpec),
                )
            }
        }
    }
}

/**
 * A glass-lit rounded square behind the focused card. It slides to the next card on a spring that overshoots a little
 * and is kept in the row's own coordinates, so scrolling carries it along. Its position is read in the layer lambda.
 */
@Composable
private fun Highlight(
    listState: LazyListState,
    layout: LibraryLayout,
    focusedIndex: () -> Int,
) {
    val density = LocalDensity.current
    val stride = with(density) { (CardWidth + CardGap).toPx() }
    val start = with(density) { RowPadding.calculateLeftPadding(LayoutDirection.Ltr).toPx() }
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
    val width = CardWidth + HighlightGrow * 2
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
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))),
                    cornerRadius = CornerRadius(HighlightRadius.toPx()),
                )
                drawRoundRect(
                    Color.White.copy(alpha = 0.3f),
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
