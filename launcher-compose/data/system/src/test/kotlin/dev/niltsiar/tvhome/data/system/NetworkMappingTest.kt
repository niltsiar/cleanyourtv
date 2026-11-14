package dev.niltsiar.tvhome.data.system

import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.model.NetworkLink
import dev.niltsiar.tvhome.core.model.NetworkState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val NetworkMappingTest by testSuite {
    val wifi = NetworkFacts(vpn = false, internet = true, validated = true, ethernet = false, wifi = true)
    val ethernet = wifi.copy(ethernet = true, wifi = false)
    val vpn = NetworkFacts(vpn = true, internet = true, validated = true, ethernet = false, wifi = true)

    test("no network with internet is offline even with a vpn listed") {
        networkState(emptyList()) { error("no Wi-Fi lookup") } shouldBe NetworkState()
        networkState(listOf(vpn, wifi.copy(internet = false))) { error("no Wi-Fi lookup") } shouldBe NetworkState()
    }

    test("wifi carries its cleaned name") {
        val state = networkState(listOf(wifi)) { "\"Living room\"" }
        state shouldBe NetworkState(NetworkLink.Wifi, ssid = "Living room", validated = true)
    }

    test("ethernet wins over wifi and asks for no name") {
        val state = networkState(listOf(wifi, ethernet)) { error("no Wi-Fi lookup") }
        state shouldBe NetworkState(NetworkLink.Ethernet, validated = true)
    }

    test("a vpn network sets the flag and is not the link") {
        val state = networkState(listOf(vpn, wifi.copy(validated = false))) { "Home" }
        state shouldBe NetworkState(NetworkLink.Wifi, ssid = "Home", validated = false, vpn = true)
    }

    test("one validated network is enough") {
        val state = networkState(listOf(wifi.copy(validated = false), ethernet.copy(validated = true))) { null }
        state.validated shouldBe true
    }

    test("online by another route shows as wifi without a name") {
        val tethered = wifi.copy(wifi = false)
        networkState(listOf(tethered)) { error("no Wi-Fi lookup") } shouldBe NetworkState(NetworkLink.Wifi, validated = true)
    }

    test("names the system hides are dropped") {
        cleanSsid("<unknown ssid>").shouldBeNull()
        cleanSsid("0x").shouldBeNull()
        cleanSsid("\"\"").shouldBeNull()
        cleanSsid("  ").shouldBeNull()
        cleanSsid(null).shouldBeNull()
        cleanSsid(" \"Cafe 0x\" ") shouldBe "Cafe 0x"
    }
}
