package com.dsh.mobile.data

import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class PairingLinkTest {
    private val now = 1_800_000_000_000L
    private fun payload() = PairingLink(1, "https://relay.example.test", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "00000000-0000-4000-8000-000000000001", "Synthetic computer", "a".repeat(64), now + 120_000)
    private fun encode(link: PairingLink) = "dshremote://pair#" +
        Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(link).toByteArray())

    private fun compactFixture(): Pair<String, PairingLink> {
        val json = javaClass.getResourceAsStream("/pairing-compact.json")!!.bufferedReader().use { it.readText() }
        val obj = Json.parseToJsonElement(json).jsonObject
        return obj.getValue("link").jsonPrimitive.content to Json.decodeFromString<PairingLink>(obj.getValue("payload").toString())
    }

    @Test fun compactFormatMatchesPluginVectorAndKeepsFullCredentials() {
        val (link, expected) = compactFixture()
        assertEquals(expected, PairingLink.parse(link, now))
        assertEquals(64, PairingLink.parse(link, now).ticket.length)
        assertEquals(expected, PairingLink.parse(encode(expected), now))
        assertFalse(ProfileCodec.encode(listOf(PairingLink.parse(link, now).profile())).contains(expected.ticket))
    }

    @Test fun compactFormatRejectsTruncationTrailingDataAndInvalidUtf8() {
        val (link, _) = compactFixture()
        val original = Base64.getUrlDecoder().decode(link.substringAfter("#2."))
        fun wrap(bytes: ByteArray) = "dshremote://pair#2." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        for (length in 0 until original.size) assertTrue(runCatching { PairingLink.parse(wrap(original.copyOf(length)), now) }.isFailure)
        assertTrue(runCatching { PairingLink.parse(wrap(original + byteArrayOf(0)), now) }.isFailure)
        val badLength = original.copyOf().also { it[72] = 0x7f; it[73] = 0x7f }
        val badUtf8 = original.copyOf().also { it[it.lastIndex] = 0xff.toByte() }
        val badServer = original.copyOf().also { it[74] = 'f'.code.toByte() }
        for (bytes in listOf(badLength, badUtf8, badServer)) assertTrue(runCatching { PairingLink.parse(wrap(bytes), now) }.isFailure)
        assertTrue(runCatching { PairingLink.parse(link, now + 120_000) }.isFailure)
        assertTrue(runCatching { PairingLink.parse(link, now - 600_000) }.isFailure)
    }

    @Test fun scanCreatesRelayProfileWithoutPersistingTemporaryTicket() {
        val link = PairingLink.parse(encode(payload()), now)
        val profile = link.profile()
        assertEquals("https://relay.example.test/h/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", profile.connectionUrl())
        assertEquals(link.computerId, profile.computerId)
        assertEquals("Synthetic computer", profile.deviceModel)
        assertFalse(profile.paired)
        assertEquals("", profile.channelToken)
        val stored = ProfileCodec.encode(listOf(profile))
        assertFalse(stored.contains(link.ticket))
        assertFalse(stored.contains("expiresAt"))
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

    @Test fun rejectsExpiredFutureMalformedAndUnsafeEndpoints() {
        val invalid = listOf(
            payload().copy(v = 2), payload().copy(expiresAt = now), payload().copy(expiresAt = now + 600_000),
            payload().copy(ticket = "short"), payload().copy(computerId = "hardware-mac"),
            payload().copy(hostId = "../other"), payload().copy(name = "\nname"),
            payload().copy(server = "http://relay.example.test"),
            payload().copy(server = "https://user:password@relay.example.test"),
            payload().copy(server = "https://relay.example.test/path"),
            payload().copy(server = "https://relay.example.test?token=x"),
        )
        invalid.forEach { assertTrue(runCatching { PairingLink.parse(encode(it), now) }.isFailure) }
        listOf("dshremote://pair#invalid", "https://example.test", "dshremote://user@pair#abc", "a".repeat(4097))
            .forEach { assertTrue(runCatching { PairingLink.parse(it, now) }.isFailure) }
    }

    @Test fun changingEndpointClearsComputerIdentityAndAuthorization() {
        val original = payload().profile().copy(paired = true, channelToken = "b".repeat(64))
        val moved = resetPairingIfEndpointChanged(original, original.copy(relayHostId = "other"))
        assertEquals("", moved.computerId)
        assertEquals("", moved.channelToken)
        assertFalse(moved.paired)
    }
}
