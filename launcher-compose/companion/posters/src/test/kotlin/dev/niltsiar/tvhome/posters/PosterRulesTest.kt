package dev.niltsiar.tvhome.posters

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/** The contract Home's `WebPosters.kt` holds the other side of. */
val PosterContractTest by testSuite {
    test("the authority, path and query parameters are the ones Home sends") {
        listOf(POSTER_AUTHORITY, POSTER_PATH, PARAM_URL, PARAM_WIDTH, PARAM_HEIGHT, PARAM_LAN) shouldBe
            listOf("dev.niltsiar.tvhome.posters", "poster", "u", "w", "h", "lan")
    }
}

/** Which URLs the helper fetches, before any address is resolved. */
val PosterUrlTest by testSuite {
    fun refused(
        lan: Boolean,
        vararg urls: String,
    ) {
        for (url in urls) withClue(url) { posterUrl(url, lan).shouldBeNull() }
    }

    test("without lan an IP literal is refused, even a public one") {
        refused(lan = false, "https://93.184.216.34/p.jpg", "https://[2606:2800:220:1:248:1893:25c8:1946]/p.jpg")
    }

    test("a numeric host that is not a dotted quad is refused in both modes") {
        refused(lan = false, "https://127.1/p.jpg", "https://2130706433/p.jpg", "https://0x7f.0.0.1/p.jpg")
        refused(lan = true, "http://127.1/p.jpg", "http://2130706433/p.jpg", "http://192.168.1/p.jpg")
    }

    test("without lan only https on port 443 is fetched") {
        refused(lan = false, "http://posters.test/p.jpg", "https://posters.test:8443/p.jpg", "ftp://posters.test/p.jpg")
        posterUrl("https://posters.test:443/p.jpg", lan = false).shouldNotBeNull()
    }

    test("a URL with user info is refused in both modes") {
        refused(lan = false, "https://me@posters.test/p.jpg", "https://me:secret@posters.test/p.jpg")
        refused(lan = true, "http://me:secret@192.168.1.20/p.jpg")
    }

    test("a URL of 2049 characters is refused and one of 2048 is fetched") {
        val start = "https://posters.test/"
        posterUrl(start + "a".repeat(2048 - start.length), lan = false).shouldNotBeNull()
        posterUrl(start + "a".repeat(2049 - start.length), lan = false).shouldBeNull()
    }

    test("with lan a private IP literal on any port is fetched and any other literal is refused") {
        posterUrl("http://192.168.1.20:8096/p.jpg", lan = true).shouldNotBeNull()
        posterUrl("http://[fd12::20]:32400/p.jpg", lan = true).shouldNotBeNull()
        refused(lan = true, "http://8.8.8.8/p.jpg", "http://127.0.0.1/p.jpg", "http://[::1]/p.jpg", "http://[::ffff:127.0.0.1]/p.jpg")
    }
}

/** The addresses the helper connects to. */
val PosterAddressTest by testSuite {
    fun range(address: String) = addressRange(InetAddress.getByName(address))

    test("loopback, any-local, link-local, CGNAT, multicast, reserved and their IPv4-mapped forms are refused") {
        val refused =
            listOf(
                "127.0.0.1",
                "0.0.0.0",
                "0.1.2.3",
                "169.254.169.254",
                "100.64.0.1",
                "100.127.255.255",
                "224.0.0.251",
                "255.255.255.255",
                "::",
                "::1",
                "::127.0.0.1",
                "fe80::1",
                "fec0::1",
                "ff02::fb",
                "::ffff:127.0.0.1",
                "::ffff:169.254.169.254",
                "::ffff:100.64.0.1",
            )
        for (address in refused) withClue(address) { range(address) shouldBe AddressRange.Refused }
    }

    test("10/8, 172.16/12, 192.168/16, fc00::/7 and their mapped forms are private, and their neighbours public") {
        val private = listOf("10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.1.20", "fc00::1", "fdff::1", "::ffff:192.168.1.20")
        val public = listOf("11.0.0.1", "172.15.0.1", "172.32.0.1", "192.169.0.1", "100.63.0.1", "100.128.0.1", "fbff::1", "2606:2800::1")
        for (address in private) withClue(address) { range(address) shouldBe AddressRange.Private }
        for (address in public) withClue(address) { range(address) shouldBe AddressRange.Public }
    }

    test("a host whose answer mixes a public and a refused address is not resolved") {
        val answer = listOf(InetAddress.getByName("93.184.216.34"), InetAddress.getByName("127.0.0.1"))
        val dns = PosterDns(Dns { answer }, lan = false)
        shouldThrow<UnknownHostException> { dns.lookup("posters.test") }
    }
}
