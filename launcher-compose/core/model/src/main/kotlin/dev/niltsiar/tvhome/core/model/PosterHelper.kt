package dev.niltsiar.tvhome.core.model

/** Whether the poster helper app can serve pictures from the web to Home (app_shelf_web_posters). */
public enum class PosterHelper {
    /** No helper serves the poster authority, or another package does. */
    Missing,

    /** The helper is installed but signed with a key other than Home's, so Home sends it nothing. */
    OtherKey,

    /** The helper is installed and signed with Home's key. */
    Ready,
}
