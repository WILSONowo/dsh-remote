package com.dsh.mobile.data

import java.security.MessageDigest

/** Stable across renames/token rotation; never reuse cached data for a different endpoint. */
fun HostProfile?.computerScope(): String = cacheKey(
    this?.let { it.id + "\n" + runCatching { it.connectionUrl() }.getOrDefault(it.url) } ?: "disconnected",
)

internal fun cacheKey(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    val hex = "0123456789abcdef"
    return buildString(bytes.size * 2) {
        for (byte in bytes) {
            val bits = byte.toInt() and 0xff
            append(hex[bits ushr 4])
            append(hex[bits and 15])
        }
    }
}
