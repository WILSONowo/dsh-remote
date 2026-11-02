package com.dsh.mobile.data

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Route
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

fun buildProxy(cfg: ProxyConfig?): Proxy? {
    if (cfg == null || cfg.type == "none" || cfg.host.isBlank() || cfg.port <= 0) return null
    val type = when (cfg.type) {
        "socks5" -> Proxy.Type.SOCKS
        else -> Proxy.Type.HTTP
    }
    return Proxy(type, InetSocketAddress(cfg.host, cfg.port))
}

fun trustAllSslContext(): SSLContext {
    val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
    return ctx
}

/** PEM/DER → X509Certificate；无法解析返回 null */
fun parseCaCertificate(bytes: ByteArray): X509Certificate? = runCatching {
    val factory = CertificateFactory.getInstance("X.509")
    val cert = factory.generateCertificate(ByteArrayInputStream(bytes))
    cert as? X509Certificate
}.getOrNull()

object OkHttpClientFactory {

    private data class ClientPair(val profile: HostProfile, val unary: OkHttpClient, val stream: OkHttpClient)

    private val cache = HashMap<String, ClientPair>()

    /**
     * 通道 token 注册表：拦截器在请求时动态读取（而非构建时固化），配对完成后
     * 更新注册表即可让已缓存的 client（含 WS 流）立即带上 Bearer 头，无需重建连接。
     */
    object ChannelTokenRegistry {
        private val tokens = HashMap<String, String>()

        @Synchronized
        fun set(profileId: String, token: String?) {
            if (token.isNullOrBlank()) tokens.remove(profileId) else tokens[profileId] = token
        }

        @Synchronized
        fun get(profileId: String): String? = tokens[profileId]
    }

    @Synchronized
    fun build(profile: HostProfile): Pair<OkHttpClient, OkHttpClient> {
        // Cached callers may still hold the pre-pairing profile. Do not replace
        // a freshly issued live credential with that stale snapshot.
        if (!cache.containsKey(profile.id))
            ChannelTokenRegistry.set(profile.id, profile.channelToken.ifBlank { null })
        else if (cache[profile.id]?.profile?.connectionUrl() != profile.connectionUrl())
            ChannelTokenRegistry.set(profile.id, null)
        cache[profile.id]?.takeIf { it.profile.copy(channelToken = profile.channelToken, paired = profile.paired) == profile }?.let { return it.unary to it.stream }
        val pair = ClientPair(
            profile = profile,
            unary = newClient(profile, stream = false),
            stream = newClient(profile, stream = true),
        )
        cache[profile.id] = pair
        return pair.unary to pair.stream
    }

    @Synchronized
    fun release(profileId: String) {
        cache.remove(profileId)
    }

    private fun newClient(profile: HostProfile, stream: Boolean): OkHttpClient {
        val endpoint = java.net.URI(profile.connectionUrl())
        val builder = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false)
        if (stream) {
            builder.connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
        } else {
            builder.connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
        }
        val ca = profile.caCertUri
        if (profile.trustSelfSigned && profile.connectionMode == "direct") {
            val ctx = trustAllSslContext()
            builder.sslSocketFactory(ctx.socketFactory, trustAllX509())
            builder.hostnameVerifier { _, _ -> true }
        } else if (ca != null) {
            val bytes = runCatching { java.io.File(ca).readBytes() }.getOrNull()
            val merged = bytes?.let { mergedCaContext(it) }
            if (merged != null) builder.sslSocketFactory(merged.first.socketFactory, merged.second)
        }
        buildProxy(profile.proxy)?.let { builder.proxy(it) }
        profile.proxy?.takeIf { it.username.isNotBlank() }?.let { p ->
            builder.proxyAuthenticator(proxyAuthenticator(p.username, p.password))
        }
        // 远程通道 token：请求时从注册表动态取（配对下发后热生效；无 token 时不加头，
        // 兼容未启用鉴权的旧 PC 端）
        builder.addInterceptor { chain ->
            val target = chain.request().url
            val sameHost = target.host == endpoint.host && target.port == (if (endpoint.port >= 0) endpoint.port else if (endpoint.scheme == "https") 443 else 80)
            val sameTransport = target.scheme == endpoint.scheme
            val samePath = target.encodedPath.startsWith(endpoint.rawPath.trimEnd('/') + "/api/")
            val token = if (sameHost && sameTransport && samePath) ChannelTokenRegistry.get(profile.id) else null
            val req = if (token.isNullOrBlank()) chain.request()
            else chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            chain.proceed(req)
        }
        return builder.build()
    }

    private fun trustAllX509(): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    /** 系统链 + 导入 CA 合成；CA 无法解析返回 null（回退系统默认） */
    internal fun mergedCaContext(caBytes: ByteArray): Pair<SSLContext, X509TrustManager>? = runCatching {
        val ca = parseCaCertificate(caBytes) ?: return null
        val imported = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("imported-ca", ca)
        }
        val importedTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(imported) }.trustManagers.filterIsInstance<X509TrustManager>().first()
        val systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }.trustManagers.filterIsInstance<X509TrustManager>().first()
        val composite = CompositeTrustManager(systemTmf, importedTmf)
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(composite), SecureRandom())
        ctx to composite
    }.getOrNull()

    private class CompositeTrustManager(
        private val primary: X509TrustManager,
        private val extra: X509TrustManager,
    ) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            primary.checkClientTrusted(chain, authType)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = try {
            primary.checkServerTrusted(chain, authType)
        } catch (e: java.security.cert.CertificateException) {
            extra.checkServerTrusted(chain, authType)
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private fun proxyAuthenticator(username: String, password: String) =
        Authenticator { _: Route?, response ->
            if (response.request.header("Proxy-Authorization") != null) {
                null
            } else {
                response.request.newBuilder()
                    .header("Proxy-Authorization", Credentials.basic(username, password))
                    .build()
            }
        }
}
