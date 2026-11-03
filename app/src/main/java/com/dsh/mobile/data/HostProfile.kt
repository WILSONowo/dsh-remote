package com.dsh.mobile.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ConnectionErrorCode {
    DNS_UNREACHABLE,   // 域名解析失败
    PORT_UNREACHABLE,  // TCP 拒绝/连接超时
    TLS_CERT_FAILED,   // 证书校验失败
    AUTH_FAILED,       // HTTP 401/403（前置网关）
    VERSION_MISMATCH,  // host.describe 版本低于下界
    PROXY_FAILED,      // 配置了代理但代理不可达
    PROTOCOL_ERROR,    // 非 200 / 解析失败 / RPC ok=false / 读超时
    UNKNOWN,
}

@Serializable
data class ProxyConfig(
    val type: String = "none", // "none" | "http" | "socks5"
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "", // TODO-S3: 迁 Keystore，暂明文存 DataStore
)

@Serializable
data class HostProfile(
    val id: String,
    val remark: String,
    val url: String,
    val trustSelfSigned: Boolean = false,
    val caCertUri: String? = null,
    val proxy: ProxyConfig? = null,
    val autoConnect: Boolean = false,
    val lastUsedAt: Long = 0,
    val lastErrorCode: String? = null,
    val paired: Boolean = false,
    /** 独立设备 token（配对码验证或 claim 下发；密文落盘，使用 Bearer 头） */
    val channelToken: String = "",
    /** 主机机型（device/info 抓取；设备记录列表展示用，不再展示公网 IP） */
    val deviceModel: String = "",
    /** Legacy field retained only to read old saved profiles. New pairing does not collect MACs. */
    val deviceMac: String = "",
    /** Random installation identity, separate from the editable display name. */
    val computerId: String = "",
    val connectionMode: String = "direct",
    val relayServerUrl: String = "",
    val relayHostId: String = "",
    /** Last verified host.describe version for this computer, never the plugin version. */
    val lastHostVersion: String? = null,
)

/** One endpoint for RPC, pairing, uploads and WS/SSE; no separately concatenated relay paths. */
fun HostProfile.connectionUrl(): String {
    if (connectionMode == "direct") {
        val normalized = normalizeBaseUrl(url)
        val uri = java.net.URI(normalized)
        require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "请输入有效的 DeepSeek Harness 地址" }
        return normalized
    }
    require(connectionMode == "relay") { "未知连接模式" }
    val uri = java.net.URI(relayServerUrl.trim())
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path in listOf("", "/")) { "中转服务器请填写 HTTPS 地址，不带路径或账号" }
    require(Regex("[a-f0-9]{32}").matches(relayHostId) && Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}").matches(computerId)) { "旧版或无效的中转记录，请删除后扫描电脑生成的新二维码" }
    require(!trustSelfSigned) { "中转连接必须校验证书，可导入受信 CA" }
    return relayServerUrl.trim().trimEnd('/') + "/h/" + relayHostId
}

fun resetPairingIfEndpointChanged(previous: HostProfile?, next: HostProfile): HostProfile =
    if (previous != null && runCatching { previous.connectionUrl() }.getOrNull() != runCatching { next.connectionUrl() }.getOrNull())
        next.copy(paired = false, channelToken = "", deviceModel = "", deviceMac = "", computerId = "", lastHostVersion = null)
    else next

/**
 * 规范化主机地址（连接、诊断、保存共享同一语义）：
 * trim → 无协议补 http:// → 去尾斜杠。
 */
fun normalizeBaseUrl(raw: String): String {
    var u = raw.trim()
    if (u.isEmpty()) return u
    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
    u = u.trimEnd('/')
    return u
}

object ProfileCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(profiles: List<HostProfile>): String =
        json.encodeToString(ListSerializer(HostProfile.serializer()), profiles)

    fun decode(text: String): List<HostProfile> = runCatching {
        json.decodeFromString(ListSerializer(HostProfile.serializer()), text)
    }.getOrDefault(emptyList())
}

private fun <T> ListSerializer(element: kotlinx.serialization.KSerializer<T>) =
    kotlinx.serialization.builtins.ListSerializer(element)
