package dev.niltsiar.tvhome.feature.settings

import androidx.annotation.StringRes
import dev.niltsiar.tvhome.core.config.Choice
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import java.util.Locale

internal class Option<T>(
    val label: OptionLabel,
    val value: T,
)

/** A row bound to its [choice]: what the config holds shows as the selected option, and a pick stores that option's value. */
internal class ChoiceRow<T>(
    val id: SettingsRowId,
    @StringRes private val label: Int,
    private val choice: Choice<T>,
    private val options: List<Option<T>>,
    private val current: (TvConfig) -> T,
) {
    fun row(config: TvConfig): SettingsRow {
        val held = current(config)
        return SettingsRow(id, label, options.map { it.label }, options.indexOfFirst { it.value == held })
    }

    /** Stores the option's value, unless it is the option the config already holds: that would pin the theme's value. */
    suspend fun pick(
        repository: ConfigRepository,
        option: Int,
    ) {
        if (option != row(repository.config.value).selected) repository.set(choice, options[option].value)
    }
}

internal class Group(
    @StringRes val heading: Int,
    val rows: List<ChoiceRow<*>>,
)

private fun text(value: Int) = OptionLabel.Text(value.toString())

private fun res(
    @StringRes id: Int,
) = OptionLabel.Res(id)

private fun numbers(vararg values: Int) = values.map { Option(text(it), it) }

private fun onOff(
    id: SettingsRowId,
    @StringRes label: Int,
    choice: Choice<Boolean>,
    current: (TvConfig) -> Boolean,
) = ChoiceRow(id, label, choice, listOf(Option(res(R.string.settings_on), true), Option(res(R.string.settings_off), false)), current)

/** Java's rows first, then ours in groups, in the order the screen lists them. */
internal val settingsGroups: List<Group> =
    listOf(
        Group(
            heading = R.string.settings_group_general,
            rows =
                listOf(
                    ChoiceRow(SettingsRowId.Columns, R.string.settings_columns, Choices.Columns, numbers(4, 5, 6, 7)) { it.columns },
                    ChoiceRow(SettingsRowId.RowsBelowDock, R.string.settings_rows_below_dock, Choices.RowsBelowDock, numbers(0, 1, 2, 3)) {
                        it.rowsBelowDock
                    },
                    ChoiceRow(
                        SettingsRowId.DockOpacity,
                        R.string.settings_dock_opacity,
                        Choices.DockOpacity,
                        listOf(Option<Int?>(res(R.string.settings_auto), null)) +
                            listOf(100, 70, 50, 30).map { Option<Int?>(OptionLabel.Text("$it%"), it) },
                    ) { it.dockOpacity },
                    ChoiceRow(
                        SettingsRowId.ClockFormat,
                        R.string.settings_clock,
                        Choices.TimeFormat,
                        listOf(
                            Option(res(R.string.settings_clock_system), ClockFormat.System),
                            Option(res(R.string.settings_clock_12), ClockFormat.Hour12),
                            Option(res(R.string.settings_clock_24), ClockFormat.Hour24),
                        ),
                    ) { it.clockFormat },
                    onOff(SettingsRowId.Animations, R.string.settings_animations, Choices.Animations) { it.animations },
                ),
        ),
        Group(
            heading = R.string.settings_group_home,
            rows =
                listOf(
                    onOff(SettingsRowId.AlwaysShowNames, R.string.settings_names, Choices.AlwaysShowNames) { it.alwaysShowNames },
                    onOff(SettingsRowId.ShowGreeting, R.string.settings_greeting, Choices.ShowGreeting) { it.showGreeting },
                    onOff(SettingsRowId.DateBelowClock, R.string.settings_date_below, Choices.DateBelowClock) { it.dateBelowClock },
                ),
        ),
        Group(
            heading = R.string.settings_group_library,
            rows =
                listOf(
                    onOff(SettingsRowId.AppLibrary, R.string.settings_app_library, Choices.AppLibrary) { it.appLibrary },
                    onOff(SettingsRowId.LibraryLeastUsed, R.string.settings_least_used, Choices.LibraryLeastUsed) { it.libraryLeastUsed },
                    ChoiceRow(
                        SettingsRowId.UnusedFadeDays,
                        R.string.settings_fade_unused,
                        Choices.UnusedFadeDays,
                        listOf(Option(res(R.string.settings_off), 0)) + numbers(30, 60, 90),
                    ) { it.unusedFadeDays },
                ),
        ),
        Group(
            heading = R.string.settings_group_clock,
            rows =
                listOf(
                    ChoiceRow(
                        SettingsRowId.HomeIdleClockMinutes,
                        R.string.settings_idle_clock,
                        Choices.HomeIdleClockMinutes,
                        listOf(Option(res(R.string.settings_off), 0)) + numbers(5, 10, 30, 60),
                    ) { it.homeIdleClockMinutes },
                    onOff(SettingsRowId.BackOpensClock, R.string.settings_back_clock, Choices.BackOpensClock) { it.backOpensClock },
                    onOff(SettingsRowId.ClockWallpaper, R.string.settings_clock_wallpaper, Choices.ClockWallpaper) { it.clockWallpaper },
                ),
        ),
        Group(
            heading = R.string.settings_group_motion,
            rows =
                listOf(
                    onOff(SettingsRowId.ReducedMotion, R.string.settings_reduced_motion, Choices.ReducedMotion) { it.reducedMotion },
                    onOff(SettingsRowId.FocusOvershoot, R.string.settings_focus_overshoot, Choices.FocusOvershoot) { it.focusOvershoot },
                    onOff(SettingsRowId.HabitHint, R.string.settings_habit_hint, Choices.HabitHint) { it.habitHint },
                    onOff(SettingsRowId.InputArrival, R.string.settings_input_arrival, Choices.InputArrival) { it.inputArrival },
                ),
        ),
    )

private val rowsById: Map<SettingsRowId, ChoiceRow<*>> = settingsGroups.flatMap { it.rows }.associateBy { it.id }

internal fun rowOf(id: SettingsRowId): ChoiceRow<*> = rowsById.getValue(id)

internal fun settingsSections(config: TvConfig): List<SettingsSection> =
    settingsGroups.map { group ->
        SettingsSection(
            group.heading,
            group.rows.map {
                it.row(config)
            },
        )
    }

/** The picker over [apps], with the entry of the [pick]ed package selected, or Off when it is not among them. */
internal fun startPicker(
    apps: List<OpenOnStartApp>,
    pick: String?,
) = OpenOnStartPicker(apps, apps.indexOfFirst { it.packageName == pick } + 1)

/**
 * The file names as the page shows them: without the extension, with "_" and "-" as spaces and the first letter upper case in
 * [locale]. The file names themselves show when that leaves a name blank or two names the same.
 */
internal fun wallpaperLabels(
    names: List<String>,
    locale: Locale = Locale.getDefault(),
): List<String> {
    val shown =
        names.map { name ->
            name
                .substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .replaceFirstChar { it.titlecase(locale) }
        }
    return if (shown.none { it.isEmpty() } && shown.distinct().size == shown.size) shown else names
}

/** The stored choice when the theme can honour it; a picture that is gone leaves the theme deciding. */
private fun honoured(
    choice: String?,
    names: List<String>,
): String? = choice?.takeIf { it == Choices.WALLPAPER_NONE || it in names }

/** The value of the Wallpaper row for a theme with [names] as pictures, or null for a theme with none. */
internal fun wallpaperLabel(
    names: List<String>,
    choice: String?,
): OptionLabel? {
    if (names.isEmpty()) return null
    return when (val held = honoured(choice, names)) {
        null -> res(R.string.wallpaper_theme_default)
        Choices.WALLPAPER_NONE -> res(R.string.wallpaper_none)
        else -> OptionLabel.Text(wallpaperLabels(names)[names.indexOf(held)])
    }
}

/** The picker over [names]: Theme default first, None last, with the entry of the honoured [choice] selected. */
internal fun wallpaperPicker(
    names: List<String>,
    choice: String?,
): WallpaperPicker {
    val entries =
        listOf(res(R.string.wallpaper_theme_default)) + wallpaperLabels(names).map(OptionLabel::Text) + res(R.string.wallpaper_none)
    val selected =
        when (val held = honoured(choice, names)) {
            null -> 0
            Choices.WALLPAPER_NONE -> entries.lastIndex
            else -> names.indexOf(held) + 1
        }
    return WallpaperPicker(entries, selected)
}

/** What the picker's entry at [index] stores: null for Theme default, which removes the choice. */
internal fun wallpaperValue(
    names: List<String>,
    index: Int,
): String? =
    when (index) {
        0 -> null
        names.size + 1 -> Choices.WALLPAPER_NONE
        else -> names[index - 1]
    }
