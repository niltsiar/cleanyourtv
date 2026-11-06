package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

public val KeyChipHeight: Dp = 32.dp

private const val IDLE_CHIP_ALPHA = 0.60f
private const val CHIP_FADE_MS = 140

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

/**
 * A key and what it does, as a pill: [key] on its own fill, then [action]. While [applies] is false the chip draws dimmer,
 * and it fades between the two.
 */
@Composable
public fun KeyChip(
    key: String,
    action: String,
    modifier: Modifier = Modifier,
    applies: Boolean = true,
    look: KeyChipLook = KeyChipOverWallpaper,
) {
    val typography = TvTheme.typography
    val alpha = animateFloatAsState(if (applies) 1f else IDLE_CHIP_ALPHA, TvTheme.motion.tweenSpec(CHIP_FADE_MS), label = "chip")
    Row(
        modifier =
            modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    this.alpha = alpha.value
                }.height(KeyChipHeight)
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
