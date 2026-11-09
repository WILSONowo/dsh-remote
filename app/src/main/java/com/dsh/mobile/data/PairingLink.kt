package com.dsh.mobile.data

import java.net.URI
import java.util.Base64
import java.util.UUID
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.Serializable

/** Short-lived scan data stays in memory, never in saved connection profiles (except the public key pin). */
@Serializable
data class PairingLink(
    val v: Int,
    val server: String,
    val hostId: String,
    val computerId: String,
    val name: String,
    val ticket: String,
    val expiresAt: Long,
    /** SHA-256 of the computer's TLS public key: the trust anchor of the end-to-end channel (docs/e2e-secure-channel.md). */
    val pin: String,
) {
    fun profile(existing: HostProfile? = null): HostProfile {
        val next = HostProfile(
            id = existing?.id ?: UUID.randomUUID().toString(), remark = "", url = "",
            connectionMode = "relay", relayServerUrl = server.trimEnd('/'), relayHostId = hostId,
            computerId = computerId, deviceModel = name, serverPin = pin,
        )
        if (existing == null) return next
        // Records from before end-to-end encryption have no pin, so compare the relay target itself.
        require(existing.connectionMode == "relay" && existing.relayServerUrl.trimEnd('/') == next.relayServerUrl &&
            existing.relayHostId == hostId) { "连接目标不一致，请重新扫码" }
        require(existing.computerId.isBlank() || existing.computerId == computerId) { "电脑身份已改变，请先删除旧记录再配对" }
        // The QR shown on the computer is authoritative. A different key means a re-installed plugin: pair again.
        return if (existing.serverPin.isNotEmpty() && existing.serverPin != pin)
            existing.copy(computerId = computerId, deviceModel = name, serverPin = pin, paired = false, channelToken = "")
        else existing.copy(computerId = computerId, deviceModel = name, serverPin = pin)
    }

    companion object {
        fun parse(raw: String, now: Long = System.currentTimeMillis()): PairingLink {
            require(raw.length <= 4096) { "连接信息过长" }
            val uri = URI(raw.trim())
            require(uri.scheme == "dshremote" && uri.host == "pair" && uri.userInfo == null &&
                uri.port == -1 && uri.path.isNullOrEmpty() && uri.query == null) { "请扫描电脑插件生成的配对二维码" }
            val fragment = requireNotNull(uri.rawFragment) { "缺少连接信息" }
            // v1 (JSON) and v2 carry no key pin and cannot establish an end-to-end channel.
            require(fragment.startsWith("3.")) { "二维码来自旧版电脑插件，不支持端到端加密，请将电脑插件更新到 4.0 后重新生成" }
            val payload = decodeCompact(fragment.substring(2))
            require(payload.expiresAt > now && payload.expiresAt - now <= 300_000) { "连接信息已过期或时间异常，请在电脑重新生成并检查两端时间" }
            require(Regex("[a-f0-9]{64}").matches(payload.ticket)) { "连接信息无效" }
            require(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}").matches(payload.computerId)) { "电脑身份无效" }
            require(payload.name.isNotBlank() && payload.name.length <= 64 && payload.name.none { it.code < 32 || it.code == 127 }) { "电脑名称无效" }
            payload.profile().connectionUrl() // Enforce strict HTTPS, opaque host ID and a valid pin.
            return payload
        }

        /** Big-endian v3 fields: expiry, host ID, computer UUID, ticket, key pin, length-prefixed UTF-8 strings. */
        private fun decodeCompact(encoded: String): PairingLink {
            require(encoded.matches(Regex("[A-Za-z0-9_-]+"))) { "连接信息编码无效" }
            val reader = CompactReader(Base64.getUrlDecoder().decode(encoded), 108..2412)
            val expiry = reader.long()
            val host = reader.hex(16)
            val computer = reader.hex(16).let { "${it.substring(0, 8)}-${it.substring(8, 12)}-${it.substring(12, 16)}-${it.substring(16, 20)}-${it.substring(20)}" }
            val ticket = reader.hex(32)
            val pin = reader.hex(32)
            val server = reader.text(2048)
            val name = reader.text(256)
            reader.end()
            return PairingLink(1, server, host, computer, name, ticket, expiry, pin)
        }
    }
}

/** Direct-address QR from the computer: address plus key pin, so scanned LAN pairing is not trust-on-first-use. */
data class DirectLink(val address: String, val name: String, val pin: String) {
    /** New record, or the existing record for this address with the scanned key (pair again if the key changed). */
    fun profile(existing: HostProfile?): HostProfile {
        val base = existing ?: HostProfile(id = UUID.randomUUID().toString(), remark = "", url = address, deviceModel = name)
        return if (base.serverPin.isNotEmpty() && base.serverPin != pin) base.copy(serverPin = pin, paired = false, channelToken = "")
        else base.copy(serverPin = pin)
    }

    companion object {
        fun isDirectLink(raw: String) = raw.trim().startsWith("dshremote://direct", ignoreCase = true)

        fun parse(raw: String): DirectLink {
            require(raw.length <= 4096) { "连接信息过长" }
            val uri = URI(raw.trim())
            require(uri.scheme == "dshremote" && uri.host == "direct" && uri.userInfo == null && uri.port == -1 &&
                uri.path.isNullOrEmpty() && uri.query == null) { "请扫描电脑插件生成的直连二维码" }
            val fragment = requireNotNull(uri.rawFragment) { "缺少连接信息" }
            require(fragment.startsWith("1.") && fragment.substring(2).matches(Regex("[A-Za-z0-9_-]+"))) { "直连二维码版本不支持，请更新 App" }
            val reader = CompactReader(Base64.getUrlDecoder().decode(fragment.substring(2)), 38..2400)
            val pin = reader.hex(32)
            val address = reader.text(2048)
            val name = reader.text(256)
            reader.end()
            require(name.isNotBlank() && name.length <= 64 && name.none { it.code < 32 || it.code == 127 }) { "电脑名称无效" }
            val parsed = URI(address)
            require(parsed.scheme in listOf("http", "https") && !parsed.host.isNullOrBlank() && parsed.userInfo == null &&
                parsed.query == null && parsed.fragment == null && parsed.path.isNullOrEmpty()) { "直连地址无效" }
            val link = DirectLink(address, name, pin)
            link.profile(null).connectionUrl()
            return link
        }
    }
}

/** Bounds-checked big-endian reader shared by the compact QR formats. */
private class CompactReader(bytes: ByteArray, sizes: IntRange) {
    private val buffer: ByteBuffer

    init {
        require(bytes.size in sizes) { "连接信息长度无效" }
        buffer = ByteBuffer.wrap(bytes)
    }

    fun long(): Long {
        require(buffer.remaining() >= 8) { "连接信息不完整" }
        return buffer.long
    }

    fun hex(size: Int): String {
        require(buffer.remaining() >= size) { "连接信息不完整" }
        return ByteArray(size).also { buffer.get(it) }.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun text(maxBytes: Int): String {
        require(buffer.remaining() >= 2) { "连接信息不完整" }
        val length = buffer.short.toInt() and 0xffff
        require(length in 1..maxBytes && length <= buffer.remaining()) { "连接信息长度无效" }
        val value = ByteArray(length).also { buffer.get(it) }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString()
    }

    fun end() = require(!buffer.hasRemaining()) { "连接信息包含额外内容" }
}
