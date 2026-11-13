package dev.niltsiar.tvhome.core.model

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

val LastOpenedTextTest by testSuite {
    robolectricTestSuite<LastOpenedTextTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
    robolectricTestSuite<LastOpenedTextSpanishTestContent>(
        "sdk 31, es-rES",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "es-rES"
            },
    )
}

private val zone = ZoneId.systemDefault()
private val now = LocalDateTime.of(2026, 11, 3, 20, 15)

private fun millis(time: LocalDateTime) = time.atZone(zone).toInstant().toEpochMilli()

private fun describe(
    opened: LocalDateTime,
    hour24: Boolean = false,
    at: LocalDateTime = now,
    locale: Locale = Locale.ENGLISH,
) = describeLastOpened(RuntimeEnvironment.getApplication(), OpenedAt(millis(opened), millis(at), hour24), locale, zone)

class LastOpenedTextTestContent :
    RobolectricTestSuiteContent({
        test("within the hour it counts minutes") {
            describe(now.minusMinutes(5)) shouldBe "5 minutes ago"
            describe(now.minusMinutes(59).minusSeconds(30)) shouldBe "59 minutes ago"
        }

        test("under a minute and the future read as one minute") {
            describe(now.minusSeconds(20)) shouldBe "1 minute ago"
            describe(now.plusMinutes(10)) shouldBe "1 minute ago"
        }

        test("from the hour on it names the day and the time") {
            describe(now.minusHours(2)) shouldBe "today, 6:15 PM"
            describe(LocalDateTime.of(2026, 11, 2, 21, 40)) shouldBe "yesterday, 9:40 PM"
            describe(LocalDateTime.of(2026, 10, 31, 9, 5)) shouldBe "3 days ago, 9:05 AM"
        }

        test("the day follows the calendar not the 24 hour count") {
            val justAfterMidnight = LocalDateTime.of(2026, 11, 4, 0, 30)

            describe(LocalDateTime.of(2026, 11, 3, 1, 30), at = justAfterMidnight) shouldBe "yesterday, 1:30 AM"
        }

        test("a week or more gives the date and a year only when it is another one") {
            describe(LocalDateTime.of(2026, 10, 12, 9, 5)) shouldBe "Oct 12, 9:05 AM"
            describe(LocalDateTime.of(2025, 12, 30, 23, 59)) shouldBe "Dec 30, 2025, 11:59 PM"
        }

        test("the time follows the 12 or 24 hour clock") {
            val noon = LocalDateTime.of(2026, 11, 3, 12, 0)
            val midnight = LocalDateTime.of(2026, 11, 3, 0, 10)

            describe(noon) shouldBe "today, 12:00 PM"
            describe(midnight) shouldBe "today, 12:10 AM"
            describe(noon, hour24 = true) shouldBe "today, 12:00"
            describe(midnight, hour24 = true) shouldBe "today, 00:10"
        }
    })

class LastOpenedTextSpanishTestContent :
    RobolectricTestSuiteContent({
        test("spanish gets the words of the tvs language") {
            val spanish = Locale.forLanguageTag("es-ES")
            Locale.setDefault(spanish)

            describe(now.minusMinutes(5), locale = spanish) shouldBe "hace 5 minutos"
            describe(LocalDateTime.of(2026, 11, 2, 21, 40), hour24 = true, locale = spanish) shouldBe "ayer, 21:40"
        }
    })
