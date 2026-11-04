package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
    public val shelfTitle: TextStyle,
    public val tileLabel: TextStyle,
)

private val DarkColors =
    TvColors(
        background = Color(0xFF0B0D12),
        surface = Color(0xFF1B1F2A),
        onSurface = Color(0xFFF2F4F8),
        onSurfaceMuted = Color(0xFF9AA3B5),
        accent = Color(0xFF7CB7FF),
    )

private fun typographyFor(colors: TvColors) =
    TvTypography(
        clock = TextStyle(color = colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Light),
        date = TextStyle(color = colors.onSurfaceMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        shelfTitle = TextStyle(color = colors.onSurface, fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
        tileLabel = TextStyle(color = colors.onSurfaceMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )

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
