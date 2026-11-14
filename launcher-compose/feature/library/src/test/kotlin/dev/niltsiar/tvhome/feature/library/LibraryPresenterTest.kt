package dev.niltsiar.tvhome.feature.library

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.OpenedAt
import dev.niltsiar.tvhome.core.model.OpenedRecord
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runCurrent
import java.util.Locale

val LibraryPresenterTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<LibraryEvent>(Channel.UNLIMITED)
            val effects = Channel<LibraryEffect>(Channel.UNLIMITED)

            val catalogue =
                listOf(
                    libraryApp("Zeta"),
                    libraryApp("Home", onHome = true),
                    libraryApp("Alpha"),
                    libraryApp("Fresh", installedAt = NOW - DAY),
                    libraryApp("Beta"),
                )

            fun presenter(
                library: FakeLibrary,
                config: TvConfig = TvConfig(appLibrary = true),
            ): Flow<LibraryUiState> =
                moleculeFlow(RecompositionMode.Immediate) {
                    libraryPresenter(events.receiveAsFlow(), library.sources, config, effects::trySend, Locale.ENGLISH)
                }

            fun LibraryUiState.labels() = cards.map { it.app.label }

            val leastUsed = TvConfig(appLibrary = true, libraryLeastUsed = true)

            /** Fresh and Home were opened, Beta and Alpha never; the record began 90 days ago. */
            fun opened() =
                OpenedRecord(
                    times = mapOf(key("Home") to NOW - 2 * DAY, key("Fresh") to NOW - 30 * DAY),
                    since = NOW - 90 * DAY,
                )
        }
    } asContextForEach {
        test("nothing shows until the library is asked to open and its catalogue loaded") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { gate = CompletableDeferred() }
                presenter(library).test {
                    awaitItem() shouldBe LibraryUiState()
                    events.send(LibraryEvent.Open)
                    val opening = awaitMatching { it.open }
                    opening.shown.shouldBeFalse()
                    opening.cards shouldBe emptyList<LibraryCard>()

                    library.gate?.complete(Unit)
                    val shown = awaitMatching { it.shown }

                    shown.labels() shouldBe listOf("Fresh", "Alpha", "Beta", "Zeta", "Home")
                    shown.cards.map { it.recent } shouldBe listOf(true, false, false, false, false)
                    shown.cards.map { it.onHome } shouldBe listOf(false, false, false, false, true)
                    shown.focusedKey shouldBe key("Fresh")
                    shown.focusRequest?.key shouldBe key("Fresh")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a package change while the catalogue loads still shows the panel once") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { gate = CompletableDeferred() }
                presenter(library).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.open }
                    runCurrent()
                    library.apps = catalogue + libraryApp("Gamma")
                    library.changes.emit(1)
                    runCurrent()
                    library.gate?.complete(Unit)

                    val first = awaitMatching { it.shown }
                    library.apps = catalogue
                    library.changes.emit(2)
                    val later = awaitMatching { it.cards.size == 5 }

                    first.cards.size shouldBe 6
                    later.shown.shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a refresh after the panel shows keeps focus on the same app and the menu closes") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue)
                library.appInfoFor = setOf("pkg.Beta")
                presenter(library).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }
                    events.send(LibraryEvent.Focus(key("Beta")))
                    events.send(LibraryEvent.Hold(key("Beta")))
                    awaitMatching { it.menu != null }
                    runCurrent()

                    library.apps = catalogue + libraryApp("Aaa")
                    library.changes.emit(1)
                    val refreshed = awaitMatching { it.cards.size == 6 }

                    refreshed.menu shouldBe null
                    refreshed.focusedKey shouldBe key("Beta")
                    refreshed.focusRequest?.key shouldBe key("Beta")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("ok opens an app on home and closes the library") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue)
                presenter(library).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }

                    events.send(LibraryEvent.Click(key("Home")))
                    val closed = awaitMatching { !it.open }

                    effects.receive() shouldBe LibraryEffect.LaunchApp(catalogue[1].app)
                    closed.cards shouldBe emptyList<LibraryCard>()
                    library.released shouldBe 1
                    library.saved shouldBe (emptyList<Pair<String, Boolean>>())
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("ok on an app not on home adds it and focus moves to the next app that is not") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue)
                presenter(library).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }
                    events.send(LibraryEvent.Focus(key("Alpha")))

                    events.send(LibraryEvent.Click(key("Alpha")))
                    val added = awaitMatching { it.cards.firstOrNull { card -> card.app.label == "Alpha" }?.onHome == true }

                    library.saved shouldBe listOf("pkg.Alpha" to true)
                    added.labels() shouldBe listOf("Fresh", "Beta", "Zeta", "Alpha", "Home")
                    added.focusedKey shouldBe key("Beta")
                    added.focusRequest?.key shouldBe key("Beta")
                    effects.tryReceive().isFailure shouldBe true
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a choice that was not saved says so and leaves the apps as they were") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { saveWorks = false }
                presenter(library).test {
                    events.send(LibraryEvent.Open)
                    val shown = awaitMatching { it.shown }

                    events.send(LibraryEvent.Click(key("Fresh")))

                    effects.receive() shouldBe LibraryEffect.SaveFailed
                    val after = awaitMatching { it.focusRequest?.token != shown.focusRequest?.token }
                    library.saved shouldBe listOf("pkg.Fresh" to true)
                    after.cards shouldBe shown.cards
                    after.focusRequest?.key shouldBe key("Fresh")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("library ok opens makes ok open every app") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue)
                presenter(library, TvConfig(appLibrary = true, libraryOkOpens = true)).test {
                    events.send(LibraryEvent.Open)
                    val shown = awaitMatching { it.shown }
                    shown.hint shouldBe LibraryHint.Open

                    events.send(LibraryEvent.Click(key("Zeta")))

                    effects.receive() shouldBe LibraryEffect.LaunchApp(catalogue[0].app)
                    library.saved shouldBe (emptyList<Pair<String, Boolean>>())
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("selection mode lists apps by label toggles with ok in place and has no menu") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue)
                presenter(library, TvConfig(appLibrary = true, appLibrarySelection = true, libraryOkOpens = true)).test {
                    events.send(LibraryEvent.Open)
                    val shown = awaitMatching { it.shown }
                    shown.labels() shouldBe listOf("Alpha", "Beta", "Fresh", "Home", "Zeta")
                    shown.cards.map { it.recent } shouldBe listOf(false, false, false, false, false)
                    shown.hint shouldBe LibraryHint.SelectAdd

                    events.send(LibraryEvent.Focus(key("Home")))
                    events.send(LibraryEvent.Hold(key("Home")))
                    events.send(LibraryEvent.Click(key("Home")))
                    val removed = awaitMatching { it.cards.firstOrNull { card -> card.app.label == "Home" }?.onHome == false }

                    library.saved shouldBe listOf("pkg.Home" to false)
                    removed.menu shouldBe null
                    removed.labels() shouldBe listOf("Alpha", "Beta", "Fresh", "Home", "Zeta")
                    removed.focusedKey shouldBe key("Home")
                    removed.focusRequest?.key shouldBe key("Home")
                    removed.hint shouldBe LibraryHint.SelectAdd
                    effects.tryReceive().isFailure shouldBe true
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("selection mode keeps focus and every card in place when it adds an app") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue)
                presenter(library, TvConfig(appLibrary = true, appLibrarySelection = true)).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }

                    events.send(LibraryEvent.Click(key("Zeta")))
                    val added = awaitMatching { it.cards.firstOrNull { card -> card.app.label == "Zeta" }?.onHome == true }

                    added.labels() shouldBe listOf("Alpha", "Beta", "Fresh", "Home", "Zeta")
                    added.focusRequest?.key shouldBe key("Zeta")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a held ok opens a menu by whether the app is on home and whether the tv has an info page") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { appInfoFor = setOf("pkg.Home") }
                presenter(library).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }

                    events.send(LibraryEvent.Hold(key("Home")))
                    awaitMatching { it.menu != null }.menu shouldBe
                        LibraryMenu(key("Home"), listOf(LibraryRow.Open, LibraryRow.RemoveFromHome, LibraryRow.AppInfo))
                    events.send(LibraryEvent.Back)
                    awaitMatching { it.menu == null }

                    events.send(LibraryEvent.Hold(key("Alpha")))
                    awaitMatching { it.menu != null }.menu shouldBe
                        LibraryMenu(key("Alpha"), listOf(LibraryRow.Open, LibraryRow.AddToHome))
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("back closes the menu and asks for focus on its card then the next back closes the library") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue)
                presenter(library).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }
                    events.send(LibraryEvent.Hold(key("Beta")))
                    awaitMatching { it.menu != null }

                    events.send(LibraryEvent.Back)
                    val menuClosed = awaitMatching { it.menu == null }
                    menuClosed.open.shouldBeTrue()
                    menuClosed.focusRequest?.key shouldBe key("Beta")

                    events.send(LibraryEvent.Back)
                    awaitMatching { !it.open }.shown.shouldBeFalse()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the menu rows open the app change its shortcut or open its info page") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { appInfoFor = setOf("pkg.Alpha") }
                presenter(library).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }

                    events.send(LibraryEvent.Hold(key("Alpha")))
                    awaitMatching { it.menu != null }
                    events.send(LibraryEvent.Pick(LibraryRow.AddToHome))
                    awaitMatching { it.cards.firstOrNull { card -> card.app.label == "Alpha" }?.onHome == true }
                    library.saved shouldBe listOf("pkg.Alpha" to true)

                    events.send(LibraryEvent.Hold(key("Alpha")))
                    awaitMatching { it.menu != null }
                    events.send(LibraryEvent.Pick(LibraryRow.RemoveFromHome))
                    awaitMatching { it.cards.firstOrNull { card -> card.app.label == "Alpha" }?.onHome == false }
                    library.saved shouldBe listOf("pkg.Alpha" to true, "pkg.Alpha" to false)

                    events.send(LibraryEvent.Hold(key("Alpha")))
                    awaitMatching { it.menu != null }
                    events.send(LibraryEvent.Pick(LibraryRow.AppInfo))
                    awaitMatching { !it.open }
                    effects.receive() shouldBe LibraryEffect.OpenAppInfo("pkg.Alpha")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("closing and opening again loads the catalogue again and shows the panel again") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue)
                presenter(library).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }
                    events.send(LibraryEvent.Close)
                    awaitMatching { !it.open }.shown.shouldBeFalse()

                    events.send(LibraryEvent.Open)
                    val again = awaitMatching { it.shown }

                    library.loads shouldBe 2
                    again.cards.size shouldBe 5
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("an empty catalogue still shows the panel with no focus") {
            with(it.testScope) {
                val library = FakeLibrary(emptyList())
                presenter(library).test {
                    events.send(LibraryEvent.Open)

                    val shown = awaitMatching { it.shown }

                    shown.cards shouldBe emptyList<LibraryCard>()
                    shown.focusRequest shouldBe null
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the least used order lists apps never opened first then the oldest open with ties by label") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { record = opened() }
                presenter(library, leastUsed).test {
                    events.send(LibraryEvent.Open)
                    val usual = awaitMatching { it.shown }
                    usual.order shouldBe LibraryOrder.Usual
                    usual.labels() shouldBe listOf("Fresh", "Alpha", "Beta", "Zeta", "Home")

                    events.send(LibraryEvent.ToggleOrder)
                    val least = awaitMatching { it.order == LibraryOrder.LeastUsed }

                    least.labels() shouldBe listOf("Alpha", "Beta", "Zeta", "Fresh", "Home")
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("in the least used order a card says when its app was opened or that it never was") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { record = opened() }
                presenter(library, leastUsed).test {
                    events.send(LibraryEvent.Open)
                    val usual = awaitMatching { it.shown }
                    usual.cards.map { it.opened } shouldBe List(5) { null }

                    events.send(LibraryEvent.ToggleOrder)
                    val least = awaitMatching { it.order == LibraryOrder.LeastUsed }

                    least.cards.map { it.opened } shouldBe
                        listOf(
                            CardOpened(null),
                            CardOpened(null),
                            CardOpened(null),
                            CardOpened(OpenedAt(NOW - 30 * DAY, NOW, hour24 = true)),
                            CardOpened(OpenedAt(NOW - 2 * DAY, NOW, hour24 = true)),
                        )
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("when no app of the catalogue has an open time the least used order says so and no card has an opened line") {
            with(it.testScope) {
                val gone = OpenedRecord(times = mapOf(key("Gone") to NOW - DAY), since = NOW - 90 * DAY)
                val library = FakeLibrary(catalogue).apply { record = gone }
                presenter(library, leastUsed).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }.noUsageYet.shouldBeFalse()

                    events.send(LibraryEvent.ToggleOrder)
                    val least = awaitMatching { it.order == LibraryOrder.LeastUsed }

                    least.noUsageYet.shouldBeTrue()
                    least.cards.map { it.opened } shouldBe List(5) { null }
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("one open time among the catalogue keeps the opened lines and the header quiet") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { record = opened() }
                presenter(library, leastUsed).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }

                    events.send(LibraryEvent.ToggleOrder)
                    val least = awaitMatching { it.order == LibraryOrder.LeastUsed }

                    least.noUsageYet.shouldBeFalse()
                    least.cards.all { it.opened != null }.shouldBeTrue()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the other order keeps each cards status and the usual order shows no opened line") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { record = opened() }
                presenter(library, leastUsed).test {
                    events.send(LibraryEvent.Open)
                    val usual = awaitMatching { it.shown }
                    events.send(LibraryEvent.ToggleOrder)
                    val least = awaitMatching { it.order == LibraryOrder.LeastUsed }
                    events.send(LibraryEvent.ToggleOrder)
                    val back = awaitMatching { it.order == LibraryOrder.Usual }

                    least.cards.associate {
                        it.app.label to
                            (it.onHome to it.recent)
                    } shouldBe usual.cards.associate { it.app.label to (it.onHome to it.recent) }
                    back.cards shouldBe usual.cards
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("switching the order keeps focus on the same app and asks the grid to scroll to it again") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { record = opened() }
                presenter(library, leastUsed).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }
                    events.send(LibraryEvent.Focus(key("Zeta")))
                    val before = awaitMatching { it.focusedKey == key("Zeta") }

                    events.send(LibraryEvent.ToggleOrder)
                    val least = awaitMatching { it.order == LibraryOrder.LeastUsed }

                    least.focusedKey shouldBe key("Zeta")
                    least.focusRequest?.key shouldBe key("Zeta")
                    least.focusRequest!!.token shouldNotBe before.focusRequest?.token
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("a package change while the least used order is on keeps that order and closing resets it") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { record = opened() }
                presenter(library, leastUsed).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }
                    events.send(LibraryEvent.ToggleOrder)
                    awaitMatching { it.order == LibraryOrder.LeastUsed }
                    runCurrent()

                    library.apps = catalogue + libraryApp("Aaa")
                    library.changes.emit(1)
                    val refreshed = awaitMatching { it.cards.size == 6 }
                    refreshed.order shouldBe LibraryOrder.LeastUsed
                    refreshed.labels().first() shouldBe "Aaa"

                    events.send(LibraryEvent.Close)
                    awaitMatching { !it.open }
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }.order shouldBe LibraryOrder.Usual
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("the order does not switch while a card menu is open") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { record = opened() }
                presenter(library, leastUsed).test {
                    events.send(LibraryEvent.Open)
                    awaitMatching { it.shown }
                    events.send(LibraryEvent.Hold(key("Beta")))
                    awaitMatching { it.menu != null }

                    events.send(LibraryEvent.ToggleOrder)
                    events.send(LibraryEvent.Back)
                    val closed = awaitMatching { it.menu == null }

                    closed.order shouldBe LibraryOrder.Usual
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        test("with library least used off the order event changes nothing and the record is never read") {
            with(it.testScope) {
                val library = FakeLibrary(catalogue).apply { record = opened() }
                presenter(library).test {
                    events.send(LibraryEvent.Open)
                    val shown = awaitMatching { it.shown }

                    events.send(LibraryEvent.ToggleOrder)
                    events.send(LibraryEvent.Focus(key("Beta")))
                    val later = awaitMatching { it.focusedKey == key("Beta") }

                    later.cards shouldBe shown.cards
                    later.order shouldBe LibraryOrder.Usual
                    library.recordReads shouldBe 0
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
}
