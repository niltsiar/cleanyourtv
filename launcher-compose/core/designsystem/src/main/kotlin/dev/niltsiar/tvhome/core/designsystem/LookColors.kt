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
private val GlassTileName = Color.White.copy(alpha = 0.85f)
private val GlassTileNamePill = Color.Black.copy(alpha = 0.55f)
private val GlassLightTileEdge = Color.Black.copy(alpha = 0.75f)
private val GlassGlassTileEdge = Color.Black.copy(alpha = 0.18f)
private val GlassTileShade = Color.Black.copy(alpha = 0.8f)
private val GlassHabitMark = Color(0xFFF2F4F8)
private val HabitMarkEdge = Color.Black.copy(alpha = 0.35f)
private val GlassCueChevron = Color.White.copy(alpha = 0.95f)
private val GlassCuePill = Color.Black.copy(alpha = 0.60f)
private val GlassIdle = Color(0xFF8E8E93)
private val GlassSubtitlePill = Color.Black.copy(alpha = 0.45f)
private val GlassSubtitlePillEdge = Color.White.copy(alpha = 0.12f)
private val GlassPositionRest = Color(0x80FFFFFF)
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

    /**
     * The focus band of a tile whose Glass ring is [ring]: the ring itself in Glass. In Expressive it is the app's colour, the
     * house primary for a white ring (no app colour, or a grid tile without app_glow), or with [neutral]
     * (cinematic_white_focus) the house's light neutral. Read it in draw.
     */
    public fun focusBand(
        ring: Color,
        neutral: Boolean,
    ): Color =
        when {
            !expressive -> ring
            neutral -> look.scheme(dark = true).inverseSurface
            ring == Color.White -> look.scheme(dark = true).primary
            else -> ring
        }

    /** The line under a hero title: when the app was last opened, a status, a hint. */
    public val heroMeta: ColorProducer = ColorProducer { if (expressive) scheme.onSurfaceVariant else colors.onSurface }

    /** The pill under that line over a bright picture, and its edge; Expressive has no edge. */
    public val subtitlePill: ColorProducer = ColorProducer { if (expressive) scheme.surfaceContainer else GlassSubtitlePill }
    public val subtitlePillEdge: ColorProducer = ColorProducer { if (expressive) Color.Transparent else GlassSubtitlePillEdge }

    /** The marks of the dock places the focus is not on, under the hero title. */
    public val positionRest: ColorProducer = ColorProducer { if (expressive) scheme.outline else GlassPositionRest }

    /** The fill of a tile with no art, under its name. */
    public val tileFill: ColorProducer = ColorProducer { if (expressive) scheme.surfaceContainerHigh else colors.surface }

    /** The name on a tile with no art. */
    public val tileLabel: ColorProducer = ColorProducer { if (expressive) scheme.onSurface else elements.tileLabel }

    /** An app's name in the pill under its tile, and the pill. */
    public val tileName: ColorProducer = ColorProducer { if (expressive) scheme.onSurface else GlassTileName }
    public val tileNamePill: ColorProducer = ColorProducer { if (expressive) scheme.surfaceContainerHighest else GlassTileNamePill }

    /** The name over an icon tile's art, and the shade under it, at its bottom. */
    public val tileCaption: ColorProducer = ColorProducer { if (expressive) scheme.onSurface else elements.tileCaption }
    public val tileShade: ColorProducer =
        ColorProducer { if (expressive) scheme.surfaceContainerLowest.copy(alpha = GlassTileShade.alpha) else GlassTileShade }

    /** The edge inside a tile of light art, so it holds on any ground, and inside a tile on the glass dock. */
    public val lightTileEdge: ColorProducer = ColorProducer { if (expressive) scheme.outline else GlassLightTileEdge }
    public val glassTileEdge: ColorProducer = ColorProducer { if (expressive) Color.Transparent else GlassGlassTileEdge }

    /** The mark under the app the household usually opens now, and its edge. */
    public val habitMark: ColorProducer = ColorProducer { if (expressive) scheme.tertiary else GlassHabitMark }
    public val habitMarkEdge: ColorProducer = ColorProducer { HabitMarkEdge }

    /** A cue that more lies beyond: its chevron, and the pill or disc under it. */
    public val cueChevron: ColorProducer = ColorProducer { if (expressive) scheme.onSecondaryContainer else GlassCueChevron }
    public val cuePill: ColorProducer = ColorProducer { if (expressive) scheme.secondaryContainer else GlassCuePill }

    /** A medallion behind a glyph, and the glyph on it; Glass has no medallion. */
    public val medallion: ColorProducer = ColorProducer { if (expressive) scheme.secondaryContainer else Color.Transparent }
    public val medallionGlyph: ColorProducer = ColorProducer { if (expressive) scheme.onSecondaryContainer else elements.tileLabel }

    /** A state with nothing to say: a port asleep or without a device. */
    public val idle: ColorProducer = ColorProducer { if (expressive) scheme.outline else GlassIdle }

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
