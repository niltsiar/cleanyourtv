package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf

/** Which layouts have room for the app_shelf cards on a 1080p TV, as Home reports it, with 18 apps so the grid has rows. */
val ShelfRoomUiTest by testSuite {
    robolectricTestSuite<ShelfRoomUiTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class ShelfRoomUiTestContent :
    RobolectricTestSuiteContent({
        testFixture { ShelfRoomUiTestFixture() } asContextForEach {
            test("without names, columns 1 to 3 have no room for the cards and 4 to 8 have, plain or cinematic") {
                for (cinematic in listOf(false, true)) {
                    roomyColumns(Shelf.copy(cinematicHome = cinematic)) shouldBe (4..8).toList()
                }
            }

            test("always_show_names has room from columns 5, and with cinematic_home from columns 6") {
                roomyColumns(Shelf.copy(alwaysShowNames = true)) shouldBe (5..8).toList()
                roomyColumns(Shelf.copy(alwaysShowNames = true, cinematicHome = true)) shouldBe (6..8).toList()
            }

            test("with animations off, always_show_names with cinematic_home has room from columns 5") {
                roomyColumns(Shelf.copy(alwaysShowNames = true, cinematicHome = true, animations = false)) shouldBe (5..8).toList()
            }

            test("dock_even_padding with focus_overshoot needs one column more") {
                val popping = Shelf.copy(dockEvenPadding = true, focusOvershoot = true)
                roomyColumns(popping) shouldBe (5..8).toList()
                roomyColumns(popping.copy(alwaysShowNames = true)) shouldBe (6..8).toList()
                roomyColumns(popping.copy(alwaysShowNames = true, cinematicHome = true)) shouldBe (7..8).toList()
            }

            test("rows_below_dock 1 leaves room at columns 8 only, and none with names or with cinematic_home and dock_even_padding") {
                val oneRow = Shelf.copy(rowsBelowDock = 1)
                roomyColumns(oneRow) shouldBe listOf(8)
                roomyColumns(oneRow.copy(cinematicHome = true)) shouldBe listOf(8)
                roomyColumns(oneRow.copy(alwaysShowNames = true)) shouldBe emptyList()
                roomyColumns(oneRow.copy(cinematicHome = true, dockEvenPadding = true)) shouldBe emptyList()
            }

            test("rows_below_dock 2 and 3 leave no room at any column") {
                for (rows in 2..3) {
                    withClue("rows_below_dock $rows") { roomyColumns(Shelf.copy(rowsBelowDock = rows)) shouldBe emptyList() }
                }
            }

            test("single_row has no room at any column") {
                roomyColumns(Shelf.copy(singleRow = true)) shouldBe emptyList()
            }

            test("with no grid rows to show, rows_below_dock 3 shrinks the tiles and leaves room from columns 3") {
                roomyColumns(Shelf.copy(rowsBelowDock = 3), apps = 3) shouldBe (3..8).toList()
            }

            test("shelfHasRoom, which Settings reads, agrees with Home's measurement in every layout above") {
                for ((index, measured) in Measured.withIndex()) {
                    val (layout, apps) = measured
                    withClue("layout $index") { ruledColumns(layout, apps) shouldBe roomyColumns(layout, apps) }
                }
            }
        }
    })

private val Shelf = TvConfig(appShelf = true)

/** Every layout the tests above measure, with its app count. */
private val Measured: List<Pair<TvConfig, Int>> =
    listOf(
        Shelf,
        Shelf.copy(cinematicHome = true),
        Shelf.copy(alwaysShowNames = true),
        Shelf.copy(alwaysShowNames = true, cinematicHome = true),
        Shelf.copy(alwaysShowNames = true, cinematicHome = true, animations = false),
        Shelf.copy(dockEvenPadding = true, focusOvershoot = true),
        Shelf.copy(dockEvenPadding = true, focusOvershoot = true, alwaysShowNames = true),
        Shelf.copy(dockEvenPadding = true, focusOvershoot = true, alwaysShowNames = true, cinematicHome = true),
        Shelf.copy(rowsBelowDock = 1),
        Shelf.copy(rowsBelowDock = 1, cinematicHome = true),
        Shelf.copy(rowsBelowDock = 1, alwaysShowNames = true),
        Shelf.copy(rowsBelowDock = 1, cinematicHome = true, dockEvenPadding = true),
        Shelf.copy(rowsBelowDock = 2),
        Shelf.copy(rowsBelowDock = 3),
        Shelf.copy(singleRow = true),
    ).map { it to 18 } + (Shelf.copy(rowsBelowDock = 3) to 3)

private class ShelfRoomUiTestFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    private var config by mutableStateOf<TvConfig?>(null)
    private var appCount by mutableIntStateOf(0)
    private var room: Boolean? = null
    private var shown = false
    private var window = IntSize.Zero
    private var density = Density(1f)

    /** The columns, of 1 to 8, at which Home with [layout] and [apps] apps reports room for the cards. */
    fun roomyColumns(
        layout: TvConfig,
        apps: Int = 18,
    ): List<Int> {
        show()
        return (1..8).filter { columns ->
            room = null
            appCount = apps
            config = layout.copy(columns = columns)
            compose.waitForIdle()
            withClue("columns $columns reported") { room.shouldNotBeNull() }
            room == true
        }
    }

    /** The columns, of 1 to 8, at which [shelfHasRoom] gives room in the window Home is shown in. */
    fun ruledColumns(
        layout: TvConfig,
        apps: Int,
    ): List<Int> {
        show()
        compose.waitForIdle()
        return (1..8).filter { shelfHasRoom(layout.copy(columns = it), apps, docked = 0, window, density) }
    }

    private fun show() {
        if (shown) return
        shown = true
        compose.setContent {
            window = LocalWindowInfo.current.containerSize
            density = LocalDensity.current
            val current = config ?: return@setContent
            key(current, appCount) {
                val entries = (1..appCount).map { AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it") }
                val sources = sources(apps = flowOf(AppList(entries)), inputs = flowOf(FourPorts))
                TvTheme(look = Look.Glass, motion = Motion(current.animations, current.reducedMotion, current.focusOvershoot)) {
                    val viewModel = rememberHomeViewModel(sources, current, mutableListOf())
                    val state = viewModel.uiState.collectAsState()
                    HomeScreen(
                        state = { state.value },
                        layout = current.homeLayout(),
                        onFocus = viewModel::onFocus,
                        onClick = viewModel::onClick,
                        topBar = current.topBarLayout(),
                        shelfLayout = current.shelfLayout(),
                        dockLook = current.dockLook(),
                        actions = viewModel.actions,
                        onShelfRoom = { room = it },
                    )
                }
            }
        }
    }
}
