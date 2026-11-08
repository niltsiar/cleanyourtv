package dev.niltsiar.tvhome

import dev.niltsiar.tvhome.core.config.GlassStyle
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.FocusStyle
import dev.niltsiar.tvhome.core.designsystem.Motion

/** The motion keys of the config, for every window's [dev.niltsiar.tvhome.core.designsystem.TvTheme]. */
internal fun TvConfig.motion(): Motion = Motion(animations = animations, reducedMotion = reducedMotion, focusOvershoot = focusOvershoot)

/** glass_style, for [dev.niltsiar.tvhome.core.designsystem.TvTheme]. */
internal fun TvConfig.focusStyle(): FocusStyle =
    when (glassStyle) {
        GlassStyle.Glass -> FocusStyle.Glass
        GlassStyle.Rim -> FocusStyle.Rim
        GlassStyle.Halo -> FocusStyle.Halo
    }
