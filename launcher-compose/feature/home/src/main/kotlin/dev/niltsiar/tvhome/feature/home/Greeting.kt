package dev.niltsiar.tvhome.feature.home

import androidx.compose.runtime.Immutable
import dev.niltsiar.tvhome.core.model.DayPart

/** The greeting line: the part of the day and, with greeting_name, whom it greets. */
@Immutable
public data class Greeting(
    val part: DayPart,
    val name: String?,
)
