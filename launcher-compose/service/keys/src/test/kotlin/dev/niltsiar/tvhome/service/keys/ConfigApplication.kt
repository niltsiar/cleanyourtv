package dev.niltsiar.tvhome.service.keys

import android.app.Application
import android.content.Intent
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.ConfigRepositoryOwner
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.AppOpened
import dev.niltsiar.tvhome.data.apps.AppLauncher
import dev.niltsiar.tvhome.data.apps.LastOpened
import dev.niltsiar.tvhome.data.apps.LaunchLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * The Application of a test that connects the service: it owns the process's one repository and notes the apps the
 * service opens, as the app does. [recordConfig] holds the choices the record follows, apart from the service's own.
 */
class ConfigApplication :
    Application(),
    ConfigRepositoryOwner,
    AppOpened {
    override val configRepository: ConfigRepository by lazy { ConfigRepository(this, CoroutineScope(SupervisorJob() + Dispatchers.IO)) }

    val recordConfig = MutableStateFlow(TvConfig())
    val lastOpened by lazy { LastOpened(this, recordConfig, Dispatchers.Unconfined) }
    private val appLauncher by lazy {
        AppLauncher(this, recordConfig, lastOpened, LaunchLog(File(filesDir, "launch_log.txt"), recordConfig, Dispatchers.Unconfined))
    }

    override fun opened(intent: Intent) = appLauncher.opened(intent)
}
