package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.designsystem.expressive.ExpressiveShapes
import dev.niltsiar.tvhome.core.designsystem.expressive.expressiveElementColors
import dev.niltsiar.tvhome.core.designsystem.expressive.houseScheme

@Immutable
public class TvColors(
    public val background: Color,
    public val surface: Color,
    public val onSurface: Color,
    public val onSurfaceMuted: Color,
    public val accent: Color,
)

/**
 * The type roles, each a style of the bundled Google Sans Flex variable font in its own colour. [menuRow] is the label and
 * value of a menu row, [menuTitle] the title over a menu's rows.
 */
@Immutable
public class TvTypography(
    public val clock: TextStyle,
    public val clockCompact: TextStyle,
    public val date: TextStyle,
    public val dateCompact: TextStyle,
    public val amPm: TextStyle,
    public val greeting: TextStyle,
    public val pillLabel: TextStyle,
    public val shelfTitle: TextStyle,
    public val heroTitle: TextStyle,
    public val chromeTitle: TextStyle,
    public val cinematicTitle: TextStyle,
    public val cinematicMeta: TextStyle,
    public val tileLabel: TextStyle,
    public val tileCaption: TextStyle,
    public val chipKey: TextStyle,
    public val chipLabel: TextStyle,
    public val caption: TextStyle,
    public val screensaverClock: TextStyle,
    public val screensaverAmPm: TextStyle,
    public val screensaverDate: TextStyle,
    public val menuRow: TextStyle,
    public val menuTitle: TextStyle,
)

/**
 * The surfaces that follow the system's night mode. [overlay] fills menus, the Inputs list and the App library, and
 * [card] the idle cards on the library's panel;
 * [chromeTint] goes over the frost of the clock pill, the top-bar buttons, the dock and the library pill. A focused row
 * or button fills with [pillFill] and draws its text and icons in [pillContent], its other text in [pillSecondary].
 * [warnInk] draws a warning: a menu row that removes something, a setting that could not be saved.
 */
@Immutable
public class TvSurfaces internal constructor(
    public val dark: Boolean,
    public val overlay: Color,
    public val card: Color,
    public val chromeTint: Color,
    public val pillFill: Color,
    public val pillContent: Color,
    public val pillSecondary: Color,
    public val warnInk: Color,
)

private val DarkColors =
    TvColors(
        background = Color(0xFF0B0D12),
        surface = Color(0xFF1B1F2A),
        onSurface = Color(0xFFF2F4F8),
        onSurfaceMuted = Color(0xFF9AA3B5),
        accent = Color(0xFF7CB7FF),
    )

/** Text and icons on a light surface. */
private val OnLightColors =
    TvColors(
        background = DarkColors.background,
        surface = DarkColors.surface,
        onSurface = Color(0xFF11141B),
        onSurfaceMuted = Color(0xFF4A5263),
        accent = DarkColors.accent,
    )

private val DarkSurfaces =
    TvSurfaces(
        dark = true,
        overlay = Color(0xFF141721),
        card = Color(0xFF1C2030),
        chromeTint = Color(0xFF0B0D12).copy(alpha = 0.55f),
        pillFill = Color(0xFFF2F4F8),
        pillContent = Color(0xFF0B0D12),
        pillSecondary = Color(0xFF4A5263),
        warnInk = Color(0xFFFF8A80),
    )

private val DarkElementColors =
    glassElementColors(ink = DarkColors.onSurface, tileInk = DarkColors.onSurface, menuTitleInk = Color.White)

private val OnLightElementColors =
    glassElementColors(ink = OnLightColors.onSurface, tileInk = DarkColors.onSurface, menuTitleInk = OnLightColors.onSurface)

private val DarkExpressiveElementColors =
    expressiveElementColors(ink = DarkColors.onSurface, tileInk = DarkColors.onSurface, menuTitleInk = Color.White)

private val OnLightExpressiveElementColors =
    expressiveElementColors(ink = OnLightColors.onSurface, tileInk = DarkColors.onSurface, menuTitleInk = OnLightColors.onSurface)

/** A token's value in each look; [TvTheme] gives the one of the window's look. */
@Immutable
private class ByLook<T>(
    val glass: T,
    val expressive: T,
) {
    fun of(look: Look): T = if (look == Look.Expressive) expressive else glass
}

private val LightSurfaces =
    TvSurfaces(
        dark = false,
        overlay = Color(0xFFF4F6FA),
        card = Color(0xFFE9EBEF),
        chromeTint = Color(0xFFF4F6FA).copy(alpha = 0.55f),
        pillFill = Color(0xFF11141B),
        pillContent = Color(0xFFF4F6FA),
        pillSecondary = Color(0xFFB8BFCC),
        warnInk = Color(0xFFC62828),
    )

private const val FULL_ROUNDNESS = 100f

/**
 * One face from the bundled Google Sans Flex variable font. Width, optical size and roundness are fixed per
 * style, so each style gets its own family.
 */
private fun googleSansFlexFace(
    weight: FontWeight,
    size: TextUnit,
    width: Float,
    roundness: Float,
): FontFamily =
    FontFamily(
        Font(
            resId = R.font.google_sans_flex,
            weight = weight,
            variationSettings =
                FontVariation.Settings(
                    weight,
                    FontStyle.Normal,
                    FontVariation.width(width),
                    FontVariation.opticalSizing(size),
                    FontVariation.Setting("ROND", roundness),
                ),
        ),
    )

/**
 * The type roles in [colors] and [elements]. With [round] every role takes full roundness, the Expressive faces; the axes
 * that set a glyph's advance (weight and width) stay, so a text is as wide in both looks.
 */
private fun typographyFor(
    colors: TvColors,
    elements: TvElementColors,
    round: Boolean = false,
): TvTypography {
    fun googleSansFlex(
        weight: FontWeight,
        size: TextUnit,
        width: Float,
        roundness: Float,
    ): FontFamily = googleSansFlexFace(weight, size, width, if (round) FULL_ROUNDNESS else roundness)
    val clockSize = 32.sp
    val clockCompactSize = 28.sp
    val dateSize = 18.sp
    val dateCompactSize = 16.sp
    val amPmSize = 14.sp
    val greetingSize = 16.sp
    val pillLabelSize = 16.sp
    val shelfTitleSize = 40.sp
    val heroTitleSize = 48.sp
    val chromeTitleSize = 32.sp
    val cinematicTitleSize = 68.sp
    val cinematicMetaSize = 15.sp
    val tileLabelSize = 16.sp
    val tileCaptionSize = 13.sp
    val chipSize = 15.sp
    val captionSize = 15.sp
    val screensaverClockSize = 160.sp
    val screensaverAmPmSize = 26.sp
    val screensaverDateSize = 20.sp
    val pillLabel =
        TextStyle(
            color = colors.onSurface,
            fontSize = pillLabelSize,
            fontWeight = FontWeight.Medium,
            fontFamily = googleSansFlex(FontWeight.Medium, pillLabelSize, width = 100f, roundness = 0f),
        )
    val chipKey =
        TextStyle(
            color = colors.onSurface,
            fontSize = chipSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = googleSansFlex(FontWeight.SemiBold, chipSize, width = 100f, roundness = 0f),
        )
    return TvTypography(
        clock =
            TextStyle(
                color = colors.onSurface,
                fontSize = clockSize,
                fontWeight = FontWeight.Light,
                fontFamily = googleSansFlex(FontWeight.Light, clockSize, width = 125f, roundness = 100f),
                fontFeatureSettings = "tnum",
            ),
        clockCompact =
            TextStyle(
                color = colors.onSurface,
                fontSize = clockCompactSize,
                fontWeight = FontWeight.Light,
                fontFamily = googleSansFlex(FontWeight.Light, clockCompactSize, width = 125f, roundness = 100f),
                fontFeatureSettings = "tnum",
            ),
        date =
            TextStyle(
                color = colors.onSurfaceMuted,
                fontSize = dateSize,
                fontWeight = FontWeight.Medium,
                fontFamily = googleSansFlex(FontWeight.Medium, dateSize, width = 100f, roundness = 0f),
            ),
        dateCompact =
            TextStyle(
                color = colors.onSurfaceMuted,
                fontSize = dateCompactSize,
                fontWeight = FontWeight.Medium,
                fontFamily = googleSansFlex(FontWeight.Medium, dateCompactSize, width = 100f, roundness = 0f),
            ),
        amPm =
            TextStyle(
                color = colors.onSurfaceMuted,
                fontSize = amPmSize,
                fontWeight = FontWeight.Medium,
                fontFamily = googleSansFlex(FontWeight.Medium, amPmSize, width = 100f, roundness = 0f),
            ),
        greeting =
            TextStyle(
                color = elements.greeting,
                fontSize = greetingSize,
                fontWeight = FontWeight.Medium,
                fontFamily = googleSansFlex(FontWeight.Medium, greetingSize, width = 100f, roundness = 0f),
            ),
        pillLabel = pillLabel,
        shelfTitle =
            TextStyle(
                color = colors.onSurface,
                fontSize = shelfTitleSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = googleSansFlex(FontWeight.SemiBold, shelfTitleSize, width = 112f, roundness = 100f),
            ),
        heroTitle =
            TextStyle(
                color = colors.onSurface,
                fontSize = heroTitleSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.025).em,
                fontFamily = googleSansFlex(FontWeight.Medium, heroTitleSize, width = 112f, roundness = 100f),
            ),
        chromeTitle =
            TextStyle(
                color = colors.onSurface,
                fontSize = chromeTitleSize,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-0.025).em,
                fontFamily = googleSansFlex(FontWeight.Normal, chromeTitleSize, width = 112f, roundness = 100f),
            ),
        cinematicTitle =
            TextStyle(
                color = colors.onSurface,
                fontSize = cinematicTitleSize,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.025).em,
                fontFamily = googleSansFlex(FontWeight.Light, cinematicTitleSize, width = 112f, roundness = 100f),
            ),
        cinematicMeta =
            TextStyle(
                color = colors.onSurface,
                fontSize = cinematicMetaSize,
                fontWeight = FontWeight.Medium,
                fontFamily = googleSansFlex(FontWeight.Medium, cinematicMetaSize, width = 100f, roundness = 0f),
            ),
        tileLabel =
            TextStyle(
                color = elements.tileLabel,
                fontSize = tileLabelSize,
                fontWeight = FontWeight.Medium,
                fontFamily = googleSansFlex(FontWeight.Medium, tileLabelSize, width = 100f, roundness = 0f),
            ),
        tileCaption =
            TextStyle(
                color = elements.tileCaption,
                fontSize = tileCaptionSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = googleSansFlex(FontWeight.SemiBold, tileCaptionSize, width = 100f, roundness = 0f),
            ),
        chipKey = chipKey,
        chipLabel =
            TextStyle(
                color = colors.onSurface,
                fontSize = chipSize,
                fontWeight = FontWeight.Normal,
                fontFamily = googleSansFlex(FontWeight.Normal, chipSize, width = 100f, roundness = 0f),
            ),
        caption =
            TextStyle(
                color = elements.caption,
                fontSize = captionSize,
                fontWeight = FontWeight.Normal,
                fontFamily = googleSansFlex(FontWeight.Normal, captionSize, width = 100f, roundness = 0f),
            ),
        screensaverClock =
            TextStyle(
                color = elements.screensaverClock,
                fontSize = screensaverClockSize,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-0.03).em,
                lineHeight = screensaverClockSize,
                fontFamily = googleSansFlex(FontWeight.ExtraLight, screensaverClockSize, width = 118f, roundness = 100f),
            ),
        screensaverAmPm =
            TextStyle(
                color = elements.screensaverAmPm,
                fontSize = screensaverAmPmSize,
                fontWeight = FontWeight.Medium,
                fontFamily = googleSansFlex(FontWeight.Medium, screensaverAmPmSize, width = 100f, roundness = 100f),
            ),
        screensaverDate =
            TextStyle(
                color = elements.screensaverDate,
                fontSize = screensaverDateSize,
                fontWeight = FontWeight.Medium,
                fontFamily = googleSansFlex(FontWeight.Medium, screensaverDateSize, width = 100f, roundness = 100f),
            ),
        menuRow = pillLabel,
        menuTitle = chipKey.copy(fontSize = pillLabelSize, color = elements.menuTitle),
    )
}

@Suppress("CompositionLocalAllowlist")
private val LocalTvColors = staticCompositionLocalOf { DarkColors }

@Suppress("CompositionLocalAllowlist")
private val LocalTvTypography = staticCompositionLocalOf { DarkTypography }

@Suppress("CompositionLocalAllowlist")
private val LocalMotion = staticCompositionLocalOf { ByLook(Motion(), Motion(look = Look.Expressive)) }

@Suppress("CompositionLocalAllowlist")
private val LocalTvElementColors = staticCompositionLocalOf { DarkElements }

@Suppress("CompositionLocalAllowlist")
private val LocalTvShapes = staticCompositionLocalOf { Shapes }

@Suppress("CompositionLocalAllowlist")
private val LocalTvSurfaces = staticCompositionLocalOf { DarkSurfaces }

@Suppress("CompositionLocalAllowlist")
private val LocalFocusStyle = staticCompositionLocalOf { FocusStyle.Glass }

@Suppress("CompositionLocalAllowlist")
private val LocalLookState = staticCompositionLocalOf { LookState(Look.Glass) { DefaultSeed } }

private val DarkTypography by lazy {
    ByLook(typographyFor(DarkColors, DarkElementColors), typographyFor(DarkColors, DarkExpressiveElementColors, round = true))
}
private val OnLightTypography by lazy {
    ByLook(
        typographyFor(OnLightColors, OnLightElementColors),
        typographyFor(OnLightColors, OnLightExpressiveElementColors, round = true),
    )
}
private val DarkElements = ByLook(DarkElementColors, DarkExpressiveElementColors)
private val OnLightElements = ByLook(OnLightElementColors, OnLightExpressiveElementColors)
private val Shapes = ByLook(GlassShapes, ExpressiveShapes)

/** The seed of a [TvTheme] whose caller gives none. */
private val DefaultSeed = DarkColors.accent

/**
 * The window's [look] and the house [seed] its colours grow from. One object per [TvTheme], so a change of either
 * invalidates only what reads it: read them in the draw phase and a change redraws without recomposing.
 */
@Stable
public class LookState internal constructor(
    look: Look,
    seed: () -> Color,
) {
    public var look: Look by mutableStateOf(look)
        internal set
    internal var seedSource: () -> Color by mutableStateOf(seed)
    public val seed: Color get() = seedSource()

    private val schemes = arrayOfNulls<ColorScheme>(2)
    private val schemeSeeds = arrayOfNulls<Color>(2)

    /** The house scheme of the [seed], [dark] or light; built once per seed and kept, so reading it in draw allocates nothing. */
    internal fun scheme(dark: Boolean): ColorScheme {
        val from = seed
        val slot = if (dark) 0 else 1
        val kept = schemes[slot]
        if (kept != null && schemeSeeds[slot] == from) return kept
        return houseScheme(from, dark).also {
            schemes[slot] = it
            schemeSeeds[slot] = from
        }
    }
}

public object TvTheme {
    public val colors: TvColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current

    public val typography: TvTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalTvTypography.current.of(LocalLookState.current.look)

    public val motion: Motion
        @Composable
        @ReadOnlyComposable
        get() = LocalMotion.current.of(LocalLookState.current.look)

    public val surfaces: TvSurfaces
        @Composable
        @ReadOnlyComposable
        get() = LocalTvSurfaces.current

    public val elementColors: TvElementColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTvElementColors.current.of(LocalLookState.current.look)

    public val shapes: TvShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalTvShapes.current.of(LocalLookState.current.look)

    public val focusStyle: FocusStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusStyle.current

    public val lookState: LookState
        @Composable
        @ReadOnlyComposable
        get() = LocalLookState.current
}

/**
 * The TV theme. Text over the wallpaper is always light; the [TvTheme.surfaces] are light or dark with [darkTheme].
 * [look] is the design and [seed] gives the house colour of [Look.Expressive]; both reach [TvTheme.lookState]. [seed] is
 * read where the colour is drawn, so a seed held in a state changes the drawing without recomposing the caller. [motion]
 * says how anything moves, [focusStyle] how a focused tile is lit. The [TvTheme.typography], [TvTheme.motion],
 * [TvTheme.elementColors] and [TvTheme.shapes] it gives are the look's, chosen as they are read: a change of [look] recomposes what reads them,
 * and leaves the composition's structure and its focus as they are.
 */
@Composable
public fun TvTheme(
    look: Look,
    seed: () -> Color = { DefaultSeed },
    motion: Motion = Motion(),
    focusStyle: FocusStyle = FocusStyle.Glass,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val lookState = remember { LookState(look, seed) }
    lookState.look = look
    lookState.seedSource = seed
    val motions = remember(motion) { ByLook(motion.copy(look = Look.Glass), motion.copy(look = Look.Expressive)) }
    CompositionLocalProvider(
        LocalTvColors provides DarkColors,
        LocalTvTypography provides DarkTypography,
        LocalTvElementColors provides DarkElements,
        LocalTvShapes provides Shapes,
        LocalMotion provides motions,
        LocalFocusStyle provides focusStyle,
        LocalTvSurfaces provides if (darkTheme) DarkSurfaces else LightSurfaces,
        LocalLookState provides lookState,
        content = content,
    )
}

/**
 * [content] on a surface drawn in [TvTheme.surfaces]: on a light one (night mode off) its text turns dark. Both cases
 * provide the same locals, so a change of the surface keeps the composition's structure and its focus.
 */
@Composable
internal fun SurfaceContent(content: @Composable () -> Unit) {
    val dark = LocalTvSurfaces.current.dark
    CompositionLocalProvider(
        LocalTvColors provides if (dark) DarkColors else OnLightColors,
        LocalTvTypography provides if (dark) DarkTypography else OnLightTypography,
        LocalTvElementColors provides if (dark) DarkElements else OnLightElements,
        content = content,
    )
}
