package dev.niltsiar.tvhome.posters

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/** Where an address leads: the internet, the home network, or a place the helper never connects to. */
internal enum class AddressRange { Public, Private, Refused }

/**
 * The range of [address]. Refused in every mode: loopback, any-local and the rest of 0.0.0.0/8, link-local, CGNAT 100.64/10,
 * multicast, 240/4, deprecated IPv6 site-local, and `::/96` with its IPv4-compatible forms. Private: 10/8, 172.16/12,
 * 192.168/16 and fc00::/7. An IPv4-mapped IPv6 address has the range of its IPv4 address.
 */
internal fun addressRange(address: InetAddress): AddressRange {
    val bytes = address.address.map { it.toInt() and 0xff }
    if (bytes.size == 4) return ipv4Range(bytes)
    if (bytes.subList(0, 10).all { it == 0 }) {
        return when {
            bytes[10] == 0xff && bytes[11] == 0xff -> ipv4Range(bytes.subList(12, 16))
            else -> AddressRange.Refused
        }
    }
    val first = bytes[0]
    val second = bytes[1]
    return when {
        first == 0xff -> AddressRange.Refused
        first == 0xfe && (second and 0x80) == 0x80 -> AddressRange.Refused
        (first and 0xfe) == 0xfc -> AddressRange.Private
        else -> AddressRange.Public
    }
}

private fun ipv4Range(bytes: List<Int>): AddressRange {
    val (first, second) = bytes
    return when {
        first == 0 || first == 127 || first >= 224 -> AddressRange.Refused
        first == 169 && second == 254 -> AddressRange.Refused
        first == 100 && second in 64..127 -> AddressRange.Refused
        first == 10 || (first == 172 && second in 16..31) || (first == 192 && second == 168) -> AddressRange.Private
        else -> AddressRange.Public
    }
}

/**
 * Resolves through [resolver] and hands OkHttp the addresses only when every one is [AddressRange.Private] for a [lan]
 * request, or [AddressRange.Public] for any other. OkHttp connects only to the addresses this returns, so a second DNS
 * answer cannot move the fetch elsewhere.
 */
internal class PosterDns(
    private val resolver: Dns,
    private val lan: Boolean,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val wanted = if (lan) AddressRange.Private else AddressRange.Public
        val addresses = resolver.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { addressRange(it) != wanted }) {
            throw UnknownHostException("$hostname resolves outside the ${wanted.name.lowercase()} range")
        }
        return addresses
    }
}
