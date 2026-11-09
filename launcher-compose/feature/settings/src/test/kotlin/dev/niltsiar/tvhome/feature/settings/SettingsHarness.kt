package dev.niltsiar.tvhome.feature.settings

import android.app.Application
import android.os.Environment
import app.cash.molecule.RecompositionMode
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.ThemeDirectory
import dev.niltsiar.tvhome.core.config.TvConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The real repository over a theme file in the app's folder, and the real view model on it. The view model's presenter runs
 * on a background thread, so a test can block the test thread on [awaitState]. A test that draws the screen sets [uiThread]:
 * Molecule's immediate recompositions then run on the UI thread, as in the app, and not beside the UI frame, which made the
 * list's scroll land in the wrong place from time to time.
 */
internal class SettingsHarness(
    private val app: Application,
    theme: String = "{}",
    uiThread: Boolean = false,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val presenterScope = if (uiThread) CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) else scope
    val repository: ConfigRepository
    val viewModel: MoleculeSettingsViewModel

    init {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        File(ThemeDirectory(app).dir!!, "config.json").writeText(theme)
        repository = ConfigRepository(app, scope)
        viewModel = MoleculeSettingsViewModel(repository, presenterScope, RecompositionMode.Immediate, EmptyCoroutineContext)
    }

    val config: TvConfig get() = repository.config.value
    val state: SettingsUiState get() = viewModel.uiState.value

    fun row(id: SettingsRowId): SettingsRow = state.row(id)

    fun pick(
        id: SettingsRowId,
        option: Int,
    ) = viewModel.actions.onPick(id, option)

    fun followTheme() = viewModel.actions.onFollowTheme()

    /** Waits for a state that satisfies [until]: a pick reaches the store on another thread and comes back through its flow. */
    fun awaitState(until: (SettingsUiState) -> Boolean): SettingsUiState =
        runBlocking { withTimeout(10_000) { viewModel.uiState.first(until) } }

    fun stop() {
        presenterScope.cancel()
        scope.cancel()
    }
}

internal fun SettingsUiState.row(id: SettingsRowId): SettingsRow = sections.flatMap { it.rows }.first { it.id == id }
