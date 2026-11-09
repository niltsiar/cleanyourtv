package dev.niltsiar.tvhome.core.model

import androidx.compose.runtime.Immutable

/** An app the Open on start picker offers: it has a TV entry point. */
@Immutable
public data class OpenOnStartApp(
    val label: String,
    val packageName: String,
)
