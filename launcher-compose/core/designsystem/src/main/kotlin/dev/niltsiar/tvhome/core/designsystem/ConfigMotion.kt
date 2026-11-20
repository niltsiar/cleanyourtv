package dev.niltsiar.tvhome.core.designsystem

import dev.niltsiar.tvhome.core.config.GlassStyle
import dev.niltsiar.tvhome.core.config.TvConfig

/** The motion keys of the config, for every window's [TvTheme]. */
public fun TvConfig.motion(): Motion = Motion(animations = animations, reducedMotion = reducedMotion, focusOvershoot = focusOvershoot)

/** glass_style, for [TvTheme]. */
public fun TvConfig.focusStyle(): FocusStyle =
    when (glassStyle) {
        GlassStyle.Glass -> FocusStyle.Glass
        GlassStyle.Rim -> FocusStyle.Rim
        GlassStyle.Halo -> FocusStyle.Halo
    }
