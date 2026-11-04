package dev.niltsiar.tvhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A translation that moves a format specifier to another conversion, or names an argument the English string lacks,
 * throws on the TV set to that language and nowhere else. aapt2 and lint do not read the specifiers.
 */
class StringResourcesTest {
    private val modules = listOf("app", "feature/home", "feature/library", "service/keys")
    private val root = File(System.getProperty("user.dir")).parentFile
    private val comment = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val string = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val plurals = Regex("""<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
    private val item = Regex("""<item\s+quantity="([^"]+)"[^>]*>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    private val specifier = Regex("""%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])""")

    /** Resource name (a plural item as "name/quantity") to its text, comments aside. */
    private fun read(file: File): Map<String, String> {
        val text = comment.replace(file.readText(), "")
        val out = mutableMapOf<String, String>()
        string.findAll(text).forEach { out[it.groupValues[1]] = it.groupValues[2] }
        plurals.findAll(text).forEach { block ->
            item.findAll(block.groupValues[2]).forEach { out["${block.groupValues[1]}/${it.groupValues[1]}"] = it.groupValues[2] }
        }
        return out
    }

    /** Argument position to conversion letter, as Java's Formatter reads them: `%s` counts apart from `%2$s`. */
    private fun conversions(text: String): Map<Int, Char> {
        var implicit = 0
        val out = mutableMapOf<Int, Char>()
        specifier.findAll(text).forEach {
            val conversion = it.groupValues[2].single()
            if (conversion == '%') return@forEach
            val position = it.groupValues[1].toIntOrNull() ?: ++implicit
            out.putIfAbsent(position, conversion)
        }
        return out
    }

    private fun problems(): Pair<List<String>, Int> {
        val problems = mutableListOf<String>()
        var checked = 0
        for (module in modules) {
            val res = File(root, "$module/src/main/res")
            val english = read(File(res, "values/strings.xml"))
            val folders = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty().sortedBy { it.name }
            for (folder in folders) {
                for ((name, text) in read(File(folder, "strings.xml"))) {
                    val page = "$module/${folder.name}"
                    val source = english[name]
                    if (source == null) {
                        // A plural quantity only this language's rules need (few, many, zero, two) has no English item.
                        if (!name.contains('/')) problems += "$page: `$name` is not a string the default has"
                        continue
                    }
                    checked++
                    val takes = conversions(source)
                    for ((position, conversion) in conversions(text)) {
                        if (takes[position] != conversion) {
                            problems += "$page: `$name` gives argument $position as %$conversion, the default takes ${takes[position]}"
                        }
                    }
                }
            }
        }
        return problems to checked
    }

    @Test
    fun every_translation_takes_the_arguments_the_default_string_takes() {
        val (problems, checked) = problems()

        assertTrue("the translated strings were not read", checked >= 300)
        assertEquals(emptyList<String>(), problems)
    }
}
