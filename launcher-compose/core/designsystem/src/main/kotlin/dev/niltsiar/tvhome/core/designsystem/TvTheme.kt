package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
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

@Immutable
public class TvColors(
    public val background: Color,
    public val surface: Color,
    public val onSurface: Color,
    public val onSurfaceMuted: Color,
    public val accent: Color,
)

@Immutable
public class TvTypography(
    public val clock: TextStyle,
    public val date: TextStyle,
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
)

/**
 * The surfaces that follow the system's night mode. [overlay] fills menus, the Inputs list and the App library, and
 * [card] the idle cards on the library's panel;
 * [chromeTint] goes over the frost of the clock pill, the top-bar buttons, the dock and the library pill. A focused row
 * or button fills with [pillFill] and draws its text and icons in [pillContent], its other text in [pillSecondary].
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
    )

private val LightSurfaces =
    TvSurfaces(
        dark = false,
        overlay = Color(0xFFF4F6FA),
        card = Color(0xFFE9EBEF),
        chromeTint = Color(0xFFF4F6FA).copy(alpha = 0.55f),
        pillFill = Color(0xFF11141B),
        pillContent = Color(0xFFF4F6FA),
        pillSecondary = Color(0xFFB8BFCC),
    )

/**
 * One face from the bundled Google Sans Flex variable font. Width, optical size and roundness are fixed per
 * style, so each style gets its own family.
 */
private fun googleSansFlex(
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

private fun typographyFor(colors: TvColors): TvTypography {
    val clockSize = 32.sp
    val dateSize = 18.sp
    val amPmSize = 14.sp
    val greetingSize = 13.sp
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
    return TvTypography(
        clock =
            TextStyle(
                color = colors.onSurface,
                fontSize = clockSize,
                fontWeight = FontWeight.Light,
                fontFamily = googleSansFlex(FontWeight.Light, clockSize, width = 125f, roundness = 100f),
                fontFeatureSettings = "tnum",
            ),
        date =
            TextStyle(
                color = colors.onSurfaceMuted,
                fontSize = dateSize,
                fontWeight = FontWeight.Medium,
                fontFamily = googleSansFlex(FontWeight.Medium, dateSize, width = 100f, roundness = 0f),
            ),
        amPm =
            TextStyle(
                color = colors.onSurfaceMuted,
                fontSize = amPmSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.08.em,
                fontFamily = googleSansFlex(FontWeight.Medium, amPmSize, width = 100f, roundness = 0f),
            ),
        greeting =
            TextStyle(
                color = colors.onSurface.copy(alpha = 0.8f),
                fontSize = greetingSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.18.em,
                fontFamily = googleSansFlex(FontWeight.SemiBold, greetingSize, width = 100f, roundness = 0f),
            ),
        pillLabel =
            TextStyle(
                color = colors.onSurface,
                fontSize = pillLabelSize,
                fontWeight = FontWeight.Medium,
                fontFamily = googleSansFlex(FontWeight.Medium, pillLabelSize, width = 100f, roundness = 0f),
            ),
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
                fontWeight = FontWeight.Normal,
                letterSpacing = (-0.025).em,
                fontFamily = googleSansFlex(FontWeight.Normal, heroTitleSize, width = 112f, roundness = 100f),
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
                color = colors.onSurfaceMuted,
                fontSize = tileLabelSize,
                fontWeight = FontWeight.Medium,
                fontFamily = googleSansFlex(FontWeight.Medium, tileLabelSize, width = 100f, roundness = 0f),
            ),
        tileCaption =
            TextStyle(
                color = Color.White,
                fontSize = tileCaptionSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = googleSansFlex(FontWeight.SemiBold, tileCaptionSize, width = 100f, roundness = 0f),
            ),
        chipKey =
            TextStyle(
                color = colors.onSurface,
                fontSize = chipSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = googleSansFlex(FontWeight.SemiBold, chipSize, width = 100f, roundness = 0f),
            ),
        chipLabel =
            TextStyle(
                color = colors.onSurface,
                fontSize = chipSize,
                fontWeight = FontWeight.Normal,
                fontFamily = googleSansFlex(FontWeight.Normal, chipSize, width = 100f, roundness = 0f),
            ),
        caption =
            TextStyle(
                color = colors.onSurface.copy(alpha = 0.85f),
                fontSize = captionSize,
                fontWeight = FontWeight.Normal,
                fontFamily = googleSansFlex(FontWeight.Normal, captionSize, width = 100f, roundness = 0f),
            ),
        screensaverClock =
            TextStyle(
                color = Color.White.copy(alpha = 0.92f),
                fontSize = screensaverClockSize,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-0.03).em,
                lineHeight = screensaverClockSize,
                fontFamily = googleSansFlex(FontWeight.ExtraLight, screensaverClockSize, width = 118f, roundness = 100f),
            ),
        screensaverAmPm =
            TextStyle(
                color = Color.White.copy(alpha = 0.6f),
                fontSize = screensaverAmPmSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.1.em,
                fontFamily = googleSansFlex(FontWeight.Medium, screensaverAmPmSize, width = 100f, roundness = 100f),
            ),
        screensaverDate =
            TextStyle(
                color = Color.White.copy(alpha = 0.55f),
                fontSize = screensaverDateSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.16.em,
                fontFamily = googleSansFlex(FontWeight.Medium, screensaverDateSize, width = 100f, roundness = 100f),
            ),
    )
}

@Suppress("CompositionLocalAllowlist")
private val LocalTvColors = staticCompositionLocalOf { DarkColors }

@Suppress("CompositionLocalAllowlist")
private val LocalTvTypography = staticCompositionLocalOf { typographyFor(DarkColors) }

@Suppress("CompositionLocalAllowlist")
private val LocalMotion = staticCompositionLocalOf { Motion() }

@Suppress("CompositionLocalAllowlist")
private val LocalTvSurfaces = staticCompositionLocalOf { DarkSurfaces }

private val OnLightTypography by lazy { typographyFor(OnLightColors) }

public object TvTheme {
    public val colors: TvColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current

    public val typography: TvTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalTvTypography.current

    public val motion: Motion
        @Composable
        @ReadOnlyComposable
        get() = LocalMotion.current

    public val surfaces: TvSurfaces
        @Composable
        @ReadOnlyComposable
        get() = LocalTvSurfaces.current
}

/**
 * The TV theme. Text over the wallpaper is always light; the [TvTheme.surfaces] are light or dark with [darkTheme].
 * [motion] says how anything moves.
 */
@Composable
public fun TvTheme(
    motion: Motion = Motion(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTvColors provides DarkColors,
        LocalTvTypography provides typographyFor(DarkColors),
        LocalMotion provides motion,
        LocalTvSurfaces provides if (darkTheme) DarkSurfaces else LightSurfaces,
        content = content,
    )
}

/** [content] on a surface drawn in [TvTheme.surfaces]: on a light one (night mode off) its text turns dark. */
@Composable
internal fun SurfaceContent(content: @Composable () -> Unit) {
    if (LocalTvSurfaces.current.dark) {
        content()
    } else {
        CompositionLocalProvider(LocalTvColors provides OnLightColors, LocalTvTypography provides OnLightTypography, content = content)
    }
}
