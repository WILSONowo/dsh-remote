package com.dsh.mobile.data

import java.net.InetAddress

/** Untrusted, short-lived discovery data. Never a saved identity or an access credential. */
data class LanPairingTarget(val session: String, val name: String, val urls: List<String>)

fun lanPairingTarget(attributes: Map<String, String>, port: Int, addresses: List<InetAddress>): LanPairingTarget? {
    val session = attributes["session"].orEmpty()
    val name = attributes["name"].orEmpty()
    if (attributes["v"] != "1" || !session.matches(Regex("[a-f0-9]{32}")) ||
        name.isBlank() || name.length > 64 || name.any { it.code < 32 || it.code == 127 } ||
        port !in 1..65535 || attributes["tls"] !in listOf("0", "1")) return null
    val scheme = if (attributes["tls"] == "1") "https" else "http"
    // Older Android NSD returns only one address. Include bounded numeric alternatives, never DNS names.
    val alternatives = attributes["ipv4"].orEmpty().takeIf { it.length <= 128 }.orEmpty().split(',').take(8).mapNotNull { literal ->
        val parts = literal.split('.')
        if (parts.size != 4 || parts.any { !it.matches(Regex("[0-9]{1,3}")) || it.toInt() !in 0..255 }) null
        else InetAddress.getByAddress(parts.map { it.toInt().toByte() }.toByteArray())
    }
    val urls = (addresses + alternatives).filter { !it.isLoopbackAddress && !it.isAnyLocalAddress && !it.isMulticastAddress }
        .sortedBy { if (it.address.size == 4) 0 else 1 }
        .mapNotNull { address -> address.hostAddress?.let { host ->
            val literal = if (host.contains(':')) "[${host.replace("%", "%25")}]" else host
            "$scheme://$literal:$port"
        } }.distinct().take(8)
    return urls.takeIf { it.isNotEmpty() }?.let { LanPairingTarget(session, name, it) }
}

fun mergeLanTargets(previous: LanPairingTarget?, next: LanPairingTarget): LanPairingTarget =
    if (previous?.session == next.session) next.copy(urls = (next.urls + previous.urls).distinct().take(8)) else next
