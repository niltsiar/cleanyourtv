package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.graphics.lerp as lerpColor

private val ButtonSize = 40.dp
private val IconSize = 22.dp
private val IconInset = (ButtonSize - IconSize) / 2
private val LabelGap = 8.dp
private val LabelPadding = 16.dp
private const val FOCUSED_SCALE = 1.08f
private const val BRIGHT_BAR_FROM_LIGHTNESS = 70f
private const val BRIGHT_BAR_FULL_LIGHTNESS = 90f
private const val BRIGHT_BAR_FILL = 0.75f
private val GrownMargin = 1.dp
private const val ACTIVE_FILL = 0.60f
private const val ACTIVE_MS = 120
private val PulseStroke = 2.dp
private const val PULSE_PEAK = 0.9f

/** How long an offered button's rim takes to light and fade, in milliseconds. */
internal const val OFFER_PULSE_MS = 600

/**
 * A round glass button, a chrome surface that fills as a [FocusPill] while focused. [icon] fills a 22dp box; it gets
 * the focus progress, from 0 (idle) to 1 (focused) in [Motion.ICON_MS] from the frame the fill starts, and the colour
 * to draw in. Read both in graphicsLayer or draw lambdas, never in composition. An icon with a motion of its own
 * watches [interactionSource] instead. With a [label] the button is a pill: the label follows the icon and the pill is
 * as wide as both, or as the incoming width limit allows. With a [focusLabel] instead it grows from a circle into such
 * a pill while focused, and lays out on the width it draws at, its focus growth included, plus [GrownMargin] a side.
 * A button with a [label] has no rim. Over a wallpaper brighter than L* 70 under the top bar
 * ([GlassState.topBarLightness]) its tint thickens, up to a total alpha of 0.75 from L* 90. While [active] (a panel it
 * opened is open) it looks as it does focused, with a white fill of 0.60 and a dark glyph, so the panel reads as hanging
 * from it. It grows about [transformOrigin].
 *
 * With a [focusLabel], a nonzero [offerToken] grows the button into its pill without focus and lights its rim once
 * ([OFFER_PULSE_MS]); a new token pulses again, 0 takes the offer back. With animations off or reduced motion the pill
 * appears and goes without motion and the rim stays dark.
 */
@Composable
public fun GlassIconButton(
    glass: GlassState,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    label: (@Composable RowScope.(ink: FocusInk) -> Unit)? = null,
    focusLabel: String? = null,
    offerToken: Int = 0,
    active: Boolean = false,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    icon: @Composable BoxScope.(focus: () -> Float, ink: ColorProducer) -> Unit,
) {
    val motion = TvTheme.motion
    val progress = remember { Animatable(0f) }
    val grow = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val grownScale = motion.focusScale(FOCUSED_SCALE)
    val growSpec = motion.springSpec<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
    val offered by rememberUpdatedState(offerToken != 0)
    val surfaces = TvTheme.surfaces
    val held = remember { Animatable(0f) }
    LaunchedEffect(active, motion) { held.animateTo(if (active) 1f else 0f, motion.tweenSpec(ACTIVE_MS)) }
    val follow =
        modifier.onFocusChanged {
            val target = if (it.isFocused) 1f else 0f
            if (progress.targetValue != target) {
                // Undispatched, as the fill's, so all three start on one frame.
                scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.animateTo(target, motion.tweenSpec(Motion.ICON_MS)) }
                scope.launch(start = CoroutineStart.UNDISPATCHED) { grow.animateTo(if (offered) 1f else target, growSpec) }
            }
        }
    LaunchedEffect(offerToken, motion) {
        val spec = if (motion.animated) growSpec else snap()
        if (offerToken != 0) {
            grow.animateTo(1f, spec)
            if (motion.animated) {
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(OFFER_PULSE_MS, easing = LinearEasing))
            }
        } else {
            pulse.snapTo(0f)
            if (progress.targetValue == 0f) grow.animateTo(0f, spec)
        }
    }
    val size =
        when {
            label != null -> follow.height(ButtonSize).widthIn(min = ButtonSize)
            focusLabel != null -> follow.growToLabel(grow.asState(), grownScale).height(ButtonSize)
            else -> follow.size(ButtonSize)
        }
    SurfaceContent {
        FocusPill(
            onClick = onClick,
            modifier = size,
            interactionSource = interactionSource,
            focusedScale = FOCUSED_SCALE,
            shadowElevation = ChromeShadow.Pill.elevation,
            transformOrigin = transformOrigin,
            background = {
                GlassSurface(
                    glass = glass,
                    modifier = Modifier.matchParentSize(),
                    shape = CircleShape,
                    rim = label == null,
                ) {
                    val tint = TvTheme.surfaces.chromeTint
                    Spacer(
                        Modifier.matchParentSize().drawBehind {
                            val extra = brightBarTint(glass.topBarLightness, tint.alpha)
                            if (extra > 0f) drawRect(tint.copy(alpha = extra))
                        },
                    )
                }
                Spacer(
                    Modifier.matchParentSize().drawBehind {
                        if (held.value > 0f) drawRect(surfaces.pillFill, alpha = ACTIVE_FILL * held.value)
                    },
                )
                if (offerToken != 0) Spacer(Modifier.matchParentSize().drawBehind { drawPulse(pulse.value) })
            },
        ) { focusInk ->
            val ink =
                remember(focusInk, surfaces) {
                    FocusInk(
                        content = { lerpColor(focusInk.content(), surfaces.pillContent, held.value) },
                        secondary = { lerpColor(focusInk.secondary(), surfaces.pillSecondary, held.value) },
                        progress = { maxOf(focusInk.progress(), held.value) },
                    )
                }
            val focus = { progress.value }
            when {
                label != null -> {
                    Row(
                        modifier =
                            Modifier.align(Alignment.Center).padding(horizontal = IconInset).semantics {
                                this.contentDescription = contentDescription
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(IconSize).clearAndSetSemantics {}) { icon(focus, ink.content) }
                        Spacer(Modifier.width(LabelGap))
                        label(ink)
                    }
                }

                focusLabel != null -> {
                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.CenterStart)
                                .wrapContentWidth(Alignment.Start, unbounded = true)
                                .padding(horizontal = IconInset)
                                .graphicsLayer { translationX = (LabelPadding - IconInset).toPx() * grow.value }
                                .clearAndSetSemantics { this.contentDescription = contentDescription },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(IconSize)) { icon(focus, ink.content) }
                        Spacer(Modifier.width(LabelGap))
                        BasicText(focusLabel, style = TvTheme.typography.pillLabel, maxLines = 1, color = ink.content)
                    }
                }

                else -> {
                    val iconBox = Modifier.align(Alignment.Center).size(IconSize)
                    Box(iconBox.clearAndSetSemantics { this.contentDescription = contentDescription }) { icon(focus, ink.content) }
                }
            }
        }
    }
}

/** The rim of an offered button at [progress] 0 to 1 of its pulse: a white edge that rises to [PULSE_PEAK] and falls back. */
private fun DrawScope.drawPulse(progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val edge = PulseStroke.toPx()
    drawRoundRect(
        Color.White,
        topLeft = Offset(edge / 2f, edge / 2f),
        size = Size(size.width - edge, size.height - edge),
        cornerRadius = CornerRadius((size.height - edge) / 2f),
        alpha = PULSE_PEAK * sin(PI.toFloat() * progress),
        style = Stroke(edge),
    )
}

/** The alpha of a second layer of a tint of [base] alpha that brings the pair to [BRIGHT_BAR_FILL] at L* 90, and to nothing at L* 70. */
private fun brightBarTint(
    lightness: Float,
    base: Float,
): Float {
    val t = ((lightness - BRIGHT_BAR_FROM_LIGHTNESS) / (BRIGHT_BAR_FULL_LIGHTNESS - BRIGHT_BAR_FROM_LIGHTNESS)).coerceIn(0f, 1f)
    return t * (BRIGHT_BAR_FILL - base) / (1f - base)
}

/**
 * As wide as a round button at [grow] 0 and as wide as the content with the label's padding at 1. At 1 it also takes
 * the room its [grownScale] focus growth draws into, and [GrownMargin] more, on each side, so its neighbours move
 * aside instead of being drawn over. The width is read while measuring, so the growth relays out the button and its
 * row but recomposes nothing.
 */
private fun Modifier.growToLabel(
    grow: State<Float>,
    grownScale: Float,
): Modifier =
    layout { measurable, constraints ->
        val round = ButtonSize.roundToPx()
        val full = measurable.maxIntrinsicWidth(round) + ((LabelPadding - IconInset) * 2).roundToPx()
        val shown = grow.value
        val width = lerp(round, maxOf(round, full), shown).coerceIn(constraints.minWidth, constraints.maxWidth)
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        val side = ((width * (grownScale - 1f) / 2f + GrownMargin.toPx()) * shown).roundToInt()
        layout(placeable.width + side * 2, placeable.height) { placeable.place(side, 0) }
    }
