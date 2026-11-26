package dev.niltsiar.tvhome.feature.settings

import android.app.Application
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.PosterHelper
import dev.niltsiar.tvhome.core.model.PublishedChannel
import dev.niltsiar.tvhome.core.model.ShelfChannel
import dev.niltsiar.tvhome.core.model.ShelfPublisher
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.atomic.AtomicBoolean

private const val SHELF_ON = """{"app_shelf": true}"""
private const val SMARTTUBE = "org.smarttube.stable"

/** A stand-in for Home's room rule: with always_show_names the cards need 5 columns. */
private val NamesNeedFiveColumns: (TvConfig) -> Boolean = { !it.alwaysShowNames || it.columns >= 5 }

/** The App highlights row, its Apps and channels row and picker, over the real repository. */
val ShelfSettingsTest by testSuite {
    robolectricTestSuite<ShelfSettingsTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class ShelfSettingsTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app: Application = RuntimeEnvironment.getApplication()
                var harness: SettingsHarness? = null
                val granted = AtomicBoolean(true)
                val publishers =
                    listOf(
                        ShelfPublisher("com.dazn", "DAZN", 148, emptyList()),
                        ShelfPublisher("com.plezy", "Plezy", 20, emptyList()),
                        ShelfPublisher(
                            SMARTTUBE,
                            "SmartTube",
                            0,
                            listOf(
                                PublishedChannel(ShelfChannel("history", "Historial"), 12),
                                PublishedChannel(ShelfChannel("7", ""), 0),
                            ),
                        ),
                    )

                val listenerGranted = AtomicBoolean(false)

                fun settings(
                    theme: String = SHELF_ON,
                    listed: List<ShelfPublisher> = publishers,
                    helper: PosterHelper = PosterHelper.Missing,
                    listenerAvailable: Boolean = true,
                    room: ((TvConfig) -> Boolean)? = null,
                ): SettingsHarness {
                    harness?.stop()
                    return SettingsHarness(
                        app,
                        theme,
                        shelfPublishers = listed,
                        shelfAccess = { granted.get() },
                        posterHelper = { helper },
                        nowPlayingAvailable = { listenerAvailable },
                        nowPlayingAccess = { listenerGranted.get() },
                        shelfRoom = flowOf(room),
                    ).also { harness = it }
                }

                fun SettingsUiState.homeRows() = sections.single { it.heading == R.string.settings_group_home }.rows.map { it.id }

                fun SettingsHarness.openShelfPicker(): ShelfPicker {
                    viewModel.actions.onOpenShelfPicker()
                    return awaitState { it.shelfPicker != null }.shelfPicker!!
                }
            }
        } closeWith {
            harness?.stop()
        } asContextForEach {
            test("with app highlights off there is no apps and channels row and nothing is listed until it turns on") {
                val settings = settings("""{"app_shelf_sources": ["com.plezy"]}""")
                settings.state.shelfSources.shouldBeNull()

                settings.pick(SettingsRowId.AppShelf, 0)

                settings.awaitState { it.shelfSources == OptionLabel.Text("1") }
                settings.shelfListings.get() shouldBe 1
            }

            test("the apps and channels row counts only the ticked sources the picker lists, and the others stay stored") {
                val stored = """["com.plezy", "com.gone", "$SMARTTUBE/history", "$SMARTTUBE/rebuilt"]"""
                val settings = settings("""{"app_shelf": true, "app_shelf_sources": $stored}""")

                settings.awaitState { it.shelfSources == OptionLabel.Text("2") }
                settings.config.appShelfSources shouldBe listOf("com.plezy", "com.gone", "$SMARTTUBE/history", "$SMARTTUBE/rebuilt")
            }

            test("the apps and channels row says the layout has no room for cards where home has none, and counts where it has") {
                val noRoom =
                    settings(
                        """{"app_shelf": true, "always_show_names": true, "columns": 4, "app_shelf_sources": ["com.plezy"]}""",
                        room = NamesNeedFiveColumns,
                    )
                noRoom.awaitState { it.shelfSources == OptionLabel.Res(R.string.settings_shelf_no_room) }

                val roomy =
                    settings(
                        """{"app_shelf": true, "always_show_names": true, "columns": 5, "app_shelf_sources": ["com.plezy"]}""",
                        room = NamesNeedFiveColumns,
                    )
                roomy.awaitState { it.shelfSources == OptionLabel.Text("1") }
            }

            test("the no room line follows the columns and names rows on the page") {
                val settings =
                    settings(
                        """{"app_shelf": true, "always_show_names": true, "columns": 4, "app_shelf_sources": ["com.plezy"]}""",
                        room = NamesNeedFiveColumns,
                    )
                settings.awaitState { it.shelfSources == OptionLabel.Res(R.string.settings_shelf_no_room) }

                settings.pick(SettingsRowId.Columns, 1)
                settings.awaitState { it.shelfSources == OptionLabel.Text("1") }

                settings.pick(SettingsRowId.Columns, 0)
                settings.awaitState { it.shelfSources == OptionLabel.Res(R.string.settings_shelf_no_room) }

                settings.pick(SettingsRowId.AlwaysShowNames, 1)
                settings.awaitState { it.shelfSources == OptionLabel.Text("1") }
            }

            test("turning app highlights off takes the no room line away with its row") {
                val settings = settings("""{"app_shelf": true, "always_show_names": true, "columns": 4}""", room = NamesNeedFiveColumns)
                settings.awaitState { it.shelfSources == OptionLabel.Res(R.string.settings_shelf_no_room) }

                settings.pick(SettingsRowId.AppShelf, 1)

                settings.awaitState { it.shelfSources == null }
                settings.config.appShelf.shouldBeFalse()
            }

            test("with none of its ticked sources listed the apps and channels row says none") {
                val settings = settings("""{"app_shelf": true, "app_shelf_sources": ["com.gone"]}""")

                settings.awaitState { it.shelfSources == OptionLabel.Res(R.string.settings_shelf_none) }
            }

            test("the app highlights row follows the home layout rows and stores its choice") {
                val settings = settings("{}")
                settings.state.homeRows() shouldBe
                    listOf(
                        SettingsRowId.AlwaysShowNames,
                        SettingsRowId.ShowGreeting,
                        SettingsRowId.DateBelowClock,
                        SettingsRowId.MoveToMenu,
                        SettingsRowId.AppShelf,
                    )

                settings.pick(SettingsRowId.AppShelf, 0)

                settings.awaitState { it.shelfSources == OptionLabel.Res(R.string.settings_shelf_none) }
                settings.config.appShelf.shouldBeTrue()
            }

            test("turning app highlights on without read tv listings asks for it and the row says no access") {
                granted.set(false)
                val settings = settings("{}")

                settings.pick(SettingsRowId.AppShelf, 0)

                settings.awaitAskedShelfAccess()
                settings.awaitState { it.shelfSources == OptionLabel.Res(R.string.settings_shelf_no_access) }
            }

            test("turning app highlights on with read tv listings asks for nothing") {
                val settings = settings("{}", listed = emptyList())

                settings.pick(SettingsRowId.AppShelf, 0)
                settings.viewModel.actions.onOpenShelfPicker()

                settings.awaitNoShelfSources()
                settings.shelfAccessAsked().shouldBeFalse()
            }

            test("the answer to the request is read again") {
                granted.set(false)
                val settings = settings()
                settings.state.shelfSources shouldBe OptionLabel.Res(R.string.settings_shelf_no_access)

                granted.set(true)
                settings.viewModel.actions.onShelfAccessAnswered()

                settings.awaitState { it.shelfSources == OptionLabel.Res(R.string.settings_shelf_none) }
            }

            test("without read tv listings the apps and channels picker asks for it, lists nothing and does not open") {
                granted.set(false)
                val settings = settings()

                settings.viewModel.actions.onOpenShelfPicker()

                settings.awaitAskedShelfAccess()
                settings.state.shelfPicker.shouldBeNull()
                settings.shelfListings.get() shouldBe 0
            }

            test("the picker lists each app's watch next and each of its channels with their counts") {
                val settings = settings()

                settings.openShelfPicker().entries shouldBe
                    listOf(
                        ShelfSourceEntry("com.dazn", "com.dazn", "DAZN", null, 148),
                        ShelfSourceEntry("com.plezy", "com.plezy", "Plezy", null, 20),
                        ShelfSourceEntry("$SMARTTUBE/history", SMARTTUBE, "SmartTube", "Historial", 12),
                        ShelfSourceEntry("$SMARTTUBE/7", SMARTTUBE, "SmartTube", "SmartTube", 0),
                    )
            }

            test("ticking stores the source key, keeps the picker open, and ticking again removes it") {
                val settings = settings()
                settings.openShelfPicker()

                settings.viewModel.actions.onToggleShelfSource(2)
                settings.viewModel.actions.onToggleShelfSource(1)

                val ticked = settings.awaitState { it.shelfPicker?.ticked?.size == 2 }
                ticked.shelfPicker!!.ticked shouldBe setOf("$SMARTTUBE/history", "com.plezy")
                ticked.shelfSources shouldBe OptionLabel.Text("2")
                settings.config.appShelfSources shouldBe listOf("com.plezy", "$SMARTTUBE/history")

                settings.viewModel.actions.onToggleShelfSource(2)
                settings.awaitState { it.shelfPicker?.ticked == setOf("com.plezy") }
                settings.config.appShelfSources shouldBe listOf("com.plezy")
            }

            test("pictures from the web shows only while app highlights is on, and pictures from your network only while both are") {
                val settings = settings("{}")
                settings.state.homeRows().last() shouldBe SettingsRowId.AppShelf

                settings.pick(SettingsRowId.AppShelf, 0)
                settings.awaitState {
                    it.homeRows().takeLast(4) ==
                        listOf(SettingsRowId.AppShelf, SettingsRowId.ShelfCards, SettingsRowId.WebPosters, SettingsRowId.NowPlaying)
                }
                settings.pick(SettingsRowId.WebPosters, 0)
                settings.awaitState {
                    it.homeRows().takeLast(3) == listOf(SettingsRowId.WebPosters, SettingsRowId.LanPosters, SettingsRowId.NowPlaying)
                }
                settings.pick(SettingsRowId.LanPosters, 0)
                settings.awaitState { it.row(SettingsRowId.LanPosters).selected == 0 }
                (settings.config.appShelfWebPosters && settings.config.appShelfLanPosters).shouldBeTrue()

                settings.pick(SettingsRowId.WebPosters, 1)
                settings.awaitState { it.homeRows().takeLast(2) == listOf(SettingsRowId.WebPosters, SettingsRowId.NowPlaying) }
                settings.pick(SettingsRowId.AppShelf, 1)
                settings.awaitState { it.homeRows().last() == SettingsRowId.AppShelf }
                settings.config.appShelfLanPosters.shouldBeTrue()
            }

            test("now playing shows under app highlights only while it is on, and a pick stores it") {
                val settings = settings("{}")
                (SettingsRowId.NowPlaying in settings.state.homeRows()).shouldBeFalse()

                settings.pick(SettingsRowId.AppShelf, 0)
                settings.awaitState { it.homeRows().last() == SettingsRowId.NowPlaying }.row(SettingsRowId.NowPlaying).selected shouldBe 1
                settings.pick(SettingsRowId.NowPlaying, 0)
                settings.awaitState { it.row(SettingsRowId.NowPlaying).selected == 0 }
                settings.config.nowPlaying.shouldBeTrue()

                settings.pick(SettingsRowId.AppShelf, 1)
                settings.awaitState { SettingsRowId.NowPlaying !in it.homeRows() }
                settings.config.nowPlaying.shouldBeTrue()
            }

            test("now playing turned on without notification access asks for it and says needs access, and ok on that asks again") {
                val settings = settings()

                settings.pick(SettingsRowId.NowPlaying, 0)

                settings.awaitAskedNowPlayingAccess()
                val row = settings.awaitState { it.row(SettingsRowId.NowPlaying).selected == 0 }.row(SettingsRowId.NowPlaying)
                row.options shouldBe
                    listOf(OptionLabel.Res(R.string.settings_now_playing_needs_access), OptionLabel.Res(R.string.settings_off))
                settings.pick(SettingsRowId.NowPlaying, 0)
                settings.awaitAskedNowPlayingAccess()
            }

            test("access given while the page was away reads again when it comes back, and the row says on") {
                val settings = settings("""{"app_shelf": true, "now_playing": true}""")
                settings
                    .awaitState { SettingsRowId.NowPlaying in it.homeRows() }
                    .row(SettingsRowId.NowPlaying)
                    .options
                    .first() shouldBe OptionLabel.Res(R.string.settings_now_playing_needs_access)

                listenerGranted.set(true)
                settings.viewModel.actions.onNowPlayingAccessChanged()

                settings.awaitState { it.row(SettingsRowId.NowPlaying).options.first() == OptionLabel.Res(R.string.settings_on) }
            }

            test("on a tv that gives no notification access now playing says not available on this tv and cannot turn on") {
                val settings = settings(listenerAvailable = false)
                val row = settings.awaitState { SettingsRowId.NowPlaying in it.homeRows() }.row(SettingsRowId.NowPlaying)
                row.options shouldBe
                    listOf(OptionLabel.Res(R.string.settings_now_playing_unavailable), OptionLabel.Res(R.string.settings_off))
                row.selected shouldBe 1

                settings.pick(SettingsRowId.NowPlaying, 0)

                settings.staysFollowingTheme().shouldBeTrue()
                settings.config.nowPlaying.shouldBeFalse()
                settings.nowPlayingAccessAsked().shouldBeFalse()
            }

            test("cards per app shows only while app highlights is on, offers 5, 10 and 20 with 10 held, and a pick stores it") {
                val settings = settings("{}")
                (SettingsRowId.ShelfCards in settings.state.homeRows()).shouldBeFalse()

                settings.pick(SettingsRowId.AppShelf, 0)
                val cards = settings.awaitState { SettingsRowId.ShelfCards in it.homeRows() }.row(SettingsRowId.ShelfCards)
                cards.options shouldBe listOf("5", "10", "20").map(OptionLabel::Text)
                cards.selected shouldBe 1
                settings.pick(SettingsRowId.ShelfCards, 2)
                settings.awaitState { it.row(SettingsRowId.ShelfCards).selected == 2 }
                settings.config.appShelfCards shouldBe 20

                settings.pick(SettingsRowId.AppShelf, 1)
                settings.awaitState { SettingsRowId.ShelfCards !in it.homeRows() }
            }

            test("while on, pictures from the web says when the helper is not installed or signed with another key") {
                val on = """{"app_shelf": true, "app_shelf_web_posters": true}"""
                val said =
                    PosterHelper.entries.associateWith { helper ->
                        settings(
                            on,
                            helper = helper,
                        ).awaitState { SettingsRowId.WebPosters in it.homeRows() }.row(SettingsRowId.WebPosters).options
                    }

                said shouldBe
                    mapOf(
                        PosterHelper.Missing to
                            listOf(OptionLabel.Res(R.string.settings_poster_helper_missing), OptionLabel.Res(R.string.settings_off)),
                        PosterHelper.OtherKey to
                            listOf(OptionLabel.Res(R.string.settings_poster_helper_other_key), OptionLabel.Res(R.string.settings_off)),
                        PosterHelper.Ready to listOf(OptionLabel.Res(R.string.settings_on), OptionLabel.Res(R.string.settings_off)),
                    )
                val off = settings(SHELF_ON, helper = PosterHelper.Missing).awaitState { SettingsRowId.WebPosters in it.homeRows() }
                off.row(SettingsRowId.WebPosters).options.first() shouldBe OptionLabel.Res(R.string.settings_on)
            }

            test("with nothing to list the picker does not open and the page says so") {
                val settings = settings(listed = emptyList())

                settings.viewModel.actions.onOpenShelfPicker()

                settings.awaitNoShelfSources()
                settings.state.shelfPicker.shouldBeNull()
            }
        }
    })
