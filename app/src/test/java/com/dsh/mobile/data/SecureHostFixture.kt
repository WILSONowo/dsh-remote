package com.dsh.mobile.data

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** Gives the synthetic HTTP responders a loopback TLS-over-WebSocket endpoint. */
class SecureHostFixture : AutoCloseable {
    companion object {
        private val loopback = InetAddress.getByName("127.0.0.1")
        // Saved test profiles can outlive a server. Reuse the synthetic computer's identity.
        private val identity = HandshakeCertificates.Builder()
            .heldCertificate(HeldCertificate.Builder().commonName("fixture").build()).build()
    }

    val requests: ServerSocket = identity.sslContext().serverSocketFactory.createServerSocket(0, 50, loopback)
    private val endpoint = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != "/api/remote-access/secure" ||
                    request.getHeader("Sec-WebSocket-Protocol") != SecureChannel.PROTOCOL
                ) return MockResponse().setResponseCode(404)
                return MockResponse().withWebSocketUpgrade(Tunnel())
                    .setHeader("Sec-WebSocket-Protocol", SecureChannel.PROTOCOL)
            }
        }
        start(loopback, 0)
    }

    val url: String get() = "http://127.0.0.1:${endpoint.port}"

    private inner class Tunnel : WebSocketListener() {
        private lateinit var socket: Socket

        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket = Socket(loopback, requests.localPort)
            thread(isDaemon = true, name = "fixture-tls") {
                try {
                    val buffer = ByteArray(8192)
                    val input = socket.getInputStream()
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0 || !webSocket.send(buffer.toByteString(0, count))) break
                    }
                } catch (_: IOException) {
                    // Either peer may close the connection first.
                } finally {
                    socket.close()
                    webSocket.close(1000, null)
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            bytes.write(socket.getOutputStream())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            socket.close()
            webSocket.close(1000, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (::socket.isInitialized) socket.close()
        }
    }

    override fun close() {
        requests.close()
        endpoint.shutdown()
    }
}
