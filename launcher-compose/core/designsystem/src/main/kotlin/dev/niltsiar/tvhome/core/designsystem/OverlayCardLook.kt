package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.expressive.ExpressiveShapes
import dev.niltsiar.tvhome.core.designsystem.expressive.houseScheme

/** A card that a service window draws as an Android View, outside any [TvTheme]: its text, its fill and its corner. */
public class OverlayCardLook internal constructor(
    public val text: Color,
    public val fill: Color,
    public val corner: Dp,
)

/** The card in [look] with the house [seed]; Expressive takes the dark house scheme, as the card sits over whatever plays. */
public fun overlayCardLook(
    look: Look,
    seed: Color,
): OverlayCardLook =
    when (look) {
        Look.Glass -> {
            OverlayCardLook(text = Color.White, fill = Color(0xE6202124), corner = 16.dp)
        }

        Look.Expressive -> {
            val scheme = houseScheme(seed, dark = true)
            OverlayCardLook(
                text = scheme.onSurface,
                fill = scheme.surfaceContainerHigh.copy(alpha = OVERLAY_CARD_ALPHA),
                corner = ExpressiveShapes.overlayCorner,
            )
        }
    }

/** The Glass card's alpha, 0xE6: the playing picture shows faintly through either look's card. */
private const val OVERLAY_CARD_ALPHA = 0xE6 / 255f
