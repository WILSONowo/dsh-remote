package com.dsh.mobile.data

import org.junit.Assert.*
import org.junit.Test

class RelayProfileTest {
    private val relay = HostProfile("a", "", "", connectionMode = "relay", computerId = "00000000-0000-4000-8000-000000000001", relayServerUrl = "https://relay.example:8443/", relayHostId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
    @Test fun directProfilesRemainCompatible() {
        val p = ProfileCodec.decode("""[{"id":"old","remark":"PC","url":"192.168.1.2:8787"}]""").single()
        assertEquals("http://192.168.1.2:8787", p.connectionUrl())
        assertEquals("direct", p.connectionMode)
    }
    @Test fun relayEndpointPreservesHostForEveryApi() {
        assertEquals("https://relay.example:8443/h/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", relay.connectionUrl())
        assertEquals(relay, ProfileCodec.decode(ProfileCodec.encode(listOf(relay))).single())
    }
    @Test fun rejectsInsecureAmbiguousOrInjectedRelayEndpoints() {
        for (url in listOf("http://relay.example", "https://user:pass@relay.example", "https://relay.example/path", "https://relay.example?token=x", "https://relay.example#fragment")) {
            assertTrue(url, runCatching { relay.copy(relayServerUrl = url).connectionUrl() }.isFailure)
        }
        for (id in listOf("../other", "a/b", "", "a?x", "a%2fb")) assertTrue(runCatching { relay.copy(relayHostId = id).connectionUrl() }.isFailure)
        assertTrue(runCatching { relay.copy(trustSelfSigned = true).connectionUrl() }.isFailure)
    }
    @Test fun legacyManualRelayProfilesRequireRescan() {
        assertTrue(runCatching { relay.copy(computerId = "").connectionUrl() }.isFailure)
        assertTrue(runCatching { relay.copy(relayHostId = "old-manual-host").connectionUrl() }.isFailure)
    }
    @Test fun changingHostDoesNotSendOldCredentialsToNewHost() {
        val paired = relay.copy(paired = true, channelToken = "secret", deviceMac = "mac")
        val changed = resetPairingIfEndpointChanged(paired, paired.copy(relayHostId = "another"))
        assertFalse(changed.paired); assertEquals("", changed.channelToken); assertEquals("", changed.deviceMac)
        assertEquals("secret", resetPairingIfEndpointChanged(paired, paired.copy(remark = "rename")).channelToken)
    }
}
