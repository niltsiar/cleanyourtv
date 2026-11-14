package dev.niltsiar.tvhome.feature.settings

import android.app.Application
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.model.PublishedChannel
import dev.niltsiar.tvhome.core.model.ShelfChannel
import dev.niltsiar.tvhome.core.model.ShelfPublisher
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.atomic.AtomicBoolean

private const val SHELF_ON = """{"app_shelf": true}"""
private const val SMARTTUBE = "org.smarttube.stable"

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

                fun settings(
                    theme: String = SHELF_ON,
                    listed: List<ShelfPublisher> = publishers,
                ) = SettingsHarness(app, theme, shelfPublishers = listed, shelfAccess = { granted.get() }).also { harness = it }

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

            test("with none of its ticked sources listed the apps and channels row says none") {
                val settings = settings("""{"app_shelf": true, "app_shelf_sources": ["com.gone"]}""")

                settings.awaitState { it.shelfSources == OptionLabel.Res(R.string.settings_shelf_none) }
            }

            test("the app highlights row follows the home layout rows and stores its choice") {
                val settings = settings("{}")
                settings.state.sections[1]
                    .rows
                    .map { it.id } shouldBe
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

            test("with nothing to list the picker does not open and the page says so") {
                val settings = settings(listed = emptyList())

                settings.viewModel.actions.onOpenShelfPicker()

                settings.awaitNoShelfSources()
                settings.state.shelfPicker.shouldBeNull()
            }
        }
    })
