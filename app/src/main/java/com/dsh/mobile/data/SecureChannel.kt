package com.dsh.mobile.data

import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * End-to-end channel to the computer (docs/e2e-secure-channel.md): HTTP/1.1 inside TLS 1.3 whose server key is
 * pinned, inside binary WebSocket frames to either the computer's port or the relay. The relay sees only bytes.
 * Pure JVM + OkHttp so it can be tested without Android.
 */
object SecureChannel {
    const val PROTOCOL = "dsh-e2e.1"
    /** Below the 32 KiB frame limit of the relay and the plugin. */
    const val FRAME_BYTES = 16 * 1024
    internal const val MAX_BUFFERED_BYTES = 32L * 1024 * 1024
    internal const val MAX_QUEUED_BYTES = 1L * 1024 * 1024
    private val PIN = Regex("[a-f0-9]{64}")

    fun isPin(value: String?): Boolean = value != null && PIN.matches(value)

    /** SHA-256 of the SubjectPublicKeyInfo DER, matching the plugin's spkiPin. */
    fun pinOf(certificate: X509Certificate): String = sha256Hex(certificate.publicKey.encoded)

    /** Human-comparable form shown on both screens, e.g. 3FA9-0C1D-77B2. */
    fun safetyCode(pin: String): String = pin.take(12).uppercase().chunked(4).joinToString("-")

    /** Stable placeholder host for one transport; never resolved by DNS. */
    fun syntheticHost(transportUrl: String): String = sha256Hex(transportUrl.toByteArray(Charsets.UTF_8)).take(16) + ".e2e.dsh.invalid"

    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /**
     * Inner client: every connection is a fresh WebSocket from [outer] to [transportUrl] carrying TLS that must
     * present [expectedPin], or, when none is recorded yet (direct trust-on-first-use), the first pin seen in [observed].
     */
    fun client(
        outer: OkHttpClient,
        transportUrl: String,
        expectedPin: String?,
        observed: AtomicReference<String?>,
        configure: OkHttpClient.Builder.() -> Unit = {},
    ): OkHttpClient {
        val host = syntheticHost(transportUrl)
        val trust = PinnedTrustManager(expectedPin, observed)
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), SecureRandom()) }
        val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).tlsVersions(TlsVersion.TLS_1_3).build()
        return OkHttpClient.Builder()
            .socketFactory(TransportSocketFactory(outer, transportUrl))
            .dns(SyntheticDns(host))
            .proxy(Proxy.NO_PROXY)
            .sslSocketFactory(tls.socketFactory, trust)
            .hostnameVerifier { name, _ -> name == host }
            .connectionSpecs(listOf(spec))
            .protocols(listOf(Protocol.HTTP_1_1))
            // Evict idle sessions before the relay's stream idle timeout closes them underneath us.
            .connectionPool(ConnectionPool(5, 60, TimeUnit.SECONDS))
            .retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false)
            .apply(configure)
            .build()
    }
}

/** Remembers the first pin seen per saved computer until it is persisted (direct trust-on-first-use only). */
object ServerPinRegistry {
    private val pins = ConcurrentHashMap<String, AtomicReference<String?>>()
    fun slot(profileId: String): AtomicReference<String?> = pins.getOrPut(profileId) { AtomicReference(null) }
    fun observed(profileId: String): String? = pins[profileId]?.get()
    fun clear(profileId: String) { pins.remove(profileId) }
}

/** The recorded computer key does not match: never retried, never silently replaced. */
class ServerIdentityChangedException(val expected: String, val actual: String) : CertificateException(
    "电脑身份已改变：安全码 ${SecureChannel.safetyCode(actual)} 与记录的 ${SecureChannel.safetyCode(expected)} 不一致。" +
        "如果电脑重装过插件，请删除这条记录后重新配对；否则可能有人在拦截连接。"
)

/** Outer transport refused (HTTP status on the WebSocket upgrade), e.g. outdated plugin/backend or computer offline. */
class SecureTransportException(val status: Int, message: String) : IOException(message)

class PinnedTrustManager(
    private val expectedPin: String?,
    private val observed: AtomicReference<String?> = AtomicReference(null),
) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("电脑未提供证书")
        val actual = SecureChannel.pinOf(leaf)
        val expected = expectedPin?.takeIf { it.isNotBlank() }
            ?: observed.get()
            ?: if (observed.compareAndSet(null, actual)) return else observed.get()!!
        if (!MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())) throw ServerIdentityChangedException(expected, actual)
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        throw CertificateException("client certificates are not used")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

internal class SyntheticDns(private val host: String) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        if (hostname == host) listOf(InetAddress.getByAddress(hostname, byteArrayOf(127, 0, 0, 1)))
        else throw UnknownHostException(hostname)
}

internal class TransportSocketFactory(private val outer: OkHttpClient, private val url: String) : SocketFactory() {
    override fun createSocket(): Socket = WebSocketTransportSocket(outer, url)
    override fun createSocket(host: String?, port: Int): Socket = createSocket().apply { connect(null, 0) }
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = createSocket(host, port)
    override fun createSocket(host: InetAddress?, port: Int): Socket = createSocket().apply { connect(null, 0) }
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = createSocket(address, port)
}

/**
 * A java.net.Socket whose bytes travel as binary WebSocket frames. TLS is layered on top by OkHttp, so this
 * class only ever sees ciphertext. Read timeouts follow soTimeout (OkHttp relies on them for health checks).
 */
internal class WebSocketTransportSocket(private val outer: OkHttpClient, private val url: String) : Socket() {
    private val incoming = LinkedBlockingQueue<ByteArray>()
    private val buffered = AtomicLong()
    private val opened = CountDownLatch(1)
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var open = false
    @Volatile private var closed = false
    @Volatile private var ended = false
    @Volatile private var failure: IOException? = null
    @Volatile private var readTimeout = 0
    @Volatile private var inputShutdown = false
    @Volatile private var outputShutdown = false

    private fun finish(error: IOException?) {
        if (error != null && failure == null) failure = error
        if (!ended) { ended = true; incoming.put(EOF) }
        opened.countDown()
    }

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        if (closed) throw SocketException("Socket is closed")
        val request = Request.Builder().url(url).header("Sec-WebSocket-Protocol", SecureChannel.PROTOCOL).build()
        val socket = outer.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (response.header("Sec-WebSocket-Protocol") != SecureChannel.PROTOCOL) {
                    finish(SecureTransportException(response.code, "对端不支持端到端加密通道，请更新电脑插件或中转服务器"))
                    webSocket.cancel(); return
                }
                open = true; opened.countDown()
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (buffered.addAndGet(data.size.toLong()) > SecureChannel.MAX_BUFFERED_BYTES) {
                    finish(IOException("secure channel receive buffer exceeded")); webSocket.cancel(); return
                }
                incoming.put(data)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                finish(IOException("unexpected text frame on secure channel")); webSocket.cancel()
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null); finish(null)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish(null)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                finish(if (!open && response != null) refusal(response.code) else t as? IOException ?: IOException(t))
        })
        webSocket = socket
        val wait = if (timeout > 0) timeout.toLong() else outer.connectTimeoutMillis.toLong().takeIf { it > 0 } ?: 15_000L
        if (!opened.await(wait, TimeUnit.MILLISECONDS)) { socket.cancel(); throw SocketTimeoutException("connect timed out") }
        if (!open) { socket.cancel(); throw failure ?: SocketException("secure channel closed") }
    }

    private val input = object : InputStream() {
        private var current: ByteArray? = null
        private var position = 0
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xff
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (inputShutdown) return -1
            var chunk = current
            if (chunk == null || position >= chunk.size) {
                val timeout = readTimeout
                chunk = if (timeout > 0) incoming.poll(timeout.toLong(), TimeUnit.MILLISECONDS)
                    ?: throw SocketTimeoutException("Read timed out") else incoming.take()
                if (chunk === EOF) {
                    incoming.put(EOF) // keep EOF sticky for later reads
                    failure?.takeIf { !closed }?.let { throw it }
                    return -1
                }
                current = chunk; position = 0
            }
            val count = minOf(len, chunk!!.size - position)
            System.arraycopy(chunk, position, b, off, count)
            position += count
            buffered.addAndGet(-count.toLong())
            return count
        }
        override fun available(): Int = current?.let { it.size - position } ?: 0
        override fun close() = this@WebSocketTransportSocket.close()
    }

    private val output = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) {
            val socket = webSocket
            if (closed || outputShutdown || ended || socket == null) throw SocketException("secure channel closed")
            var offset = off
            val end = off + len
            while (offset < end) {
                // OkHttp queues sends without limit; wait so a slow link cannot grow memory unbounded.
                while (socket.queueSize() > SecureChannel.MAX_QUEUED_BYTES) {
                    if (closed || ended) throw SocketException("secure channel closed")
                    Thread.sleep(2)
                }
                val size = minOf(SecureChannel.FRAME_BYTES, end - offset)
                if (!socket.send(b.toByteString(offset, size))) throw SocketException("secure channel closed")
                offset += size
            }
        }
        override fun close() = this@WebSocketTransportSocket.close()
    }

    override fun getInputStream(): InputStream { if (closed) throw SocketException("Socket is closed"); return input }
    override fun getOutputStream(): OutputStream { if (closed) throw SocketException("Socket is closed"); return output }
    override fun close() {
        if (closed) return
        closed = true
        webSocket?.close(1000, null)
        finish(null)
    }
    override fun shutdownInput() { inputShutdown = true }
    override fun shutdownOutput() { outputShutdown = true }
    override fun isConnected(): Boolean = open
    override fun isClosed(): Boolean = closed
    override fun isInputShutdown(): Boolean = inputShutdown || ended
    override fun isOutputShutdown(): Boolean = outputShutdown || ended
    override fun setSoTimeout(timeout: Int) { readTimeout = timeout }
    override fun getSoTimeout(): Int = readTimeout
    override fun getInetAddress(): InetAddress = LOOPBACK
    override fun getLocalAddress(): InetAddress = LOOPBACK
    override fun getPort(): Int = 443
    override fun getLocalPort(): Int = 0
    override fun getRemoteSocketAddress(): SocketAddress = InetSocketAddress(LOOPBACK, 443)
    override fun getLocalSocketAddress(): SocketAddress = InetSocketAddress(LOOPBACK, 0)
    // Socket options have no meaning for a WebSocket-backed stream; accept and ignore them.
    override fun setTcpNoDelay(on: Boolean) {}
    override fun getTcpNoDelay(): Boolean = true
    override fun setKeepAlive(on: Boolean) {}
    override fun getKeepAlive(): Boolean = false
    override fun setSoLinger(on: Boolean, linger: Int) {}
    override fun getSoLinger(): Int = -1
    override fun setSendBufferSize(size: Int) {}
    override fun getSendBufferSize(): Int = SecureChannel.FRAME_BYTES
    override fun setReceiveBufferSize(size: Int) {}
    override fun getReceiveBufferSize(): Int = SecureChannel.FRAME_BYTES
    override fun toString(): String = "SecureChannelSocket[$url]"

    private companion object {
        val EOF = ByteArray(0)
        val LOOPBACK: InetAddress = InetAddress.getByAddress("localhost", byteArrayOf(127, 0, 0, 1))
        fun refusal(status: Int) = SecureTransportException(status, when (status) {
            404 -> "电脑插件或中转服务器版本过旧，不支持端到端加密（需要插件 4.0、后端 3.0 或以上）"
            503 -> "电脑当前不在线，或已暂停远程连接"
            429 -> "连接过于频繁或已达到并发上限，请稍后重试"
            401, 403 -> "安全通道被拒绝，请检查电脑的允许连接设置"
            else -> "安全通道建立失败（HTTP $status）"
        })
    }
}
