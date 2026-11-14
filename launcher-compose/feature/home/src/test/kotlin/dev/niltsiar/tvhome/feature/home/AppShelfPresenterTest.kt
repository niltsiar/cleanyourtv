package dev.niltsiar.tvhome.feature.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.util.Locale

private fun item(title: String) = ShelfItem(packageName = "pkg.2", title = title, kind = ShelfKind.Continue)

/** app_shelf on the presenter: when a dock tile's shelf is read, what it shows, the cache, and OK on a card. */
val AppShelfPresenterTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    testFixture {
        object {
            val events = Channel<HomeEvent>(Channel.UNLIMITED)
            val effects = mutableListOf<HomeEffect>()
            val reads = mutableListOf<String>()
            val published = mapOf("pkg.2" to listOf(item("Severance"), item("Andor")), "pkg.3" to emptyList())
            val config = MutableStateFlow(SixColumns.copy(appShelf = true, appShelfSources = listOf("pkg.2", "pkg.3/history", "pkg.8")))

            fun TestScope.home(): StateFlow<HomeUiState> {
                val sources =
                    sources(shelfItems = { pkg ->
                        reads += pkg
                        published[pkg].orEmpty()
                    })
                val flow =
                    moleculeFlow(RecompositionMode.Immediate) {
                        homePresenter(events.receiveAsFlow(), sources, config, { effects += it }, Locale.ENGLISH, "EEEE d MMMM")
                    }
                return flow.stateIn(backgroundScope, SharingStarted.Eagerly, HomeUiState()).also { runCurrent() }
            }

            suspend fun TestScope.send(event: HomeEvent) {
                events.send(event)
                runCurrent()
            }

            suspend fun TestScope.focus(key: String) = send(HomeEvent.Focus(key))

            fun TestScope.pass(millis: Long) {
                advanceTimeBy(millis)
                runCurrent()
            }

            fun configure(change: TvConfig.() -> TvConfig) {
                config.value = config.value.change()
            }
        }
    } asContextForEach {
        test("a dock tile with ticked sources shows its shelf once it has held focus for 600 ms") {
            with(it.testScope) {
                val home = home()
                focus("pkg.2")

                pass(SHELF_HOLD_MS - 1)
                reads.shouldBeEmpty()
                home.value.appShelf.shouldBeNull()

                pass(1)
                reads shouldBe listOf("pkg.2")
                home.value.appShelf shouldBe AppShelf("pkg.2", published.getValue("pkg.2"))
            }
        }

        test("moving along the dock faster than the hold reads nothing") {
            with(it.testScope) {
                val home = home()
                for (key in listOf("pkg.2", "pkg.3", "pkg.2", "pkg.3", "pkg.2")) {
                    focus(key)
                    pass(SHELF_HOLD_MS - 100)
                }

                reads.shouldBeEmpty()
                home.value.appShelf.shouldBeNull()
            }
        }

        test("moving to another dock tile with ticked sources takes the first shelf away at once") {
            with(it.testScope) {
                val home = home()
                focus("pkg.2")
                pass(SHELF_HOLD_MS)

                focus("pkg.3")

                home.value.appShelf.shouldBeNull()
            }
        }

        test("with app_shelf off a held dock tile reads nothing and shows no shelf") {
            with(it.testScope) {
                configure { copy(appShelf = false) }
                val home = home()
                focus("pkg.2")
                pass(10 * SHELF_HOLD_MS)

                reads.shouldBeEmpty()
                home.value.appShelf.shouldBeNull()
            }
        }

        test("a dock tile whose app has no ticked source reads nothing") {
            with(it.testScope) {
                val home = home()
                focus("pkg.4")
                pass(10 * SHELF_HOLD_MS)

                reads.shouldBeEmpty()
                home.value.appShelf.shouldBeNull()
            }
        }

        test("a grid tile reads nothing and shows no shelf, its sources ticked or not") {
            with(it.testScope) {
                val home = home()
                focus("pkg.8")
                pass(10 * SHELF_HOLD_MS)

                reads.shouldBeEmpty()
                home.value.appShelf.shouldBeNull()
            }
        }

        test("an app that publishes nothing shows no shelf") {
            with(it.testScope) {
                val home = home()
                focus("pkg.3")
                pass(SHELF_HOLD_MS)

                reads shouldBe listOf("pkg.3")
                home.value.appShelf.shouldBeNull()
            }
        }

        test("a package is read once, and again after home comes to the front") {
            with(it.testScope) {
                val home = home()
                focus("pkg.2")
                pass(SHELF_HOLD_MS)
                focus("pkg.3")
                pass(SHELF_HOLD_MS)
                focus("pkg.2")
                pass(SHELF_HOLD_MS)
                reads shouldBe listOf("pkg.2", "pkg.3")

                send(HomeEvent.Stopped)
                send(HomeEvent.Started)

                reads shouldBe listOf("pkg.2", "pkg.3", "pkg.2")
                home.value.appShelf shouldBe AppShelf("pkg.2", published.getValue("pkg.2"))
            }
        }

        test("a change of the ticked sources reads the shown shelf again") {
            with(it.testScope) {
                home()
                focus("pkg.2")
                pass(SHELF_HOLD_MS)

                configure { copy(appShelfSources = listOf("pkg.2")) }
                runCurrent()

                reads shouldBe listOf("pkg.2", "pkg.2")
            }
        }

        test("ok on a card opens its item with the app of its dock tile") {
            with(it.testScope) {
                val home = home()
                val severance = published.getValue("pkg.2").first()

                send(HomeEvent.OpenShelfItem("pkg.2", severance))

                effects shouldBe listOf(HomeEffect.OpenShelfItem(home.value.dock[1], severance))
            }
        }

        test("with press_feedback a card press waits out the press before it, as a tile press does") {
            with(it.testScope) {
                configure { copy(pressFeedback = true) }
                val home = home()
                val (severance, andor) = published.getValue("pkg.2")

                send(HomeEvent.OpenShelfItem("pkg.2", severance))
                send(HomeEvent.OpenShelfItem("pkg.2", andor))
                send(HomeEvent.Click("pkg.1"))

                effects shouldBe listOf(HomeEffect.OpenShelfItem(home.value.dock[1], severance))
                home.value.opening shouldBe Opening("App 2", says = false)
            }
        }
    }
}
