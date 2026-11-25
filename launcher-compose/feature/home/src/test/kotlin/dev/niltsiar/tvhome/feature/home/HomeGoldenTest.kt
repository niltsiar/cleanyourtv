package dev.niltsiar.tvhome.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.ExpressiveTile
import dev.niltsiar.tvhome.core.config.GlassStyle
import dev.niltsiar.tvhome.core.config.Look
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.TvTheme
import dev.niltsiar.tvhome.core.designsystem.focusStyle
import dev.niltsiar.tvhome.core.designsystem.golden.assertMatchesGolden
import dev.niltsiar.tvhome.core.designsystem.golden.bands
import dev.niltsiar.tvhome.core.designsystem.motion
import dev.niltsiar.tvhome.core.model.AppEntry
import dev.niltsiar.tvhome.core.model.AppList
import dev.niltsiar.tvhome.core.model.InputConnection
import dev.niltsiar.tvhome.core.model.InputsMenu
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind
import dev.niltsiar.tvhome.core.model.ShelfPoster
import dev.niltsiar.tvhome.core.model.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import java.time.ZoneOffset

/** Golden images of Home in each look on the real presenter and screen (docs/testing.md, "Golden tests"). */
val HomeGoldenTest by testSuite {
    robolectricTestSuite<HomeGoldenTestContent>(
        "sdk 31, 960x540 dp",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                qualifiers = "w960dp-h540dp-xhdpi"
            },
    )
}

class HomeGoldenTestContent :
    RobolectricTestSuiteContent({
        testFixture { HomeGoldenFixture() } asContextForEach {
            for (look in Look.entries) {
                val x = TvConfig(look = look)
                val prefix = if (look == Look.Expressive) "expressive-" else ""
                val named = if (look == Look.Expressive) ", in Expressive" else ""

                test("home with focus on the dock$named") {
                    showHome(x)
                    golden("${prefix}dock-focus")
                }

                if (look == Look.Expressive) {
                    for (tile in listOf(ExpressiveTile.Wave, ExpressiveTile.Cinch)) {
                        test("home with focus on the dock, in Expressive with the ${tile.name.lowercase()} tile") {
                            showHome(x.copy(expressiveTile = tile))
                            golden("expressive-dock-focus-${tile.name.lowercase()}")
                        }
                    }
                }

                test("home with focus in the grid$named") {
                    showHome(x)
                    press(Key.DirectionDown)
                    compose.onNodeWithTag("tile:pkg.5").assertIsFocused()
                    golden("${prefix}grid-focus")
                }

                test("cinematic home with art$named") {
                    showHome(x.copy(cinematicHome = true), heroArt = { art(it) })
                    golden("${prefix}cinematic-art")
                }

                test("cinematic home without art$named") {
                    showHome(x.copy(cinematicHome = true))
                    golden("${prefix}cinematic-no-art")
                }

                test("cinematic home over a bright picture$named") {
                    showHome(x.copy(cinematicHome = true), wallpaper = brightPicture)
                    golden("${prefix}cinematic-bright")
                }

                test("the app highlights shelf$named") {
                    showHome(x.copy(cinematicHome = true, appShelf = true, appShelfSources = listOf("pkg.1")), heroArt = { art(it) })
                    compose.onNodeWithTag("shelf:card:0").assertExists()
                    golden("${prefix}app-shelf")
                }

                test("a tile menu open$named") {
                    showHome(x)
                    press(Key.Menu)
                    compose.onNodeWithTag("menu").assertExists()
                    golden("${prefix}tile-menu")
                }

                test("the inputs list with a connected input$named") {
                    showHome(x)
                    inputsRequests++
                    settle()
                    compose.onNodeWithTag("menu").assertExists()
                    golden("${prefix}inputs-list")
                }

                test("reorder mode$named") {
                    showHome(x)
                    press(Key.Menu)
                    press(Key.DirectionDown)
                    press(Key.DirectionCenter)
                    compose.onNodeWithTag("reorder:hint").assertExists()
                    golden("${prefix}reorder")
                }

                test("an input tile in the dock$named") {
                    showHome(
                        x.copy(inputTiles = true),
                        apps = listOf(eighteenApps[0], PlayStationTile) + eighteenApps.drop(1),
                        inputs = playStationInputs(),
                    )
                    golden("${prefix}input-tiles")
                }

                test("tile faces with names under them on a glass dock$named") {
                    showHome(
                        x.copy(alwaysShowNames = true, dockGlass = true, inputTiles = true),
                        apps = faces,
                        inputs = playStationInputs(InputConnection.Standby),
                    )
                    golden("${prefix}tile-faces")
                }

                test("the habit mark$named") {
                    showHome(x.copy(habitHint = true), habit = "pkg.3")
                    golden("${prefix}habit-mark")
                }

                test("the grid edges and the more cue$named") {
                    showHome(x, apps = apps(30))
                    press(Key.DirectionDown)
                    press(Key.DirectionDown)
                    golden("${prefix}grid-edges-more-cue")
                }

                test("the top bar online with focus on it$named") {
                    showHome(x)
                    press(Key.DirectionUp)
                    golden("${prefix}top-bar-online")
                }

                test("the top bar with the sleep pill$named") {
                    showHome(x.copy(sleepTimer = true), sleepMinutesLeft = 25)
                    compose.onNodeWithTag("sleep_timer").assertExists()
                    golden("${prefix}top-bar-sleep")
                }

                val status = x.copy(wifiStatusColor = true, wifiStatusWarnings = true, iconOnlyButtons = false, reorderButton = true)

                test("the top bar with a VPN up and reorder on$named") {
                    showHome(status, network = NetworkState(NetworkLink.Wifi, ssid = "Home", validated = true, vpn = true))
                    press(Key.Menu)
                    press(Key.DirectionDown)
                    press(Key.DirectionCenter)
                    golden("${prefix}top-bar-vpn-reorder")
                }

                test("the top bar without internet and its VPN down$named") {
                    showHome(status.copy(vpnApp = "pkg.18"), network = NetworkState(NetworkLink.Wifi, ssid = "Home", validated = false))
                    golden("${prefix}top-bar-no-internet")
                }

                test("home with night mode off and a tile menu open$named") {
                    showHome(x, dark = false)
                    press(Key.Menu)
                    compose.onNodeWithTag("menu").assertExists()
                    golden("${prefix}night-off-tile-menu")
                }

                for (style in listOf(GlassStyle.Rim, GlassStyle.Halo)) {
                    test("home with glass style ${style.name}$named") {
                        showHome(x.copy(glassStyle = style))
                        golden("${prefix}glass-style-${style.name.lowercase()}")
                    }
                }
            }
        }
    })

/** The house seed of the Expressive scenes, the warm one the design-system goldens use. */
private val Ember = Color(0xFFFF6A3D)

private fun hue(n: Int) = Color.hsv((n * 47f) % 360f, 0.55f, 0.75f)

/** Apps 1 to [count], each with a two-band banner of its own hues. */
private fun apps(count: Int) =
    (1..count).map {
        AppEntry(key = "pkg.$it", packageName = "pkg.$it", label = "App $it", image = bands(32, 18, listOf(hue(it), hue(it + 3))))
    }

/** The hero art of tile [key]. */
private fun art(key: String) = bands(64, 36, listOf(Color(0xFF203A60), hue(key.length), Color(0xFFB0603A)))

private class HomeGoldenFixture : JUnit4RulesContext() {
    val compose = rule(createAndroidComposeRule<ComponentActivity>())

    var inputsRequests by mutableIntStateOf(0)

    val eighteenApps = apps(18)

    /** A plain banner, a light one, an icon, a text tile, an input tile asleep and a banner with its own name, then more apps. */
    val faces =
        listOf(
            AppEntry("pkg.1", "pkg.1", "Text App"),
            AppEntry(
                "pkg.light",
                "pkg.light",
                "Light App",
                image = bands(32, 18, listOf(Color(0xFFF4F1E8), Color(0xFFE0E6EE))),
                lightArt = true,
            ),
            AppEntry(
                "pkg.icon",
                "pkg.icon",
                "Icon App",
                image = bands(32, 18, listOf(Color(0xFF2E7D6B), Color(0xFF7FD1B9))),
                iconArt = true,
            ),
            PlayStationTile,
            AppEntry(
                "pkg.home",
                "pkg.home",
                "Home App",
                image = bands(32, 18, listOf(Color(0xFF3A2A6E), Color(0xFF8A5AC8))),
                homeLauncher = true,
            ),
        ) + eighteenApps.drop(5)

    val picture = bands(64, 36, listOf(Color(0xFF1B2F4A), Color(0xFF345C7A), Color(0xFF7A8F6A), Color(0xFFC09060)))

    /** A wallpaper bright enough behind the title that the hero scrim and the subtitle pill come on. */
    val brightPicture = bands(64, 36, listOf(Color(0xFFF4F1E8), Color(0xFFE6EEF4), Color(0xFFF2E4D0), Color(0xFFE0ECDC)))

    fun showHome(
        config: TvConfig = TvConfig(),
        dark: Boolean = true,
        apps: List<AppEntry> = eighteenApps,
        inputs: InputsMenu = FourPorts,
        habit: String? = null,
        sleepMinutesLeft: Int? = null,
        network: NetworkState = NetworkState(NetworkLink.Wifi, ssid = "Home", validated = true),
        heroArt: suspend (String) -> ImageBitmap? = { null },
        wallpaper: ImageBitmap = picture,
    ) {
        val poster = ShelfPoster(bands(18, 27, listOf(Color(0xFF5A2E6E), Color(0xFFD08A3A))))
        val sources =
            sources(
                apps = flowOf(AppList(apps)),
                network = flowOf(network),
                wallpaper = flowOf(Wallpaper(picture = wallpaper, thumbnail = wallpaper)),
                inputs = flowOf(inputs),
                libraryCount = flowOf(apps.size),
                habit = { habit },
                timeMillis = { Evening.toEpochSecond(ZoneOffset.UTC) * 1_000 },
                sleepTimerAvailable = { sleepMinutesLeft != null },
                sleepMinutesLeft = flowOf(sleepMinutesLeft),
                shelfItems = {
                    listOf(
                        ShelfItem(packageName = "pkg.1", title = "Severance", kind = ShelfKind.Continue, posterUri = "content://pkg.1/1"),
                        ShelfItem(packageName = "pkg.1", title = "Andor", kind = ShelfKind.Next, posterUri = "content://pkg.1/2"),
                        ShelfItem(packageName = "pkg.1", title = "Silo", kind = ShelfKind.Next, posterUri = "content://pkg.1/3"),
                    )
                },
                shelfPoster = { _, _, _ -> poster },
            )
        compose.mainClock.autoAdvance = false
        // A TV has no touch screen; the native graphics mode starts in touch mode, where nothing takes key focus.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.setContent {
            CompositionLocalProvider(LocalBakeDispatcher provides Dispatchers.Unconfined) {
                TvTheme(
                    look = config.look,
                    seed = { Ember },
                    motion = config.motion(),
                    focusStyle = config.focusStyle(),
                    expressiveTile = config.expressiveTile,
                    darkTheme = dark,
                ) {
                    val viewModel = rememberHomeViewModel(sources, config, mutableListOf())
                    val state = viewModel.uiState.collectAsState()
                    HomeScreen(
                        state = { state.value },
                        layout = config.homeLayout(),
                        onFocus = viewModel::onFocus,
                        onClick = viewModel::onClick,
                        topBar = config.topBarLayout(),
                        wallpaperLayout = config.wallpaperLayout(),
                        shelfLayout = config.shelfLayout(),
                        dockLook = config.dockLook(),
                        heroArt = heroArt,
                        actions = viewModel.actions,
                        inputsRequests = { inputsRequests },
                        onOpenShelfItem = viewModel::onOpenShelfItem,
                        shelfPoster = sources.shelfPoster,
                        onShelfRoom = viewModel::onShelfRoom,
                    )
                }
            }
        }
        settle()
        compose.onNodeWithTag("tile:pkg.1").assertIsFocused()
    }

    /** Moves the test clock past every focus spring, fade and shelf hold. */
    fun settle() {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    fun golden(scene: String) =
        compose
            .onRoot()
            .captureToImage()
            .assertMatchesGolden("HomeGoldenTest", scene)
}
