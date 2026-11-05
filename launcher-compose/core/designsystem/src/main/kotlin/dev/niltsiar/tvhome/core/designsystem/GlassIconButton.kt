package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

private val ButtonSize = 40.dp
private val IconSize = 22.dp
private val IconInset = (ButtonSize - IconSize) / 2
private val LabelGap = 8.dp
private val LabelPadding = 16.dp
private const val FOCUSED_SCALE = 1.08f

/**
 * A round glass button, a chrome surface that fills as a [FocusPill] while focused. [icon] fills a 22dp box; it gets
 * the focus progress, a spring from 0 (idle) to 1 (focused) that can overshoot, and the colour to draw in. Read both in
 * graphicsLayer or draw lambdas, never in composition. An icon with a motion of its own watches [interactionSource]
 * instead. With a [label] the button is a pill: the label follows the icon and the pill is as wide as both, or as the
 * incoming width limit allows. With a [focusLabel] instead it grows from a circle into such a pill while focused,
 * keeping its left edge, so a parent that holds it at its right edge sees it grow leftward.
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
    val focused by interactionSource.collectIsFocusedAsState()
    val motion = TvTheme.motion
    val progress =
        animateFloatAsState(
            if (focused) 1f else 0f,
            motion.springSpec(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
            label = "iconFocus",
        )
    val grow =
        animateFloatAsState(
            if (focused) 1f else 0f,
            motion.springSpec(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
            label = "labelGrow",
        )
    val size =
        when {
            label != null -> modifier.height(ButtonSize).widthIn(min = ButtonSize)
            focusLabel != null -> modifier.growToLabel(grow).height(ButtonSize)
            else -> modifier.size(ButtonSize)
        }
    SurfaceContent(light = glass.kind == GlassKind.Flat) {
        FocusPill(
            onClick = onClick,
            modifier = size,
            interactionSource = interactionSource,
            focusedScale = FOCUSED_SCALE,
            shadowElevation = if (glass.kind == GlassKind.Flat) ChromeShadow.Pill.elevation else 0.dp,
            background = {
                GlassSurface(
                    glass = glass,
                    modifier = Modifier.matchParentSize(),
                    shape = CircleShape,
                    interactionSource = interactionSource,
                ) {}
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

/**
 * As wide as a round button at [grow] 0 and as wide as the content with the label's padding at 1. The width is read
 * while measuring, so the growth relays out the button and its row but recomposes nothing.
 */
private fun Modifier.growToLabel(grow: State<Float>): Modifier =
    layout { measurable, constraints ->
        val round = ButtonSize.roundToPx()
        val full = measurable.maxIntrinsicWidth(round) + ((LabelPadding - IconInset) * 2).roundToPx()
        val width = lerp(round, maxOf(round, full), grow.value).coerceIn(constraints.minWidth, constraints.maxWidth)
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
