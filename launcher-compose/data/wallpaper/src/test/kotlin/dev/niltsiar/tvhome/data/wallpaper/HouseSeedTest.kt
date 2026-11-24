package dev.niltsiar.tvhome.data.wallpaper

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.DayPart
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

val HouseSeedTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    val seed = 0xFF336699.toInt()
    val glow = 0xFFE0A070.toInt()
    val evening = LocalDateTime.of(2026, 11, 3, 20, 15)
    val english = TvConfig(locale = Locale.ENGLISH)

    test("the seed key wins over the wallpaper the sky and the time of day") {
        houseSeed(english.copy(expressiveSeed = seed), hue = 200f, skyGlow = glow, part = DayPart.Night) shouldBe Color(seed)
    }

    test("without the key the wallpaper's hue wins over the sky and the time of day") {
        houseSeed(english, hue = 200f, skyGlow = glow, part = DayPart.Night) shouldBe Color.hsv(200f, 0.6f, 0.8f)
    }

    test("without a hue the sky's glow wins over the time of day") {
        houseSeed(english, hue = null, skyGlow = glow, part = DayPart.Night) shouldBe Color(glow)
    }

    test("with nothing else the part of the day gives the seed") {
        houseSeed(english, hue = null, skyGlow = null, part = DayPart.Night) shouldBe NightTeal
    }

    test("the sky's glow counts only while sky intensity is on") {
        val skyOn = english.copy(skyIntensity = 40)
        houseSeed(skyOn, null, evening, ZoneOffset.UTC) shouldBe Color(sky(skyOn, evening, ZoneOffset.UTC).glow)
        houseSeed(english, null, evening, ZoneOffset.UTC) shouldBe EveningMagenta
    }

    test("the part of the day is the one of the launcher's language") {
        // At 20:15 Spain's Spanish still says good afternoon.
        houseSeed(TvConfig(locale = Locale.forLanguageTag("es-ES")), null, evening, ZoneOffset.UTC) shouldBe AfternoonPeriwinkle
    }

    test("each part of the day has its own seed in the hue its name says") {
        val hues =
            mapOf(
                DayPart.Night to 170f..190f,
                DayPart.Morning to 30f..45f,
                DayPart.Forenoon to 195f..215f,
                DayPart.Afternoon to 225f..245f,
                DayPart.Evening to 310f..335f,
            )
        DayPart.entries.map { it.seed() }.shouldBeUnique()
        for ((part, range) in hues) {
            val hue = WallpaperHue.dominant(intArrayOf(part.seed().toArgb()))!!
            withClue(part) { hue.shouldBeBetween(range.start, range.endInclusive, 0f) }
        }
    }

    test("the seeds follow the clock and the wallpaper's hue and skip a tick with no time") {
        with(testScope) {
            val clock = MutableStateFlow<LocalDateTime?>(null)
            val hue = MutableStateFlow<Float?>(null)
            houseSeeds(MutableStateFlow(english), clock, hue) { ZoneOffset.UTC }.test {
                expectNoEvents()
                clock.value = evening
                awaitItem() shouldBe EveningMagenta
                hue.value = 200f
                awaitItem() shouldBe Color.hsv(200f, 0.6f, 0.8f)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
