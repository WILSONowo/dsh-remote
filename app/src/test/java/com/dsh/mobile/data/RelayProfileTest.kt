package com.dsh.mobile.data

import org.junit.Assert.*
import org.junit.Test

class RelayProfileTest {
    private val pin = "c".repeat(64)
    private val relay = HostProfile("a", "", "", connectionMode = "relay", computerId = "00000000-0000-4000-8000-000000000001",
        relayServerUrl = "https://relay.example:8443/", relayHostId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", serverPin = pin)

    @Test fun directProfilesRemainCompatibleAndUseTheSecureEntry() {
        val p = ProfileCodec.decode("""[{"id":"old","remark":"PC","url":"192.168.1.2:8787"}]""").single()
        assertEquals("http://192.168.1.2:8787", p.reachabilityUrl())
        assertEquals("ws://192.168.1.2:8787/api/remote-access/secure", p.transportUrl())
        assertEquals("wss://pc.example/api/remote-access/secure", p.copy(url = "https://pc.example").transportUrl())
        assertEquals("direct", p.connectionMode)
        assertEquals("", p.serverPin)
    }

    @Test fun relayTrafficGoesThroughTheOpaqueSpliceNotThePlaintextPath() {
        assertEquals("wss://relay.example:8443/relay/v2/connect/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", relay.transportUrl())
        assertEquals("https://relay.example:8443", relay.reachabilityUrl())
        assertFalse(relay.connectionUrl().contains("/h/"))
        assertTrue(relay.connectionUrl().endsWith(".e2e.dsh.invalid"))
        assertEquals(relay, ProfileCodec.decode(ProfileCodec.encode(listOf(relay))).single())
    }

    @Test fun rejectsInsecureAmbiguousOrInjectedRelayEndpoints() {
        for (url in listOf("http://relay.example", "https://user:pass@relay.example", "https://relay.example/path", "https://relay.example?token=x", "https://relay.example#fragment")) {
            assertTrue(url, runCatching { relay.copy(relayServerUrl = url).connectionUrl() }.isFailure)
        }
        for (id in listOf("../other", "a/b", "", "a?x", "a%2fb")) assertTrue(runCatching { relay.copy(relayHostId = id).connectionUrl() }.isFailure)
        assertTrue(runCatching { relay.copy(trustSelfSigned = true).connectionUrl() }.isFailure)
    }

    @Test fun legacyRelayProfilesRequireRescan() {
        assertTrue(runCatching { relay.copy(computerId = "").connectionUrl() }.isFailure)
        assertTrue(runCatching { relay.copy(relayHostId = "old-manual-host").connectionUrl() }.isFailure)
        // No first-use trust through a relay: the key must come from the computer's QR.
        assertTrue(runCatching { relay.copy(serverPin = "").connectionUrl() }.isFailure)
    }

    @Test fun changingHostDoesNotSendOldCredentialsToNewHost() {
        val paired = relay.copy(paired = true, channelToken = "secret", deviceMac = "mac")
        val changed = resetPairingIfEndpointChanged(paired, paired.copy(relayHostId = "b".repeat(32)))
        assertFalse(changed.paired); assertEquals("", changed.channelToken); assertEquals("", changed.deviceMac)
        assertEquals("secret", resetPairingIfEndpointChanged(paired, paired.copy(remark = "rename")).channelToken)
    }
}
