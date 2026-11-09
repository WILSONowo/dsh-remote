package com.dsh.mobile.data

import java.nio.ByteBuffer
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class PairingLinkTest {
    private val now = 1_800_000_000_000L
    private val pin = "c".repeat(64)
    private fun payload() = PairingLink(1, "https://relay.example.test", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "00000000-0000-4000-8000-000000000001", "Synthetic computer", "a".repeat(64), now + 120_000, pin)

    /** Independent v3 encoder (big-endian, length-prefixed UTF-8), mirroring docs/e2e-secure-channel.md. */
    private fun encode(link: PairingLink): String {
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val server = link.server.toByteArray(); val name = link.name.toByteArray()
        val buffer = ByteBuffer.allocate(108 + server.size + name.size)
        buffer.putLong(link.expiresAt)
        buffer.put(hex(link.hostId)).put(hex(link.computerId.replace("-", ""))).put(hex(link.ticket)).put(hex(link.pin))
        buffer.putShort(server.size.toShort()).put(server).putShort(name.size.toShort()).put(name)
        return "dshremote://pair#3." + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array())
    }

    private val fixture by lazy {
        val json = javaClass.getResourceAsStream("/pairing-compact.json")!!.bufferedReader().use { it.readText() }
        Json.parseToJsonElement(json).jsonObject
    }

    private fun compactFixture(): Pair<String, PairingLink> =
        fixture.getValue("link").jsonPrimitive.content to Json.decodeFromString<PairingLink>(fixture.getValue("payload").toString())

    @Test fun compactV3MatchesPluginVectorAndCarriesThePin() {
        val (link, expected) = compactFixture()
        val parsed = PairingLink.parse(link, now)
        assertEquals(expected, parsed)
        assertEquals(64, parsed.ticket.length)
        assertEquals(expected.pin, parsed.pin)
        assertEquals(expected, PairingLink.parse(encode(expected), now))
        val stored = ProfileCodec.encode(listOf(parsed.profile()))
        assertFalse(stored.contains(expected.ticket))
        assertTrue(stored.contains(expected.pin))
    }

    @Test fun pinlessV1AndV2LinksAreRefusedWithAnUpgradeHint() {
        val (link, expected) = compactFixture()
        val v1 = "dshremote://pair#" + Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(PairingLink.serializer(), expected).toByteArray())
        val v2 = link.replace("#3.", "#2.")
        for (legacy in listOf(v1, v2)) {
            val error = runCatching { PairingLink.parse(legacy, now) }.exceptionOrNull()
            assertNotNull(error); assertTrue(error!!.message!!.contains("4.0"))
        }
    }

    @Test fun compactFormatRejectsTruncationTrailingDataAndInvalidUtf8() {
        val (link, _) = compactFixture()
        val original = Base64.getUrlDecoder().decode(link.substringAfter("#3."))
        fun wrap(bytes: ByteArray) = "dshremote://pair#3." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        for (length in 0 until original.size) assertTrue(runCatching { PairingLink.parse(wrap(original.copyOf(length)), now) }.isFailure)
        assertTrue(runCatching { PairingLink.parse(wrap(original + byteArrayOf(0)), now) }.isFailure)
        val badLength = original.copyOf().also { it[104] = 0x7f; it[105] = 0x7f }
        val badUtf8 = original.copyOf().also { it[it.lastIndex] = 0xff.toByte() }
        val badServer = original.copyOf().also { it[106] = 'f'.code.toByte() }
        for (bytes in listOf(badLength, badUtf8, badServer)) assertTrue(runCatching { PairingLink.parse(wrap(bytes), now) }.isFailure)
        assertTrue(runCatching { PairingLink.parse(link, now + 120_000) }.isFailure)
        assertTrue(runCatching { PairingLink.parse(link, now - 600_000) }.isFailure)
    }

    @Test fun scanCreatesPinnedRelayProfileOverTheSecureChannel() {
        val link = PairingLink.parse(encode(payload()), now)
        val profile = link.profile()
        assertEquals("wss://relay.example.test/relay/v2/connect/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", profile.transportUrl())
        assertTrue(profile.connectionUrl().matches(Regex("https://[a-f0-9]{16}\\.e2e\\.dsh\\.invalid")))
        assertFalse(profile.connectionUrl().contains("relay.example.test"))
        assertEquals("https://relay.example.test", profile.reachabilityUrl())
        assertEquals(pin, profile.serverPin)
        assertEquals(link.computerId, profile.computerId)
        assertFalse(profile.paired)
        assertEquals("", profile.channelToken)
        val stored = ProfileCodec.encode(listOf(profile))
        assertFalse(stored.contains(link.ticket))
        assertFalse(stored.contains("expiresAt"))
    }

    @Test fun relayRecordsWithoutPinCannotConnectButCanBeRescanned() {
        val legacy = payload().profile().copy(serverPin = "", paired = true, channelToken = "b".repeat(64))
        assertTrue(runCatching { legacy.connectionUrl() }.isFailure)
        val rescanned = payload().profile(legacy)
        assertEquals(legacy.id, rescanned.id)
        assertEquals(pin, rescanned.serverPin)
        assertTrue(runCatching { rescanned.connectionUrl() }.isSuccess)
    }

    @Test fun duplicateScanAndRenamePreserveCredentialsAndIdentity() {
        val original = payload().profile().copy(paired = true, channelToken = "b".repeat(64), remark = "My label")
        val renamed = payload().copy(name = "New display name").profile(original)
        assertEquals(original.id, renamed.id)
        assertEquals(original.channelToken, renamed.channelToken)
        assertEquals(original.computerId, renamed.computerId)
        assertEquals(original.remark, renamed.remark)
        assertEquals("New display name", renamed.deviceModel)
        assertTrue(renamed.paired)
        assertTrue(runCatching { payload().copy(computerId = "00000000-0000-4000-8000-000000000002").profile(original) }.isFailure)
        assertTrue(runCatching { payload().copy(hostId = "another").profile(original) }.isFailure)
    }

    @Test fun rescanWithANewComputerKeyRequiresPairingAgain() {
        val original = payload().profile().copy(paired = true, channelToken = "b".repeat(64))
        val rekeyed = payload().copy(pin = "d".repeat(64)).profile(original)
        assertEquals("d".repeat(64), rekeyed.serverPin)
        assertFalse(rekeyed.paired)
        assertEquals("", rekeyed.channelToken)
    }

    @Test fun rejectsExpiredFutureMalformedAndUnsafeEndpoints() {
        val invalid = listOf(
            payload().copy(expiresAt = now), payload().copy(expiresAt = now + 600_000),
            payload().copy(name = "\nname"), payload().copy(name = " "),
            payload().copy(server = "http://relay.example.test"),
            payload().copy(server = "https://user:password@relay.example.test"),
            payload().copy(server = "https://relay.example.test/path"),
            payload().copy(server = "https://relay.example.test?token=x"),
        )
        invalid.forEach { assertTrue(it.toString(), runCatching { PairingLink.parse(encode(it), now) }.isFailure) }
        listOf("dshremote://pair#invalid", "dshremote://pair#3.!!", "https://example.test", "dshremote://user@pair#abc", "a".repeat(4097))
            .forEach { assertTrue(runCatching { PairingLink.parse(it, now) }.isFailure) }
    }

    @Test fun changingEndpointClearsComputerIdentityAndAuthorization() {
        val original = payload().profile().copy(paired = true, channelToken = "b".repeat(64))
        val moved = resetPairingIfEndpointChanged(original, original.copy(relayHostId = "b".repeat(32)))
        assertEquals("", moved.computerId)
        assertEquals("", moved.channelToken)
        assertFalse(moved.paired)
        assertEquals(pin, moved.serverPin)
    }

    @Test fun directQrMatchesPluginVectorAndPinsTheKey() {
        val direct = fixture.getValue("direct").jsonObject
        val link = DirectLink.parse(direct.getValue("link").jsonPrimitive.content)
        val expected = direct.getValue("payload").jsonObject
        assertEquals(expected.getValue("address").jsonPrimitive.content, link.address)
        assertEquals(expected.getValue("name").jsonPrimitive.content, link.name)
        assertEquals(expected.getValue("pin").jsonPrimitive.content, link.pin)
        val profile = link.profile(null)
        assertEquals("ws://192.0.2.10:8787/api/remote-access/secure", profile.transportUrl())
        assertEquals(link.pin, profile.serverPin)
        val paired = profile.copy(paired = true, channelToken = "b".repeat(64))
        assertTrue(link.profile(paired).paired)
        assertFalse(link.copy(pin = "e".repeat(64)).profile(paired).paired)
        for (raw in listOf("dshremote://direct#2.AAAA", "dshremote://direct#1.!!", "dshremote://pair#1.AAAA", "dshremote://direct"))
            assertTrue(raw, runCatching { DirectLink.parse(raw) }.isFailure)
    }

    @Test fun manualDirectAddressIsTrustOnFirstUseAndForgetsTheKeyWhenMoved() {
        val manual = HostProfile(id = "p", remark = "", url = "192.0.2.10:8787")
        assertEquals("ws://192.0.2.10:8787/api/remote-access/secure", manual.transportUrl())
        assertEquals("", manual.serverPin)
        // Recording the first-use key is not a new connection; a different key is.
        assertTrue(manual.sameConnection(manual.copy(serverPin = pin)))
        assertFalse(manual.copy(serverPin = pin).sameConnection(manual.copy(serverPin = "d".repeat(64))))
        val pinned = manual.copy(serverPin = pin, paired = true, channelToken = "b".repeat(64))
        val moved = resetPairingIfEndpointChanged(pinned, pinned.copy(url = "192.0.2.11:8787"))
        assertEquals("", moved.serverPin)
        assertFalse(moved.paired)
        assertTrue(runCatching { manual.copy(serverPin = "not-a-pin").connectionUrl() }.isFailure)
    }
}
