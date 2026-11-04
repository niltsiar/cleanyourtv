package dev.niltsiar.tvhome.data.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import dev.niltsiar.tvhome.core.model.NetworkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

private const val TAG = "TvHome.Network"

/**
 * The network button's state, read the way the reference launcher reads it. The default network's callback and a
 * callback for any VPN say when to look again; each look scans every network ([networkState]).
 * Callbacks are registered only while Home is started ([onStart], [onStop]); the last state stays until then.
 * Reading the Wi-Fi name needs ACCESS_FINE_LOCATION, granted with `pm grant`; without it the name is null.
 */
public class NetworkMonitor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val started = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    public val state: Flow<NetworkState> =
        started
            .transformLatest { if (it) emitAll(changes().map { read() }.flowOn(Dispatchers.IO)) }
            .distinctUntilChanged()

    /** Home is on screen. Main thread. */
    public fun onStart() {
        started.value = true
    }

    /** Home left the screen. Main thread. */
    public fun onStop() {
        started.value = false
    }

    /** One item now, then one for each connectivity change; changes that come faster than a read are merged. */
    private fun changes(): Flow<Unit> =
        callbackFlow {
            val registered = mutableListOf<ConnectivityManager.NetworkCallback>()
            try {
                val defaultNetwork =
                    object : ConnectivityManager.NetworkCallback() {
                        private var lastValidated = false

                        override fun onAvailable(network: Network) {
                            trySend(Unit)
                        }

                        override fun onLost(network: Network) {
                            trySend(Unit)
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            // Signal-strength updates land here too and change nothing the button shows.
                            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            if (validated != lastValidated) trySend(Unit)
                            lastValidated = validated
                        }
                    }
                connectivity.registerDefaultNetworkCallback(defaultNetwork)
                registered += defaultNetwork
                // A VPN network lacks NOT_VPN, which a request has by default. This one also sees a VPN that the default network skips.
                val vpnRequest =
                    NetworkRequest
                        .Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .build()
                val anyVpn =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(Unit)
                        }

                        override fun onLost(network: Network) {
                            trySend(Unit)
                        }
                    }
                connectivity.registerNetworkCallback(vpnRequest, anyVpn)
                registered += anyVpn
            } catch (e: RuntimeException) {
                // SecurityException without ACCESS_NETWORK_STATE; TooManyRequestsException past the app's callback limit.
                Log.w(TAG, "cannot follow the network: $e")
            }
            trySend(Unit)
            awaitClose { registered.forEach(connectivity::unregisterNetworkCallback) }
        }.conflate()

    @Suppress("DEPRECATION")
    private fun read(): NetworkState =
        try {
            val facts =
                connectivity.allNetworks.mapNotNull { network ->
                    connectivity.getNetworkCapabilities(network)?.let { caps ->
                        NetworkFacts(
                            vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                            internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                            ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                            wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                        )
                    }
                }
            networkState(facts) { appContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid }
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot read the network: $e")
            NetworkState()
        }
}
