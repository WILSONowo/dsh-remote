package com.dsh.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * 设备记录里每台电脑的在线状态，不建立加密通道、不发送任何凭证：
 * - 直连：对电脑地址做一次 TCP 连接，连得上即在线，顺带得到往返延迟；
 * - 中转：问中转服务器 `/relay/v2/presence/<hostId>` 这台电脑的插件是否连着（旧中转没有这个接口 → 未知）。
 * 思路参考 dshgo 的多电脑并发探测。
 */
enum class Presence { ONLINE, OFFLINE, UNKNOWN }

data class ProbeResult(val presence: Presence, val latencyMs: Long? = null)

object HostProbe {

    const val TIMEOUT_MS = 2_500

    private val json = Json { ignoreUnknownKeys = true }

    /** 解析中转的在线状态响应；404 是不支持此接口的旧中转 */
    fun parsePresence(code: Int, body: String?): Presence = when {
        code == 404 -> Presence.UNKNOWN
        code != 200 || body == null -> Presence.UNKNOWN
        else -> when (runCatching { (json.parseToJsonElement(body) as? JsonObject)?.get("online")?.jsonPrimitive?.booleanOrNull }.getOrNull()) {
            true -> Presence.ONLINE
            false -> Presence.OFFLINE
            null -> Presence.UNKNOWN
        }
    }

    fun label(result: ProbeResult?): String = when (result?.presence) {
        null -> "检测中…"
        Presence.ONLINE -> result.latencyMs?.let { "在线 · $it ms" } ?: "在线"
        Presence.OFFLINE -> "离线"
        Presence.UNKNOWN -> "状态未知"
    }

    /** 外层连接用的客户端（沿用记录里的代理和自定义 CA），超时缩短到探测用的长度 */
    private fun clientFor(profile: HostProfile): OkHttpClient =
        OkHttpClientFactory.outerClient(profile).newBuilder()
            .connectTimeout(TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MS * 2L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .pingInterval(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

    suspend fun probe(profile: HostProfile): ProbeResult = withContext(Dispatchers.IO) {
        val base = runCatching { profile.reachabilityUrl() }.getOrNull() ?: return@withContext ProbeResult(Presence.UNKNOWN)
        if (profile.connectionMode == "direct" && profile.proxy != null) {
            // 走代理的直连：直接 TCP 不代表经代理能到，改发一次 HTTP 请求，有任何响应即在线
            val started = System.nanoTime()
            return@withContext try {
                clientFor(profile).newCall(Request.Builder().url(base).head().build()).execute().use { }
                ProbeResult(Presence.ONLINE, (System.nanoTime() - started) / 1_000_000)
            } catch (_: Exception) {
                ProbeResult(Presence.OFFLINE)
            }
        }
        if (profile.connectionMode == "direct") {
            val uri = URI(base)
            val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
            val started = System.nanoTime()
            try {
                Socket().use { it.connect(InetSocketAddress(uri.host, port), TIMEOUT_MS) }
                ProbeResult(Presence.ONLINE, (System.nanoTime() - started) / 1_000_000)
            } catch (_: Exception) {
                ProbeResult(Presence.OFFLINE)
            }
        } else {
            val started = System.nanoTime()
            try {
                clientFor(profile).newCall(Request.Builder().url("$base/relay/v2/presence/${profile.relayHostId}").get().build()).execute().use { resp ->
                    val presence = parsePresence(resp.code, resp.body?.string())
                    ProbeResult(presence, if (presence == Presence.ONLINE) (System.nanoTime() - started) / 1_000_000 else null)
                }
            } catch (_: Exception) {
                // 连不上中转：手机没网或中转故障，说不清电脑本身是否在线
                ProbeResult(Presence.UNKNOWN)
            }
        }
    }
}
