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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val ButtonSize = 40.dp

/**
 * A round glass button. [icon] fills a 22dp box; it gets the focus progress, a spring from 0 (idle) to 1 (focused) that
 * can overshoot. Read it in graphicsLayer or draw lambdas, never in composition, so a focus change never recomposes.
 * An icon with a motion of its own watches [interactionSource] instead. With a [label] the button is a pill: the label
 * follows the icon and the pill is as wide as both, or as the incoming width limit allows.
 */
@Composable
public fun GlassIconButton(
    glass: GlassState,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    label: (@Composable RowScope.() -> Unit)? = null,
    icon: @Composable BoxScope.(focus: () -> Float) -> Unit,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val progress =
        animateFloatAsState(
            if (focused) 1f else 0f,
            TvTheme.motion.springSpec(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
            label = "iconFocus",
        )
    FocusSurface(
        onClick = onClick,
        modifier = if (label == null) modifier.size(ButtonSize) else modifier.height(ButtonSize).widthIn(min = ButtonSize),
        shape = CircleShape,
        interactionSource = interactionSource,
        glow = false,
    ) {
        GlassSurface(glass = glass, modifier = Modifier.matchParentSize(), shape = CircleShape, interactionSource = interactionSource) {
            if (label == null) {
                Box(Modifier.align(Alignment.Center).size(22.dp).semantics { this.contentDescription = contentDescription }) {
                    icon { progress.value }
                }
            }
        }
        if (label != null) {
            Row(
                modifier =
                    Modifier.align(Alignment.Center).padding(horizontal = 9.dp).semantics {
                        this.contentDescription =
                            contentDescription
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.dp)) { icon { progress.value } }
                Spacer(Modifier.width(8.dp))
                label()
            }
        }
    }
}
