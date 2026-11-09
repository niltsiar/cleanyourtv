package dev.niltsiar.tvhome.core.config

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

private const val TAG = "TvHome.Config"
private val THEME_SIGNATURE = stringPreferencesKey("theme_signature")

/**
 * The config as a flow: the theme's config.json overlaid with the choices stored on the TV.
 *
 * The store sits in credential-protected storage, so it opens only once the user is unlocked. Until then [config] holds the
 * theme alone and switches when the unlock arrives. When the user is already unlocked, the constructor reads the store once,
 * so the first value carries the choices. A pushed theme is told from the last one by [ThemeDirectory.signature] and clears
 * the choices except open on start; that is compared only while external storage is mounted, which looks like a reset otherwise.
 * The store does its file work on [Dispatchers.IO] under [scope], which runs the collection and ends the store when cancelled.
 */
public class ConfigRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val themes = ThemeDirectory(context)
    private val state: MutableStateFlow<TvConfig>

    @Volatile private var store: DataStore<Preferences>? = null
    private var unlockReceiver: BroadcastReceiver? = null

    public val config: StateFlow<TvConfig>

    /** What is stored, for tests. */
    internal val stored: Flow<Preferences> get() = openStore().choices()

    init {
        val theme = themes.loadConfig().config
        state = MutableStateFlow(theme)
        config = state.asStateFlow()
        val users = context.getSystemService(UserManager::class.java)
        if (users.isUserUnlocked) {
            runBlocking { open(theme) }
        } else {
            waitForUnlock(users)
        }
    }

    /** Stores [value] for [choice]; [config] follows through the store's flow. */
    public suspend fun <T> set(
        choice: Choice<T>,
        value: T,
    ) {
        openStore().edit { choice.put(it, value) }
    }

    /** "Follow theme": removes every stored choice, open on start too. */
    public suspend fun followTheme() {
        openStore().edit { prefs -> Choices.all.forEach { prefs.remove(it.key) } }
    }

    private fun openStore(): DataStore<Preferences> = checkNotNull(store) { "the choices cannot be stored before the unlock" }

    private suspend fun open(theme: TvConfig) {
        val opened =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
            ) { context.preferencesDataStoreFile("launcher_settings") }
        store = opened
        val signature = themes.signature().takeIf { themes.mounted }
        try {
            val first = opened.updateData { current -> current.toMutablePreferences().apply { signature?.let { resetForNewTheme(it) } } }
            state.value = overlay(theme, first)
        } catch (e: IOException) {
            Log.w(TAG, "could not read the stored choices: ${e.message}")
        }
        scope.launch { opened.choices().collect { state.value = overlay(theme, it) } }
    }

    private fun waitForUnlock(users: UserManager) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = unlocked()
            }
        unlockReceiver = receiver
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        // The unlock may have happened between the check and the registration.
        if (users.isUserUnlocked) unlocked()
    }

    private fun unlocked() {
        val receiver = unlockReceiver ?: return
        unlockReceiver = null
        context.unregisterReceiver(receiver)
        scope.launch { open(themes.loadConfig().config) }
    }
}

private fun DataStore<Preferences>.choices(): Flow<Preferences> =
    data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }

/** The first read after a push: a different signature clears the choices. Without a saved one, they stay unless tools/theme.sh left a revision. */
private fun MutablePreferences.resetForNewTheme(signature: ThemeSignature) {
    val seen = this[THEME_SIGNATURE]
    if (seen != signature.value && (seen != null || signature.pushed)) Choices.themeScoped.forEach { remove(it.key) }
    this[THEME_SIGNATURE] = signature.value
}

private fun overlay(
    theme: TvConfig,
    prefs: Preferences,
): TvConfig = Choices.all.fold(theme) { config, choice -> choice.overlay(config, prefs) }
