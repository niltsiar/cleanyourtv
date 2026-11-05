package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

/**
 * A round glass button, a chrome surface that fills as a [FocusPill] while focused. [icon] fills a 22dp box; it gets
 * the focus progress, from 0 (idle) to 1 (focused) in [Motion.ICON_MS] from the frame the fill starts, and the colour
 * to draw in. Read both in graphicsLayer or draw lambdas, never in composition. An icon with a motion of its own
 * watches [interactionSource] instead. With a [label] the button is a pill: the label follows the icon and the pill is
 * as wide as both, or as the incoming width limit allows. With a [focusLabel] instead it grows from a circle into such
 * a pill while focused, and lays out on the width it draws at, its focus growth included, plus [GrownMargin] a side.
 * A button with a [label] has no rim. Over a wallpaper brighter than L* 70 under the top bar
 * ([GlassState.topBarLightness]) its tint thickens, up to a total alpha of 0.75 from L* 90.
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
    icon: @Composable BoxScope.(focus: () -> Float, ink: ColorProducer) -> Unit,
) {
    val motion = TvTheme.motion
    val progress = remember { Animatable(0f) }
    val grow = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val grownScale = motion.focusScale(FOCUSED_SCALE)
    val follow =
        modifier.onFocusChanged {
            val target = if (it.isFocused) 1f else 0f
            if (progress.targetValue != target) {
                // Undispatched, as the fill's, so all three start on one frame.
                scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.animateTo(target, motion.tweenSpec(Motion.ICON_MS)) }
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    grow.animateTo(target, motion.springSpec(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
                }
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
            },
        ) { ink ->
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
