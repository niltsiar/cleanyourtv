package dev.niltsiar.tvhome.service.keys

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.tv.TvContract
import android.os.Build
import dev.niltsiar.tvhome.core.model.LauncherIntents
import dev.niltsiar.tvhome.core.model.startActivitySafely
import dev.niltsiar.tvhome.core.model.tvLaunchIntent
import dev.niltsiar.tvhome.data.system.SystemSettings

private const val PROGRAM_DIR_TYPE = "vnd.android.cursor.dir/program"
private const val CHANNEL_TYPE = "vnd.android.cursor.item/channel"

/**
 * Runs what an app_keys target stands for. [performGlobal] is the accessibility service's global action; Home has none
 * (null), so back, home, recents and sleep are swallowed there and do nothing.
 */
internal class TargetLauncher(
    private val context: Context,
    private val settings: SystemSettings,
    private val performGlobal: ((Int) -> Boolean)?,
    private val liveTvPackage: String?,
) {
    fun launch(target: String) {
        when (val parsed = parseTarget(target)) {
            is KeyTarget.Action -> {
                run(parsed.action)
            }

            KeyTarget.Inputs -> {
                context.homeIntent(LauncherIntents.VIEW_INPUTS)?.let(::start)
            }

            is KeyTarget.Guide -> {
                start(
                    Intent(
                        Intent.ACTION_VIEW,
                    ).setDataAndType(TvContract.Programs.CONTENT_URI, PROGRAM_DIR_TYPE).setPackage(parsed.packageName),
                )
            }

            is KeyTarget.Input -> {
                val view =
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(TvContract.buildChannelUriForPassthroughInput(parsed.id), CHANNEL_TYPE)
                // An app that also claims the channel URI turns the press into a chooser, so the TV app is asked first.
                val liveTv = liveTvPackage?.takeIf { it.isNotBlank() }
                if (liveTv == null || !start(Intent(view).setPackage(liveTv))) start(view)
            }

            is KeyTarget.Activity -> {
                ComponentName.unflattenFromString(parsed.component)?.let { start(Intent().setComponent(it)) }
            }

            is KeyTarget.App -> {
                openApp(parsed.packageName)
            }
        }
    }

    private fun openApp(packageName: String) {
        context.packageManager.tvLaunchIntent(packageName)?.let(::start)
    }

    private fun run(action: ActionWord) {
        globalAction(action, Build.VERSION.SDK_INT)?.let {
            performGlobal?.invoke(it)
            return
        }
        when (action) {
            // The media stream a TV plays everything on, with its own volume bar, as a remote's mute button would.
            ActionWord.Mute -> {
                context.getSystemService(AudioManager::class.java)?.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE,
                    AudioManager.FLAG_SHOW_UI,
                )
            }

            ActionWord.Screensaver -> {
                startScreensaver()
            }

            ActionWord.Settings -> {
                settings.openSettings()
            }

            ActionWord.Wifi -> {
                settings.openWifiSettings()
            }

            ActionWord.Library -> {
                context.homeIntent(LauncherIntents.APP_LIBRARY)?.let(::start)
            }

            // "ignore", or "sleep" below Android 9: consumed, nothing to do
            else -> {
                Unit
            }
        }
    }

    /** The screensaver chosen in the TV's settings, through the system's "start now" shortcut; the launcher's own clock where the firmware has none. */
    private fun startScreensaver() {
        val dream = Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
        if (context.packageManager.resolveActivity(dream, 0) != null) {
            start(dream)
        } else {
            start(Intent(LauncherIntents.CLOCK).setPackage(context.packageName))
        }
    }

    private fun start(intent: Intent): Boolean = context.startActivitySafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
