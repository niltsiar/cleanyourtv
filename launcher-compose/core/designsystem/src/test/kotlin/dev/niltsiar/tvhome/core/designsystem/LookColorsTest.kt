package dev.niltsiar.tvhome.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.Look
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan

/** The element colours of [LookColors] in Expressive, each read as a draw reads it. */
val LookColorsTest by testSuite {
    val tokens = LookColors::class.java.methods.filter { it.parameterCount == 0 && it.returnType == ColorProducer::class.java }

    for (seed in listOf(Color(0xFFFF6A3D), Color(0xFF2F9FD8))) {
        test("in Expressive with seed ${seed.value.toString(16).take(8)} no element colour on either surface is a house tertiary role") {
            tokens.size shouldBeGreaterThan 40
            val look = LookState(Look.Expressive) { seed }
            val sets = lookColorSets(look)
            val tertiary =
                listOf(true, false)
                    .flatMap { dark -> look.scheme(dark).run { listOf(tertiary, onTertiary, tertiaryContainer, onTertiaryContainer) } }
                    .toSet()
                    // Tones 0 and 100 of every palette are black and white, which carry no hue.
                    .minus(setOf(Color.Black, Color.White))
            for ((surface, colours) in listOf("dark" to sets.dark, "light" to sets.light)) {
                val hits = tokens.filter { (it.invoke(colours) as ColorProducer)().copy(alpha = 1f) in tertiary }.map { it.name }
                withClue("on the $surface surface") { hits.shouldBeEmpty() }
            }
        }
    }
}
