package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.shouldBe

val AppListRulesTest by testSuite {
    testFixture {
        object {
            val config = TvConfig(dockApps = listOf("b", "a"), hiddenApps = listOf("hidden"))

            fun candidate(
                pkg: String,
                label: String = pkg.uppercase(),
                activity: String = ".Main",
                exported: Boolean = true,
            ) = Candidate("$pkg/$activity", pkg, label, exported)

            fun arrange(
                vararg candidates: Candidate,
                config: TvConfig = this.config,
                order: Order? = null,
                tv: Set<String> = candidates.map { it.packageName }.toSet(),
                choices: LibraryChoices = LibraryChoices(added = config.libraryApps.toSet()),
                ownPackage: String = "own",
            ) = arrange(candidates.toList(), tv, config, order, ownPackage = ownPackage, choices = choices)

            fun Arrangement.keys() = tiles.map { it.candidate.packageName }
        }
    } asContextForEach {
        test("no build of this launcher gets a tile whichever one is running") {
            val builds = arrayOf(candidate("own"), candidate("own.benchmark"), candidate("owner"), candidate("com.vendor.home"))

            arrange(*builds, ownPackage = "own").keys() shouldBe listOf("com.vendor.home", "owner")
            arrange(*builds, ownPackage = "own.benchmark").keys() shouldBe listOf("com.vendor.home", "owner")
        }

        test("dock apps come first in dock apps order then the rest by name") {
            val result = arrange(candidate("z"), candidate("a"), candidate("c", "banana"), candidate("b"), candidate("d", "Apple"))

            result.keys() shouldBe listOf("b", "a", "d", "c", "z")
            result.docked shouldBe 2
        }

        test("the saved order goes before dock apps and names") {
            val order = Order(Order.dockKey(config.dockApps), listOf("z/.Main", "c/.Main"))

            val result = arrange(candidate("c"), candidate("a"), candidate("z"), candidate("b"), order = order)

            result.keys() shouldBe listOf("z", "c", "b", "a")
        }

        test("a saved order made under other dock apps is ignored") {
            val order = Order(Order.dockKey(listOf("x")), listOf("z/.Main", "c/.Main"))

            val result = arrange(candidate("c"), candidate("a"), candidate("z"), candidate("b"), order = order)

            result.keys() shouldBe listOf("b", "a", "c", "z")
        }

        test("a saved dock size decides which leading tiles are docked") {
            val order = Order(Order.dockKey(config.dockApps), listOf("z/.Main", "b/.Main", "a/.Main"), docked = 1)

            val result = arrange(candidate("a"), candidate("b"), candidate("z"), order = order)

            result.keys() shouldBe listOf("z", "b", "a")
            result.docked shouldBe 1
        }

        test("hidden unexported and own activities are not shown") {
            val result =
                arrange(candidate("hidden"), candidate("closed", exported = false), candidate("own"), candidate("shown"))

            result.keys() shouldBe listOf("shown")
        }

        test("a phone app shows only when library apps adds its package") {
            val candidates = arrayOf(candidate("phone"), candidate("tv"))
            val tv = setOf("tv")

            arrange(*candidates, tv = tv).keys() shouldBe listOf("tv")
            arrange(*candidates, tv = tv, config = config.copy(libraryApps = listOf("phone"))).keys() shouldBe listOf("phone", "tv")
        }

        test("a library app shows even when hidden apps lists it") {
            val result = arrange(candidate("hidden"), config = config.copy(libraryApps = listOf("hidden")), tv = setOf("hidden"))

            result.keys() shouldBe listOf("hidden")
        }

        test("a package the owner added in the library shows like one from library apps") {
            val candidates = arrayOf(candidate("phone"), candidate("hidden"), candidate("tv"))
            val tv = setOf("hidden", "tv")

            val result = arrange(*candidates, tv = tv, choices = LibraryChoices(added = setOf("phone", "hidden")))

            result.keys().toSet() shouldBe setOf("phone", "hidden", "tv")
        }

        test("a package the owner removed never shows whatever else adds it") {
            val candidates = arrayOf(candidate("tv"), candidate("docked", "Docked"), candidate("seeded"), candidate("kept"))
            val choices = LibraryChoices(added = setOf("seeded", "docked"), removed = setOf("tv", "docked", "seeded"))

            val result = arrange(*candidates, config = config.copy(libraryApps = listOf("seeded")), choices = choices)

            result.keys() shouldBe listOf("kept")
        }

        test("app labels rename a tile") {
            val result = arrange(candidate("c", "Cee"), config = config.copy(appLabels = mapOf("c" to "Channel")))

            result.tiles.single().label shouldBe "Channel"
        }

        test("two activities with one package and name are one tile but two names are two") {
            val result =
                arrange(
                    candidate("c", "Clock", ".First"),
                    candidate("c", "Clock", ".Second"),
                    candidate("c", "Timers", ".Third"),
                )

            result.tiles.map { it.candidate.key } shouldBe listOf("c/.First", "c/.Third")
        }

        test("a label cannot redraw itself with control characters") {
            cleanLabel("‮evil name\n", "pkg") shouldBe "evil name"
        }

        test("a label is cut at 40 characters without splitting a surrogate pair") {
            val label = "x".repeat(39) + "😀"

            cleanLabel(label, "pkg") shouldBe "x".repeat(39)
        }

        test("a label with nothing visible falls back to the package") {
            cleanLabel(" ​\n", "pkg") shouldBe "pkg"
        }
    }
}
