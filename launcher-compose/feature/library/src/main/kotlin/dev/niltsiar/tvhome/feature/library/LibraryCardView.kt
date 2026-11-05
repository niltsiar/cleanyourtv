package dev.niltsiar.tvhome.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.designsystem.FocusSurface
import dev.niltsiar.tvhome.core.designsystem.OkHold
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.holdOk
import dev.niltsiar.tvhome.core.model.LibraryArt
import kotlinx.coroutines.delay

internal val CardHeight = 170.dp
private val CardShape = RoundedCornerShape(18.dp)
private val ArtShape = RoundedCornerShape(10.dp)
private const val ART_ASPECT = 16f / 9f
private val CardSidePadding = 10.dp
private val CardTopBottomPadding = 12.dp
private val EnterRise = 28.dp
private const val POP_SCALE = 0.12f
private const val FOCUSED_SCALE = 1.08f
private val OnHomeTint = Color(0x1475BBA4)
private const val IDLE_TINT_ALPHA = 0.05f
private const val RIM_ALPHA = 0.09f
private val OnHomeGreen = Color(0xFF246E58)
private val RecentGreen = Color(0xFF70D9A0)

/**
 * One card of the row, [width] wide: the app's art on a small tile, its name in a box of two lines and, on one line at
 * the bottom, where it stands. It rises in once per opening, with the delay [CardEntrance] gives it, and pops a little
 * when its Home shortcut changes. Both run in the layer lambda.
 */
@Composable
internal fun LibraryCardView(
    card: LibraryCard,
    index: Int,
    width: Dp,
    layout: LibraryLayout,
    art: suspend (String) -> LibraryArt?,
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
    val tint =
        animateColorAsState(if (card.onHome) OnHomeTint else ink.copy(alpha = IDLE_TINT_ALPHA), tween(CARD_TINT_MS), label = "cardTint")
    Box(
        modifier =
            modifier.size(width, CardHeight).graphicsLayer {
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag("card:$key")
                    .focusRequester(requester)
                    .then(
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
                    .drawBehind { drawRect(tint.value) }
                    .border(1.dp, ink.copy(alpha = RIM_ALPHA), CardShape)
                    .semantics { contentDescription = card.app.label },
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(start = CardSidePadding, top = CardTopBottomPadding, end = CardSidePadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Art(card, artwork)
                    Name(card.app.label)
                }
                Status(
                    card,
                    layout,
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        ).padding(start = CardSidePadding, end = CardSidePadding, bottom = CardTopBottomPadding),
                )
                Badge(card, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
            }
        }
    }
}

private const val CARD_TINT_MS = 300

@Composable
private fun Art(
    card: LibraryCard,
    artwork: LibraryArt?,
) {
    Box(
        modifier =
            Modifier
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
private fun Name(label: String) {
    val typography = TvTheme.typography
    val style = remember(typography) { typography.pillLabel.copy(fontSize = 14.sp, textAlign = TextAlign.Center) }
    BasicText(
        text = label,
        style = style,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
    )
}

@Composable
private fun Status(
    card: LibraryCard,
    layout: LibraryLayout,
    modifier: Modifier = Modifier,
) {
    val typography = TvTheme.typography
    val style = remember(typography) { typography.date.copy(fontSize = 11.sp, textAlign = TextAlign.Center) }
    val text =
        when {
            card.onHome -> stringResource(R.string.library_on_home)
            layout.okOpens -> ""
            card.recent -> stringResource(R.string.library_recent)
            else -> stringResource(R.string.library_add_home)
        }
    BasicText(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.fillMaxWidth())
}

/** A check in a green disc for an app on Home, a small green dot for a recent install that is not. */
@Composable
private fun Badge(
    card: LibraryCard,
    modifier: Modifier = Modifier,
) {
    if (!card.onHome && !card.recent) return
    val onHome = card.onHome
    Box(
        modifier =
            modifier.size(if (onHome) 18.dp else 8.dp).drawBehind {
                if (onHome) {
                    drawCircle(OnHomeGreen)
                    val unit = size.width / BADGE_VIEWPORT
                    val check =
                        Path().apply {
                            moveTo(6.5f * unit, 12f * unit)
                            lineTo(10.2f * unit, 15.7f * unit)
                            lineTo(17.5f * unit, 8.5f * unit)
                        }
                    drawPath(check, Color.White, style = Stroke(width = 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else {
                    drawCircle(RecentGreen)
                }
            },
    )
}

private const val BADGE_VIEWPORT = 24f
