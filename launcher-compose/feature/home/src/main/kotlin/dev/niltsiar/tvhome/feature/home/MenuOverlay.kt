package dev.niltsiar.tvhome.feature.home

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
import dev.niltsiar.tvhome.core.designsystem.FocusSurface
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.designsystem.GlassSurface
import dev.niltsiar.tvhome.core.designsystem.TvTheme

private val MenuGap = 10.dp
private val MenuRise = 14.dp
private val MenuMinWidth = 220.dp
private val RowShape = RoundedCornerShape(14.dp)
private const val ROW_FOCUS_SCALE = 1.04f
private const val MENU_START_SCALE = 0.94f

@StringRes
private fun MenuRow.label(): Int =
    when (this) {
        MenuRow.AddToDock -> R.string.tile_add_to_dock
        MenuRow.ReplaceInDock -> R.string.tile_replace_in_dock
        MenuRow.Move -> R.string.tile_move
        MenuRow.AppInfo -> R.string.app_info
        MenuRow.NativeSettings -> R.string.native_settings
        MenuRow.EditDock -> R.string.dock_edit
        MenuRow.Reorder -> R.string.reorder
    }

/**
 * A glass panel of [rows] hanging from [anchor] (in the root's coordinates): below it, or above when it would not fit,
 * from its left edge, or from its right edge when [alignEnd]. It rises and fades in with a spring and takes focus on
 * its first row; focus cannot leave it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun MenuOverlay(
    glass: GlassState,
    rows: List<MenuRow>,
    anchor: State<Rect>,
    alignEnd: Boolean,
    onPick: (MenuRow) -> Unit,
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
                        MenuItem(row, onPick, if (index == 0) Modifier.focusRequester(first) else Modifier)
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
    row: MenuRow,
    onPick: (MenuRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSurface(
        onClick = { onPick(row) },
        modifier = modifier.fillMaxWidth().testTag("menu:${row.name}"),
        shape = RowShape,
        glow = false,
        focusedScale = ROW_FOCUS_SCALE,
    ) {
        BasicText(
            text = stringResource(row.label()),
            style = TvTheme.typography.pillLabel,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@StringRes
internal fun ReorderHint.label(): Int =
    when (this) {
        ReorderHint.Reorder -> R.string.reorder_hint
        ReorderHint.DockChoosePlace -> R.string.dock_edit_hint
        ReorderHint.DockChooseApp -> R.string.dock_edit_pick_app
        ReorderHint.DockPlaceForApp -> R.string.dock_edit_pick_place
    }

/** The line that says what the keys do in reorder and edit-dock modes. It rises into place. */
@Composable
internal fun ReorderHintPill(
    glass: GlassState,
    hint: ReorderHint,
    modifier: Modifier = Modifier,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) }
    GlassSurface(
        glass = glass,
        shape = RoundedCornerShape(28.dp),
        modifier =
            modifier.testTag("reorder:hint").graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = reveal.value.coerceIn(0f, 1f)
                translationY = (1f - reveal.value) * MenuRise.toPx()
            },
    ) {
        BasicText(
            text = stringResource(hint.label()),
            style = TvTheme.typography.pillLabel,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}
