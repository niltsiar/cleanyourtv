package dev.niltsiar.tvhome

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Feature code reads its look from design-system tokens: a colour, shape, gradient or alpha written in a feature is a Glass
 * value the Expressive look cannot reach. Each form is counted in every file under `feature/<name>/src/main`, previews
 * included, comments, strings and imports aside, across line breaks and in any case. A count must equal its [Allowed]
 * entries, and a file with no entry must have none, so an entry only shrinks: when a literal moves to a token, its count
 * goes down with it.
 */
val LookLiteralsTest by testSuite {
    val root = File(System.getProperty("user.dir")).parentFile

    test("the scan reads every feature source file, the settings page among them") {
        val files = featureSources(root)
        files.size shouldBeGreaterThan 50
        files.map { it.name } shouldContain "SettingsScreen.kt"
    }

    test("each forbidden form is found in any case and across a line break") {
        val samples =
            mapOf(
                LookForm.ColorLiteral to "val a = color(\n    0xff2b5cff,\n)",
                LookForm.NamedColor to "val a = Color\n    .WHITE",
                LookForm.CircleShape to "shape = circleShape",
                LookForm.RoundedCornerShape to "RoundedCornerShape\n(8.dp)",
                LookForm.CutCornerShape to "CutCornerShape (4.dp)",
                LookForm.Brush to "brush\n    .verticalGradient(x)",
                LookForm.Alpha to "graphicsLayer { Alpha\n= 0.5f }",
                LookForm.CopyAlpha to "ink.copy(\n    ALPHA = 0.4f)",
            )
        samples.keys shouldBe LookForm.entries.toSet()
        for ((form, code) in samples) {
            withClue(form.label) { form.count(code) shouldBe 1 }
        }
        withClue("comments and strings are not code") { LookForm.CircleShape.count("// CircleShape\nval s = \"CircleShape\"") shouldBe 0 }
        withClue("an import is not a use") { LookForm.CircleShape.count("import androidx.compose.foundation.shape.CircleShape") shouldBe 0 }
        withClue("an extension on Color is not a named colour") { LookForm.NamedColor.count("fun Color.withFade(f: Float)") shouldBe 0 }
        withClue("a value named alpha is not an alpha") { LookForm.Alpha.count("val alpha = fade.value") shouldBe 0 }
    }

    test("feature code has no look literal beyond its allow list") {
        val problems = mutableListOf<String>()
        val allowed = ALLOWED.groupBy { it.file }
        for (file in featureSources(root)) {
            val path = file.relativeTo(root).invariantSeparatorsPath
            val code = codeOf(file.readText())
            val entries = allowed[path].orEmpty()
            for (form in LookForm.entries) {
                val found = form.count(code)
                val expected = entries.filter { it.form == form }.sumOf { it.count }
                if (found != expected) problems += "$path: ${form.label} found $found, allowed $expected"
            }
        }
        ALLOWED
            .map { it.file }
            .distinct()
            .filter { !File(root, it).isFile }
            .forEach { problems += "$it: in the allow list but not a file" }
        problems.shouldBeEmpty()
    }
}

/** The forms a look value takes when it is written in place rather than read from a token. */
private enum class LookForm(
    val label: String,
    pattern: String,
) {
    ColorLiteral("Color(literal)", """\bColor\s*\(\s*(?:0x|[0-9]|\.[0-9]|red\s*=|alpha\s*=)"""),
    NamedColor("Color.name", """\bColor\s*\.\s*(?:Black|White|Transparent|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|DarkGray|LightGray)\b"""),
    CircleShape("CircleShape", """\bCircleShape\b"""),
    RoundedCornerShape("RoundedCornerShape(", """\bRoundedCornerShape\s*\("""),
    CutCornerShape("CutCornerShape(", """\bCutCornerShape\s*\("""),
    Brush("Brush.", """\bBrush\s*\."""),
    Alpha("alpha =", """(?<!va[lr]\s)\balpha\s*=(?!=)"""),
    CopyAlpha("copy(alpha", """\.\s*copy\s*\(\s*alpha\b"""),
    ;

    private val regex = Regex(pattern, RegexOption.IGNORE_CASE)

    fun count(source: String): Int = regex.findAll(codeOf(source)).count()
}

/** [source] with its comments, strings, characters and imports blanked, line breaks kept. */
private fun codeOf(source: String): String {
    val out = StringBuilder(source.length)
    var i = 0

    fun blank(end: Int) {
        for (j in i until end) out.append(if (source[j] == '\n') '\n' else ' ')
        i = end
    }

    fun lineEnd(): Int = source.indexOf('\n', i).let { if (it < 0) source.length else it }

    fun quoted(quote: Char): Int {
        var j = i + 1
        while (j < source.length && source[j] != quote && source[j] != '\n') j += if (source[j] == '\\') 2 else 1
        return minOf(j + 1, source.length)
    }
    while (i < source.length) {
        when {
            source.startsWith("//", i) -> blank(lineEnd())
            source.startsWith("/*", i) -> blank(source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 })
            source.startsWith("\"\"\"", i) -> blank(source.indexOf("\"\"\"", i + 3).let { if (it < 0) source.length else it + 3 })
            source[i] == '"' -> blank(quoted('"'))
            source[i] == '\'' -> blank(quoted('\''))
            source.startsWith("import ", i) && (i == 0 || source[i - 1] == '\n') -> blank(lineEnd())
            else -> out.append(source[i++])
        }
    }
    return out.toString()
}

private fun featureSources(root: File): List<File> =
    File(root, "feature")
        .listFiles()
        .orEmpty()
        .flatMap { File(it, "src/main").walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList() }
        .sortedBy { it.path }

/** A look literal a feature keeps: [count] of [form] in [file], from the Gradle root, for [reason]. */
private class Allowed(
    val file: String,
    val form: LookForm,
    val count: Int,
    val reason: String,
)

private const val CLOCK = "feature/clock/src/main/kotlin/dev/niltsiar/tvhome/feature/clock/"
private const val HOME = "feature/home/src/main/kotlin/dev/niltsiar/tvhome/feature/home/"
private const val LIBRARY = "feature/library/src/main/kotlin/dev/niltsiar/tvhome/feature/library/"
private const val SETTINGS = "feature/settings/src/main/kotlin/dev/niltsiar/tvhome/feature/settings/"

private const val PREVIEW = "a preview's stand-in picture or app art, content the TV never shows"
private const val FADE = "an opacity that animates or is computed, applied to a token colour or a layer"
private const val FOCUS_WHITE = "White as the focus_style white sentinel, which focusBand maps to each look's band"
private const val THEME_GROUND = "the theme's background colour, content, and its default when the theme names none"
private const val WALLPAPER_SCRIM =
    "a legibility scrim or text shadow over the wallpaper, black in both looks; the sky and sun gradients take their colours " +
        "from the time-of-day sky, which is content"

private val ALLOWED =
    listOf(
        Allowed("${CLOCK}ClockMotion.kt", LookForm.Brush, 1, "the screensaver glow's radial falloff; its colour is screensaverGlow"),
        Allowed("${CLOCK}ClockMotion.kt", LookForm.Alpha, 3, "the screensaver glow's falloff stops, the same in both looks"),
        Allowed("${CLOCK}ClockMotion.kt", LookForm.CopyAlpha, 3, "the screensaver glow's falloff stops, the same in both looks"),
        Allowed("${CLOCK}ClockPreviews.kt", LookForm.ColorLiteral, 4, PREVIEW),
        Allowed("${CLOCK}ClockPreviews.kt", LookForm.NamedColor, 1, PREVIEW),
        Allowed("${CLOCK}ClockPreviews.kt", LookForm.Brush, 1, PREVIEW),
        Allowed("${CLOCK}ClockPreviews.kt", LookForm.Alpha, 1, PREVIEW),
        Allowed("${CLOCK}ClockPreviews.kt", LookForm.CopyAlpha, 1, PREVIEW),
        Allowed("${CLOCK}ClockSceneState.kt", LookForm.Alpha, 1, FADE),
        Allowed("${HOME}AppShelfCards.kt", LookForm.RoundedCornerShape, 1, "a shelf card's corner, 12 dp in both looks as the tile's"),
        Allowed("${HOME}AppShelfCards.kt", LookForm.Alpha, 2, FADE),
        Allowed(
            "${HOME}CinematicBackdrop.kt",
            LookForm.ColorLiteral,
            6,
            "the cinematic backdrop's near-black scrim and the grey glow of an app with no art: the picture's ground in both looks",
        ),
        Allowed("${HOME}CinematicBackdrop.kt", LookForm.Brush, 3, "the cinematic backdrop's glow and scrim gradients"),
        Allowed("${HOME}CinematicBackdrop.kt", LookForm.Alpha, 8, "the cinematic backdrop's glow and scrim stops, and their fades"),
        Allowed("${HOME}CinematicBackdrop.kt", LookForm.CopyAlpha, 5, "the cinematic backdrop's glow and scrim stops"),
        Allowed(
            "${HOME}CinematicShelf.kt",
            LookForm.NamedColor,
            4,
            "the hero's black text shadows and white time over the darkened picture in both looks, and the Glass position " +
                "dots; the Expressive capsule reads tokens",
        ),
        Allowed("${HOME}CinematicShelf.kt", LookForm.Alpha, 5, "the hero's text shadows and fades"),
        Allowed("${HOME}CinematicShelf.kt", LookForm.CopyAlpha, 2, "the hero's text shadows"),
        Allowed(
            "${HOME}DockLook.kt",
            LookForm.ColorLiteral,
            2,
            "the Glass dock tints; the Expressive dock fills from the house scheme and reads the tint only for dark or light",
        ),
        Allowed("${HOME}DockLook.kt", LookForm.Alpha, 2, "the Glass dock tint's opacity"),
        Allowed("${HOME}DockLook.kt", LookForm.CopyAlpha, 2, "the Glass dock tint's opacity"),
        Allowed(
            "${HOME}GridEdges.kt",
            LookForm.NamedColor,
            3,
            "the grid's fade-out mask at its edges: black and clear select, they are not drawn",
        ),
        Allowed("${HOME}GridEdges.kt", LookForm.Brush, 2, "the grid's fade-out mask at its edges"),
        Allowed("${HOME}GridEdges.kt", LookForm.Alpha, 1, "the grid's fade-out mask at its edges"),
        Allowed("${HOME}GridEdges.kt", LookForm.CopyAlpha, 1, "the grid's fade-out mask at its edges"),
        Allowed("${HOME}HabitMark.kt", LookForm.Alpha, 2, FADE),
        Allowed("${HOME}HomeScreen.kt", LookForm.ColorLiteral, 2, PREVIEW),
        Allowed("${HOME}HomeScreen.kt", LookForm.NamedColor, 1, FOCUS_WHITE),
        Allowed("${HOME}HomeScreen.kt", LookForm.NamedColor, 2, "the clear top of the shade under a tile's name, whose colour is a token"),
        Allowed("${HOME}HomeScreen.kt", LookForm.CircleShape, 1, "a top-bar button's spotlight, round as the button in both looks"),
        Allowed(
            "${HOME}HomeScreen.kt",
            LookForm.RoundedCornerShape,
            2,
            "the tile (12 dp) and dock band (32 dp) corners, the same in both looks; the focused tile takes the look's focusedShape",
        ),
        Allowed("${HOME}HomeScreen.kt", LookForm.Brush, 1, PREVIEW),
        Allowed("${HOME}HomeScreen.kt", LookForm.Brush, 1, "the shade under a tile's name, from clear to its token colour"),
        Allowed("${HOME}HomeScreen.kt", LookForm.Alpha, 5, "the dock's and the grid's dims and fades"),
        Allowed("${HOME}InputsMenu.kt", LookForm.Alpha, 2, "an input's icon and an empty port dim the token ink by a share"),
        Allowed("${HOME}InputsMenu.kt", LookForm.CopyAlpha, 1, "an empty port dims the token ink by a share"),
        Allowed("${HOME}MoreCue.kt", LookForm.Alpha, 2, FADE),
        Allowed("${HOME}MoreCue.kt", LookForm.CopyAlpha, 2, FADE),
        Allowed("${HOME}ReorderLook.kt", LookForm.NamedColor, 1, "the lifted tile's shadow, black in both looks"),
        Allowed("${HOME}ReorderLook.kt", LookForm.Alpha, 1, "the lifted tile's shadow, black in both looks"),
        Allowed("${HOME}ReorderLook.kt", LookForm.Alpha, 8, FADE),
        Allowed("${HOME}ReorderLook.kt", LookForm.CopyAlpha, 1, "the lifted tile's shadow, black in both looks"),
        Allowed(
            "${HOME}RingTravel.kt",
            LookForm.NamedColor,
            3,
            "the ring travel's start values, replaced by a tile's ring before the first draw",
        ),
        Allowed("${HOME}ShelfLayout.kt", LookForm.NamedColor, 1, FOCUS_WHITE),
        Allowed("${HOME}TopBar.kt", LookForm.Alpha, 5, FADE),
        Allowed("${HOME}TopBar.kt", LookForm.CopyAlpha, 2, FADE),
        Allowed("${HOME}TrimmedEllipsisText.kt", LookForm.Alpha, 1, "hides the text while it scrolls"),
        Allowed("${HOME}UnusedFade.kt", LookForm.Alpha, 1, FADE),
        Allowed("${HOME}WallpaperBackdrop.kt", LookForm.ColorLiteral, 1, WALLPAPER_SCRIM),
        Allowed("${HOME}WallpaperBackdrop.kt", LookForm.NamedColor, 8, WALLPAPER_SCRIM),
        Allowed("${HOME}WallpaperBackdrop.kt", LookForm.Brush, 7, WALLPAPER_SCRIM),
        Allowed("${HOME}WallpaperBackdrop.kt", LookForm.Alpha, 15, WALLPAPER_SCRIM),
        Allowed("${HOME}WallpaperBackdrop.kt", LookForm.CopyAlpha, 6, WALLPAPER_SCRIM),
        Allowed("${HOME}WallpaperLayout.kt", LookForm.NamedColor, 1, THEME_GROUND),
        Allowed("${LIBRARY}LibraryCardView.kt", LookForm.Alpha, 6, FADE),
        Allowed("${LIBRARY}LibraryPreviews.kt", LookForm.ColorLiteral, 5, PREVIEW),
        Allowed("${LIBRARY}LibraryPreviews.kt", LookForm.Brush, 2, PREVIEW),
        Allowed("${LIBRARY}LibraryScreen.kt", LookForm.Alpha, 1, FADE),
        Allowed("${SETTINGS}SettingsContract.kt", LookForm.NamedColor, 1, THEME_GROUND),
        Allowed("${SETTINGS}SettingsScreen.kt", LookForm.Alpha, 4, FADE),
    )
