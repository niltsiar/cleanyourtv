package dev.niltsiar.tvhome.feature.home

import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppEntry
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.Locale

val HomeUiStateTest by testSuite {
    fun state(
        apps: List<AppEntry> = NineApps,
        now: LocalDateTime? = Evening,
        focusedPackage: String? = null,
        header: HeaderStyle = HeaderStyle(),
    ) = homeUiState(homeApps(apps), now, focusedPackage, Locale.ENGLISH, header)

    test("the time formats as clock and date") {
        val state = state()
        state.clock shouldBe "20:15"
        state.date shouldBe "Tuesday 3 November"
    }

    test("no time yet gives an empty clock and date") {
        val state = state(now = null)
        state.clock shouldBe ""
        state.date shouldBe ""
    }

    test("a focused package gives its label") {
        state(focusedPackage = "pkg.4").focusedLabel shouldBe "App 4"
    }

    test("an unknown focused package falls back to the first app") {
        state(focusedPackage = "pkg.missing").focusedLabel shouldBe "App 1"
    }

    test("nine apps split into six in the dock and three in the grid") {
        val state = state()
        state.dock shouldBe NineApps.take(6)
        state.grid shouldBe NineApps.drop(6)
    }

    test("fewer apps than the dock holds all go to the dock") {
        val state = state(apps = NineApps.take(4))
        state.dock shouldBe NineApps.take(4)
        state.grid shouldBe emptyList<Any>()
    }

    test("no apps gives an empty dock grid and label") {
        val state = state(apps = emptyList(), focusedPackage = "pkg.1")
        state.dock shouldBe emptyList<Any>()
        state.grid shouldBe emptyList<Any>()
        state.focusedLabel shouldBe ""
    }

    test("dock apps found come first and the rest of the row fills up") {
        val apps = homeApps(docked = 2)
        apps.dock shouldBe NineApps.take(6)
        apps.grid shouldBe NineApps.drop(6)
    }

    test("fill dock off leaves the free dock slots empty") {
        val apps = homeApps(docked = 2, config = TvConfig(columns = 6, fillDock = false))
        apps.dock shouldBe NineApps.take(2)
        apps.grid shouldBe NineApps.drop(2)
    }

    test("fill dock off with no dock app installed still fills the first row") {
        val apps = homeApps(docked = 0, config = TvConfig(columns = 6, fillDock = false))
        apps.dock shouldBe NineApps.take(6)
    }

    test("the columns are the dock size") {
        val apps = homeApps(config = TvConfig(columns = 4))
        apps.dock shouldBe NineApps.take(4)
        apps.grid shouldBe NineApps.drop(4)
    }

    test("a single row holds every app in the dock") {
        val apps = homeApps(config = TvConfig(columns = 4, singleRow = true))
        apps.dock shouldBe NineApps
        apps.grid shouldBe emptyList<Any>()
    }

    test("empty dock slots follow the reference rules") {
        emptyDockSlots(docked = 3, dockSize = 4, tiles = 20, fill = false) shouldBe 1
        emptyDockSlots(docked = 3, dockSize = 4, tiles = 20, fill = true) shouldBe 0
        emptyDockSlots(docked = 4, dockSize = 4, tiles = 20, fill = false) shouldBe 0
        emptyDockSlots(docked = 0, dockSize = 4, tiles = 20, fill = false) shouldBe 0
        emptyDockSlots(docked = 2, dockSize = 4, tiles = 2, fill = false) shouldBe 0
    }

    test("the twelve hour clock has no leading zero and an am pm marker") {
        val state = state(header = HeaderStyle(hour24 = false))
        (state.clock to state.amPm) shouldBe ("8:15" to "PM")
        val midnight = state(now = Evening.withHour(0).withMinute(5), header = HeaderStyle(hour24 = false))
        (midnight.clock to midnight.amPm) shouldBe ("12:05" to "AM")
        state().amPm shouldBe ""
    }

    test("the date follows the pattern from the resources") {
        state(header = HeaderStyle(datePattern = "EEEE  d MMMM")).date shouldBe "Tuesday  3 November"
    }

    test("the greeting names the part of the day and the configured name") {
        state(header = HeaderStyle(greetingName = "Sam")).greeting shouldBe Greeting(DayPart.Evening, "Sam")
        state().greeting shouldBe Greeting(DayPart.Evening, null)
    }

    test("show greeting false and no time both leave no greeting") {
        state(header = HeaderStyle(showGreeting = false)).greeting shouldBe null
        state(now = null).greeting shouldBe null
    }
}
