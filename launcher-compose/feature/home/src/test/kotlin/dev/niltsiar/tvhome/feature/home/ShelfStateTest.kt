package dev.niltsiar.tvhome.feature.home

import androidx.compose.ui.graphics.Color
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.OpenedAt
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

val ShelfStateTest by testSuite {
    testFixture {
        object {
            val apps = NineApps.mapIndexed { i, app -> app.copy(accent = Color(0xFF000000 or (i + 1L) * 0x111111)) }

            fun shelf(
                focusedKey: String?,
                cinematic: Boolean = true,
                lastOpened: Map<String, Long> = emptyMap(),
                now: LocalDateTime? = Evening,
                list: List<AppEntry> = apps,
            ) = homeUiState(
                homeApps(list),
                now,
                focusedKey,
                Locale.ENGLISH,
                cinematic = cinematic,
                lastOpened = lastOpened,
                zone = ZoneOffset.UTC,
            ).shelf
        }
    } asContextForEach {
        test("without cinematic home there is no shelf") {
            shelf("pkg.2", cinematic = false).shouldBeNull()
        }

        test("a focused dock app fills the shelf with its label colour and place among the dots") {
            val shelf = shelf("pkg.3")!!

            shelf.key shouldBe "pkg.3"
            shelf.label shouldBe "App 3"
            shelf.accent shouldBe apps[2].accent
            shelf.position shouldBe 2
            shelf.count shouldBe 6
        }

        test("a grid app has no shelf") {
            shelf("pkg.7").shouldBeNull()
        }

        test("before any focus or with a gone app the first dock app is shown") {
            shelf(null)!!.key shouldBe "pkg.1"
            shelf("pkg.gone")!!.key shouldBe "pkg.1"
        }

        test("a dock with fewer apps has one dot per app") {
            shelf("pkg.2", list = apps.take(3))!!.count shouldBe 3
        }

        test("the last opened line is the relative time of that tile only") {
            val now = Evening.toEpochSecond(ZoneOffset.UTC) * 1000L
            val fiveMinutesAgo = now - 5 * 60_000L

            shelf("pkg.2", lastOpened = mapOf("pkg.2" to fiveMinutesAgo))!!.lastOpened shouldBe OpenedAt(fiveMinutesAgo, now, hour24 = true)
            shelf("pkg.3", lastOpened = mapOf("pkg.2" to fiveMinutesAgo))!!.lastOpened.shouldBeNull()
        }

        test("the last opened line waits for a valid time") {
            shelf("pkg.2", lastOpened = mapOf("pkg.2" to 1_000L), now = null)!!.lastOpened.shouldBeNull()
        }

        test("an app is never opened only without a record even while the time is not valid") {
            shelf("pkg.2")!!.neverOpened shouldBe true
            shelf("pkg.2", lastOpened = mapOf("pkg.2" to 1_000L), now = null)!!.neverOpened shouldBe false
            shelf("pkg.2", lastOpened = mapOf("pkg.2" to 1_000L))!!.neverOpened shouldBe false
        }
    }
}
