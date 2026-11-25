package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.expressive.HouseStatus
import dev.niltsiar.tvhome.core.designsystem.expressive.houseStatus

private const val CLOCK_DATE_ALPHA = 0.88f
private const val CLOCK_DIVIDER_ALPHA = 0.2f
private val GlassSuccess = Color(0xFF34C759)
private val GlassWarning = Color(0xFFFF9F0A)
private val GlassError = Color(0xFFFF453A)
private val DarkStatus by lazy { houseStatus(dark = true) }
private val LightStatus by lazy { houseStatus(dark = false) }

/**
 * The colours of elements that differ by look, named for the element: Glass's own value, or in [Look.Expressive] a role of
 * the house scheme for the ground the element sits on, dark (over the wallpaper and on a dark surface) or light.
 * [TvTheme.lookColors] gives the set of the ground. Each is read where it draws, so a change of look or seed redraws
 * what reads it and recomposes nothing.
 */
@Stable
public class LookColors internal constructor(
    private val look: LookState,
    private val dark: Boolean,
    private val colors: TvColors,
    private val elements: TvElementColors,
) {
    private val expressive: Boolean get() = look.look == Look.Expressive
    private val scheme: ColorScheme get() = look.scheme(dark)
    private val status: HouseStatus get() = if (dark) DarkStatus else LightStatus

    /** Text and icons. */
    public val text: ColorProducer = ColorProducer { if (expressive) scheme.onSurface else colors.onSurface }

    /** Text that says less: a time's AM or PM, a row's value. */
    public val textMuted: ColorProducer = ColorProducer { if (expressive) scheme.onSurfaceVariant else colors.onSurfaceMuted }

    /** The date in the clock pill. */
    public val clockDate: ColorProducer =
        ColorProducer { if (expressive) scheme.onSurfaceVariant else colors.onSurface.copy(alpha = CLOCK_DATE_ALPHA) }

    /** The line between the clock and the date. */
    public val clockDivider: ColorProducer =
        ColorProducer { if (expressive) scheme.outlineVariant else colors.onSurface.copy(alpha = CLOCK_DIVIDER_ALPHA) }

    /** The greeting beside the clock. */
    public val greeting: ColorProducer = ColorProducer { if (expressive) scheme.onSurface else elements.greeting }

    /** The moon of the sleep timer. */
    public val sleepIcon: ColorProducer = ColorProducer { if (expressive) scheme.tertiary else colors.onSurface }

    /** An icon whose mode is on, as the reorder button's while Move mode runs. */
    public val active: ColorProducer = ColorProducer { if (expressive) scheme.primary else colors.accent }

    /** A state that works: online, a VPN up, a connected input. */
    public val success: ColorProducer = ColorProducer { if (expressive) status.success else GlassSuccess }

    /** A state that half works: connected without internet. */
    public val warning: ColorProducer = ColorProducer { if (expressive) status.warning else GlassWarning }

    /** A state that does not work: offline, a VPN down. */
    public val error: ColorProducer = ColorProducer { if (expressive) scheme.error else GlassError }
}

/** The [LookColors] of each ground of one [TvTheme]. */
internal class LookColorSets(
    look: LookState,
    dark: Pair<TvColors, TvElementColors>,
    light: Pair<TvColors, TvElementColors>,
) {
    val dark: LookColors = LookColors(look, dark = true, colors = dark.first, elements = dark.second)
    val light: LookColors = LookColors(look, dark = false, colors = light.first, elements = light.second)
}
