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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** The three colours a [KeyChip] draws behind and around its text. */
public data class KeyChipLook(
    val backing: Color,
    val rim: Color,
    val keyFill: Color,
)

/** A key and what it does, as a pill: [key] on its own fill, then [action]. */
@Composable
public fun KeyChip(
    key: String,
    action: String,
    modifier: Modifier = Modifier,
    look: KeyChipLook = KeyChipOverWallpaper,
) {
    val typography = TvTheme.typography
    Row(
        modifier =
            modifier
                .height(KeyChipHeight)
                .background(look.backing, CircleShape)
                .border(1.dp, look.rim, CircleShape)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            key,
            style = typography.chipKey,
            maxLines = 1,
            modifier = Modifier.background(look.keyFill, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        )
        BasicText(action, style = typography.chipLabel, maxLines = 1)
    }
}
