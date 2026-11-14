package dev.niltsiar.tvhome

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * locale: [base] in the theme's language, which its strings, formats and layout direction follow; [base] itself when the
 * theme sets none. Formatters that take no Context (the relative "Last opened" time) read the default locale, so that
 * follows too. The theme is read once per process, so every activity of a process gets the same language.
 */
internal fun launcherContext(base: Context): Context {
    val locale =
        (base.applicationContext as TvHomeApplication)
            .graph.configRepository.config.value.locale ?: return base
    Locale.setDefault(locale)
    // Only the locale: a full copy would also pin uiMode, which MainActivity takes in place when night mode changes.
    return base.createConfigurationContext(Configuration().apply { setLocale(locale) })
}
