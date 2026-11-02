package com.dsh.mobile.data

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class LanPairingTargetTest {
    private val attrs = mapOf("v" to "1", "session" to "a".repeat(32), "name" to "Synthetic computer", "tls" to "0")
    private fun ip(a: Int, b: Int, c: Int, d: Int) = InetAddress.getByAddress(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()))

    @Test fun resolvesDynamicPortAndDeduplicatesMultipleInterfaces() {
        val target = lanPairingTarget(attrs + ("ipv4" to "192.0.2.10,192.0.2.11"), 17421, listOf(ip(192, 0, 2, 10)))!!
        assertEquals(listOf("http://192.0.2.10:17421", "http://192.0.2.11:17421"), target.urls)
        val merged = mergeLanTargets(target, target.copy(urls = listOf("http://192.0.2.12:17421")))
        assertEquals(3, merged.urls.size)
        assertEquals("a".repeat(32), merged.session)
    }

    @Test fun rejectsUntrustedRecordsAndNonNumericFallbacks() {
        val ips = listOf(ip(192, 0, 2, 10))
        for (invalid in listOf(attrs + ("v" to "2"), attrs + ("session" to "../../bad"), attrs + ("name" to "bad\nname"), attrs + ("tls" to "unknown"))) {
            assertNull(lanPairingTarget(invalid, 7941, ips))
        }
        assertNull(lanPairingTarget(attrs, 0, ips))
        assertNull(lanPairingTarget(attrs + ("ipv4" to "example.test,999.0.0.1"), 7941, emptyList()))
        assertNull(lanPairingTarget(attrs, 7941, listOf(ip(127, 0, 0, 1), ip(224, 0, 0, 1), ip(0, 0, 0, 0))))
    }

    @Test fun tlsAndIpv6ArePreservedAndDifferentSessionsNeverMerge() {
        val ipv6 = InetAddress.getByAddress(byteArrayOf(0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))
        val target = lanPairingTarget(attrs + ("tls" to "1"), 8443, listOf(ipv6))!!
        assertEquals("https://[2001:db8:0:0:0:0:0:1]:8443", target.urls.single())
        val other = target.copy(session = "b".repeat(32), urls = listOf("https://192.0.2.12:8443"))
        assertEquals(other, mergeLanTargets(target, other))
    }
}
