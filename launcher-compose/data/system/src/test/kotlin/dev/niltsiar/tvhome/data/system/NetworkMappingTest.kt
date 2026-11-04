package dev.niltsiar.tvhome.data.system

import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkMappingTest {
    private val wifi = NetworkFacts(vpn = false, internet = true, validated = true, ethernet = false, wifi = true)
    private val ethernet = wifi.copy(ethernet = true, wifi = false)
    private val vpn = NetworkFacts(vpn = true, internet = true, validated = true, ethernet = false, wifi = true)

    @Test
    fun no_network_with_internet_is_offline_even_with_a_vpn_listed() {
        assertEquals(NetworkState(), networkState(emptyList()) { error("no Wi-Fi lookup") })
        assertEquals(NetworkState(), networkState(listOf(vpn, wifi.copy(internet = false))) { error("no Wi-Fi lookup") })
    }

    @Test
    fun wifi_carries_its_cleaned_name() {
        val state = networkState(listOf(wifi)) { "\"Living room\"" }
        assertEquals(NetworkState(NetworkLink.Wifi, ssid = "Living room", validated = true), state)
    }

    @Test
    fun ethernet_wins_over_wifi_and_asks_for_no_name() {
        val state = networkState(listOf(wifi, ethernet)) { error("no Wi-Fi lookup") }
        assertEquals(NetworkState(NetworkLink.Ethernet, validated = true), state)
    }

    @Test
    fun a_vpn_network_sets_the_flag_and_is_not_the_link() {
        val state = networkState(listOf(vpn, wifi.copy(validated = false))) { "Home" }
        assertEquals(NetworkState(NetworkLink.Wifi, ssid = "Home", validated = false, vpn = true), state)
    }

    @Test
    fun one_validated_network_is_enough() {
        val state = networkState(listOf(wifi.copy(validated = false), ethernet.copy(validated = true))) { null }
        assertEquals(true, state.validated)
    }

    @Test
    fun online_by_another_route_shows_as_wifi_without_a_name() {
        val tethered = wifi.copy(wifi = false)
        assertEquals(NetworkState(NetworkLink.Wifi, validated = true), networkState(listOf(tethered)) { error("no Wi-Fi lookup") })
    }

    @Test
    fun names_the_system_hides_are_dropped() {
        assertNull(cleanSsid("<unknown ssid>"))
        assertNull(cleanSsid("0x"))
        assertNull(cleanSsid("\"\""))
        assertNull(cleanSsid("  "))
        assertNull(cleanSsid(null))
        assertEquals("Cafe 0x", cleanSsid(" \"Cafe 0x\" "))
    }
}
