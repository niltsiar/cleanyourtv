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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "TvHome.Config"
private const val READ_RETRY_MS = 250L
private const val READ_RETRY_MAX_MS = 30_000L
private val THEME_SIGNATURE = stringPreferencesKey("theme_signature")

/**
 * The config as a flow: the theme's config.json overlaid with the choices stored on the TV.
 *
 * The store sits in credential-protected storage, so it opens only once the user is unlocked. While the user is locked,
 * [config] holds the theme alone and switches when the unlock arrives, and [set], [clear] and [followTheme] wait for the
 * store. A caller whose first value must carry the choices builds the repository after the unlock, as the key service does.
 * When the user is already unlocked, the constructor reads the store once, so the first value carries the choices.
 * A pushed theme is told from the last one by [ThemeDirectory.signature] and clears the choices except open on start; that is
 * compared only while external storage is mounted, which looks like a reset otherwise.
 * The store does its file work on [Dispatchers.IO] under [scope], which runs the collection and ends the store when cancelled.
 * A read error keeps the last published value and the collection tries again later.
 */
public class ConfigRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val themes = ThemeDirectory(context)
    private val state: MutableStateFlow<TvConfig>

    private val store = CompletableDeferred<DataStore<Preferences>>()

    /** The theme the store's choices overlay; set before [store] completes. */
    private lateinit var theme: TvConfig
    private val unlockReceiver = AtomicReference<BroadcastReceiver?>()

    private val following = MutableStateFlow(true)

    public val config: StateFlow<TvConfig>

    /** True while no choice is stored, open on start included: the config is the theme's alone. It changes with [config]. */
    public val followsTheme: StateFlow<Boolean> = following.asStateFlow()

    private val picked = MutableStateFlow<String?>(null)

    /** The package stored by [Choices.OpenOnStart], null for Off. It changes with [config]. */
    public val openOnStartApp: StateFlow<String?> = picked.asStateFlow()

    private val wallpaper = MutableStateFlow<String?>(null)

    /** The stored [Choices.WallpaperChoice], null when the theme decides. It changes with [config]. */
    public val wallpaperChoice: StateFlow<String?> = wallpaper.asStateFlow()

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
        store.await().edit { choice.put(it, value) }
    }

    /** Removes the stored value of [choice], so the theme's stands again (Off, for open on start). */
    public suspend fun <T> clear(choice: Choice<T>) {
        store.await().edit { it.remove(choice.key) }
    }

    /**
     * Ticks [source] in [Choices.AppShelfSources], or unticks it when ticked. While no set is stored, the theme's list is the
     * one changed. The read and the write are one edit, so toggles in quick succession all count.
     */
    public suspend fun toggleShelfSource(source: String) {
        store.await().edit { prefs ->
            val ticked = prefs.stored(AppShelfSourcesKey) ?: theme.appShelfSources.toSet()
            prefs[AppShelfSourcesKey] = if (source in ticked) ticked - source else ticked + source
        }
    }

    /** "Follow theme": removes every stored choice, open on start too. */
    public suspend fun followTheme() {
        store.await().edit { prefs -> Choices.all.forEach { prefs.remove(it.key) } }
    }

    private suspend fun open(theme: TvConfig) {
        val opened =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
            ) { context.preferencesDataStoreFile("launcher_settings") }
        this.theme = theme
        store.complete(opened)
        val signature = themes.signature().takeIf { themes.mounted }
        try {
            val first = opened.updateData { current -> current.toMutablePreferences().apply { signature?.let { resetForNewTheme(it) } } }
            publish(theme, first)
        } catch (e: IOException) {
            Log.w(TAG, "could not read the stored choices: ${e.message}")
        }
        scope.launch { opened.choices().collect { publish(theme, it) } }
    }

    private fun publish(
        theme: TvConfig,
        prefs: Preferences,
    ) {
        state.value = overlay(theme, prefs)
        following.value = Choices.all.none { it.key in prefs }
        picked.value = prefs.stored(OpenOnStartKey)?.takeIf { it.isNotEmpty() }
        wallpaper.value = prefs.stored(WallpaperChoiceKey)
    }

    private fun waitForUnlock(users: UserManager) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = unlocked()
            }
        unlockReceiver.set(receiver)
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        // The unlock may have happened between the check and the registration.
        if (users.isUserUnlocked) unlocked()
    }

    private fun unlocked() {
        val receiver = unlockReceiver.getAndSet(null) ?: return
        context.unregisterReceiver(receiver)
        scope.launch { open(themes.loadConfig().config) }
    }
}

/** The store's values. A read error ends the store's flow, so it is followed again after a pause that grows to [READ_RETRY_MAX_MS]. */
private fun DataStore<Preferences>.choices(): Flow<Preferences> =
    data.retryWhen { cause, attempt ->
        if (cause !is IOException) return@retryWhen false
        Log.w(TAG, "could not read the stored choices, trying again: ${cause.message}")
        delay(minOf(READ_RETRY_MS shl minOf(attempt, 7L).toInt(), READ_RETRY_MAX_MS))
        true
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
