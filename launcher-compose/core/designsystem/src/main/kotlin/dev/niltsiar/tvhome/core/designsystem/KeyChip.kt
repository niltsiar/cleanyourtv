package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

public val KeyChipHeight: Dp = 32.dp

/** The backing, rim and key fill of a chip over a wallpaper. */
public val KeyChipOverWallpaper: KeyChipLook =
    KeyChipLook(
        backing = Color(0xFF07080C).copy(alpha = 0.55f),
        rim = Color.White.copy(alpha = 0.20f),
        keyFill = Color.White.copy(alpha = 0.16f),
    )

/**
 * What a [KeyChip] draws: the [backing], [rim] and [keyFill] colours, and the [action] text colour when it is not the
 * theme's. Without a [pill] only the key keeps its fill: no backing, no rim, no padding round the chip.
 */
public data class KeyChipLook(
    val backing: Color,
    val rim: Color,
    val keyFill: Color,
    val action: Color = Color.Unspecified,
    val pill: Boolean = true,
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
    val actionStyle =
        remember(
            typography,
            look.action,
        ) { if (look.action.isSpecified) typography.chipLabel.copy(color = look.action) else typography.chipLabel }
    val outline =
        if (look.pill) {
            Modifier
                .background(
                    look.backing,
                    CircleShape,
                ).border(1.dp, look.rim, CircleShape)
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
                modifier = Modifier.background(look.keyFill, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        BasicText(action, style = actionStyle, maxLines = 1)
    }
}
