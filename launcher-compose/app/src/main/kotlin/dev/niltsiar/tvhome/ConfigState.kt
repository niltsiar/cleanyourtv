package dev.niltsiar.tvhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import dev.niltsiar.tvhome.core.config.TvConfig

/** What [read] makes of the config: it changes only when that value does, so an unrelated choice invalidates nothing that reads it. */
@Composable
internal fun <T> State<TvConfig>.derived(read: TvConfig.() -> T): State<T> = remember(this) { derivedStateOf { value.read() } }
