package dev.niltsiar.tvhome.core.designsystem

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
    public val tileLabel: TextStyle,
    public val caption: TextStyle,
)

private val DarkColors =
    TvColors(
        background = Color(0xFF0B0D12),
        surface = Color(0xFF1B1F2A),
        onSurface = Color(0xFFF2F4F8),
        onSurfaceMuted = Color(0xFF9AA3B5),
        accent = Color(0xFF7CB7FF),
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
    val greetingSize = 11.sp
    val pillLabelSize = 16.sp
    val shelfTitleSize = 40.sp
    val tileLabelSize = 16.sp
    val captionSize = 15.sp
    return TvTypography(
        clock =
            TextStyle(
                color = colors.onSurface,
                fontSize = clockSize,
                fontWeight = FontWeight.Light,
                fontFamily = googleSansFlex(FontWeight.Light, clockSize, width = 125f, roundness = 100f),
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
                color = colors.onSurfaceMuted,
                fontSize = greetingSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.18.em,
                fontFamily = googleSansFlex(FontWeight.Medium, greetingSize, width = 100f, roundness = 0f),
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
        tileLabel =
            TextStyle(
                color = colors.onSurfaceMuted,
                fontSize = tileLabelSize,
                fontWeight = FontWeight.Medium,
                fontFamily = googleSansFlex(FontWeight.Medium, tileLabelSize, width = 100f, roundness = 0f),
            ),
        caption =
            TextStyle(
                color = colors.onSurface.copy(alpha = 0.85f),
                fontSize = captionSize,
                fontWeight = FontWeight.Normal,
                fontFamily = googleSansFlex(FontWeight.Normal, captionSize, width = 100f, roundness = 0f),
            ),
    )
}

@Suppress("CompositionLocalAllowlist")
private val LocalTvColors = staticCompositionLocalOf { DarkColors }

@Suppress("CompositionLocalAllowlist")
private val LocalTvTypography = staticCompositionLocalOf { typographyFor(DarkColors) }

@Suppress("CompositionLocalAllowlist")
internal val LocalGlassKind = staticCompositionLocalOf { GlassKind.Refractive }

public object TvTheme {
    public val colors: TvColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTvColors.current

    public val typography: TvTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalTvTypography.current
}

/** Dark-only TV theme. [glassKind] picks how glass surfaces draw. */
@Composable
public fun TvTheme(
    glassKind: GlassKind = GlassKind.Refractive,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTvColors provides DarkColors,
        LocalTvTypography provides typographyFor(DarkColors),
        LocalGlassKind provides glassKind,
        content = content,
    )
}
