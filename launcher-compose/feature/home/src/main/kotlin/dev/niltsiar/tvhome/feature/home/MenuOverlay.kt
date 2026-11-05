package dev.niltsiar.tvhome.feature.home

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.designsystem.GlassMenu
import dev.niltsiar.tvhome.core.designsystem.GlassState
import dev.niltsiar.tvhome.core.designsystem.GlassSurface
import dev.niltsiar.tvhome.core.designsystem.TvTheme

private val MenuRise = 14.dp

@StringRes
private fun MenuRow.label(): Int =
    when (this) {
        MenuRow.AddToDock -> R.string.tile_add_to_dock
        MenuRow.ReplaceInDock -> R.string.tile_replace_in_dock
        MenuRow.Move -> R.string.tile_move
        MenuRow.RemoveFromHome -> R.string.tile_remove_from_home
        MenuRow.AppInfo -> R.string.app_info
        MenuRow.NativeSettings -> R.string.native_settings
        MenuRow.EditDock -> R.string.dock_edit
        MenuRow.Reorder -> R.string.reorder
        MenuRow.PowerMenu -> R.string.power_menu
    }

/** The tile menu or the Settings menu: [rows] hanging from [anchor], each tagged `menu:<row name>`. */
@Composable
internal fun MenuOverlay(
    glass: GlassState,
    rows: List<MenuRow>,
    anchor: State<Rect>,
    alignEnd: Boolean,
    onPick: (MenuRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassMenu(glass, rows, anchor, alignEnd, label = MenuRow::label, rowTag = { "menu:${it.name}" }, onPick = onPick, modifier = modifier)
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
    val motion = TvTheme.motion
    val reveal = remember { Animatable(if (motion.animations) 0f else 1f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, motion.springSpec(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) }
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
