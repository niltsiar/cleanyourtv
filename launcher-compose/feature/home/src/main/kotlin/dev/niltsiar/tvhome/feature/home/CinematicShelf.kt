package dev.niltsiar.tvhome.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.translate
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
import dev.niltsiar.tvhome.core.model.OpenedAt
import dev.niltsiar.tvhome.core.model.describeLastOpened
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How far a new shelf title rises into place as it fades in. */
internal val TitleRise = 8.dp

/** The one duration and easing of the cinematic shelf's moves from one app to the next: title, meta line, glow, art and dots. */
internal const val CINEMATIC_MS = 280
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
private val TitleShadow = Color.Black.copy(alpha = 0.55f)
private val SubtitleShadow = Color.Black.copy(alpha = 0.75f)
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
 * name and time cross-fade with the old ones ([TitleSlots]); the dots stay and only their long dot slides. The name
 * draws at [titleAlpha], the time and the dots at [detailAlpha]. Every animated value is read in a layer, placement or
 * draw lambda.
 */
@Composable
internal fun CinematicShelf(
    shelf: () -> Shelf?,
    layout: ShelfLayout,
    modifier: Modifier = Modifier,
    titleAlpha: () -> Float = { 1f },
    detailAlpha: () -> Float = { 1f },
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
    val titleStyle = TvTheme.typography.cinematicTitle.withTitleShadow()
    val metaStyle = TvTheme.typography.cinematicMeta.withSubtitleShadow()
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
        Column(
            modifier =
                Modifier.graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    this.alpha = alpha.value
                },
        ) {
            TitleSlots(shown, layout.animated, rise, key = { it.key }) { app, live ->
                Column {
                    BasicText(
                        text = if (live) app.label else "",
                        style = titleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .testTag(if (live) "shelf:name" else "")
                                .lineBetween(TitleAscent, TitleDescent)
                                .graphicsLayer { this.alpha = titleAlpha() },
                    )
                    BasicText(
                        text = if (live) lastOpenedLine(app.lastOpened, app.usual) else AnnotatedString(""),
                        style = metaStyle,
                        maxLines = 1,
                        modifier =
                            Modifier
                                .padding(start = MetaIndent, top = MetaGap)
                                .testTag(if (live) "shelf:meta" else "")
                                .height(MetaSlot)
                                .drawnAt(detailAlpha),
                    )
                }
            }
            if (layout.dots) {
                PositionDots(
                    position = shown.position,
                    count = shown.count,
                    animated = layout.animated,
                    modifier = Modifier.padding(start = MetaIndent, top = DotsGap).drawnAt(detailAlpha),
                )
            } else {
                Spacer(Modifier.height(DotsGap + DotSize))
            }
        }
    }
}

/**
 * Shows [target] in two slots that stay in the tree, so a new value only changes a slot's text. With [animated], a value
 * of another [key] goes to the hidden slot and fades in over [inMillis] (of the old value and the new) while it rises
 * [risePx] into place, and the shown one fades out in [TITLE_OUT_MS]; a value of the same key replaces the shown one.
 * The fade is a layer's alpha and the rise a draw translation, so no node moves and nothing below them moves either.
 * [content] shows one slot's value; `live` is false for a slot that has faded out, which shows nothing and has no tags.
 */
@Composable
internal fun <T : Any> TitleSlots(
    target: T,
    animated: Boolean,
    risePx: Int,
    key: (T) -> Any = { it },
    inMillis: (from: T, to: T) -> Int = { _, _ -> CINEMATIC_MS },
    content: @Composable (value: T, live: Boolean) -> Unit,
) {
    val slots = remember { TitleSlotsState(target) }
    slots.show(target, key)
    val generation = slots.generation
    val cleared = slots.cleared
    SideEffect { slots.start(generation, animated, risePx.toFloat()) }
    LaunchedEffect(slots, generation) {
        if (generation > 0 && animated) slots.run(generation, inMillis(slots.from, slots.values[slots.front]))
    }
    Box {
        for (slot in 0..1) {
            Box(
                Modifier
                    .graphicsLayer { alpha = slots.alpha[slot].floatValue }
                    .drawWithContent { translate(top = slots.rise[slot].floatValue) { this@drawWithContent.drawContent() } },
            ) {
                content(slots.values[slot], slot == slots.front || cleared < generation)
            }
        }
    }
}

/**
 * The two slots of [TitleSlots]. [show] runs in composition and only writes plain fields: [front] is the slot of the
 * newest value and [generation] counts the swaps. [start] puts a new front slot where its fade begins before the frame
 * draws it, and [run] animates the swap, then marks it [cleared].
 */
@Stable
private class TitleSlotsState<T : Any>(
    first: T,
) {
    val values = mutableListOf(first, first)
    var front = 0
    var generation = 0
    var from: T = first
    var cleared by mutableIntStateOf(0)
    val alpha = listOf(mutableFloatStateOf(1f), mutableFloatStateOf(0f))
    val rise = listOf(mutableFloatStateOf(0f), mutableFloatStateOf(0f))
    private var started = 0

    fun show(
        target: T,
        key: (T) -> Any,
    ) {
        if (key(target) != key(values[front])) {
            from = values[front]
            front = 1 - front
            generation++
        }
        values[front] = target
    }

    fun start(
        generation: Int,
        animated: Boolean,
        risePx: Float,
    ) {
        if (started == generation) return
        started = generation
        if (animated) {
            alpha[front].floatValue = 0f
            rise[front].floatValue = risePx
        } else {
            alpha[front].floatValue = 1f
            rise[front].floatValue = 0f
            alpha[1 - front].floatValue = 0f
            cleared = generation
        }
    }

    suspend fun run(
        generation: Int,
        inMillis: Int,
    ) = coroutineScope {
        val incoming = front
        val outgoing = 1 - front
        launch { tweenTo(alpha[incoming], 1f, inMillis) }
        launch { tweenTo(rise[incoming], 0f, inMillis) }
        tweenTo(alpha[outgoing], 0f, TITLE_OUT_MS)
        cleared = generation
    }

    private suspend fun tweenTo(
        value: MutableFloatState,
        target: Float,
        millis: Int,
    ) = animate(value.floatValue, target, animationSpec = tween(millis, easing = CinematicEasing)) { v, _ -> value.floatValue = v }
}

/** Draws this at [alpha] in a layer that multiplies each of its draws, for content that only rests at 0 or 1. */
internal fun Modifier.drawnAt(alpha: () -> Float): Modifier =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        this.alpha = alpha()
    }

/** This style with the shadow that keeps a title readable on a bright picture. */
@Composable
internal fun TextStyle.withTitleShadow(): TextStyle {
    val density = LocalDensity.current.density
    return remember(this, density) { copy(shadow = Shadow(TitleShadow, Offset(0f, density), 9 * density)) }
}

/** This style with the shadow that keeps a subtitle readable on a bright picture. */
@Composable
internal fun TextStyle.withSubtitleShadow(): TextStyle {
    val density = LocalDensity.current.density
    return remember(this, density) { copy(shadow = Shadow(SubtitleShadow, Offset(0f, 2 * density), 12 * density)) }
}

/**
 * "Last opened <time>" with the time in white, then, for the [usual] app of this time of day, "· You usually open it now"
 * (alone when Home never opened it); a space when there is neither, which keeps the line's height.
 */
@Composable
internal fun lastOpenedLine(
    opened: OpenedAt?,
    usual: Boolean = false,
): AnnotatedString {
    if (opened == null && !usual) return AnnotatedString(" ")
    val context = LocalContext.current
    val relative = remember(opened) { opened?.let { describeLastOpened(context, it) } }
    val last = stringResource(R.string.last_opened, relative.orEmpty())
    val habit = stringResource(R.string.habit_usual)
    return remember(last, relative, habit, usual) {
        buildAnnotatedString {
            if (relative != null) {
                append(last)
                val at = last.indexOf(relative)
                if (at >= 0) addStyle(SpanStyle(color = Color.White), at, at + relative.length)
            }
            if (usual) {
                if (relative != null) append(" \u00B7 ")
                append(habit)
            }
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
