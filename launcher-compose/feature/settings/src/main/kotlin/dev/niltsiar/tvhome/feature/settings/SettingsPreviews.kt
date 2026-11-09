package dev.niltsiar.tvhome.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.niltsiar.tvhome.core.config.ClockFormat
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.designsystem.Motion
import dev.niltsiar.tvhome.core.designsystem.TvTheme

@Preview(name = "Settings 1080p", device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun SettingsPreview() {
    val config =
        TvConfig(columns = 6, dockOpacity = 70, clockFormat = ClockFormat.Hour24, alwaysShowNames = true, homeIdleClockMinutes = 10)
    TvTheme(motion = Motion(animations = false)) {
        Box(Modifier.fillMaxSize()) {
            SettingsScreen(
                state = { SettingsUiState(settingsSections(config), followsTheme = false) },
                actions = SettingsActions(),
                onOpenTvSettings = {},
            )
        }
    }
}
