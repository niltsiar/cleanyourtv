package dev.niltsiar.tvhome

import android.content.Context
import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The system screensaver: the same clock as [ClockActivity], on black or over Home's wallpaper. No network, no images
 * unless clock_wallpaper is on. A dream window has no lifecycle of its own, which Compose needs, so this service is the
 * owner: created when the window attaches, running while the dream plays.
 */
class ClockDream :
    DreamService(),
    SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val graph get() = (application as TvHomeApplication).graph

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(launcherContext(newBase))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        isInteractive = false // any key wakes the TV back to where it was
        isFullscreen = true
        isScreenBright = false
        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@ClockDream)
                setViewTreeSavedStateRegistryOwner(this@ClockDream)
                setContent { ClockHost(graph) }
            },
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        registry.currentState = Lifecycle.State.STARTED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}
