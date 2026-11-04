package dev.niltsiar.tvhome.core.config

/** The files [ThemeProvider] may write or delete under the theme directory, by the URI's path segments. */
internal object ThemeFiles {
    const val CONFIG_JSON = "config.json"
    const val WALLPAPER_JPG = "wallpaper.jpg"
    const val THEME_REVISION = "theme_revision"
    const val WALLPAPERS = "wallpapers"
    const val CAPTIONS = "captions.txt"

    /** The names tools/theme.sh accepts for a collection image. (?i) is ASCII-only, as the reference's Pattern. */
    private val image = Regex("(?i)[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)")

    /** The path relative to the theme directory, such as "config.json" or "wallpapers/dusk.jpg", or null if refused. */
    fun path(segments: List<String>?): String? =
        when {
            segments == null -> {
                null
            }

            segments.size == 1 -> {
                segments[0].takeIf { it == CONFIG_JSON || it == WALLPAPER_JPG || it == THEME_REVISION }
            }

            segments.size == 2 && segments[0] == WALLPAPERS && (collectionImage(segments[1]) || segments[1] == CAPTIONS) -> {
                "$WALLPAPERS/${segments[1]}"
            }

            else -> {
                null
            }
        }

    /** True for "wallpapers" alone: a delete of it clears the collection's images. */
    fun isCollection(segments: List<String>?): Boolean = segments?.singleOrNull() == WALLPAPERS

    fun collectionImage(name: String?): Boolean = name != null && !name.startsWith(".") && image.matches(name)
}
