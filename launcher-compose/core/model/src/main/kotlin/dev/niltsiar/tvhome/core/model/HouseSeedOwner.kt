package dev.niltsiar.tvhome.core.model

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.StateFlow

/**
 * Implemented by the Application, so the key service's windows take the same house seed as the app's. It holds the last
 * seed while nothing collects it.
 */
public interface HouseSeedOwner {
    public val houseSeed: StateFlow<Color>
}
