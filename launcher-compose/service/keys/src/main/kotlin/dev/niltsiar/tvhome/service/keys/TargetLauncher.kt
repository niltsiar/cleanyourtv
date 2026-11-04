package dev.niltsiar.tvhome.service.keys

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.tv.TvContract
import android.os.Build
import android.util.Log
import dev.niltsiar.tvhome.core.model.HomeActions
import dev.niltsiar.tvhome.data.system.SystemSettings

private const val TAG = "TvHome.Keys"
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
) {
    fun launch(target: String) {
        when (val parsed = parseTarget(target)) {
            is KeyTarget.Action -> {
                run(parsed.action)
            }

            KeyTarget.Inputs -> {
                context.homeIntent(HomeActions.VIEW_INPUTS)?.let(::start)
            }

            is KeyTarget.Guide -> {
                start(
                    Intent(
                        Intent.ACTION_VIEW,
                    ).setDataAndType(TvContract.Programs.CONTENT_URI, PROGRAM_DIR_TYPE).setPackage(parsed.packageName),
                )
            }

            is KeyTarget.Input -> {
                start(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(TvContract.buildChannelUriForPassthroughInput(parsed.id), CHANNEL_TYPE),
                )
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
        val pm = context.packageManager
        val open = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
        if (open != null) start(open)
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
                context.homeIntent(HomeActions.APP_LIBRARY)?.let(::start)
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
            start(Intent(HomeActions.CLOCK).setPackage(context.packageName))
        }
    }

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "cannot open $intent: $e")
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot open $intent: $e")
        }
    }
}
