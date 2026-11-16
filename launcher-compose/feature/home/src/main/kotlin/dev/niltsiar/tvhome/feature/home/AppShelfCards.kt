package dev.niltsiar.tvhome.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.FocusRingReach
import dev.niltsiar.tvhome.core.designsystem.FocusSurface
import dev.niltsiar.tvhome.core.designsystem.ScrollsClearOfEdge
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.popsOver
import dev.niltsiar.tvhome.core.designsystem.pressDip
import dev.niltsiar.tvhome.core.designsystem.ringBlendsWith
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.NowPlayingArt
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind
import dev.niltsiar.tvhome.core.model.ShelfPoster
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** The tallest a shelf card is, 16:9 at 240 x 135 dp. */
internal val ShelfCardHeight = 135.dp

/** The largest a shelf card is. */
public val ShelfCardMaxSize: DpSize = DpSize(ShelfCardHeight * TILE_ASPECT, ShelfCardHeight)

/** The least a shelf card shrinks to in a layout with less room; a layout without room for it shows no cards. */
internal val ShelfCardMinHeight = 90.dp

/** Between a focused card's ring and the meta line over it or the dock panel under it. */
private val ShelfRingClearance = 4.dp
private val ShelfCardShape = RoundedCornerShape(12.dp)
private val TextCardInset = 12.dp
private const val SHELF_FADE_MS = 200
private const val TEXT_CARD_ART_ALPHA = 0.3f
private const val MINUTE_MS = 60_000.0
private const val SHELF_LINE_SEPARATOR = " · "

/**
 * Where the shelf cards go in a layout: cards [cardHeight] high, [clear] of room over and under them for a focused card's ring,
 * and how far the title block [rise]s while they show.
 */
@Immutable
internal data class ShelfBand(
    val cardHeight: Dp,
    val clear: Dp,
    val rise: Dp,
) {
    val cardWidth: Dp get() = cardHeight * TILE_ASPECT
}

/**
 * The band for a layout whose meta line ends [room] over the dock panel in dock focus, and whose title block may rise [lift]:
 * the group's lowering, so it never stands higher than in the grid layout. The cards are as tall as fits, up to
 * [ShelfCardHeight], with the ring of a card grown [peak] times clear of the meta line and of the panel. Null when that is
 * under [ShelfCardMinHeight].
 */
internal fun shelfBand(
    room: Dp,
    lift: Dp,
    peak: Float,
): ShelfBand? {
    val ring = FocusRingReach * peak + ShelfRingClearance
    val height = minOf(ShelfCardHeight, (room + lift - ring * 2) / peak)
    if (height < ShelfCardMinHeight) return null
    val clear = height * ((peak - 1f) / 2f) + ring
    return ShelfBand(height, clear, maxOf(0.dp, height + clear * 2 - room))
}

/**
 * The cards on screen. [shown] is the shelf they show, kept while they fade out; [focused] the index of the card with focus,
 * -1 when none has it, and [alpha] their fade. Read [alpha] in layer lambdas only.
 */
@Stable
internal class ShelfCardsState {
    var shown by mutableStateOf<AppShelf?>(null)
    var focused by mutableIntStateOf(-1)
    val first = FocusRequester()
    val alpha = Animatable(0f)

    /** The item of the card with focus, null when none has it. */
    fun focusedItem(): ShelfItem? = shown?.items?.getOrNull(focused)
}

/**
 * The app shelf's cards between the title and the dock, in a row that starts under the title and scrolls sideways; their
 * bottom is [band]'s clear over the dock panel, whose top is [shelfBottom] drawn [groupShift] lower. They fade in over
 * [SHELF_FADE_MS] while [visible] and out as fast, at once without motion, and take focus only while [visible]. Left and Right
 * stop at the ends; Down and Back return to the dock tile, and the row scrolls back to its first card, at once without motion;
 * Up leaves for the top bar, as from the dock ([upToOffer]).
 * OK opens a card's item through [onOpen]; with [HomeLayout.pressFeedback] the card dips unless an app is launching.
 * A card shows its poster, cropped to cover it, or whole and centred on the poster's [ShelfPoster.fill] when that is
 * specified; for none that decodes, it shows the app's [AppEntry.image] dimmed under the item's title. While the cards are
 * [visible], a card without a picture asks [poster] again each time [posterChanges] rises.
 */
@Composable
internal fun AppShelfCards(
    cards: ShelfCardsState,
    band: ShelfBand,
    shelf: () -> AppShelf?,
    visible: State<Boolean>,
    app: (String) -> AppEntry?,
    ring: (AppEntry) -> Color,
    shelfBottom: Dp,
    groupShift: () -> Float,
    poster: suspend (String, Int, Int) -> ShelfPoster?,
    posterChanges: Flow<Int>,
    layout: HomeLayout,
    input: HomeInput,
    onOpen: (String, ShelfItem) -> Unit,
) {
    val animated = TvTheme.motion.animated
    val posterGeneration = remember { mutableIntStateOf(0) }
    LaunchedEffect(posterChanges, visible) {
        snapshotFlow { visible.value }.collectLatest { on -> if (on) posterChanges.collect { posterGeneration.intValue = it } }
    }
    LaunchedEffect(cards, animated, input) {
        snapshotFlow { visible.value }.collectLatest { on ->
            // Cards that go while one has focus (a new read found nothing) hand focus back to the dock tile.
            if (!on && cards.focused >= 0) cards.shown?.let { input.focus(it.key) }
            val target = if (on) 1f else 0f
            if (animated) cards.alpha.animateTo(target, tween(SHELF_FADE_MS)) else cards.alpha.snapTo(target)
            if (!on) cards.shown = null
        }
    }
    val current by remember(shelf) { derivedStateOf(shelf) }
    val shown = (if (visible.value) current else cards.shown) ?: return
    SideEffect { cards.shown = shown }
    val tile by remember(app, shown.key) { derivedStateOf { app(shown.key) } }
    val owner = tile ?: return
    BackHandler(enabled = cards.focused >= 0) { input.focus(shown.key) }
    val dock = input.requester(shown.key)
    Box(
        Modifier
            .padding(top = shelfBottom - band.cardHeight - band.clear * 2)
            .fillMaxWidth()
            .height(band.cardHeight + band.clear * 2)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = cards.alpha.value
                translationY = groupShift()
            },
    ) {
        ScrollsClearOfEdge(ScreenMargin) {
            key(shown.key) {
                val row = rememberLazyListState()
                LaunchedEffect(row, animated) {
                    snapshotFlow { cards.focused < 0 }.collectLatest { left ->
                        if (!left) return@collectLatest
                        if (animated) row.animateScrollToItem(0) else row.scrollToItem(0)
                    }
                }
                val colour = ring(owner)
                val keys = remember(shown.items) { cardKeys(shown.items) }
                LazyRow(
                    state = row,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = ScreenMargin, vertical = band.clear),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    itemsIndexed(shown.items, key = { index, _ -> keys[index] }) { index, item ->
                        // The dock tile's Up asks for the first card, so it stays composed while the row is scrolled away.
                        if (index == 0) PinnedWhileShown()
                        ShelfCard(
                            item = item,
                            art = owner.image,
                            ring = colour,
                            accent = owner.accent,
                            band = band,
                            poster = poster,
                            posterGeneration = posterGeneration,
                            pressFeedback = layout.pressFeedback,
                            launching = input.launching,
                            onClick = { onOpen(shown.key, item) },
                            modifier =
                                Modifier
                                    .testTag("shelf:card:$index")
                                    .then(if (index == 0) Modifier.focusRequester(cards.first) else Modifier)
                                    .upToOffer(input, layout)
                                    .focusProperties {
                                        canFocus = visible.value
                                        if (index == 0) left = FocusRequester.Cancel
                                        if (index == shown.items.lastIndex) right = FocusRequester.Cancel
                                        down = dock
                                    }.onFocusChanged {
                                        if (it.isFocused) {
                                            cards.focused = index
                                        } else if (cards.focused == index) {
                                            cards.focused = -1
                                        }
                                    },
                        )
                    }
                }
            }
        }
    }
}

/** Keeps the lazy item it is called in composed while it is scrolled out of view. */
@Composable
private fun PinnedWhileShown() {
    val container = LocalPinnableContainer.current
    DisposableEffect(container) {
        val handle = container?.pin()
        onDispose { handle?.release() }
    }
}

/**
 * A key per card that stays with its item when a new read moves it; items with the same key get their place among them. The
 * now-playing card keeps one key while its track changes, so it keeps focus.
 */
private fun cardKeys(items: List<ShelfItem>): List<String> {
    val seen = HashMap<String, Int>()
    return items.map { item ->
        val key = if (item.kind == ShelfKind.NowPlaying) "now-playing" else "${item.intentUri}|${item.posterUri}|${item.title}"
        "${seen.merge(key, 1, Int::plus)}|$key"
    }
}

@Composable
private fun ShelfCard(
    item: ShelfItem,
    art: ImageBitmap?,
    ring: Color,
    accent: Color,
    band: ShelfBand,
    poster: suspend (String, Int, Int) -> ShelfPoster?,
    posterGeneration: IntState,
    pressFeedback: Boolean,
    launching: State<Boolean>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val width = with(density) { band.cardWidth.roundToPx() }
    val height = with(density) { band.cardHeight.roundToPx() }
    val carried = (item.playing?.art as? NowPlayingArt.Picture)?.poster
    val decoded by produceState<ShelfPoster?>(null, item.posterUri, width, height, posterGeneration) {
        val uri = item.posterUri
        var asked = posterGeneration.intValue
        value = uri?.let { poster(it, width, height) }
        if (uri == null) return@produceState
        snapshotFlow { posterGeneration.intValue }.collect { now ->
            if (value == null && now != asked) {
                asked = now
                value = poster(uri, width, height)
            }
        }
    }
    val motion = TvTheme.motion
    val scope = rememberCoroutineScope()
    val press = if (pressFeedback) remember { Animatable(0f) } else null
    val focus = remember { Animatable(0f) }
    val ringBlends = remember(ring, accent) { ringBlendsWith(ring, accent) }
    FocusSurface(
        onClick = {
            if (press != null && !launching.value) scope.launch { press.pressDip(motion) }
            onClick()
        },
        shape = ShelfCardShape,
        glow = false,
        ring = { ring },
        ringBlends = ringBlends,
        plate = TvTheme.colors.onSurface,
        focus = focus,
        press = press,
        modifier = modifier.popsOver(focus).size(band.cardWidth, band.cardHeight).semantics { contentDescription = item.title },
    ) {
        val surface = TvTheme.colors.surface
        Box(Modifier.fillMaxSize().drawBehind { drawRect(surface) }, contentAlignment = Alignment.BottomStart) {
            val shown = carried ?: decoded
            if (shown != null) {
                val fill = shown.fill
                Image(
                    shown.picture,
                    contentDescription = null,
                    contentScale = if (fill.isSpecified) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(fill),
                )
            } else {
                if (art != null) {
                    Image(
                        art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = TEXT_CARD_ART_ALPHA,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                BasicText(
                    item.title,
                    style = TvTheme.typography.tileLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(TextCardInset).testTag("shelf:card-title"),
                )
            }
        }
    }
}

/**
 * What the line under the title says for a focused shelf card: the item's title, then its season and episode when the app
 * gives them, then the minutes left when it gives a position and a duration. A now-playing card gives the title, the artist
 * when the session names one, and Paused while it is paused.
 */
@Composable
internal fun shelfLine(item: ShelfItem): String {
    item.playing?.let { playing ->
        val paused = if (playing.paused) stringResource(R.string.shelf_paused) else null
        return listOfNotNull(playing.title, playing.artist, paused).joinToString(SHELF_LINE_SEPARATOR)
    }
    val season = item.seasonNumber
    val episode = item.episodeNumber
    val number =
        when {
            season != null && episode != null -> stringResource(R.string.shelf_season_episode, season, episode)
            episode != null -> stringResource(R.string.shelf_episode, episode)
            else -> null
        }
    val left = minutesLeft(item)?.let { pluralStringResource(R.plurals.shelf_minutes_left, it, it) }
    return listOfNotNull(item.title, number, left).joinToString(SHELF_LINE_SEPARATOR)
}

/** The whole minutes left of [item], rounded up; null without a position and a duration, or with none left. */
internal fun minutesLeft(item: ShelfItem): Int? {
    val position = item.positionMs ?: return null
    val duration = item.durationMs ?: return null
    val left = duration - position
    return if (left <= 0) null else ceil(left / MINUTE_MS).toInt()
}
