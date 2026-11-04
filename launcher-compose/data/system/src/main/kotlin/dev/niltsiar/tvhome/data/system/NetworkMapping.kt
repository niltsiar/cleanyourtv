package dev.niltsiar.tvhome.data.system

import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState

/** What one of ConnectivityManager's networks can do, as the network button reads it. */
internal data class NetworkFacts(
    val vpn: Boolean,
    val internet: Boolean,
    val validated: Boolean,
    val ethernet: Boolean,
    val wifi: Boolean,
)

/**
 * The button's state from every network, not just the default one: with a VPN up the default network is the VPN
 * itself. A VPN network only sets [NetworkState.vpn]. The first network with internet puts the TV online, and
 * Ethernet wins over Wi-Fi. Online by another route (USB tethering, cellular) shows as Wi-Fi without a name.
 * [wifiSsid] is read only when the link is Wi-Fi, as it asks the system.
 */
internal fun networkState(
    networks: List<NetworkFacts>,
    wifiSsid: () -> String?,
): NetworkState {
    val online = networks.filter { !it.vpn && it.internet }
    if (online.isEmpty()) return NetworkState()
    val vpn = networks.any { it.vpn }
    val validated = online.any { it.validated }
    return if (online.any { it.ethernet }) {
        NetworkState(NetworkLink.Ethernet, validated = validated, vpn = vpn)
    } else {
        val ssid = if (online.any { it.wifi }) cleanSsid(wifiSsid()) else null
        NetworkState(NetworkLink.Wifi, ssid = ssid, validated = validated, vpn = vpn)
    }
}

/** The name without the quotes the system puts around it; null when it is blank or the system hides it ("<unknown ssid>", "0x"). */
internal fun cleanSsid(raw: String?): String? {
    val ssid = raw?.replace("\"", "")?.trim().orEmpty()
    return ssid.takeUnless { it.isEmpty() || it.contains("unknown") || it == "0x" }
}
