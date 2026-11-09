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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/** The real repository over a theme file in the app's folder, and the real view model on it. */
internal class SettingsHarness(
    private val app: Application,
    theme: String = "{}",
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository: ConfigRepository
    val viewModel: MoleculeSettingsViewModel

    init {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        File(ThemeDirectory(app).dir!!, "config.json").writeText(theme)
        repository = ConfigRepository(app, scope)
        viewModel = MoleculeSettingsViewModel(repository, scope, RecompositionMode.Immediate, EmptyCoroutineContext)
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

    fun stop() =
        runBlocking {
            scope.coroutineContext.job.cancelAndJoin()
        }
}

internal fun SettingsUiState.row(id: SettingsRowId): SettingsRow = sections.flatMap { it.rows }.first { it.id == id }
