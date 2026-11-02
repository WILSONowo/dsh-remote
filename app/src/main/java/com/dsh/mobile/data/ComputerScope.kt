package com.dsh.mobile.data

import java.security.MessageDigest

/** Stable across renames/token rotation; never reuse cached data for a different endpoint. */
fun HostProfile?.computerScope(): String = cacheKey(
    this?.let { it.id + "\n" + runCatching { it.connectionUrl() }.getOrDefault(it.url) } ?: "disconnected",
)

internal fun cacheKey(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
