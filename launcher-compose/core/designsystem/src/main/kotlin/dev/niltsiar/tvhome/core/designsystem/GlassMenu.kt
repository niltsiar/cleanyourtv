package dev.niltsiar.tvhome.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private val MenuGap = 10.dp
private val MenuRise = 14.dp
private val MenuMinWidth = 220.dp
private val RowShape = RoundedCornerShape(14.dp)
private const val ROW_FOCUS_SCALE = 1.04f
private const val MENU_START_SCALE = 0.94f

/**
 * A glass panel of [rows] hanging from [anchor] (in the root's coordinates): below it, or above when it would not fit,
 * from its left edge, or from its right edge when [alignEnd]. Each row shows the string [label] gives and carries the
 * test tag [rowTag]; the panel's own tag is `menu`. It rises and fades in with a spring and takes focus on its first
 * row; focus cannot leave it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun <T> GlassMenu(
    glass: GlassState,
    rows: List<T>,
    anchor: State<Rect>,
    alignEnd: Boolean,
    label: (T) -> Int,
    rowTag: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reveal = remember { Animatable(0f) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.requestFocus()
    }
    LaunchedEffect(Unit) { reveal.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) }
    Layout(
        content = {
            GlassSurface(
                glass = glass,
                shape = RoundedCornerShape(20.dp),
                modifier =
                    Modifier.testTag("menu").graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = reveal.value.coerceIn(0f, 1f)
                        translationY = (1f - reveal.value) * MenuRise.toPx()
                        val scale = MENU_START_SCALE + (1f - MENU_START_SCALE) * reveal.value
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                Column(
                    modifier =
                        Modifier
                            .width(IntrinsicSize.Max)
                            .widthIn(min = MenuMinWidth)
                            .padding(10.dp)
                            .focusProperties { exit = { FocusRequester.Cancel } }
                            .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    rows.forEachIndexed { index, row ->
                        MenuItem(label(row), rowTag(row), { onPick(row) }, if (index == 0) Modifier.focusRequester(first) else Modifier)
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val panel = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val at = anchor.value
            val gap = MenuGap.roundToPx()
            val x =
                (if (alignEnd) at.right.toInt() - panel.width else at.left.toInt()).coerceIn(
                    0,
                    maxOf(
                        0,
                        constraints.maxWidth - panel.width,
                    ),
                )
            val below = at.bottom.toInt() + gap
            val y = if (below + panel.height <= constraints.maxHeight) below else maxOf(0, at.top.toInt() - gap - panel.height)
            panel.place(x, y)
        }
    }
}

@Composable
private fun MenuItem(
    @StringRes label: Int,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag(tag),
        shape = RowShape,
        glow = false,
        focusedScale = ROW_FOCUS_SCALE,
    ) {
        BasicText(
            text = stringResource(label),
            style = TvTheme.typography.pillLabel,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
