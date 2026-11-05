package dev.niltsiar.tvhome.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.designsystem.FocusSurface
import dev.niltsiar.tvhome.core.designsystem.OkHold
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.holdOk
import dev.niltsiar.tvhome.core.model.LibraryArt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val CardHeight = 113.dp
private val CardShape = RoundedCornerShape(16.dp)
private val ArtShape = RoundedCornerShape(8.dp)
private const val ART_ASPECT = 16f / 9f
private val CardSide = 6.dp
private val ArtInset = 8.dp
private val CardTop = 8.dp
private val CardBottom = 8.dp
private val NameGap = 4.dp
private val NameHeight = 32.dp
internal const val NAME_LINES = 2
private val EnterRise = 28.dp
private const val POP_SCALE = 0.12f
private const val FOCUSED_SCALE = 1.08f
private val OnHomeTint = Color(0x1475BBA4)
private const val RIM_ALPHA = 0.09f
private val RecentGreen = Color(0xFF70D9A0)

/**
 * One card of the grid: the app's art on a small tile, its name in a box of [NAME_LINES] lines and, right under it, where
 * the app stands. An idle card is opaque, so nothing of Home shows through it; a focused one lets the plate [FocusSurface]
 * draws behind it show. It rises in once per opening, with the delay [CardEntrance] gives it, and pops a little when its
 * Home shortcut changes. Both run in the layer lambda. The status line keeps its place while the card grows, so it stays level with
 * the cards beside it. [widthPx] is the narrowest the card can be.
 */
@Composable
internal fun LibraryCardView(
    card: LibraryCard,
    index: Int,
    widthPx: Int,
    layout: LibraryLayout,
    art: suspend (String) -> LibraryArt?,
    measurer: TextMeasurer,
    entrance: CardEntrance,
    actions: LibraryActions,
    hold: OkHold,
    anchor: (Rect) -> Unit,
    requester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val key = card.app.key
    val delayMs = remember(key) { if (layout.animated) entrance.delayFor(key, index) else null }
    val enter = remember(key) { Animatable(if (delayMs == null) 1f else 0f) }
    LaunchedEffect(key) {
        if (delayMs != null) {
            delay(delayMs.toLong())
            enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
        }
    }
    val pop = remember { Animatable(0f) }
    val wasOnHome = remember { booleanArrayOf(card.onHome) }
    LaunchedEffect(card.onHome) {
        if (wasOnHome[0] != card.onHome) {
            wasOnHome[0] = card.onHome
            if (layout.animated) {
                pop.snapTo(1f)
                pop.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    val artwork by produceState<LibraryArt?>(null, key) { value = art(key) }
    val ink = TvTheme.colors.onSurface
    val fill = TvTheme.surfaces.card
    val tint = animateColorAsState(if (card.onHome) OnHomeTint else Color.Transparent, tween(CARD_TINT_MS), label = "cardTint")
    val motion = TvTheme.motion
    val density = LocalDensity.current
    val focus = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val ring = remember(artwork, layout) { layout.ringColour(artwork?.accent ?: Color.Unspecified) }
    Box(
        modifier =
            modifier.fillMaxWidth().height(CardHeight).graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = enter.value.coerceIn(0f, 1f)
                translationY = (1f - enter.value) * EnterRise.toPx()
                val scale = 1f + POP_SCALE * pop.value
                scaleX = scale
                scaleY = scale
            },
    ) {
        FocusSurface(
            onClick = { actions.onClick(key) },
            onFocused = { actions.onFocus(key) },
            shape = CardShape,
            glow = false,
            focusedScale = FOCUSED_SCALE,
            ring = { ring },
            plate = ink,
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag("card:$key")
                    .focusRequester(requester)
                    .onFocusChanged {
                        val target = if (it.isFocused) 1f else 0f
                        if (focus.targetValue != target) {
                            scope.launch(start = CoroutineStart.UNDISPATCHED) { focus.animateTo(target, motion.focusSpec) }
                        }
                    }.then(
                        if (layout.selection) {
                            Modifier
                        } else {
                            Modifier.holdOk(hold, onHold = { bounds ->
                                anchor(bounds)
                                actions.onHold(key)
                            })
                        },
                    ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(fill, alpha = (1f - focus.value).coerceIn(0f, 1f))
                        drawRect(tint.value)
                    }.border(1.dp, ink.copy(alpha = RIM_ALPHA), CardShape)
                    .semantics { contentDescription = card.app.label },
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(start = CardSide, top = CardTop, end = CardSide),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Art(card, artwork, Modifier.padding(horizontal = ArtInset))
                    Name(card.app.label, measurer, widthPx - with(density) { CardSide.roundToPx() * 2 })
                }
                Status(
                    card,
                    layout,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = CardSide, end = CardSide, bottom = CardBottom)
                        .holdingStill(focus, motion.focusScale(FOCUSED_SCALE)),
                )
                if (card.recent && !card.onHome) RecentDot(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
            }
        }
    }
}

private const val CARD_TINT_MS = 300

@Composable
private fun Art(
    card: LibraryCard,
    artwork: LibraryArt?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(ART_ASPECT)
                .clip(ArtShape)
                .background(TvTheme.colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        val image = artwork?.image
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (artwork != null) {
            BasicText(
                card.app.label
                    .firstCodePoint()
                    .uppercase(),
                style = TvTheme.typography.shelfTitle.copy(fontSize = 28.sp),
            )
        }
    }
}

/** The first character, whole: a letter outside the Basic Multilingual Plane is two chars. */
internal fun String.firstCodePoint(): String = if (isEmpty()) this else take(Character.charCount(codePointAt(0)))

@Composable
private fun Name(
    label: String,
    measurer: TextMeasurer,
    widthPx: Int,
) {
    val typography = TvTheme.typography
    val style = remember(typography) { typography.pillLabel.copy(fontSize = 13.sp, lineHeight = 16.sp, textAlign = TextAlign.Center) }
    val shown = remember(label, style, widthPx) { shownName(label, measurer, style, widthPx) }
    val fit = remember(style) { WholeWords(style.fontSize) }
    BasicText(
        text = shown,
        style = style,
        maxLines = NAME_LINES,
        overflow = TextOverflow.Ellipsis,
        autoSize = if (shown == label) fit else null,
        modifier = Modifier.padding(top = NameGap).fillMaxWidth().height(NameHeight),
    )
}

private val Ellipsis = Char(0x2026).toString()

/**
 * [label] as it shows in [NAME_LINES] lines of [widthPx]: whole, or, where no size down to the smallest [WholeWords] tries
 * shows it whole, cut where the last line ends with the ellipsis right after its last character, where a plain ellipsis
 * can follow a space.
 */
internal fun shownName(
    label: String,
    measurer: TextMeasurer,
    style: TextStyle,
    widthPx: Int,
): String {
    fun measure(size: TextUnit) =
        measurer.measure(
            label,
            style.copy(fontSize = size),
            TextOverflow.Ellipsis,
            maxLines = NAME_LINES,
            constraints = Constraints(maxWidth = widthPx),
        )
    val laidOut = measure(style.fontSize)
    val last = laidOut.lineCount - 1
    if (!laidOut.isLineEllipsized(last)) return label
    if (wholeWordsSize(style.fontSize, label, ::measure) != null) return label
    return label.substring(0, laidOut.getLineEnd(last, visibleEnd = true)).trimEnd() + Ellipsis
}

@Composable
private fun Status(
    card: LibraryCard,
    layout: LibraryLayout,
    modifier: Modifier = Modifier,
) {
    val typography = TvTheme.typography
    val style = remember(typography) { typography.date.copy(fontSize = 11.sp, lineHeight = 13.sp, textAlign = TextAlign.Center) }
    val text =
        when {
            card.onHome -> stringResource(R.string.library_on_home)
            layout.okOpens -> ""
            card.recent -> stringResource(R.string.library_recent)
            else -> stringResource(R.string.library_add_home)
        }
    BasicText(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.fillMaxWidth())
}

/**
 * Undoes, for the line's place, the growth [FocusSurface] draws about the card's centre: [focus] is the card's own
 * copy of that animation, so the line stays where it was while the card grows.
 */
private fun Modifier.holdingStill(
    focus: Animatable<Float, AnimationVector1D>,
    grownScale: Float,
): Modifier =
    graphicsLayer {
        val fromCentre = CardHeight.toPx() / 2f - CardBottom.toPx() - size.height / 2f
        translationY = -fromCentre * (grownScale - 1f) * focus.value
    }

/** A small green dot for a recent install that is not on Home. */
@Composable
private fun RecentDot(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(8.dp).drawBehind { drawCircle(RecentGreen) })
}
