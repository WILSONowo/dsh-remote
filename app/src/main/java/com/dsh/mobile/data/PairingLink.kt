package com.dsh.mobile.data

import java.net.URI
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Short-lived scan data stays in memory, never in saved connection profiles. */
@Serializable
data class PairingLink(
    val v: Int,
    val server: String,
    val hostId: String,
    val computerId: String,
    val name: String,
    val ticket: String,
    val expiresAt: Long,
) {
    fun profile(existing: HostProfile? = null): HostProfile {
        val next = HostProfile(
            id = existing?.id ?: UUID.randomUUID().toString(), remark = "", url = "",
            connectionMode = "relay", relayServerUrl = server.trimEnd('/'), relayHostId = hostId,
            computerId = computerId, deviceModel = name,
        )
        if (existing == null) return next
        require(existing.connectionUrl() == next.connectionUrl()) { "连接目标不一致，请重新扫码" }
        require(existing.computerId.isBlank() || existing.computerId == computerId) { "电脑身份已改变，请先删除旧记录再配对" }
        return existing.copy(computerId = computerId, deviceModel = name)
    }

    companion object {
        fun parse(raw: String, now: Long = System.currentTimeMillis()): PairingLink {
            require(raw.length <= 4096) { "连接信息过长" }
            val uri = URI(raw.trim())
            require(uri.scheme == "dshremote" && uri.host == "pair" && uri.userInfo == null &&
                uri.port == -1 && uri.path.isNullOrEmpty() && uri.query == null) { "请扫描电脑插件生成的配对二维码" }
            val payload = Json.decodeFromString<PairingLink>(String(Base64.getUrlDecoder().decode(uri.rawFragment), Charsets.UTF_8))
            require(payload.v == 1) { "连接信息版本不支持，请更新 App" }
            require(payload.expiresAt > now && payload.expiresAt - now <= 300_000) { "连接信息已过期或时间异常，请在电脑重新生成并检查两端时间" }
            require(Regex("[a-f0-9]{64}").matches(payload.ticket)) { "连接信息无效" }
            require(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}").matches(payload.computerId)) { "电脑身份无效" }
            require(payload.name.isNotBlank() && payload.name.length <= 64 && payload.name.none { it.code < 32 || it.code == 127 }) { "电脑名称无效" }
            payload.profile().connectionUrl() // Enforce strict HTTPS and opaque host-ID validation.
            return payload
        }
    }
}
