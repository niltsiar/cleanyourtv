package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import java.util.Locale

/** The part of the day the greeting names. */
public enum class DayPart { Night, Morning, Forenoon, Afternoon, Evening }

/** The greeting line: the part of the day and, with greeting_name, whom it greets. */
@Immutable
public data class Greeting(
    val part: DayPart,
    val name: String?,
)

/**
 * The part of the day at [hour] (0 to 23) in [locale]. Indonesian, Spain's Spanish, Catalan and Danish split the day
 * differently. Latin American Spanish keeps the default hours: there "Buenas noches" from about 17 is the local use.
 */
internal fun dayPart(
    hour: Int,
    locale: Locale,
): DayPart {
    val lang = locale.language
    return when {
        lang == "in" || lang == "id" -> {
            when {
                hour < 10 -> DayPart.Morning
                hour < 15 -> DayPart.Afternoon
                hour < 18 -> DayPart.Evening
                else -> DayPart.Night
            }
        }

        (lang == "es" && locale.country == "ES") || lang == "ca" -> {
            when {
                hour < 6 -> DayPart.Night
                hour < 12 -> DayPart.Morning
                hour < 21 -> DayPart.Afternoon
                else -> DayPart.Night
            }
        }

        lang == "da" -> {
            when {
                hour < 5 -> DayPart.Night
                hour < 11 -> DayPart.Morning
                hour < 12 -> DayPart.Forenoon
                hour < 18 -> DayPart.Afternoon
                hour < 22 -> DayPart.Evening
                else -> DayPart.Night
            }
        }

        else -> {
            when {
                hour < 5 -> DayPart.Night
                hour < 12 -> DayPart.Morning
                hour < 17 -> DayPart.Afternoon
                hour < 22 -> DayPart.Evening
                else -> DayPart.Night
            }
        }
    }
}
