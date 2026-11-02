package com.dsh.mobile.data

import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LanPairingProbeTest {
    @Test fun checksSessionAndFallsBackWithoutCredentialsOrRedirects() = runTest {
        val server = ServerSocket(0, 8, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        val session = "a".repeat(32)
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val worker = Thread {
            while (!server.isClosed) {
                try { server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val reader = socket.getInputStream().bufferedReader()
                    val lines = mutableListOf<String>()
                    while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; lines.add(line) }
                    requests.add(lines.joinToString("\n"))
                    val request = lines.first()
                    val redirect = request.contains("/redirect/")
                    val body = when {
                        request.contains("/oversize/") -> "x".repeat(4097)
                        request.contains("/stale/") -> """{"ok":true,"v":1,"session":"${"b".repeat(32)}","name":"Synthetic computer"}"""
                        else -> """{"ok":true,"v":1,"session":"$session","name":"Synthetic computer"}"""
                    }.toByteArray()
                    val headers = if (redirect) "HTTP/1.1 302 Found\r\nLocation: /should-not-follow?session=$session\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        else "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write(headers.toByteArray())
                    if (!redirect) socket.getOutputStream().write(body)
                } } catch (_: java.io.IOException) { if (server.isClosed) break }
            }
        }.apply { isDaemon = true; start() }
        try {
            val base = "http://127.0.0.1:${server.localPort}"
            val target = LanPairingTarget(session, "Synthetic computer", listOf("$base/redirect", "$base/oversize", "$base/stale", "$base/valid"))
            assertEquals("$base/valid", probeLanPairingTarget(target))
            assertEquals(4, requests.size)
            assertTrue(requests.all { it.startsWith("GET ") && it.contains("?session=$session HTTP/1.1") })
            assertFalse(requests.any { it.contains("Authorization", ignoreCase = true) || it.contains("should-not-follow") })
            assertNull(probeLanPairingTarget(target.copy(name = "Other computer", urls = listOf("$base/valid"))))
        } finally { server.close(); worker.join(3000) }
    }
}
