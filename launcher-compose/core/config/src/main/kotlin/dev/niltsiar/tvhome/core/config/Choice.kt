package dev.niltsiar.tvhome.core.config

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * One option the person sets on the TV. A stored value wins over the theme's until a different theme is pushed.
 * Its key is named as in the reference launcher's preferences, or as the config.json key for the options added here.
 */
public class Choice<T> internal constructor(
    internal val key: Preferences.Key<*>,
    internal val put: (MutablePreferences, T) -> Unit,
    internal val overlay: (TvConfig, Preferences) -> TvConfig,
)

/** The stored value of [key]; null when none is stored or it has another type, as an older build may have used the name. */
internal inline fun <reified T : Any> Preferences.stored(key: Preferences.Key<T>): T? = asMap()[key] as? T

private fun boolChoice(
    name: String,
    apply: TvConfig.(Boolean) -> TvConfig,
): Choice<Boolean> {
    val key = booleanPreferencesKey(name)
    return Choice(key, { prefs, value -> prefs[key] = value }) { config, prefs -> prefs.stored(key)?.let { config.apply(it) } ?: config }
}

/** A stored int outside [range] is ignored, as the reference launcher ignores it. */
private fun intChoice(
    name: String,
    range: IntRange,
    apply: TvConfig.(Int) -> TvConfig,
): Choice<Int> {
    val key = intPreferencesKey(name)
    return Choice(key, { prefs, value -> prefs[key] = value }) { config, prefs ->
        prefs.stored(key)?.takeIf { it in range }?.let { config.apply(it) } ?: config
    }
}

private fun storedString(key: Preferences.Key<String>): Choice<String> =
    Choice(key, { prefs, value -> prefs[key] = value }) { config, _ -> config }

private const val DOCK_OPACITY_AUTO = 0

internal val OpenOnStartKey = stringPreferencesKey("open_on_start")
internal val WallpaperChoiceKey = stringPreferencesKey("wallpaper_choice")

/** Every [Choice]. */
public object Choices {
    /** 4 to 7, the reference launcher's range. */
    public val Columns: Choice<Int> = intChoice("columns", 4..7) { copy(columns = it) }
    public val RowsBelowDock: Choice<Int> = intChoice("rows_below_dock", 0..3) { copy(rowsBelowDock = it) }

    /** 30 to 100, or null for the adaptive tint, which stays chosen when the theme sets a number. */
    public val DockOpacity: Choice<Int?> =
        intPreferencesKey("dock_opacity").let { key ->
            Choice(key, { prefs, value -> prefs[key] = value ?: DOCK_OPACITY_AUTO }) { config, prefs ->
                when (val stored = prefs.stored(key)) {
                    DOCK_OPACITY_AUTO -> config.copy(dockOpacity = null)
                    in 30..100 -> config.copy(dockOpacity = stored)
                    else -> config
                }
            }
        }

    public val TimeFormat: Choice<ClockFormat> =
        stringPreferencesKey("clock_format").let { key ->
            Choice(key, { prefs, value -> prefs[key] = value.jsonValue }) { config, prefs ->
                ClockFormat.entries.firstOrNull { it.jsonValue == prefs.stored(key) }?.let { config.copy(clockFormat = it) } ?: config
            }
        }
    public val Animations: Choice<Boolean> = boolChoice("animations") { copy(animations = it) }

    /**
     * A file name of the theme's wallpapers folder, its single wallpaper.jpg, or [WALLPAPER_NONE].
     * [ConfigRepository.wallpaperChoice] reads it; no [TvConfig] field carries it.
     */
    public val WallpaperChoice: Choice<String> = storedString(WallpaperChoiceKey)

    /** The [WallpaperChoice] that shows no wallpaper. */
    public const val WALLPAPER_NONE: String = "none"

    /** The picked package, absent or empty for Off. [ConfigRepository.openOnStartApp] reads it; no [TvConfig] field carries it. */
    public val OpenOnStart: Choice<String> = storedString(OpenOnStartKey)

    public val AlwaysShowNames: Choice<Boolean> = boolChoice("always_show_names") { copy(alwaysShowNames = it) }
    public val ShowGreeting: Choice<Boolean> = boolChoice("show_greeting") { copy(showGreeting = it) }
    public val DateBelowClock: Choice<Boolean> = boolChoice("date_below_clock") { copy(dateBelowClock = it) }
    public val AppLibrary: Choice<Boolean> = boolChoice("app_library") { copy(appLibrary = it) }
    public val LibraryLeastUsed: Choice<Boolean> = boolChoice("library_least_used") { copy(libraryLeastUsed = it) }
    public val UnusedFadeDays: Choice<Int> = intChoice("unused_fade_days", 0..3650) { copy(unusedFadeDays = it) }
    public val HomeIdleClockMinutes: Choice<Int> = intChoice("home_idle_clock_minutes", 0..1440) { copy(homeIdleClockMinutes = it) }
    public val BackOpensClock: Choice<Boolean> = boolChoice("back_opens_clock") { copy(backOpensClock = it) }
    public val ClockWallpaper: Choice<Boolean> = boolChoice("clock_wallpaper") { copy(clockWallpaper = it) }
    public val ReducedMotion: Choice<Boolean> = boolChoice("reduced_motion") { copy(reducedMotion = it) }
    public val FocusOvershoot: Choice<Boolean> = boolChoice("focus_overshoot") { copy(focusOvershoot = it) }
    public val HabitHint: Choice<Boolean> = boolChoice("habit_hint") { copy(habitHint = it) }
    public val InputArrival: Choice<Boolean> = boolChoice("input_arrival") { copy(inputArrival = it) }

    internal val all: List<Choice<*>> =
        listOf(
            Columns,
            RowsBelowDock,
            DockOpacity,
            TimeFormat,
            Animations,
            WallpaperChoice,
            OpenOnStart,
            AlwaysShowNames,
            ShowGreeting,
            DateBelowClock,
            AppLibrary,
            LibraryLeastUsed,
            UnusedFadeDays,
            HomeIdleClockMinutes,
            BackOpensClock,
            ClockWallpaper,
            ReducedMotion,
            FocusOvershoot,
            HabitHint,
            InputArrival,
        )

    /** The choices a pushed theme resets: all but open on start. */
    internal val themeScoped: List<Choice<*>> = all - OpenOnStart
}
