package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.Look

public val KeyChipHeight: Dp = 32.dp

/** The backing, rim and key fill of a chip over a wallpaper, from [TvTheme.elementColors]. */
public val KeyChipOverWallpaper: KeyChipLook
    @Composable
    @ReadOnlyComposable
    get() =
        TvTheme.elementColors.let {
            KeyChipLook(backing = it.keyChipBacking, rim = it.keyChipRim, keyFill = it.keyChipKeyFill)
        }

/**
 * The look of a chip inside a panel that has a surface of its own: [KeyChipLook.inPanel] in the text colour in Glass; in
 * [Look.Expressive] the key on the house secondary container of the panel's ground and the action in the label style.
 */
public val KeyChipInPanel: KeyChipLook
    @Composable
    @ReadOnlyComposable
    get() =
        if (TvTheme.lookState.look == Look.Expressive) {
            KeyChipLook(
                backing = Color.Transparent,
                rim = Color.Transparent,
                keyFill = Color.Unspecified,
                pill = false,
                dark = TvTheme.surfaces.dark,
            )
        } else {
            KeyChipLook.inPanel(TvTheme.colors.onSurface)
        }

/**
 * What a [KeyChip] draws: the [backing], [rim] and [keyFill] colours, and the [action] text colour when it is not the
 * theme's. Without a [pill] only the key keeps its fill: no backing, no rim, no padding round the chip. An unspecified
 * [backing] or [keyFill] is the house scheme's high container or secondary container, of its [dark] or light scheme, read in
 * the draw phase.
 */
public data class KeyChipLook(
    val backing: Color,
    val rim: Color,
    val keyFill: Color,
    val action: Color = Color.Unspecified,
    val pill: Boolean = true,
    val dark: Boolean = true,
) {
    public companion object {
        /** A chip inside a panel that has a surface of its own: the key on its fill and the action as plain text in [ink]. */
        public fun inPanel(ink: Color): KeyChipLook =
            KeyChipLook(
                backing = Color.Transparent,
                rim = Color.Transparent,
                keyFill = ink.copy(alpha = 0.16f),
                action = ink.copy(alpha = IN_PANEL_ACTION_ALPHA),
                pill = false,
            )
    }
}

private const val IN_PANEL_ACTION_ALPHA = 0.70f

/** A key and what it does, as a pill: [key] on its own fill, then [action]. */
@Composable
public fun KeyChip(
    key: String,
    action: String,
    modifier: Modifier = Modifier,
    look: KeyChipLook = KeyChipOverWallpaper,
) {
    KeyChip(listOf(key), action, modifier, look)
}

/** Keys that do the same thing, as a pill: each of [keys] on its own fill, then [action]. */
@Composable
public fun KeyChip(
    keys: List<String>,
    action: String,
    modifier: Modifier = Modifier,
    look: KeyChipLook = KeyChipOverWallpaper,
) {
    val typography = TvTheme.typography
    val shapes = TvTheme.shapes
    val lookState = TvTheme.lookState
    val actionStyle =
        remember(
            typography,
            look.action,
        ) { if (look.action.isSpecified) typography.chipLabel.copy(color = look.action) else typography.chipLabel }
    val outline =
        if (look.pill) {
            Modifier
                .chipFill(look.backing, shapes.chip, lookState, look.dark) { surfaceContainerHigh }
                .border(1.dp, look.rim, shapes.chip)
                .padding(horizontal = 12.dp)
        } else {
            Modifier
        }
    Row(
        modifier = modifier.height(KeyChipHeight).then(outline),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keys.forEach { key ->
            BasicText(
                key,
                style = typography.chipKey,
                maxLines = 1,
                modifier =
                    Modifier
                        .chipFill(look.keyFill, shapes.chipKey, lookState, look.dark) { secondaryContainer }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        BasicText(action, style = actionStyle, maxLines = 1)
    }
}

/** [colour] behind [shape], or with it unspecified the [house] colour of the look's [dark] or light scheme, read in the draw phase. */
private fun Modifier.chipFill(
    colour: Color,
    shape: Shape,
    look: LookState,
    dark: Boolean,
    house: ColorScheme.() -> Color,
): Modifier =
    if (colour.isSpecified) {
        background(colour, shape)
    } else {
        drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawBehind { drawOutline(outline, look.scheme(dark).house()) }
        }
    }
