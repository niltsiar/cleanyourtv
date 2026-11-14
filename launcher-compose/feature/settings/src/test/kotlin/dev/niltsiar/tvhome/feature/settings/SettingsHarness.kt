package dev.niltsiar.tvhome.feature.settings

import android.app.Application
import android.os.Environment
import app.cash.molecule.RecompositionMode
import dev.niltsiar.tvhome.core.config.Choices
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.OpenOnStartApp
import dev.niltsiar.tvhome.core.model.WallpaperPicture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The real repository over a theme file in the app's folder, and the real view model on it. The view model's presenter runs
 * on a background thread, so a test can block the test thread on [awaitState]. A test that draws the screen sets [uiThread]:
 * Molecule's immediate recompositions then run on the UI thread, as in the app, and not beside the UI frame, which made the
 * list's scroll land in the wrong place from time to time. That scope uses Dispatchers.Main and not Main.immediate: with
 * Immediate recomposition, the immediate dispatcher left the recomposer without a wake-up now and then, and the state
 * stayed old for good. The picker offers [apps], and the Open on start row names the pick by their labels; [skippedAt]
 * records the pick stored at each skipThisBoot call. The apps are not listed before [appsGate] opens. The apps and the theme's pictures ([wallpapers]) are listed off the
 * calling thread as the app does: a list that came back at once would be stored while the first composition applies, and
 * Molecule's immediate recompositions on the UI thread would stall. [awaitApplied] waits for a wallpaper pick that was stored,
 * [awaitSaveFailed] for a write the store refused. The pickers show [art], and Home shows [themeDefault] for the theme default.
 */
internal class SettingsHarness(
    private val app: Application,
    theme: String = "{}",
    uiThread: Boolean = false,
    apps: List<OpenOnStartApp> = emptyList(),
    appsGate: Deferred<Unit>? = null,
    wallpapers: List<WallpaperPicture> = emptyList(),
    art: PickerArt = PickerArt(),
    themeDefault: WallpaperPicture? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val presenterScope = if (uiThread) CoroutineScope(SupervisorJob() + Dispatchers.Main) else scope
    val repository: ConfigRepository
    val viewModel: MoleculeSettingsViewModel
    val skippedAt = CopyOnWriteArrayList<String?>()
    private val applied = Channel<Unit>(Channel.UNLIMITED)
    private val failed = Channel<Unit>(Channel.UNLIMITED)

    init {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        File(ThemeDirectory(app).dir!!, "config.json").writeText(theme)
        repository = ConfigRepository(app, scope)
        val sources =
            SettingsSources(
                {
                    appsGate?.await()
                    withContext(Dispatchers.Default) { apps }
                },
                { skippedAt += repository.openOnStartApp.value },
                { withContext(Dispatchers.Default) { wallpapers } },
                art,
                { withContext(Dispatchers.Default) { themeDefault } },
            )
        viewModel = MoleculeSettingsViewModel(repository, sources, presenterScope, RecompositionMode.Immediate, EmptyCoroutineContext)
        scope.launch {
            viewModel.effects.collect {
                when (it) {
                    SettingsEffect.WallpaperApplied -> applied.trySend(Unit)
                    SettingsEffect.SaveFailed -> failed.trySend(Unit)
                }
            }
        }
    }

    val config: TvConfig get() = repository.config.value
    val state: SettingsUiState get() = viewModel.uiState.value

    fun row(id: SettingsRowId): SettingsRow = state.row(id)

    fun pick(
        id: SettingsRowId,
        option: Int,
    ) = viewModel.actions.onPick(id, option)

    fun followTheme() = viewModel.actions.onFollowTheme()

    val pickedApp: String? get() = repository.openOnStartApp.value
    val wallpaperChoice: String? get() = repository.wallpaperChoice.value

    /** Waits for the next wallpaper pick that was reported as stored. */
    fun awaitApplied() = runBlocking { withTimeout(30_000) { applied.receive() } }

    /** Waits for the next write the store refused. */
    fun awaitSaveFailed() = runBlocking { withTimeout(30_000) { failed.receive() } }

    /** True when the theme still decides after a short wait: no stored choice turned [SettingsUiState.followsTheme] off. */
    fun staysFollowingTheme(): Boolean = runBlocking { withTimeoutOrNull(500) { viewModel.uiState.first { !it.followsTheme } } == null }

    /** True when no further wallpaper pick is reported within a short wait. */
    fun noMoreApplied(): Boolean = runBlocking { withTimeoutOrNull(300) { applied.receive() } == null }

    fun storePick(pkg: String) = runBlocking { repository.set(Choices.OpenOnStart, pkg) }

    /** Waits for a state that satisfies [until]: a pick reaches the store on another thread and comes back through its flow. */
    fun awaitState(until: (SettingsUiState) -> Boolean): SettingsUiState =
        runBlocking { withTimeout(30_000) { viewModel.uiState.first(until) } }

    fun stop() {
        presenterScope.cancel()
        scope.cancel()
    }
}

internal fun SettingsUiState.row(id: SettingsRowId): SettingsRow = sections.flatMap { it.rows }.first { it.id == id }
