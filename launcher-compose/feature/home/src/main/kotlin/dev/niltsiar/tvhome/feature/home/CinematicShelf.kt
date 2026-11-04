package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import kotlin.math.abs

internal val CinematicShelfHeight = 144.dp
private val ShelfRise = 14.dp

private val TitleAscent = 50.sp
private val TitleDescent = 16.sp
private val MetaIndent = 4.dp
private val MetaGap = 12.dp
private val DotsGap = 16.dp
private val DotSize = 6.dp
private val DotWide = 20.dp
private val DotSpacing = 6.dp
private val DotRest = Color(0x4DFFFFFF)
private const val SHOW_MS = 260
private const val HIDE_MS = 160
private const val DOTS_MS = 220
private const val MIN_ALPHA = 0.3f

private class HeldShelf {
    var shelf: Shelf? = null
}

/**
 * The cinematic shelf above the dock: the focused dock app's name, when Home last opened it, and one dot per dock app.
 * It fades in with the first dock focus and out when [shelf] is null, keeping the last app's text meanwhile. A new app
 * rises into place; the dots slide. Every animated value is read in a layer or draw lambda.
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
    val reveal = remember(shown.key) { Animatable(0f) }
    LaunchedEffect(reveal, layout.animated) {
        if (layout.animated) reveal.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) else reveal.snapTo(1f)
    }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
        Column(
            modifier =
                Modifier.graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    this.alpha = alpha.value * (MIN_ALPHA + (1f - MIN_ALPHA) * reveal.value).coerceIn(0f, 1f)
                    translationY = (1f - reveal.value) * ShelfRise.toPx()
                },
        ) {
            BasicText(
                text = shown.label,
                style = TvTheme.typography.cinematicTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("shelf:name").lineBetween(TitleAscent, TitleDescent),
            )
            BasicText(
                text = lastOpenedLine(shown.lastOpened),
                style = TvTheme.typography.cinematicMeta,
                maxLines = 1,
                modifier = Modifier.padding(start = MetaIndent, top = MetaGap),
            )
            if (layout.dots) {
                PositionDots(
                    position = shown.position,
                    count = shown.count,
                    modifier = Modifier.padding(start = MetaIndent, top = DotsGap),
                )
            }
        }
    }
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
    modifier: Modifier = Modifier,
) {
    val focus = remember { Animatable(position.toFloat()) }
    LaunchedEffect(position) { focus.animateTo(position.toFloat(), tween(DOTS_MS, easing = FastOutSlowInEasing)) }
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
private fun Modifier.lineBetween(
    above: TextUnit,
    below: TextUnit,
): Modifier =
    layout { measurable, constraints ->
        val text = measurable.measure(constraints)
        val top = above.roundToPx()
        layout(text.width, top + below.roundToPx()) { text.place(0, top - text[FirstBaseline]) }
    }
