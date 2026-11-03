package dev.niltsiar.tvhome.core.model

import kotlinx.coroutines.flow.Flow

public interface AppsProvider {
    /** The launchable apps, one entry per package. May emit again once tile images are ready. */
    public val apps: Flow<List<AppEntry>>
}
