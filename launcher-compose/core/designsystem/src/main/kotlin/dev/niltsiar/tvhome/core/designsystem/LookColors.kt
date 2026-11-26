package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.expressive.AppBands
import dev.niltsiar.tvhome.core.designsystem.expressive.HouseStatus
import dev.niltsiar.tvhome.core.designsystem.expressive.houseStatus
import androidx.compose.ui.util.lerp as lerpFloat

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
private val GlassCarriedRing = Color(0xFFFF6B4A)
private val GlassConnectedFillOnDark = Color(0x2934C759)
private val GlassConnectedRimOnDark = Color(0x6634C759)
private val GlassConnectedTextOnDark = Color(0xFF6EE787)
private val GlassConnectedFillOnLight = Color(0x2E34C759)
private val GlassConnectedRimOnLight = Color(0x8034C759)
private val GlassConnectedTextOnLight = Color(0xFF1B7A35)
private val GlassConnectedSolid = Color(0xFF146C2E)
private val GlassSuccess = Color(0xFF34C759)
private val GlassWarning = Color(0xFFFF9F0A)
private val GlassError = Color(0xFFFF453A)
private const val CARD_RIM_ALPHA = 0.09f
private val GlassRecentMark = Color(0xFF70D9A0)
private const val ON_HOME_CHECK_REST_ALPHA = 0.85f
private const val ON_HOME_CHECK_FOCUSED_ALPHA = 0.90f
private val GlassOnHomeCheckInk = Color(0xFF07080C)
private val GlassOffHomeRing = Color.White.copy(alpha = 0.40f)
private val GlassOffHomeRingFill = Color.White.copy(alpha = 0.10f)
private const val NEVER_OPENED_ALPHA = 0.45f
private const val CHIP_FILL_ALPHA = 0.10f
private const val CHIP_RIM_ALPHA = 0.24f
private const val CHIP_KEY_FILL_ALPHA = 0.16f
private const val CHIP_KEY_FOCUSED_ON_LIGHT_ALPHA = 0.47f
private const val CHIP_KEY_FOCUSED_ON_DARK_ALPHA = 0.39f

/** The colours the Glass screensaver glow turns through, deep and low in brightness: it is drawn at partial alpha on black. */
public val ScreensaverGlows: List<Color> =
    listOf(Color(0xFF2B5CFF), Color(0xFF00A39A), Color(0xFF8A3FFC), Color(0xFFE0661B), Color(0xFFD6336C))
private const val SCREENSAVER_SHAPE_TONES = 3
private const val SCREENSAVER_SHAPE_DEPTH = 0.5f
private const val LABEL_IDLE_ALPHA = 0.78f
private const val HEADING_ALPHA = 0.65f
private const val DIVIDER_ALPHA = 0.12f
private const val SELECTED_FILL_ALPHA = 0.16f
private const val SELECTED_RIM_ALPHA = 0.42f
private const val ROW_FOCUS_ALPHA = 0.10f
private const val ART_SLOT_ALPHA = 0.08f
private val GlassSwatchOutline = Color.White.copy(alpha = 0.20f)
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
    private val surfaces: TvSurfaces,
) {
    private val expressive: Boolean get() = look.look == Look.Expressive
    private val scheme: ColorScheme get() = look.scheme(dark)
    private val status: HouseStatus get() = if (dark) DarkStatus else LightStatus
    private val light: Float = if (dark) 0f else 1f

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

    /** The moon of the sleep timer, a mode that is on: in Expressive the house primary, as [active]. */
    public val sleepIcon: ColorProducer = ColorProducer { if (expressive) scheme.primary else colors.onSurface }

    /** An icon whose mode is on, as the reorder button's while Move mode runs. */
    public val active: ColorProducer = ColorProducer { if (expressive) scheme.primary else colors.accent }

    /**
     * The focus band of a tile whose Glass ring is [ring]: the ring itself in Glass. In Expressive it is the primary tone of
     * the ring's own palette, so it keeps the house primary's contrast whatever the art; the house primary for a white ring
     * (no app colour, or a grid tile without app_glow); or with [neutral] (cinematic_white_focus) the house's light
     * neutral. Read it in draw.
     */
    public fun focusBand(
        ring: Color,
        neutral: Boolean,
    ): Color =
        when {
            !expressive -> ring
            neutral -> look.scheme(dark = true).inverseSurface
            ring == Color.White -> look.scheme(dark = true).primary
            else -> AppBands[ring].band
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

    /**
     * The edge inside a tile of light art, so it holds on any light ground, and inside a tile on the glass dock. Expressive's
     * is the house's darkest ink on either ground: on a dark ground the light art holds by itself.
     */
    public val lightTileEdge: ColorProducer = ColorProducer { if (expressive) look.scheme(dark = false).onSurface else GlassLightTileEdge }
    public val glassTileEdge: ColorProducer = ColorProducer { if (expressive) Color.Transparent else GlassGlassTileEdge }

    /**
     * The mark under the app the household usually opens now, and its edge. Expressive's is the house secondary, a hint
     * quieter than the focus.
     */
    public val habitMark: ColorProducer = ColorProducer { if (expressive) scheme.secondary else GlassHabitMark }
    public val habitMarkEdge: ColorProducer = ColorProducer { HabitMarkEdge }

    /** A cue that more lies beyond: its chevron, and the pill or disc under it. */
    public val cueChevron: ColorProducer = ColorProducer { if (expressive) scheme.onSecondaryContainer else GlassCueChevron }
    public val cuePill: ColorProducer = ColorProducer { if (expressive) scheme.secondaryContainer else GlassCuePill }

    /** The ring of a tile carried in reorder mode whose Glass ring is [ring]: Glass's own, in Expressive the tile's [focusBand]. */
    public fun carriedRing(
        ring: Color,
        neutral: Boolean,
    ): Color = if (expressive) focusBand(ring, neutral) else GlassCarriedRing

    /** The plate a carried tile lifts off: the container of the name pills on the dock; Glass lifts on a shadow instead. */
    public val liftPlate: ColorProducer = ColorProducer { if (expressive) scheme.surfaceContainerHighest else Color.Transparent }

    /** A medallion behind a glyph, and the glyph on it; Glass has no medallion. */
    public val medallion: ColorProducer = ColorProducer { if (expressive) scheme.secondaryContainer else Color.Transparent }
    public val medallionGlyph: ColorProducer = ColorProducer { if (expressive) scheme.onSecondaryContainer else elements.tileLabel }

    /** A state with nothing to say: a port asleep or without a device. */
    public val idle: ColorProducer = ColorProducer { if (expressive) scheme.outline else GlassIdle }

    /**
     * The "Connected" capsule of an input row whose ink is [focus] of the way to the focused row's: its fill, rim and word.
     * Glass's is a faint green on the idle row and a solid green pill with white text on the focused one; Expressive's is
     * one success container that stays as it is.
     */
    public fun connectedFill(focus: Float): Color =
        if (expressive) {
            status.successContainer
        } else {
            lerp(
                lerp(GlassConnectedFillOnDark, GlassConnectedFillOnLight, light),
                GlassConnectedSolid,
                focus,
            )
        }

    public fun connectedRim(focus: Float): Color =
        if (expressive) {
            Color.Transparent
        } else {
            lerp(
                lerp(GlassConnectedRimOnDark, GlassConnectedRimOnLight, light),
                GlassConnectedSolid,
                focus,
            )
        }

    public fun connectedText(focus: Float): Color =
        if (expressive) {
            status.onSuccessContainer
        } else {
            lerp(
                lerp(GlassConnectedTextOnDark, GlassConnectedTextOnLight, light),
                Color.White,
                focus,
            )
        }

    /** A state that works: online, a VPN up, a connected input. */
    public val success: ColorProducer = ColorProducer { if (expressive) status.success else GlassSuccess }

    /** A state that half works: connected without internet. */
    public val warning: ColorProducer = ColorProducer { if (expressive) status.warning else GlassWarning }

    /** A state that does not work: offline, a VPN down. */
    public val error: ColorProducer = ColorProducer { if (expressive) scheme.error else GlassError }

    /** The fill of a focused library card in a focus style with no plate. */
    public val cardFill: ColorProducer = ColorProducer { if (expressive) scheme.surfaceContainerHighest else surfaces.card }

    /** The rim round a focused library card; Expressive has none, its band is the card's one edge. */
    public val cardRim: ColorProducer =
        ColorProducer { if (expressive) Color.Transparent else colors.onSurface.copy(alpha = CARD_RIM_ALPHA) }

    /** The dot of an app installed lately and not on Home. */
    public val recentMark: ColorProducer = ColorProducer { if (expressive) status.success else GlassRecentMark }

    /** The mark of an app on Home, its card [focus] of the way to focused, and the check on it. */
    public fun onHomeMark(focus: Float): Color =
        if (expressive) {
            scheme.primary
        } else {
            Color.White.copy(alpha = lerpFloat(ON_HOME_CHECK_REST_ALPHA, ON_HOME_CHECK_FOCUSED_ALPHA, focus))
        }

    public val onHomeCheck: ColorProducer = ColorProducer { if (expressive) scheme.onPrimary else GlassOnHomeCheckInk }

    /** The ring where an app not on Home would have its mark, in the chooser, and its fill. */
    public val offHomeRing: ColorProducer = ColorProducer { if (expressive) scheme.onSurfaceVariant else GlassOffHomeRing }
    public val offHomeRingFill: ColorProducer = ColorProducer { if (expressive) Color.Transparent else GlassOffHomeRingFill }

    /** The line of an app Home never opened. */
    public val neverOpened: ColorProducer =
        ColorProducer {
            if (expressive) scheme.outline else colors.onSurfaceMuted.copy(alpha = colors.onSurfaceMuted.alpha * NEVER_OPENED_ALPHA)
        }

    /** The fill and rim of a chip on a panel, as the library's order chip, at rest. */
    public val chipFill: ColorProducer =
        ColorProducer { if (expressive) Color.Transparent else colors.onSurface.copy(alpha = CHIP_FILL_ALPHA) }
    public val chipRim: ColorProducer =
        ColorProducer { if (expressive) scheme.outlineVariant else colors.onSurface.copy(alpha = CHIP_RIM_ALPHA) }

    /**
     * The cap of a key inside a chip, under a key name in [ink], the chip [focus] of the way to focused. In Expressive the
     * focused cap is the ink itself and the key name on it is [chipKeyName], so both hold against the pill's fill.
     */
    public fun chipKey(
        ink: Color,
        focus: Float,
    ): Color {
        if (expressive) return lerp(ink.copy(alpha = CHIP_KEY_FILL_ALPHA), ink, focus)
        val focused = if (surfaces.pillFill.luminance() > 0.5f) CHIP_KEY_FOCUSED_ON_LIGHT_ALPHA else CHIP_KEY_FOCUSED_ON_DARK_ALPHA
        return ink.copy(alpha = lerpFloat(CHIP_KEY_FILL_ALPHA, focused, focus))
    }

    /** The key name on [chipKey]: [ink], or in Expressive the ink turning to the pill's fill as the chip fills. */
    public fun chipKeyName(
        ink: Color,
        focus: Float,
    ): Color = if (expressive) lerp(ink, look.pillFill(surfaces), focus) else ink

    /** A label that has no focus, as a settings row's while none of its options has focus. */
    public val labelIdle: ColorProducer =
        ColorProducer { if (expressive) scheme.onSurfaceVariant else colors.onSurface.copy(alpha = LABEL_IDLE_ALPHA) }

    /** The heading over a group of rows, and the line between two groups. */
    public val heading: ColorProducer =
        ColorProducer { if (expressive) scheme.onSurfaceVariant else colors.onSurface.copy(alpha = HEADING_ALPHA) }
    public val divider: ColorProducer =
        ColorProducer { if (expressive) scheme.outlineVariant else colors.onSurface.copy(alpha = DIVIDER_ALPHA) }

    /** The fill and rim of the selected option of a row. */
    public val selectedFill: ColorProducer =
        ColorProducer { if (expressive) scheme.secondaryContainer else colors.onSurface.copy(alpha = SELECTED_FILL_ALPHA) }
    public val selectedRim: ColorProducer =
        ColorProducer { if (expressive) Color.Transparent else colors.onSurface.copy(alpha = SELECTED_RIM_ALPHA) }

    /** The faint pill behind a row while one of its options has focus; Expressive leaves the focused option the one fill. */
    public val rowFocus: ColorProducer =
        ColorProducer { if (expressive) Color.Transparent else colors.onSurface.copy(alpha = ROW_FOCUS_ALPHA) }

    /** A notice on a panel, a [warning] (a setting not saved) or not: its fill, rim and text. */
    public fun noticeFill(warning: Boolean): Color =
        when {
            !expressive -> (if (warning) surfaces.warnInk else colors.onSurface).copy(alpha = SELECTED_FILL_ALPHA)
            warning -> scheme.errorContainer
            else -> scheme.surfaceContainerHighest
        }

    public fun noticeRim(warning: Boolean): Color =
        if (expressive) Color.Transparent else (if (warning) surfaces.warnInk else colors.onSurface).copy(alpha = SELECTED_RIM_ALPHA)

    public fun noticeText(warning: Boolean): Color =
        when {
            !expressive -> if (warning) surfaces.warnInk else colors.onSurface
            warning -> scheme.onErrorContainer
            else -> scheme.onSurface
        }

    /** The slot of a picker entry's art while it loads, and the line round a colour swatch in that slot. */
    public val pickerArtSlot: ColorProducer =
        ColorProducer { if (expressive) scheme.surfaceContainerHighest else colors.onSurface.copy(alpha = ART_SLOT_ALPHA) }
    public val swatchOutline: ColorProducer = ColorProducer { if (expressive) scheme.outlineVariant else GlassSwatchOutline }

    /**
     * The footer of an App highlights card that knows how far it was watched, in Expressive: the strip in the card's fill,
     * the progress wave in its ink and the track after it. Glass draws no footer.
     */
    public val cardFooter: ColorProducer = ColorProducer { if (expressive) scheme.surfaceContainerHigh else Color.Transparent }
    public val cardProgress: ColorProducer = ColorProducer { if (expressive) scheme.onSurface else Color.Transparent }
    public val cardProgressTrack: ColorProducer = ColorProducer { if (expressive) scheme.outlineVariant else Color.Transparent }

    /** The screensaver's ground: black in both looks, as an OLED needs. */
    public val screensaverGround: ColorProducer = ColorProducer { Color.Black }

    /** The screensaver's glow, which is [glass] in Glass, one of [ScreensaverGlows]; in Expressive the house's container colour. */
    public fun screensaverGlow(glass: Color): Color = if (expressive) look.scheme(dark = true).primaryContainer else glass

    /**
     * The fill of the Expressive screensaver's shape on its [turn]: the dark house scheme's primary, secondary and tertiary
     * containers in turn, taken halfway to black and opaque, so a large shape stays deep on the black ground.
     */
    public fun screensaverShape(turn: Int): Color {
        val scheme = look.scheme(dark = true)
        val container =
            when (turn.mod(SCREENSAVER_SHAPE_TONES)) {
                0 -> scheme.primaryContainer
                1 -> scheme.secondaryContainer
                else -> scheme.tertiaryContainer
            }
        return lerp(Color.Black, container, SCREENSAVER_SHAPE_DEPTH)
    }

    /** The screensaver's time, its AM or PM and its date, on its black ground: Expressive writes them in the dark house ink. */
    public val screensaverTime: ColorProducer =
        ColorProducer { if (expressive) look.scheme(dark = true).onSurface else elements.screensaverClock }
    public val screensaverAmPm: ColorProducer =
        ColorProducer { if (expressive) look.scheme(dark = true).onSurfaceVariant else elements.screensaverAmPm }
    public val screensaverDate: ColorProducer =
        ColorProducer { if (expressive) look.scheme(dark = true).onSurfaceVariant else elements.screensaverDate }

    /** A cue on a panel that more lies beyond: its chevron, and the pill under it. */
    public val panelCue: ColorProducer =
        ColorProducer { if (expressive) scheme.onSecondaryContainer else colors.onSurface.copy(alpha = SCROLL_CUE_ALPHA) }
    public val panelCuePill: ColorProducer =
        ColorProducer { if (expressive) scheme.secondaryContainer else colors.onSurface.copy(alpha = SCROLL_CUE_PILL_ALPHA) }
}

/** The [LookColors] of each ground of one [TvTheme]. */
internal class LookColorSets(
    look: LookState,
    dark: Triple<TvColors, TvElementColors, TvSurfaces>,
    light: Triple<TvColors, TvElementColors, TvSurfaces>,
) {
    val dark: LookColors = LookColors(look, dark = true, colors = dark.first, elements = dark.second, surfaces = dark.third)
    val light: LookColors = LookColors(look, dark = false, colors = light.first, elements = light.second, surfaces = light.third)
}
