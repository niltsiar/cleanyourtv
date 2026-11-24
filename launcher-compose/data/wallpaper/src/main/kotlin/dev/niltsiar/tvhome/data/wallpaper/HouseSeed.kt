package dev.niltsiar.tvhome.data.wallpaper

import androidx.compose.ui.graphics.Color
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.DayPart
import dev.niltsiar.tvhome.core.model.dayPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

internal val NightTeal = Color(0xFF1F8A8A)
internal val MorningAmber = Color(0xFFE8A33D)
internal val ForenoonSkyBlue = Color(0xFF4A9FE0)
internal val AfternoonPeriwinkle = Color(0xFF8B93E8)
internal val EveningMagenta = Color(0xFFC03A8C)

/** The saturation and value a wallpaper's hue takes as a seed. */
private const val WALLPAPER_SEED_SATURATION = 0.6f
private const val WALLPAPER_SEED_VALUE = 0.8f

/** The seed of the part of the day, the last of the chain. */
internal fun DayPart.seed(): Color =
    when (this) {
        DayPart.Night -> NightTeal
        DayPart.Morning -> MorningAmber
        DayPart.Forenoon -> ForenoonSkyBlue
        DayPart.Afternoon -> AfternoonPeriwinkle
        DayPart.Evening -> EveningMagenta
    }

/** The first of expressive_seed, the wallpaper's [hue], the sky's [skyGlow] and the seed of [part]. */
internal fun houseSeed(
    config: TvConfig,
    hue: Float?,
    skyGlow: Int?,
    part: DayPart,
): Color =
    config.expressiveSeed?.let(::Color)
        ?: hue?.let { Color.hsv(it, WALLPAPER_SEED_SATURATION, WALLPAPER_SEED_VALUE) }
        ?: skyGlow?.let(::Color)
        ?: part.seed()

/**
 * The colour every window's Expressive scheme grows from at [now]: expressive_seed, else the wallpaper's dominant [hue], else
 * the sky's glow while sky_intensity is on, else a seed for the part of the day in the launcher's language.
 */
public fun houseSeed(
    config: TvConfig,
    hue: Float?,
    now: LocalDateTime,
    zone: ZoneId,
): Color {
    val glow = if (config.skyIntensity > 0) sky(config, now, zone).glow else null
    return houseSeed(config, hue, glow, dayPart(now.hour, config.locale ?: Locale.getDefault()))
}

/** [houseSeed] again at each tick of [clock] and each change of [config] or [hue]; a tick with no valid time is skipped. */
public fun houseSeeds(
    config: StateFlow<TvConfig>,
    clock: Flow<LocalDateTime?>,
    hue: StateFlow<Float?>,
    zone: () -> ZoneId = ZoneId::systemDefault,
): Flow<Color> =
    combine(config, clock.filterNotNull(), hue) { current, now, shown -> houseSeed(current, shown, now, zone()) }.distinctUntilChanged()
