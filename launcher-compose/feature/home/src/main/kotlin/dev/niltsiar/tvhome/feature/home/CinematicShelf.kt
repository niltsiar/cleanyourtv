package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import kotlin.math.abs

/** How far a new shelf title rises into place as it fades in. */
internal val TitleRise = 8.dp

/** The one duration and easing of the cinematic shelf's moves from one app to the next: title, meta line, glow, art and dots. */
private const val CINEMATIC_MS = 280
private val CinematicEasing: Easing = FastOutSlowInEasing

/** How long the old title takes to fade out under the new one. */
private const val TITLE_OUT_MS = 120

internal fun <T> cinematicTween(): TweenSpec<T> = tween(CINEMATIC_MS, easing = CinematicEasing)

private val TitleAscent = 50.sp

/** Below the baseline of either title voice, so both stand on one line. */
internal val TitleDescent = 16.sp
internal val MetaIndent = 4.dp
internal val MetaGap = 12.dp
internal val MetaSlot = 20.dp
private val DotsGap = 16.dp
private val DotSize = 6.dp

/** Under the title line: the meta line and the dots. */
internal val ShelfDetailHeight = MetaGap + MetaSlot + DotsGap + DotSize

/** The cinematic title line (its ascent and descent at the default font scale) and [ShelfDetailHeight]. */
internal val CinematicTextHeight = 66.dp + ShelfDetailHeight
private val HeroShadow = Color(0x80000000)
private val DotWide = 20.dp
private val DotSpacing = 6.dp
private val DotRest = Color(0x4DFFFFFF)
private const val SHOW_MS = 260
private const val HIDE_MS = 160

private class HeldShelf {
    var shelf: Shelf? = null
}

/**
 * The cinematic shelf above the dock: the focused dock app's name, when Home last opened it, and one dot per dock app
 * (their slot stays without dots, so the title stands where the band's other titles do).
 * It fades in with the first dock focus and out when [shelf] is null, keeping the last app's text meanwhile. A new app's
 * name and time cross-fade with the old ones ([titleSwap]); the dots stay and only their long dot slides. Every animated
 * value is read in a layer, placement or draw lambda.
 */
@Composable
internal fun CinematicShelf(
    shelf: () -> Shelf?,
    layout: ShelfLayout,
    modifier: Modifier = Modifier,
) {
    val current by remember(shelf) { derivedStateOf(shelf) }
    val held = remember { HeldShelf() }
    current?.let { held.shelf = it }
    val visible = current != null
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(visible, layout.animated) {
        val target = if (visible) 1f else 0f
        if (layout.animated) alpha.animateTo(target, tween(if (visible) SHOW_MS else HIDE_MS)) else alpha.snapTo(target)
    }
    val shown = held.shelf ?: return
    val rise = with(LocalDensity.current) { TitleRise.roundToPx() }
    val titleStyle = TvTheme.typography.cinematicTitle.withHeroShadow()
    val metaStyle = TvTheme.typography.cinematicMeta.withHeroShadow()
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
        Column(
            modifier =
                Modifier.graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    this.alpha = alpha.value
                },
        ) {
            AnimatedContent(
                targetState = shown,
                transitionSpec = { titleSwap(layout.animated, rise) },
                contentKey = { it.key },
                label = "shelfTitle",
            ) { app ->
                Column {
                    BasicText(
                        text = app.label,
                        style = titleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("shelf:name").lineBetween(TitleAscent, TitleDescent),
                    )
                    BasicText(
                        text = lastOpenedLine(app.lastOpened),
                        style = metaStyle,
                        maxLines = 1,
                        modifier =
                            Modifier
                                .padding(start = MetaIndent, top = MetaGap)
                                .testTag("shelf:meta")
                                .height(MetaSlot),
                    )
                }
            }
            if (layout.dots) {
                PositionDots(
                    position = shown.position,
                    count = shown.count,
                    animated = layout.animated,
                    modifier = Modifier.padding(start = MetaIndent, top = DotsGap),
                )
            } else {
                Spacer(Modifier.height(DotsGap + DotSize))
            }
        }
    }
}

/**
 * How a shelf title gives way to the next: the old one fades out in [TITLE_OUT_MS] while the new one fades in from
 * nothing over [millis] and rises [risePx] into place. Both keep their place in the layout, so nothing below them moves.
 */
internal fun titleSwap(
    animated: Boolean,
    risePx: Int,
    millis: Int = CINEMATIC_MS,
): ContentTransform =
    if (animated) {
        ContentTransform(
            fadeIn(tween(millis, easing = CinematicEasing)) + slideInVertically(tween(millis, easing = CinematicEasing)) { risePx },
            fadeOut(tween(TITLE_OUT_MS, easing = CinematicEasing)),
            sizeTransform = null,
        )
    } else {
        ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    }

/** This style with the shadow that keeps the band's text readable on a bright picture. */
@Composable
internal fun TextStyle.withHeroShadow(): TextStyle {
    val density = LocalDensity.current.density
    return remember(this, density) { copy(shadow = Shadow(HeroShadow, Offset(0f, 2 * density), 8 * density)) }
}

/** "Last opened <time>" with the time in white; a space when there is none, which keeps the line's height. */
@Composable
private fun lastOpenedLine(opened: OpenedAt?): AnnotatedString {
    if (opened == null) return AnnotatedString(" ")
    val context = LocalContext.current
    val relative = remember(opened) { describeLastOpened(context, opened) }
    val text = stringResource(R.string.last_opened, relative)
    val at = text.indexOf(relative)
    return remember(text, relative) {
        buildAnnotatedString {
            append(text)
            if (at >= 0) addStyle(SpanStyle(color = Color.White), at, at + relative.length)
        }
    }
}

/** One dot per dock app; the focused one is long and white. The long dot slides to a new focus, taking width from its neighbours. */
@Composable
private fun PositionDots(
    position: Int,
    count: Int,
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    val focus = remember { Animatable(position.toFloat()) }
    LaunchedEffect(position, animated) {
        if (animated) focus.animateTo(position.toFloat(), cinematicTween()) else focus.snapTo(position.toFloat())
    }
    val width = (DotSize + DotSpacing) * (count - 1).coerceAtLeast(0) + DotWide
    Spacer(
        modifier =
            modifier.size(width, DotSize).drawBehind {
                if (count < 2) return@drawBehind
                val dot = DotSize.toPx()
                val wide = DotWide.toPx()
                val spacing = DotSpacing.toPx()
                val radius = CornerRadius(dot / 2f)
                var x = 0f
                for (i in 0 until count) {
                    val near = (1f - abs(i - focus.value)).coerceIn(0f, 1f)
                    val dotWidth = dot + (wide - dot) * near
                    drawRoundRect(lerp(DotRest, Color.White, near), Offset(x, 0f), Size(dotWidth, dot), radius)
                    x += dotWidth + spacing
                }
            },
    )
}

/**
 * Makes a one-line text take [above] over its baseline and [below] under it, whatever the font's own line height:
 * the title's box runs from just above its capitals to just below its descenders, so the lines under it sit close.
 */
internal fun Modifier.lineBetween(
    above: TextUnit,
    below: TextUnit,
): Modifier =
    layout { measurable, constraints ->
        val text = measurable.measure(constraints)
        val top = above.roundToPx()
        layout(text.width, top + below.roundToPx()) { text.place(0, top - text[FirstBaseline]) }
    }
