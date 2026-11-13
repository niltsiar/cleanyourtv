package dev.niltsiar.tvhome.service.keys

import dev.niltsiar.tvhome.core.config.TvConfig

/**
 * What [KeyService] reads from the config and the Open on start pick, and nothing else: a change to any other field, such as
 * the columns the person sets on the TV, or a pick that leaves the power-on plan as it was, compares equal and leaves the
 * service as it is.
 */
internal data class KeyServiceConfig(
    val appKeys: List<String>,
    val restoreKeyServices: Boolean,
    val liveTvPackage: String?,
    val consumeVolumeKeys: Boolean,
    val stockHomeRedirect: String?,
    val powerOn: PowerOnPlan,
    val keyFilter: Boolean,
    val gamepadPassthrough: Boolean,
    val volumePausesFilter: Boolean,
    val homeLongPressHome: Boolean,
    val settingsKeyScancodes: List<Int>,
    val settingsKeyKeycodes: List<Int>,
    val settingsKeyClearTask: Boolean,
    val inputKeyKeycodes: List<Int>,
    val captureInputKeys: Boolean,
)

/** [pick] is the Open on start app ([dev.niltsiar.tvhome.core.config.ConfigRepository.openOnStartApp]), which only the power-on plan reads. */
internal fun TvConfig.forKeyService(pick: String?): KeyServiceConfig =
    KeyServiceConfig(
        appKeys = appKeys,
        restoreKeyServices = restoreKeyServices,
        liveTvPackage = liveTvPackage,
        consumeVolumeKeys = consumeVolumeKeys,
        stockHomeRedirect = stockHomeRedirect,
        powerOn = powerOnPlan(this, pick),
        keyFilter = keyFilter,
        gamepadPassthrough = gamepadPassthrough,
        volumePausesFilter = volumePausesFilter,
        homeLongPressHome = homeLongPressHome,
        settingsKeyScancodes = settingsKeyScancodes,
        settingsKeyKeycodes = settingsKeyKeycodes,
        settingsKeyClearTask = settingsKeyClearTask,
        inputKeyKeycodes = inputKeyKeycodes,
        captureInputKeys = captureInputKeys,
    )
