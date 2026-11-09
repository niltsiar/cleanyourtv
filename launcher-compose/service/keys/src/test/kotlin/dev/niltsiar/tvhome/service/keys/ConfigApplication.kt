package dev.niltsiar.tvhome.service.keys

import android.app.Application
import dev.niltsiar.tvhome.core.config.ConfigRepository
import dev.niltsiar.tvhome.core.config.ConfigRepositoryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The Application of a test that connects the service: it owns the process's one repository, as the app does. */
class ConfigApplication :
    Application(),
    ConfigRepositoryOwner {
    override val configRepository: ConfigRepository by lazy { ConfigRepository(this, CoroutineScope(SupervisorJob() + Dispatchers.IO)) }
}
