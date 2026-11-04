package dev.niltsiar.tvhome.core.model

import androidx.compose.runtime.Immutable

/** How the TV reaches the internet. */
public enum class NetworkLink {
    Offline,
    Wifi,
    Ethernet,
}

/**
 * What the network button shows. [ssid] is the Wi-Fi network's name where the TV lets the launcher read it.
 * [validated] is true when Android reached the internet through the link; a captive portal stays false until login.
 * [vpn] is true while a VPN is up.
 */
@Immutable
public data class NetworkState(
    val link: NetworkLink = NetworkLink.Offline,
    val ssid: String? = null,
    val validated: Boolean = false,
    val vpn: Boolean = false,
)
